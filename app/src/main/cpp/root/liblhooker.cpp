// liblhooker.cpp
//
// Reconstructed, compilable source for the ART method-hook engine.
// Recovered from do/complete/liblhooker64.c (arm64) and liblhooker.c (arm).
// This single ABI-aware source covers both arm64-v8a and armeabi-v7a.
//
// LHooker rewrites the entry_point_from_quick_compiled_code field of an
// ArtMethod so calls route through a generated trampoline that loads the
// replacement ArtMethod and jumps to its entry point.  Layout offsets differ
// per Android API level and per ABI and are configured in LHooker_init().
//
// Trampolines (generated into an RWX pool):
//   arm64 (24 bytes, entry at base+4):
//     ldr  x0, #16           ; x0 = ArtMethod* (literal at base+16)
//     ldr  x16, [x0, #off]   ; x16 = method->entry_point_from_quick_code
//     br   x16
//     <ArtMethod*>
//   arm  (16 bytes, entry at base+4):
//     ldr  r0, [pc, #0]      ; r0 = ArtMethod* (literal at base+12)
//     ldr  pc, [r0, #off]    ; jump to method->entry_point_from_quick_code
//     <ArtMethod*>
//
// JNI surface (com.kail.location.lib.lhooker.LHooker):
//   init, findMethodNative, hookMethodNative, shouldVisiblyInit.
//
// NOTE: ART internal layout is highly version-sensitive.  The per-API offset
// tables below come directly from the decompilation; validate against the
// target ROM before relying on cross-version behaviour.

#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cerrno>
#include <cstdarg>
#include <string>

#include <jni.h>
#include <sys/mman.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>

#include <android/log.h>

#include "fakeloc_common.h"
#include "kail_log.h"

static const char *kTag = "LHooker.Native";

#if defined(__LP64__)
using word_t = uint64_t;     // pointer-sized GOT/entry word
#else
using word_t = uint32_t;
#endif

// ---------------------------------------------------------------------------
// Configuration discovered at init() time.
// ---------------------------------------------------------------------------
static int  gInited           = -1;   // isInited
static int  gSdkInt           = 0;    // SDK_INT
static int  gEntryPointOffset = 0;    // OFFSET_entry_point_from_quick_compiled_code
static int  gHotnessOffset    = 0;    // dword_9830 / dword_6F8C (copied on clone; 0 when unused)
static int  gArtMethodSize    = 0;    // qword_9828 / dword_6F88 (bytes copied when cloning)
static jfieldID gArtMethodField = nullptr;  // qword_9818 / dword_6F84 (Executable.artMethod, SDK>=30)

// Access-flag layout, derived identically on both ABIs:
//   offset:  SDK >= 24 -> +4 else +0          (byte_9824 / byte_6F80)
//   or-bits: SDK >= 27 -> 0x2000000 else 0x1000000  (byte_9820 / byte_6F7C)
//   masking: SDK >= 31 -> 0xFF7FFFFF else 0xFFDFFFFF (byte_9810 / byte_6F78)
static bool gAccessFlag4ByteOffset = false;
static bool gUseExtendedOrBit      = false;
static bool gUseApi31AndMask       = false;

// ---------------------------------------------------------------------------
// Init-time log buffer.
//
// The ArtMethod layout probe runs inside system_server during init().  Its
// results go to logcat (KLOG*) but logcat is volatile and easy to miss.  We
// also accumulate a human-readable summary here so the Java side can pull it
// out via nativeGetInitLog() right after init() and persist it through
// InjectLog into the app's own log file (/sdcard/Documents/KailLocation/logs).
// This makes the auto-detected layout permanently inspectable for debugging.
// ---------------------------------------------------------------------------
static std::string gInitLog;

static void initLogAppend(const char *line) {
  gInitLog.append(line);
  gInitLog.push_back('\n');
}

// Append a printf-style formatted line to the init-log buffer (logcat is done
// separately by the caller via KLOG*).
static void initLogf(const char *fmt, ...) __attribute__((format(printf, 1, 2)));
static void initLogf(const char *fmt, ...) {
  char buf[256];
  va_list ap;
  va_start(ap, fmt);
  vsnprintf(buf, sizeof(buf), fmt, ap);
  va_end(ap);
  initLogAppend(buf);
}

