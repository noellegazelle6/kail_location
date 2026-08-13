// injectx64.cpp
//
// x86_64 variant of the ptrace injector. The 64-bit (arm64-v8a) injector lives
// in inject64.cpp; this file is the same reconstructable injector adapted for
// the x86_64 calling convention (System V): arguments go in rdi/rsi/rdx/rcx/r8/r9,
// the return value comes back in rax, and a remote call is driven by pointing
// rip at the target function with a zero return address on the tracee's stack so
// the eventual `ret` faults at address 0 (SIGSEGV), which we intercept to read
// rax before restoring the tracee's registers.
//
// Everything else (maps parsing, ASLR-slide symbol resolution, POKE/PEEK string
// pumping, dlopen -> dlsym(doRun) -> doRun(JavaVM*, arg) flow) is ABI-neutral
// and mirrors inject64.cpp.
//
// Build: this is compiled as an executable named "kail_inject" and packaged into
// the APK as libkail_inject.so (see app/src/main/cpp/CMakeLists.txt).

#include <cerrno>
#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>

#include <dirent.h>
#include <dlfcn.h>
#include <elf.h>
#include <getopt.h>
#include <time.h>
#include <unistd.h>

#include <sys/ptrace.h>
#include <sys/user.h>
#include <sys/uio.h>
#include <sys/wait.h>

#include <android/log.h>

#include "kail_log.h"

// ---------------------------------------------------------------------------
// Globals
// ---------------------------------------------------------------------------
static const char *kInjectorLogTag = "LINJECT/Injector";
static int   gVerboseLoggingEnabled = 1;          // dword_5278
static int   gTargetPid             = 0;          // dword_5288

static uint64_t gRemoteDlopen  = 0;               // qword_5290
static uint64_t gRemoteDlerror = 0;               // qword_5298
static uint64_t gRemoteDlsym   = 0;
static uint64_t gRemoteCalloc  = 0;               // qword_52A0
static uint64_t gRemoteFree    = 0;               // qword_52A8

static constexpr uint64_t kDoRunSuccess = 0x4b4c1000;
static constexpr uint64_t kDoRunAlreadyLoaded = 0x4b4c1001;

// Callback invoked when a recoverable ptrace error (ESRCH) is hit; used to
// re-stop the remote process before retrying.  (off_52B0)
static void (*gRemoteStopCallback)() = nullptr;

#define LOGV(...) \
  do { if (gVerboseLoggingEnabled) \
         KLOGD(kInjectorLogTag, __VA_ARGS__); } while (0)

// Forward declarations
static void     waitForRemoteStop();
static long     ptraceWithRetry(const char *label, int request, uintptr_t addr, uintptr_t data);
static uint64_t findLibraryBaseAddress(const char *libraryPath, int pid);

// ---------------------------------------------------------------------------
// findPidByProcessName  (sub_19D0)
//   Scan /proc, read each /proc/<pid>/cmdline and match the requested name.
// ---------------------------------------------------------------------------
static int findPidByProcessName(const char *processName) {
  if (!processName)
    return -1;

  DIR *proc = opendir("/proc");
  if (!proc)
    return -1;

  int foundPid = -1;
  for (struct dirent *entry = readdir(proc); entry; entry = readdir(proc)) {
    int pid = atoi(entry->d_name);
    if (pid == 0)
      continue;

    char cmdlinePath[32];
    snprintf(cmdlinePath, sizeof(cmdlinePath), "/proc/%d/cmdline", pid);

    FILE *fp = fopen(cmdlinePath, "r");
    if (!fp)
      continue;

    char cmdline[256] = {0};
    fgets(cmdline, sizeof(cmdline), fp);
    fclose(fp);

    if (strcmp(processName, cmdline) == 0) {
      foundPid = pid;
      break;
    }
  }

  closedir(proc);
  return foundPid;
}