// Persist the init-log to a world-writable file that any injected process
// (including system_server, which CANNOT write to the app's /sdcard log dir)
// is allowed to append to. ServiceGoRoot creates /data/kail-loc as 0777, so
// the system_server-hosted hook engine can drop diagnostics here even though
// scoped storage blocks it from the normal app log path. The Java side mirrors
// this into the app log via InjectLog when it runs in a storage-permitted app.
static void writeInitLogToFile() {
  if (gInitLog.empty())
    return;
  // Try the root deploy dir first (always 0777), fall back to the lib dir.
  const char *paths[] = {
      "/data/system/kail-loc/lhooker_init.log",
      "/data/kail-loc/lhooker_init.log",
      "/data/local/kail-lib/lhooker_init.log",
  };
  for (const char *path : paths) {
    int fd = open(path, O_WRONLY | O_CREAT | O_APPEND, 0666);
    if (fd < 0)
      continue;
    char hdr[128];
    int hn = snprintf(hdr, sizeof(hdr),
                      "===== LHooker init (pid=%d, ptr=%zu) =====\n",
                      getpid(), sizeof(void *));
    if (hn > 0)
      (void)!write(fd, hdr, (size_t)hn);
    (void)!write(fd, gInitLog.data(), gInitLog.size());
    fchmod(fd, 0666);
    close(fd);
    KLOGI(kTag, "init log written to %s", path);
    return;
  }
  KLOGW(kTag, "could not persist init log to any path");
}

// ---------------------------------------------------------------------------
// Trampoline templates and entry-offset patching.
// ---------------------------------------------------------------------------
#if defined(__x86_64__)

// 19-byte x86_64 trampoline; ArtMethod* literal at +2 (movabs imm), the entry
// offset is folded into the disp32 of `mov rax, [rdi + disp32]`. At a method
// entry point rdi already holds the ArtMethod* (SysV first arg); we overwrite
// it with the literal target method (mirroring the arm64 ldr x0,#16) so the
// jumped-to code always runs as that method.
//   [0]  48 BF <imm64>            movabs rdi, <ArtMethod*>
//   [10] 48 8B 87 <disp32>        mov    rax, [rdi + entryOffset]
//   [17] FF E0                    jmp    rax
static const uint8_t kTrampolineBase[19] = {
    0x48, 0xBF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x48, 0x8B, 0x87, 0x00, 0x00, 0x00, 0x00,
    0xFF, 0xE0,
};
static uint8_t gTrampoline[19];
static const size_t kNoBackupSize = 32;

static void setupTrampoline(int entryOffset) {
  // Restore the pristine template first so this is idempotent (the runtime
  // ArtMethod probe may refine the entry offset and call us again).
  memcpy(gTrampoline, kTrampolineBase, sizeof(gTrampoline));
  gTrampoline[13] = (uint8_t)(entryOffset & 0xff);
  gTrampoline[14] = (uint8_t)((entryOffset >> 8) & 0xff);
  gTrampoline[15] = (uint8_t)((entryOffset >> 16) & 0xff);
  gTrampoline[16] = (uint8_t)((entryOffset >> 24) & 0xff);
}

static void *emitTrampoline(uint8_t *slot, uintptr_t method) {
  memcpy(slot, gTrampoline, sizeof(gTrampoline));
  *(uint64_t *)(slot + 2) = (uint64_t)method;
  return slot;
}

#elif defined(__LP64__)

// 24-byte block; instruction stream begins at +4, ArtMethod* literal at +16.
//   [0]  padding
//   [4]  0x58000060  ldr x0, #16
//   [8]  0xF8400010  ldr x16, [x0, #imm]   (imm patched via bytes 9..10)
//   [12] 0xD61F0200  br  x16
//   [16] ArtMethod*
static const uint8_t kTrampolineBase[16] = {
    0x00, 0x00, 0x00, 0x00,
    0x60, 0x00, 0x00, 0x58,
    0x10, 0x00, 0x40, 0xf8,
    0x00, 0x02, 0x1f, 0xd6,
};
static uint8_t gTrampoline[16];
static const size_t kNoBackupSize = 24;

static void setupTrampoline(int entryOffset) {
  // Restore the pristine template first so this is idempotent: the runtime
  // ArtMethod probe may call us a second time with a refined entry offset.
  memcpy(gTrampoline, kTrampolineBase, sizeof(gTrampoline));
  // Mirror the decompiled bit-twiddle that folds the entry offset into the
  // ldr x16,[x0,#imm] immediate (bytes 9 and 10 of the template).
  gTrampoline[9]  |= (uint8_t)(16 * (entryOffset & 0x0f));
  gTrampoline[10] |= (uint8_t)((entryOffset & 0xf0) >> 4);
}

static void *emitTrampoline(uint8_t *slot, uintptr_t method) {
  memcpy(slot, gTrampoline, sizeof(gTrampoline));
  *(uint64_t *)(slot + 16) = (uint64_t)method;
  return slot + 4;
}

#else  // arm

// 16-byte block; instruction stream begins at +4, ArtMethod* literal at +12.
//   [0]  padding
//   [4]  0xE59F0000  ldr r0, [pc, #0]      ; r0 = *(base+12) = ArtMethod*
//   [8]  0xE590F000  ldr pc, [r0, #imm]    ; imm = byte[8] (entry offset)
//   [12] ArtMethod*
static const uint8_t kTrampolineBase[12] = {
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x9f, 0xe5,
    0x00, 0xf0, 0x90, 0xe5,
};
static uint8_t gTrampoline[12];
static const size_t kNoBackupSize = 16;

static void setupTrampoline(int entryOffset) {
  // Restore the pristine template first so this is idempotent (the runtime
  // ArtMethod probe may refine the entry offset and call us again).
  memcpy(gTrampoline, kTrampolineBase, sizeof(gTrampoline));
  // Decompiled setupTrampoline stored the entry offset into byte_6C48, which is
  // byte 8 of the block: the low byte of "ldr pc, [r0, #imm]".
  gTrampoline[8] = (uint8_t)entryOffset;
}

static void *emitTrampoline(uint8_t *slot, uintptr_t method) {
  memcpy(slot, gTrampoline, sizeof(gTrampoline));
  *(uint32_t *)(slot + 12) = (uint32_t)method;
  return slot + 4;
}

#endif

// ---------------------------------------------------------------------------
// Trampoline pool allocator  (genTrampoline / RWX pool globals)
// ---------------------------------------------------------------------------
static uintptr_t gPoolCur = 0;
static uintptr_t gPoolEnd = 0;

static void *genTrampoline(uintptr_t method) {
  if (gPoolCur + kNoBackupSize > gPoolEnd) {
    void *page = mmap(nullptr, 0x1000, PROT_READ | PROT_WRITE | PROT_EXEC,
                      MAP_ANONYMOUS | MAP_PRIVATE, -1, 0);
    if (page == MAP_FAILED) {
      KLOGE(kTag, "mmap failed, errno = %s", strerror(errno));
      gPoolCur = 0;
      return nullptr;
    }
    gPoolCur = (uintptr_t)page;
    gPoolEnd = gPoolCur + 0x1000;
  }

  void *entry = emitTrampoline((uint8_t *)gPoolCur, method);
  __builtin___clear_cache((char *)gPoolCur, (char *)gPoolCur + kNoBackupSize);
  gPoolCur += kNoBackupSize;
  return entry;
}

// ---------------------------------------------------------------------------
// ArtMethod access-flag helpers (setNonCompilable / setPrivate).
// ---------------------------------------------------------------------------
static int accessFlagsOffset() { return gAccessFlag4ByteOffset ? 4 : 0; }

static void setNonCompilable(uintptr_t method) {
  if (gSdkInt < 24)
    return;
  int off = accessFlagsOffset();
  uint32_t flags   = *(uint32_t *)(method + off);
  uint32_t orBits  = gUseExtendedOrBit ? 0x02000000u : 0x01000000u;
  uint32_t andMask = 0xffffffffu;
  if (gSdkInt > 29)
    andMask = gUseApi31AndMask ? 0xff7fffffu : 0xffdfffffu;
  *(uint32_t *)(method + off) = (flags | orBits) & andMask;
}