// ---------------------------------------------------------------------------
// waitForRemoteStop  (sub_20E0)
//   Send SIGSTOP and spin on waitpid until the tracee is genuinely stopped,
//   forwarding any other delivered signal back via PTRACE_CONT.  Bails out
//   after ~128 ms if the expected stop never arrives.
// ---------------------------------------------------------------------------
static uint64_t nowMillis() {
  struct timespec tp;
  clock_gettime(CLOCK_MONOTONIC, &tp);
  return (uint64_t)((double)tp.tv_nsec / 1000000.0 + (double)(1000 * tp.tv_sec));
}

static void waitForRemoteStop() {
  kill(gTargetPid, SIGSTOP);

  uint64_t start = nowMillis();
  int status = 0;
  while (true) {
    if (nowMillis() - start > 200ULL)
      return;
    pid_t r = waitpid(gTargetPid, &status, WUNTRACED | WNOHANG);
    if (r == gTargetPid) {
      int stopSig = status & 0x7f;
      if (((status + 1) & 0x7e) != 0) {
        // Stopped by a signal other than the expected stop: forward it.
        ptraceWithRetry("remote_stop", PTRACE_CONT, 0, (uintptr_t)stopSig);
      } else if ((status & 0x7f) == 0 || stopSig == 0x7f) {
        return;
      }
    } else if (r == -1) {
      return;
    }
    usleep(1000);
  }
}

// ---------------------------------------------------------------------------
// ptraceWithRetry  (sub_2240)
//   ptrace() wrapper with logging and retry-on-ESRCH (up to 10 attempts).
// ---------------------------------------------------------------------------
static const char *ptraceRequestName(int request) {
  switch (request) {
    case PTRACE_PEEKTEXT:   return "PTRACE_PEEKTEXT";
    case PTRACE_PEEKDATA:   return "PTRACE_PEEKDATA";
    case PTRACE_POKETEXT:   return "PTRACE_POKETEXT";
    case PTRACE_POKEDATA:   return "PTRACE_POKEDATA";
    case PTRACE_CONT:       return "PTRACE_CONT";
    case PTRACE_KILL:       return "PTRACE_KILL";
    case PTRACE_SINGLESTEP: return "PTRACE_SINGLESTEP";
    case PTRACE_ATTACH:     return "PTRACE_ATTACH";
    case PTRACE_DETACH:     return "PTRACE_DETACH";
    case PTRACE_SYSCALL:    return "PTRACE_SYSCALL";
    case PTRACE_SETOPTIONS: return "PTRACE_SETOPTIONS";
    case PTRACE_GETEVENTMSG:return "PTRACE_GETEVENTMSG";
    case PTRACE_GETSIGINFO: return "PTRACE_GETSIGINFO";
    case PTRACE_SETSIGINFO: return "PTRACE_SETSIGINFO";
    case PTRACE_GETREGSET:  return "PTRACE_GETREGSET";
    case PTRACE_SETREGSET:  return "PTRACE_SETREGSET";
    default:                return nullptr;
  }
}

static long ptraceWithRetry(const char *label, int request, uintptr_t addr, uintptr_t data) {
  int *err = &errno;
  *err = 0;

  long result = -1;
  for (int attempts = 10; attempts > 0; --attempts) {
    result = ptrace((int)request, gTargetPid, (void *)addr, (void *)data);
    if (result != -1)
      break;

    int e = *err;
    // Only retry for a small set of transient errnos (ESRCH/EFAULT/EIO).
    if ((unsigned)e > 0x10 || ((1 << e) & 0x14008) == 0)
      break;

    char errBuf[16];
    const char *errName = "ESRCH";
    if (e != ESRCH) {
      snprintf(errBuf, sizeof(errBuf), "%d", e);
      errName = errBuf;
    }

    char reqBuf[16];
    const char *reqName = ptraceRequestName(request);
    if (!reqName) {
      snprintf(reqBuf, sizeof(reqBuf), "%d", request);
      reqName = reqBuf;
    }

    LOGV("ptrace [%s] error [%s] on request [%s]", label, errName, reqName);
    if (*err != ESRCH)
      break;

    LOGV("ptrace remote_stop/retry");
    if (gRemoteStopCallback)
      gRemoteStopCallback();
  }
  return result;
}