static void setPrivate(uintptr_t method) {
  int off = accessFlagsOffset();
  uint32_t flags = *(uint32_t *)(method + off);
  if ((flags & 8) == 0)
    *(uint32_t *)(method + off) = (flags & 0xfffffff8u) | 2;
}

// Resolve the native ArtMethod pointer behind a Java reflect Method/Constructor.
static uintptr_t artMethodFromReflected(JNIEnv *env, jobject method) {
  if (!method)
    return 0;
  if (gSdkInt < 30)
    return (uintptr_t)env->FromReflectedMethod(method);
  return (uintptr_t)env->GetLongField(method, gArtMethodField);
}

// installHook (sub_3250): point `target`'s entry at a trampoline to `hook`.
//
// IMPORTANT — for Android 14 (SDK 34) we deliberately DO NOT touch the
// `kAccFastInterpreterToInterpreterInvoke` (0x40000000) bit on the target
// method. ART 14 changed how it walks ArtMethods during class verification:
// the verifier follows the entry_point_from_quick_compiled_code into the
// trampoline and treats the bytes there as method metadata when the JIT is
// asked to produce an OatMethodHeader. Clearing `kAccFastInterpreter...`
// makes ART treat the method as JIT-callable, which on this device hits the
// `bindServiceInstance -> Monitor::Lock -> art_quick_lock_object_no_inline
// -> StackVisitor::WalkStack -> GetOatQuickMethodHeader` path and SIGSEGVs
// at FindOatMethodFor when it tries to dereference the trampoline as a
// real ArtMethod*.
//
// We instead leave the flags untouched. The hook still installs because the
// entry-point overwrite is the only thing that actually redirects calls;
// the flag fiddling is a JIT/AOT optimisation that FakeLocation 1.50 only
// does as a "be nice to ART" hint.
static int installHook(uintptr_t target, uintptr_t hook) {
  void *tramp = genTrampoline(hook);
  if (!tramp) {
    KLOGE(kTag, "failed to allocate space for trampoline of target method");
    return 1;
  }

  *(word_t *)(target + gEntryPointOffset) = (word_t)(uintptr_t)tramp;
  if (gHotnessOffset)
    *(word_t *)(target + gHotnessOffset) = *(word_t *)(hook + gHotnessOffset);

  // SDK 26-29 still benefit from the kAccSkipAccessChecks hint.  SDK 30+
  // we leave alone (see comment above).
  if (gSdkInt >= 26 && gSdkInt < 30) {
    int off = accessFlagsOffset();
    uint32_t flags = *(uint32_t *)(target + off);
    uint32_t newFlags = flags;
    if (gSdkInt > 28)
      newFlags &= ~0x40000000u;   // clear kAccFastInterpreterToInterpreterInvoke
    newFlags |= 0x100u;           // set kAccSkipAccessChecks-ish
    if (newFlags != flags)
      *(uint32_t *)(target + off) = newFlags;
  }
  return 0;
}

// ---------------------------------------------------------------------------
// Runtime ArtMethod layout probe.
//
// On ART a jmethodID *is* the ArtMethod*, and all methods declared in one class
// live in a single contiguous LengthPrefixedArray<ArtMethod>. The minimum
// positive gap between several sibling methods' pointers is therefore the exact
// sizeof(ArtMethod) on the running device — no per-SDK table required. The
// quick entry point is always the last pointer-sized field, so its offset is
// (size - sizeof(void*)).
//
// Returns true and fills *outSize / *outEntryOff on success.
// ---------------------------------------------------------------------------
static bool probeArtMethodLayout(JNIEnv *env, int *outSize, int *outEntryOff) {
  jclass probe = env->FindClass("com/kail/location/lib/lhooker/ArtMethodProbe");
  if (!probe) {
    env->ExceptionClear();
    KLOGW(kTag, "probe: ArtMethodProbe class not found");
    return false;
  }

  static const char *kNames[] = {
      "m0", "m1", "m2", "m3", "m4", "m5",
      "m6", "m7", "m8", "m9", "m10", "m11",
  };
  const int kCount = (int)(sizeof(kNames) / sizeof(kNames[0]));

  uintptr_t addrs[12];
  int n = 0;
  for (int i = 0; i < kCount; i++) {
    jmethodID mid = env->GetStaticMethodID(probe, kNames[i], "()V");
    if (!mid) {
      env->ExceptionClear();
      continue;
    }
    addrs[n++] = (uintptr_t)mid;  // jmethodID == ArtMethod* on ART
  }
  env->DeleteLocalRef(probe);

  KLOGI(kTag, "probe: resolved %d/%d ArtMethod pointers", n, kCount);
  initLogf("probe: resolved %d/%d ArtMethod pointers", n, kCount);
  for (int i = 0; i < n; i++) {
    KLOGI(kTag, "probe:   m%d @ %p", i, (void *)addrs[i]);
  }

  if (n < 3) {
    KLOGW(kTag, "probe: only %d method ids resolved, too few to measure", n);
    initLogf("probe: FAILED, only %d method ids (need >=3)", n);
    return false;
  }

  // Minimum positive pairwise gap == sizeof(ArtMethod). Methods may not be in
  // declaration order in memory, so scan all pairs rather than assuming
  // monotonic addresses.
  uintptr_t best = 0;
  for (int i = 0; i < n; i++) {
    for (int j = i + 1; j < n; j++) {
      uintptr_t d = addrs[i] > addrs[j] ? addrs[i] - addrs[j] : addrs[j] - addrs[i];
      if (d != 0 && (best == 0 || d < best))
        best = d;
    }
  }

  KLOGI(kTag, "probe: minimum pairwise gap = %zu bytes", (size_t)best);
  initLogf("probe: minimum pairwise gap = %zu bytes", (size_t)best);

  // Sanity bounds: a real ArtMethod is small and pointer-aligned. Anything
  // outside this range means the measurement is unreliable (e.g. methods landed
  // in different arrays), so we reject and fall back to the SDK table.
  const uintptr_t kMin = 16;
  const uintptr_t kMax = 64;
  if (best < kMin || best > kMax || (best % sizeof(void *)) != 0) {
    KLOGW(kTag, "probe: measured ArtMethod size %zu out of range; rejecting",
          (size_t)best);
    initLogf("probe: REJECTED measured size %zu (out of range/unaligned)",
             (size_t)best);
    return false;
  }

  *outSize = (int)best;
  *outEntryOff = (int)best - (int)sizeof(void *);
  KLOGI(kTag, "probe: SUCCESS -> ArtMethod size=%d, quick-entry-point offset=%d "
              "(pointer size=%zu)",
        *outSize, *outEntryOff, sizeof(void *));
  initLogf("probe: SUCCESS ArtMethod size=%d quick-entry-point offset=%d (ptr=%zu)",
           *outSize, *outEntryOff, sizeof(void *));
  return true;
}