// ---------------------------------------------------------------------------
// findLibraryBaseAddress  (sub_24C4)
//   Parse /proc/<pid>/maps and return the first base address of a mapping
//   whose path contains libraryPath.  pid == -1 means "this process".
// ---------------------------------------------------------------------------
static uint64_t findLibraryBaseAddress(const char *libraryPath, int pid) {
  char mapsPath[256] = {0};
  int target = (pid == -1) ? gTargetPid : pid;
  snprintf(mapsPath, sizeof(mapsPath), "/proc/%d/maps", target);

  FILE *fp = fopen(mapsPath, "rt");
  if (!fp) {
    LOGV("fopen error");
    return 0;
  }

  // Extract basename for fallback matching (handles path resolution differences
  // on Android 16+ where loaded library paths in maps may differ from dlopen arg)
  const char *baseName = strrchr(libraryPath, '/');
  baseName = baseName ? baseName + 1 : libraryPath;

  uint64_t base = 0;
  char line[1024] = {0};
  while (fgets(line, sizeof(line), fp)) {
    if (strstr(line, libraryPath)) {
      base = strtoul(line, nullptr, 16);
      break;
    }
  }

  // Fallback: match by basename only (catches cases where maps shows a
  // resolved/symlinked path different from the dlopen argument)
  if (!base && baseName) {
    rewind(fp);
    while (fgets(line, sizeof(line), fp)) {
      if (strstr(line, baseName) && strchr(line, '/')) {
        base = strtoul(line, nullptr, 16);
        break;
      }
    }
  }

  fclose(fp);
  return base;
}

// ---------------------------------------------------------------------------
// resolveRemoteSymbolAddress  (sub_25E8)
//   Translate a local symbol address into the remote process by applying the
//   difference between the local and remote module base addresses.
// ---------------------------------------------------------------------------
static uint64_t resolveRemoteSymbolAddress(const char *libraryPath, uint64_t localAddr) {
  uint64_t localBase  = findLibraryBaseAddress(libraryPath, getpid());
  uint64_t remoteBase = findLibraryBaseAddress(libraryPath, gTargetPid);
  if (!localAddr || !localBase || !remoteBase) {
    KLOGE(kInjectorLogTag, "resolveRemoteSymbolAddress: localAddr=0x%llx localBase=0x%llx remoteBase=0x%llx path=%s",
          (unsigned long long)localAddr, (unsigned long long)localBase, (unsigned long long)remoteBase, libraryPath);
    return 0;
  }
  return localAddr - localBase + remoteBase;
}

// ---------------------------------------------------------------------------
// callRemoteFunction  (sub_26FC)
//   Backup the tracee's registers, load up to 8 arguments into x0..x7, set pc
//   to the target function and lr to 0 (so the return faults with SIGSEGV),
//   continue, wait for the fault, read x0 as the return value, then restore.
// ---------------------------------------------------------------------------
//
// The watchdog timeout is configurable so the long-running doRun() call (which
// does the InjectDex JNI bring-up, including DexClassLoader compile of the
// 33MB APK from system_server) does not get killed by the same 5s timeout
// that protects the cheap calloc/dlopen calls.
static uint64_t gCallTimeoutMs = 5000;