// ---------------------------------------------------------------------------
// JNI exports
// ---------------------------------------------------------------------------
extern "C" {

JNIEXPORT jint JNICALL
Java_com_kail_location_lib_lhooker_LHooker_init(JNIEnv *env, jobject, jint sdkInt) {
  int sig = fakeloc::verifyReleaseSignature(env);
  if (sig != 0 && sig != -2) {
    gInited = -1;
    return -1;
  }

  gSdkInt = sdkInt;
  KLOGI(kTag, "SDK %d", sdkInt);
  gInitLog.clear();
  initLogf("LHooker init: SDK=%d ABI=%s", sdkInt,
           sizeof(void *) == 8 ? "arm64" : "arm");

  // Common access-flag switches.
  gAccessFlag4ByteOffset = (sdkInt >= 24);
  gUseExtendedOrBit      = (sdkInt >= 27);
  gUseApi31AndMask       = (sdkInt >= 31);

  // entry-point offset / ArtMethod size / hotness(copy) offset are ABI-specific.
#if defined(__LP64__)
  switch (sdkInt) {
    case 21: gEntryPointOffset = 40; gArtMethodSize = 72; gHotnessOffset = 24; break;
    case 22: gEntryPointOffset = 56; gArtMethodSize = 64; gHotnessOffset = 40; break;
    case 23: gEntryPointOffset = 48; gArtMethodSize = 56; gHotnessOffset = 32; break;
    case 24:
    case 25: gEntryPointOffset = 48; gArtMethodSize = 56; break;
    case 26:
    case 27: gEntryPointOffset = 40; gArtMethodSize = 48; break;
    case 28:
    case 29: gEntryPointOffset = 32; gArtMethodSize = 40; break;
    case 30: {
      jclass executable = env->FindClass("java/lang/reflect/Executable");
      gArtMethodField = env->GetFieldID(executable, "artMethod", "J");
      gEntryPointOffset = 32; gArtMethodSize = 40;
      break;
    }
    case 31:
    case 32:
    case 33:
    case 34:
    case 35:
    case 36:
    default: {
      // SDK 31..36 (Android 12..16) share the same ArtMethod layout
      // (entry-point @24, size 32 on arm64). Newer/unknown SDKs fall through
      // here too: assume the latest known-good layout rather than leaving the
      // offsets at 0, which would make installHook overwrite the ArtMethod's
      // declaring_class field and crash system_server. ART's ArtMethod has not
      // changed size since API 31.
      if (sdkInt > 36) {
        KLOGW(kTag, "SDK %d newer than known; using API 31-36 ArtMethod layout", sdkInt);
      }
      jclass executable = env->FindClass("java/lang/reflect/Executable");
      gArtMethodField = env->GetFieldID(executable, "artMethod", "J");
      gEntryPointOffset = 24; gArtMethodSize = 32;
      break;
    }
  }
#else
  switch (sdkInt) {
    case 21: gEntryPointOffset = 40; gArtMethodSize = 72; gHotnessOffset = 24; break;
    case 22: gEntryPointOffset = 44; gArtMethodSize = 48; gHotnessOffset = 36; break;
    case 23: gEntryPointOffset = 36; gArtMethodSize = 40; gHotnessOffset = 28; break;
    case 24:
    case 25: gEntryPointOffset = 32; gArtMethodSize = 36; break;
    case 26:
    case 27: gEntryPointOffset = 28; gArtMethodSize = 32; break;
    case 28:
    case 29: gEntryPointOffset = 24; gArtMethodSize = 28; break;
    case 30: {
      jclass executable = env->FindClass("java/lang/reflect/Executable");
      gArtMethodField = env->GetFieldID(executable, "artMethod", "J");
      gEntryPointOffset = 24; gArtMethodSize = 28;
      break;
    }
    case 31:
    case 32:
    case 33:
    case 34:
    case 35:
    case 36:
    default: {
      // SDK 31..36 (Android 12..16) share the same 32-bit ArtMethod layout
      // (entry-point @20, size 24). Unknown newer SDKs fall back here too,
      // rather than leaving offsets at 0 (which corrupts the ArtMethod and
      // crashes system_server).
      if (sdkInt > 36) {
        KLOGW(kTag, "SDK %d newer than known; using API 31-36 ArtMethod layout", sdkInt);
      }
      jclass executable = env->FindClass("java/lang/reflect/Executable");
      gArtMethodField = env->GetFieldID(executable, "artMethod", "J");
      gEntryPointOffset = 20; gArtMethodSize = 24;
      break;
    }
  }
#endif

  setupTrampoline(gEntryPointOffset);

  // Prefer runtime-measured layout over the static SDK table. This makes the
  // engine version-agnostic: on any ROM (including future Android releases) the
  // probe measures the true sizeof(ArtMethod) and derives the quick-entry-point
  // offset, so we never corrupt system_server with a stale/zero offset.
  {
    int probedSize = 0, probedEntry = 0;
    if (probeArtMethodLayout(env, &probedSize, &probedEntry)) {
      if (probedEntry != gEntryPointOffset || probedSize != gArtMethodSize) {
        KLOGI(kTag,
              "ArtMethod layout: table(entry=%d,size=%d) -> probed(entry=%d,size=%d)",
              gEntryPointOffset, gArtMethodSize, probedEntry, probedSize);
        initLogf("ArtMethod layout: table(entry=%d,size=%d) -> probed(entry=%d,size=%d)",
                 gEntryPointOffset, gArtMethodSize, probedEntry, probedSize);
      } else {
        KLOGI(kTag, "ArtMethod layout probe confirms table (entry=%d,size=%d)",
              probedEntry, probedSize);
        initLogf("ArtMethod layout probe confirms table (entry=%d,size=%d)",
                 probedEntry, probedSize);
      }
      gEntryPointOffset = probedEntry;
      gArtMethodSize    = probedSize;
      setupTrampoline(gEntryPointOffset);
    } else {
      KLOGW(kTag, "ArtMethod probe failed; using SDK table (entry=%d,size=%d)",
            gEntryPointOffset, gArtMethodSize);
      initLogf("ArtMethod probe failed; using SDK table (entry=%d,size=%d)",
               gEntryPointOffset, gArtMethodSize);
    }
  }

  // Fail-safe: never proceed with a zero entry-point offset or zero ArtMethod
  // size. installHook() writes the trampoline pointer to
  // *(target + gEntryPointOffset); if that offset were 0 it would clobber the
  // ArtMethod's declaring_class_ field and crash system_server. The runtime
  // probe + per-API table + API 31-36 fallback guarantee non-zero values, but
  // if everything fails we refuse to install rather than corrupt ART. Hooks
  // simply won't take effect.
  if (gEntryPointOffset == 0 || gArtMethodSize == 0) {
    KLOGE(kTag, "refusing to init: bad ArtMethod layout for SDK %d "
                "(entryPoint=%d size=%d). Hooks disabled to protect system_server.",
          gSdkInt, gEntryPointOffset, gArtMethodSize);
    initLogf("INIT FAILED: bad layout (entry=%d size=%d); hooks disabled to protect system_server",
             gEntryPointOffset, gArtMethodSize);
    writeInitLogToFile();
    gInited = -1;
    return -1;
  }

  initLogf("LHooker init OK: using entry=%d size=%d", gEntryPointOffset, gArtMethodSize);
  writeInitLogToFile();
  gInited = 0;
  return 0;
}

JNIEXPORT jobject JNICALL
Java_com_kail_location_lib_lhooker_LHooker_findMethodNative(
    JNIEnv *env, jobject, jclass clazz, jstring nameStr, jstring sigStr) {
  if (gInited != 0)
    return nullptr;

  const char *name = env->GetStringUTFChars(nameStr, nullptr);
  const char *sig  = env->GetStringUTFChars(sigStr, nullptr);

  jmethodID mid = env->GetMethodID(clazz, name, sig);
  jobject result;
  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    jmethodID smid = env->GetStaticMethodID(clazz, name, sig);
    if (env->ExceptionCheck()) {
      env->ExceptionClear();
      result = nullptr;
    } else {
      result = env->ToReflectedMethod(clazz, smid, JNI_TRUE);
    }
  } else {
    result = env->ToReflectedMethod(clazz, mid, JNI_FALSE);
  }

  env->ReleaseStringUTFChars(nameStr, name);
  env->ReleaseStringUTFChars(sigStr, sig);
  return result;
}

JNIEXPORT jboolean JNICALL
Java_com_kail_location_lib_lhooker_LHooker_hookMethodNative(
    JNIEnv *env, jobject, jobject target, jobject hook, jobject backup, jobject backup2) {
  if (gInited != 0)
    return JNI_FALSE;

  uintptr_t targetMethod  = artMethodFromReflected(env, target);
  uintptr_t hookMethod    = artMethodFromReflected(env, hook);
  uintptr_t backupMethod  = backup  ? artMethodFromReflected(env, backup)  : 0;
  uintptr_t backup2Method = backup2 ? artMethodFromReflected(env, backup2) : 0;

  if (gSdkInt >= 24) {
    setNonCompilable(targetMethod);
    setNonCompilable(hookMethod);
    if (backupMethod)  setNonCompilable(backupMethod);
    if (backup2Method) setNonCompilable(backup2Method);
  }

  int rc = 0;
  if (backupMethod) {
    // Layout (matching FakeLocation 1.50):
    //   target  -> trampoline -> hook       (the hook wins on first invoke)
    //   backup  -> trampoline -> backup2    (so callers of `_bak` reach the
    //                                        cloned ArtMethod which still has
    //                                        the ORIGINAL entry_point because
    //                                        we cloned it BEFORE rewriting
    //                                        target)
    //   backup2 = memcpy(target)            (the actual "original" call site)
    //
    // The earlier order of "clone then `installHook(backup, hook)`" pointed
    // _bak directly at the hook, which made user code that calls
    // `someMethod_bak()` recurse into `someMethod()` and StackOverflow.
    memcpy((void *)backup2Method, (void *)targetMethod, gArtMethodSize);
    rc += installHook(backupMethod, backup2Method);
    if (gSdkInt >= 30) {
      // FakeLocation 1.50 sets kAccPrivate on the *copy* (v13 in the IDA
      // dump) and the *hook* (v39), NOT the backup. Setting kAccPrivate on
      // backup2/copy keeps ART from including the now-clobbered method in
      // public-method tables (its dex_method_index_ no longer matches its
      // class's method id table after the memcpy, so any reflective lookup
      // would dereference a bogus DexCache slot and SIGSEGV during class
      // verification).
      setPrivate(backup2Method);
      setPrivate(hookMethod);
    }
  }
  rc += installHook(targetMethod, hookMethod);

  KLOGI(kTag, "Hook method done.");
  if (rc != 0)
    return JNI_FALSE;

  env->DeleteLocalRef(hook);
  if (backup)  env->DeleteLocalRef(backup);
  if (backup2) env->DeleteLocalRef(backup2);
  return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_kail_location_lib_lhooker_LHooker_shouldVisiblyInit(JNIEnv *, jobject) {
  return gSdkInt > 29 ? JNI_TRUE : JNI_FALSE;
}

// Return the accumulated init-time diagnostic log (ArtMethod layout probe
// results, final offsets, fail-safe outcome) so the Java side can persist it
// through InjectLog into the app's own log file for offline troubleshooting.
JNIEXPORT jstring JNICALL
Java_com_kail_location_lib_lhooker_LHooker_nativeGetInitLog(JNIEnv *env, jobject) {
  return env->NewStringUTF(gInitLog.c_str());
}

// ---------------------------------------------------------------------------
// Stubs for the remaining `LHooker` native declarations so the JVM can resolve
// every entry point without throwing UnsatisfiedLinkError at first call.
//
// FakeLocation's reference implementation reaches into ART internals
// (`Runtime::instance_`, `MakeInitializedClassesVisiblyInitialized`,
// `Thread::Self`) for `suspendAll`, `resumeAll`, `getThread`, `visiblyInit`.
// On Android 14 the offsets shift between vendor builds, so we keep these as
// safe no-ops: the hook engine still works because the entry-point rewrite is
// done with the target thread suspended via the Java callers' own
// suspend-all-via-debugger semantics, and the ART class init path is
// triggered explicitly from Java when needed.
// ---------------------------------------------------------------------------

JNIEXPORT jlong JNICALL
Java_com_kail_location_lib_lhooker_LHooker_suspendAll(JNIEnv *, jobject) {
  return 0;
}

JNIEXPORT void JNICALL
Java_com_kail_location_lib_lhooker_LHooker_resumeAll(JNIEnv *, jobject, jlong) {
}

JNIEXPORT jlong JNICALL
Java_com_kail_location_lib_lhooker_LHooker_getThread(JNIEnv *, jobject) {
  return 0;
}

JNIEXPORT jint JNICALL
Java_com_kail_location_lib_lhooker_LHooker_visiblyInit(JNIEnv *, jobject, jlong) {
  return 0;
}

JNIEXPORT void JNICALL
Java_com_kail_location_lib_lhooker_LHooker_ensureMethodCached(
    JNIEnv *, jobject, jobject, jobject) {
}

JNIEXPORT void JNICALL
Java_com_kail_location_lib_lhooker_LHooker_ensureDeclareClass(
    JNIEnv *, jobject, jobject, jobject) {
}

JNIEXPORT jobjectArray JNICALL
Java_com_kail_location_lib_lhooker_LHooker_getObjs(
    JNIEnv *, jobject, jbyteArray, jstring) {
  return nullptr;
}

}  // extern "C"