static uint64_t callRemoteFunction(uint64_t func, int argc, ...) {
  // x86_64 user_regs_struct: 27 * 8 = 216 bytes (PTRACE_GETREGSET/NT_PRSTATUS).
  // System V calling convention: rdi rsi rdx rcx r8 r9, return in rax, rip = pc.
  user_regs_struct regs, backup;

  waitForRemoteStop();

  struct iovec iov;
  iov.iov_base = &regs;
  iov.iov_len  = sizeof(regs);
  ptraceWithRetry("backup", PTRACE_GETREGSET, NT_PRSTATUS, (uintptr_t)&iov);
  backup = regs;

  int count = argc;
  if (count > 6)
    count = 6;   // SysV only passes up to 6 args in registers

  va_list ap;
  va_start(ap, argc);
  uint64_t a[6] = {0};
  for (int i = 0; i < count; ++i)
    a[i] = va_arg(ap, uint64_t);
  va_end(ap);

  regs.rdi = a[0];
  regs.rsi = a[1];
  regs.rdx = a[2];
  regs.rcx = a[3];
  regs.r8  = a[4];
  regs.r9  = a[5];

  // Poke a zero return address on the tracee's stack so the callee's final
  // `ret` pops 0 -> rip=0 -> SIGSEGV, which we catch to read rax. The original
  // word is restored together with the registers afterwards, so the tracee
  // resumes byte-for-byte where it stopped.
  uint64_t retAddr = (uint64_t)regs.rsp;
  long origRetWord = ptraceWithRetry("retaddr", PTRACE_PEEKTEXT, retAddr, 0);
  ptraceWithRetry("retaddr", PTRACE_POKETEXT, retAddr, 0);

  regs.rip = func;

  iov.iov_base = &regs;
  iov.iov_len  = sizeof(regs);
  ptraceWithRetry("call", PTRACE_SETREGSET, NT_PRSTATUS, (uintptr_t)&iov);
  ptraceWithRetry("call", PTRACE_CONT, 0, 0);
  KLOGD(kInjectorLogTag, "callRemoteFunction: continued tracee at func=0x%llx", (unsigned long long)func);

  // WATCHDOG: if the remote function hangs (typically because the linker
  // mutex is held by a sibling thread of the tracee), waitpid will block
  // forever and the tracee stays in ptrace_stop, which on system_server
  // freezes the entire device. Cap the total wait at gCallTimeoutMs ms,
  // then PTRACE_DETACH so the tracee resumes runnable.
  int status = 0;
  uint64_t startMs = nowMillis();
  bool timed_out = false;
  while (true) {
    if (nowMillis() - startMs > gCallTimeoutMs) {
      KLOGE(kInjectorLogTag, "callRemoteFunction watchdog tripped (%llums); "
                          "remote func 0x%llx never returned. Aborting.", (unsigned long long)gCallTimeoutMs, (unsigned long long)func);
      timed_out = true;
      break;
    }
    pid_t r = waitpid(gTargetPid, &status, WUNTRACED | WNOHANG);
    if (r == gTargetPid) {
      if ((status & 0xff7f) == 0xb7f) // stopped by SIGSEGV
        break;
      ptraceWithRetry("waitpid", PTRACE_CONT, 0, 0);
      continue;
    }
    if (r == -1) {
      // tracee gone (probably crashed); bail to detach path.
      timed_out = true;
      break;
    }
    usleep(2000);
  }

  if (timed_out) {
    // Restore registers we have and PTRACE_DETACH so the tracee resumes,
    // even if it does so in a weird mid-call state. Better than
    // permafrozen. Caller will get garbage in rax; treat as 0.
    ptraceWithRetry("retaddr", PTRACE_POKETEXT, retAddr, (uintptr_t)origRetWord);
    iov.iov_base = &backup;
    iov.iov_len  = sizeof(backup);
    ptraceWithRetry("restore", PTRACE_SETREGSET, NT_PRSTATUS, (uintptr_t)&iov);
    ptraceWithRetry("detach", PTRACE_DETACH, 0, 0);
    kill(gTargetPid, SIGCONT);
    return 0;
  }

  iov.iov_base = &regs;
  iov.iov_len  = sizeof(regs);
  ptraceWithRetry("return", PTRACE_GETREGSET, NT_PRSTATUS, (uintptr_t)&iov);

  ptraceWithRetry("retaddr", PTRACE_POKETEXT, retAddr, (uintptr_t)origRetWord);
  iov.iov_base = &backup;
  iov.iov_len  = sizeof(backup);
  ptraceWithRetry("restore", PTRACE_SETREGSET, NT_PRSTATUS, (uintptr_t)&iov);
  ptraceWithRetry("continue", PTRACE_CONT, 0, 0);

  return (uint64_t)regs.rax;
}

// ---------------------------------------------------------------------------
// writeRemoteString  (sub_2638)
//   Allocate a buffer in the tracee with the remote calloc, then POKE the
//   string bytes 8-at-a-time into that buffer.  Returns the remote address.
// ---------------------------------------------------------------------------
static uint64_t writeRemoteString(const char *value) {
  size_t len = strlen(value) + 1;
  uint64_t remote = callRemoteFunction(gRemoteCalloc, 2, (uint64_t)len, (uint64_t)1);

  waitForRemoteStop();

  size_t padded = len + (len & 7);
  char *local = (char *)malloc(padded);
  memset(local, 0, padded);
  memcpy(local, value, len);

  for (size_t off = 0; off < len; off += 8) {
    if (ptraceWithRetry("string", PTRACE_POKETEXT, (uintptr_t)(remote + off),
                        *(uint64_t *)&local[off]) == -1)
      break;
  }

  free(local);
  return remote;
}

static void readRemoteCString(uint64_t remote, char *out, size_t outSize, const char *label) {
  if (!remote || !out || outSize == 0)
    return;

  waitForRemoteStop();
  memset(out, 0, outSize);
  for (size_t off = 0; off < outSize - 1; off += 8) {
    long word = ptraceWithRetry(label, PTRACE_PEEKTEXT, (uintptr_t)(remote + off), 0);
    if (word == -1)
      break;
    *(uint64_t *)&out[off] = (uint64_t)word;
    if (memchr(&out[off], '\0', 8))
      break;
  }
  out[outSize - 1] = '\0';
}

// ---------------------------------------------------------------------------
// injectLibraryIntoProcess  (sub_1B88)
// ---------------------------------------------------------------------------
static int injectLibraryIntoProcess(int pid, const char *libraryPath, const char *entryArg) {
  gTargetPid = pid;
  gRemoteStopCallback = waitForRemoteStop;

  if (ptraceWithRetry("attach", PTRACE_ATTACH, 0, 0) == -1) {
    LOGV("Failed to attach to process %d", gTargetPid);
    return 1;
  }

  kill(gTargetPid, SIGSTOP);
  waitForRemoteStop();

  // Resolve which runtime tree the target uses (VMOS / Twoyi / VPhoneGaGa /
  // stock Android) by probing libc base addresses.
  const char *libcPath   = "/data/data/com.vmos.app/osimg/r/ot01/system/lib64/libc.so";
  const char *libdlPath;
  const char *linkerPath;

  uint64_t vmosApp     = findLibraryBaseAddress("/data/data/com.vmos.app/osimg/r/ot01/system/lib64/libc.so", gTargetPid);
  uint64_t vmosProOt01 = findLibraryBaseAddress("/data/data/com.vmos.pro/osimg/r/ot01/system/lib64/libc.so", gTargetPid);
  uint64_t vmosProOt02 = findLibraryBaseAddress("/data/data/com.vmos.pro/osimg/r/ot02/system/lib64/libc.so", gTargetPid);
  uint64_t twoyi       = findLibraryBaseAddress("/data/data/io.twoyi/rootfs/system/lib64/libc.so", gTargetPid);
  uint64_t vphone      = findLibraryBaseAddress("/data/data/com.vphonegaga.titan/files/androidfs_7.1.2/system/lib64/libc.so", gTargetPid);

  if (vmosApp) {
    linkerPath = "/data/data/com.vmos.app/osimg/r/ot01/system/bin/linker64";
    libdlPath  = "/data/data/com.vmos.app/osimg/r/ot01/system/lib64/libdl.so";
  } else if (vmosProOt01) {
    linkerPath = "/data/data/com.vmos.pro/osimg/r/ot01/system/bin/linker64";
    libcPath   = "/data/data/com.vmos.pro/osimg/r/ot01/system/lib64/libc.so";
    libdlPath  = "/data/data/com.vmos.pro/osimg/r/ot01/system/lib64/libdl.so";
  } else if (vmosProOt02) {
    linkerPath = "/data/data/com.vmos.pro/osimg/r/ot02/system/bin/linker64";
    libcPath   = "/data/data/com.vmos.pro/osimg/r/ot02/system/lib64/libc.so";
    libdlPath  = "/data/data/com.vmos.pro/osimg/r/ot02/system/lib64/libdl.so";
  } else if (twoyi) {
    linkerPath = "/data/data/io.twoyi/rootfs/system/bin/linker64";
    libcPath   = "/data/data/io.twoyi/rootfs/system/lib64/libc.so";
    libdlPath  = "/data/data/io.twoyi/rootfs/system/lib64/libdl.so";
  } else if (vphone) {
    linkerPath = "/data/data/com.vphonegaga.titan/files/androidfs_7.1.2/system/bin/linker64";
    libcPath   = "/data/data/com.vphonegaga.titan/files/androidfs_7.1.2/system/lib64/libc.so";
    libdlPath  = "/data/data/com.vphonegaga.titan/files/androidfs_7.1.2/system/lib64/libdl.so";
  } else {
    libcPath  = "/apex/com.android.runtime/lib64/bionic/libc.so";
    libdlPath = "/apex/com.android.runtime/lib64/bionic/libdl.so";
    if (access("/apex/com.android.runtime/lib64/bionic/libc.so", R_OK))
      libcPath = "/system/lib64/libc.so";
    linkerPath = "/apex/com.android.runtime/bin/linker64";
    if (access("/apex/com.android.runtime/lib64/bionic/libdl.so", R_OK))
      libdlPath = "/system/lib64/libdl.so";
    if (access("/apex/com.android.runtime/bin/linker64", R_OK))
      linkerPath = "/system/bin/linker64";
  }

  LOGV("libc:%s", libcPath);

  gRemoteCalloc = resolveRemoteSymbolAddress(libcPath, (uint64_t)&calloc);
  gRemoteFree   = resolveRemoteSymbolAddress(libcPath, (uint64_t)&free);

  // On newer Androids dlopen/dlerror live in libdl.so rather than the linker.
  if (findLibraryBaseAddress(libdlPath, -1) && findLibraryBaseAddress(libdlPath, gTargetPid)) {
    void *handle = dlopen(libdlPath, RTLD_NOW);
    gRemoteDlopen  = resolveRemoteSymbolAddress(libdlPath, (uint64_t)dlsym(handle, "dlopen"));
    gRemoteDlerror = resolveRemoteSymbolAddress(libdlPath, (uint64_t)dlsym(handle, "dlerror"));
    gRemoteDlsym   = resolveRemoteSymbolAddress(libdlPath, (uint64_t)dlsym(handle, "dlsym"));
    dlclose(handle);
  } else {
    gRemoteDlopen  = resolveRemoteSymbolAddress(linkerPath, (uint64_t)&dlopen);
    gRemoteDlerror = resolveRemoteSymbolAddress(linkerPath, (uint64_t)&dlerror);
    gRemoteDlsym   = resolveRemoteSymbolAddress(linkerPath, (uint64_t)&dlsym);
  }

  void *runtime = dlopen("/system/lib64/libandroid_runtime.so", RTLD_NOW);
  uint64_t javaVm = resolveRemoteSymbolAddress(
      "/system/lib64/libandroid_runtime.so",
      (uint64_t)dlsym(runtime, "_ZN7android14AndroidRuntime7mJavaVME"));
  dlclose(runtime);

  LOGV("calloc:%p free:%p dlopen:%p dlsym:%p dlerror:%p javavm:%p",
       (void *)gRemoteCalloc, (void *)gRemoteFree, (void *)gRemoteDlopen,
       (void *)gRemoteDlsym, (void *)gRemoteDlerror, (void *)javaVm);
  printf("inject diag: calloc=0x%llx free=0x%llx dlopen=0x%llx dlsym=0x%llx dlerror=0x%llx javavm=0x%llx\n",
         (unsigned long long)gRemoteCalloc, (unsigned long long)gRemoteFree,
         (unsigned long long)gRemoteDlopen, (unsigned long long)gRemoteDlsym,
         (unsigned long long)gRemoteDlerror,
         (unsigned long long)javaVm);

  uint64_t remotePath   = writeRemoteString(libraryPath);
  KLOGI(kInjectorLogTag, "writeRemoteString done, remotePath=0x%llx", (unsigned long long)remotePath);
  uint64_t remoteHandle = callRemoteFunction(gRemoteDlopen, 2, remotePath, (uint64_t)RTLD_NOW);
  KLOGI(kInjectorLogTag, "remote dlopen returned 0x%llx", (unsigned long long)remoteHandle);
  printf("inject diag: remotePath=0x%llx remoteHandle=0x%llx\n",
         (unsigned long long)remotePath, (unsigned long long)remoteHandle);

  callRemoteFunction(gRemoteFree, 1, remotePath);

  int result;
  if (remoteHandle) {
    void *handle = dlopen(libraryPath, RTLD_NOW);
    void *doRunLocal = handle ? dlsym(handle, "doRun") : nullptr;
    uint64_t doRunRemoteBySlide = resolveRemoteSymbolAddress(libraryPath, (uint64_t)doRunLocal);
    if (handle)
      dlclose(handle);

    uint64_t doRunRemoteByDlsym = 0;
    if (gRemoteDlsym) {
      uint64_t remoteSymbolName = writeRemoteString("doRun");
      if (remoteSymbolName) {
        doRunRemoteByDlsym = callRemoteFunction(gRemoteDlsym, 2, remoteHandle, remoteSymbolName);
        callRemoteFunction(gRemoteFree, 1, remoteSymbolName);
      }
    }

    uint64_t doRunRemote = doRunRemoteByDlsym ? doRunRemoteByDlsym : doRunRemoteBySlide;
    KLOGI(kInjectorLogTag, "doRun local=%p remote=0x%llx dlsym=0x%llx slide=0x%llx",
          doRunLocal, (unsigned long long)doRunRemote,
          (unsigned long long)doRunRemoteByDlsym, (unsigned long long)doRunRemoteBySlide);
    printf("inject diag: doRunLocal=%p doRunRemote=0x%llx remoteDlsym=0x%llx slide=0x%llx\n",
           doRunLocal, (unsigned long long)doRunRemote,
           (unsigned long long)doRunRemoteByDlsym, (unsigned long long)doRunRemoteBySlide);

    if (!doRunRemoteByDlsym && gRemoteDlerror) {
      char errbuf[1024] = {0};
      uint64_t errPtr = callRemoteFunction(gRemoteDlerror, 0);
      readRemoteCString(errPtr, errbuf, sizeof(errbuf), "dlerror");
      if (errbuf[0]) {
        KLOGW(kInjectorLogTag, "remote dlerror: %s", errbuf);
        printf("inject diag: remote dlerror: %s\n", errbuf);
      }
    }

    result = 1;
    if (doRunRemote) {
      uint64_t remoteArg = writeRemoteString(entryArg);
      // doRun does the InjectDex JNI bring-up, which includes
      // DexClassLoader compile of the 33MB APK from inside system_server.
      // The first cold-cache run on the ZTE NX769J takes >5s, so allow a
      // generous 120s window for this single call. Subsequent cleanup
      // calls go back to the default 5s timeout.
      gCallTimeoutMs = 120000;
      uint64_t doRunResult = callRemoteFunction(doRunRemote, 2, javaVm, remoteArg);
      gCallTimeoutMs = 5000;
      callRemoteFunction(gRemoteFree, 1, remoteArg);
      KLOGI(kInjectorLogTag, "doRun call returned 0x%llx", (unsigned long long)doRunResult);
      printf("inject diag: doRunResult=0x%llx\n", (unsigned long long)doRunResult);
      if (doRunResult == kDoRunSuccess || doRunResult == kDoRunAlreadyLoaded) {
        result = 0;
      } else {
        KLOGE(kInjectorLogTag, "doRun returned failure stage 0x%llx", (unsigned long long)doRunResult);
        printf("inject diag: doRun failed stage=0x%llx\n", (unsigned long long)doRunResult);
      }
    } else {
      KLOGE(kInjectorLogTag, "doRun resolve failed: local=%p remote=0x%llx", doRunLocal, (unsigned long long)doRunRemote);
      printf("inject diag: doRun resolve failed\n");
    }
  } else {
    // dlopen failed remotely: read the error string out of the tracee.
    char errbuf[1024] = {0};
    uint64_t errPtr = callRemoteFunction(gRemoteDlerror, 0);
    readRemoteCString(errPtr, errbuf, sizeof(errbuf), "dlerror");
    LOGV("dlopen failed: %s", errbuf);
    printf("inject diag: dlopen failed: %s\n", errbuf);
    result = 1;
  }

  waitForRemoteStop();
  ptraceWithRetry("detach", PTRACE_DETACH, 0, 0);
  kill(gTargetPid, SIGCONT);
  return result;
}

// ---------------------------------------------------------------------------
// injectMain  (sub_2A04)
// ---------------------------------------------------------------------------
static int injectMain(int argc, char **argv) {
  int   pid          = 0;
  char *processName  = nullptr;
  char *libraryPath  = nullptr;
  char *packageName  = nullptr;
  char *entryArg     = nullptr;

  int opt;
  bool ok = true;
  while ((opt = getopt(argc, argv, "p:P:l:n:a:h")) != -1) {
    switch (opt) {
      case 'l':
        libraryPath = optarg;
        break;
      case 'n':
        packageName = optarg;
        break;
      case 'a':
        entryArg = optarg;
        break;
      case 'p':
        pid = atoi(optarg);
        break;
      case 'P':
        processName = optarg;
        if (!optarg || !*optarg) {
          KLOGE("LINJECT", "process name is NULL");
          ok = false;
        } else {
          pid = findPidByProcessName(optarg);
          if (pid <= 0) {
            KLOGE("LINJECT", "failed to get Pid of process %s", processName);
            ok = false;
          }
        }
        break;
      default:
        ok = false;
        break;
    }
    if (!ok)
      break;
  }

  KLOGD("LINJECT", "Inject[%d|%s] library: %s\n", pid, processName, libraryPath);

  if (!ok || (pid < 1 && processName == nullptr) || !libraryPath || !packageName ||
      strcmp("com.kail.location", packageName))
    exit(-1);

  if (access(libraryPath, R_OK) == -1) {
    fprintf(stderr, "%s must chmod r\n", libraryPath);
    KLOGE("LINJECT", "%s must chmod r\n", libraryPath);
    exit(-1);
  }

  int rc = injectLibraryIntoProcess(pid, libraryPath,
                                    (entryArg && *entryArg) ? entryArg : packageName);
  if (rc) {
    printf("Inject fail %d.\n", rc);
    KLOGE("LINJECT", "Inject fail %d.\n", rc);
  } else {
    puts("Inject ok.");
    KLOGD("LINJECT", "Inject ok.\n");
  }
  return rc;
}

int main(int argc, char **argv) {
  return injectMain(argc, argv);
}
