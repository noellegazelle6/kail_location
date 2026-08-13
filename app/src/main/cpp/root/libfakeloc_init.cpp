// libfakeloc_init.cpp
//
// Reconstructed, compilable source for the generic app init entry library.
// Recovered from do/complete/libfakeloc_init..c (arm64).  The 32-bit behaviour
// is identical at the JNI level, so this single portable source covers both.
//
// Flow:
//   doRun(JavaVM**, arg) -> AttachCurrentThread -> init(env)
//   init(env):
//     - verify payload MD5 and release signature
//     - build a DexClassLoader over /data/kail-loc/libfakeloc.so with an opt
//       directory of /data/kail-loc/system_dex
//     - load com.kail.location.inject.fakelocation.InjectDex
//     - call InjectDex.init(context) reflectively

#include <cstdio>
#include <cstdlib>
#include <cstdarg>
#include <cerrno>
#include <sys/stat.h>

#include "fakeloc_common.h"

using namespace fakeloc;

static const char *kOptDir = "/data/kail-loc/system_dex";
static bool gInitLoaded = false;     // byte_7038

static constexpr uint64_t kRunSuccess = 0x4b4c1000;
static constexpr uint64_t kRunAlreadyLoaded = 0x4b4c1001;
static constexpr uint64_t kRunVerifyMd5Failed = 0x4b4c2001;
static constexpr uint64_t kRunNullEnv = 0x4b4c2002;
static constexpr uint64_t kRunVerifySignatureFailed = 0x4b4c2003;
static constexpr uint64_t kRunNullContext = 0x4b4c2004;
static constexpr uint64_t kRunPayloadOpenFailed = 0x4b4c2005;
static constexpr uint64_t kRunClassLoaderFailed = 0x4b4c2006;
static constexpr uint64_t kRunLoadClassFailed = 0x4b4c2007;
static constexpr uint64_t kRunInitMethodMissing = 0x4b4c2008;
static constexpr uint64_t kRunInitException = 0x4b4c2009;
static constexpr uint64_t kRunNullVmPtr = 0x4b4c3001;
static constexpr uint64_t kRunNullVm = 0x4b4c3002;
static constexpr uint64_t kRunAttachFailed = 0x4b4c3003;

static void writeInitTrace(const char *fmt, ...) __attribute__((format(printf, 1, 2)));
static void writeInitTrace(const char *fmt, ...) {
  const char *path = "/data/system/kail-loc/fakeloc_init.log";
  FILE *fp = fopen(path, "a");
  if (!fp) {
    path = "/data/kail-loc/fakeloc_init.log";
    fp = fopen(path, "a");
  }
  if (!fp) {
    KLOGW(kLogTag, "writeInitTrace open failed errno=%d", errno);
    return;
  }
  va_list ap;
  va_start(ap, fmt);
  fprintf(fp, "pid=%d ", getpid());
  vfprintf(fp, fmt, ap);
  fprintf(fp, "\n");
  va_end(ap);
  fclose(fp);
  chmod(path, 0666);
}

// ---------------------------------------------------------------------------
// init  (sub_2430)
// ---------------------------------------------------------------------------
//
// Loads the slim inject dex via InMemoryDexClassLoader (a ByteBuffer-backed
// loader) instead of DexClassLoader. This matters a lot inside system_server
// on Android 14: DexClassLoader insists on writing an optimized .vdex/.odex
// into its optimizedDirectory, but system_server is SELinux-confined and the
// write to /data/kail-loc/oat/ is denied. ART then spins for the entire
// ptrace watchdog window (~2 min) before giving up, freezing and ultimately
// killing the framework.
//
// InMemoryDexClassLoader runs the dex straight from memory (interpreter /
// in-memory JIT) with no on-disk oat, so there is nothing to deny and the
// load completes in milliseconds.
// ---------------------------------------------------------------------------
static const char *sessionHookerPathFromArg(const char *arg) {
  if (!arg)
    return nullptr;
  if (arg[0] == '/')
    return arg;
  const char *sep = strchr(arg, '|');
  if (!sep || !sep[1])
    return nullptr;
  return sep + 1;
}

static uint64_t init(JNIEnv *env, const char *sessionHookerPath) {
  KLOGI(kLogTag, "InitApp is Executing");
  writeInitTrace("InitApp executing");

  int md5Result = verifyApkMd5();
  if (md5Result != 0) {
    KLOGE(kLogTag, "verifyApkMd5 failed: %d", md5Result);
    writeInitTrace("verifyApkMd5 failed: %d", md5Result);
    return kRunVerifyMd5Failed;
  }
  if (!env) {
    KLOGI(kLogTag, "jni_env is NULL!!");
    writeInitTrace("jni_env is NULL");
    return kRunNullEnv;
  }
  int sigResult = verifyReleaseSignature(env);
  if (sigResult != 0) {
    KLOGE(kLogTag, "verifyReleaseSignature failed: %d", sigResult);
    writeInitTrace("verifyReleaseSignature failed: %d", sigResult);
    return kRunVerifySignatureFailed;
  }

  jobject context = getGlobalContext(env);
  if (!context) {
    KLOGE(kLogTag, "global context is NULL");
    writeInitTrace("global context is NULL");
    return kRunNullContext;
  }
  jclass ctxClass = env->FindClass("android/content/Context");
  jmethodID getCl = env->GetMethodID(ctxClass, "getClassLoader", "()Ljava/lang/ClassLoader;");
  jobject parentLoader = env->CallObjectMethod(context, getCl);
  writeInitTrace("global context=%p parentLoader=%p", context, parentLoader);

  // Read the slim dex bytes from /data/kail-loc/libfakeloc.so into a direct
  // ByteBuffer. The file is a bare .dex (RootDeployer deploys the raw dex,
  // not a zip, for the in-memory path).
  jobject loader = nullptr;
  FILE *fp = fopen(kPayloadPath, "rb");
  if (fp) {
    fseek(fp, 0, SEEK_END);
    long sz = ftell(fp);
    fseek(fp, 0, SEEK_SET);
    if (sz > 0) {
      writeInitTrace("payload size=%ld", sz);
      void *buf = malloc((size_t)sz);
      if (buf && fread(buf, 1, (size_t)sz, fp) == (size_t)sz) {
        jobject byteBuffer = env->NewDirectByteBuffer(buf, sz);
        jclass imdclClass = env->FindClass("dalvik/system/InMemoryDexClassLoader");
        if (imdclClass && byteBuffer) {
          jmethodID imdclCtor = env->GetMethodID(
              imdclClass, "<init>",
              "(Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V");
          if (imdclCtor) {
            loader = env->NewObject(imdclClass, imdclCtor, byteBuffer, parentLoader);
            if (env->ExceptionCheck()) {
              writeInitTrace("InMemoryDexClassLoader constructor exception");
              env->ExceptionDescribe();
              env->ExceptionClear();
              loader = nullptr;
            }
          }
        }
        // NB: the direct ByteBuffer keeps referencing `buf`; ART copies the
        // dex into its own memory during construction, but to be safe we do
        // not free `buf` until after the loader is built. Freeing here is
        // fine because InMemoryDexClassLoader has already mapped the data.
        if (loader) free(buf);
        else { /* keep buf around on failure path; small one-shot leak */ }
      } else if (buf) {
        free(buf);
      }
    }
    fclose(fp);
  } else {
    writeInitTrace("payload open failed: %s", kPayloadPath);
    return kRunPayloadOpenFailed;
  }

  if (!loader) {
    // Fallback: legacy DexClassLoader over the same path (works if the file
    // is actually a zip/apk and the opt dir is writable).
    KLOGW(kLogTag, "InMemoryDexClassLoader failed; falling back to DexClassLoader");
    writeInitTrace("InMemoryDexClassLoader failed; falling back to DexClassLoader");
    jstring optDir  = env->NewStringUTF(kOptDir);
    jstring dexPath = env->NewStringUTF(kPayloadPath);
    jclass dclClass = env->FindClass("dalvik/system/DexClassLoader");
    jmethodID dclCtor = env->GetMethodID(
        dclClass, "<init>",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/ClassLoader;)V");
    loader = env->NewObject(dclClass, dclCtor, dexPath, optDir, nullptr, parentLoader);
    env->DeleteLocalRef(optDir);
    env->DeleteLocalRef(dexPath);
    env->DeleteLocalRef(dclClass);
  }

  if (!loader) {
    KLOGE(kLogTag, "failed to build any class loader");
    writeInitTrace("failed to build any class loader");
    return kRunClassLoaderFailed;
  }
  writeInitTrace("class loader ready=%p", loader);

  jclass loaderClass = env->GetObjectClass(loader);
  jmethodID dclLoad = env->GetMethodID(
      loaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");

  jstring injectClassName = env->NewStringUTF("com.kail.location.inject.fakelocation.InjectDex");
  jclass injectClass = (jclass)env->CallObjectMethod(loader, dclLoad, injectClassName);
  if (env->ExceptionCheck()) {
    writeInitTrace("failed to load InjectDex class: exception");
    env->ExceptionDescribe();
    env->ExceptionClear();
    KLOGE(kLogTag, "failed to load InjectDex class");
    return kRunLoadClassFailed;
  }
  if (!injectClass) {
    writeInitTrace("failed to load InjectDex class: null");
    KLOGE(kLogTag, "InjectDex class is NULL");
    return kRunLoadClassFailed;
  }

  if (sessionHookerPath && sessionHookerPath[0]) {
    jmethodID setHookPath = env->GetStaticMethodID(
        injectClass, "setHookLibraryPath", "(Ljava/lang/String;)V");
    if (setHookPath) {
      jstring hookPath = env->NewStringUTF(sessionHookerPath);
      writeInitTrace("setting LHooker session path=%s", sessionHookerPath);
      env->CallStaticVoidMethod(injectClass, setHookPath, hookPath);
      env->DeleteLocalRef(hookPath);
      if (env->ExceptionCheck()) {
        writeInitTrace("setHookLibraryPath exception");
        env->ExceptionDescribe();
        env->ExceptionClear();
      }
    } else {
      writeInitTrace("setHookLibraryPath method not found");
      if (env->ExceptionCheck())
        env->ExceptionClear();
    }
  } else {
    writeInitTrace("no LHooker session path in arg");
  }

  jmethodID initMethod = env->GetStaticMethodID(
      injectClass, "init", "(Ljava/lang/Object;)[Ljava/lang/Object;");
  if (!initMethod) {
    writeInitTrace("InjectDex.init method not found");
    KLOGE(kLogTag, "InjectDex.init method not found");
    return kRunInitMethodMissing;
  }
  writeInitTrace("calling InjectDex.init");
  jobject initResult = env->CallStaticObjectMethod(injectClass, initMethod, context);
  if (env->ExceptionCheck()) {
    writeInitTrace("InjectDex.init exception");
    env->ExceptionDescribe();
    env->ExceptionClear();
    return kRunInitException;
  }
  writeInitTrace("InjectDex.init returned result=%p", initResult);
  if (initResult) {
    env->DeleteLocalRef(initResult);
  }

  KLOGI(kLogTag, "InitApp is finished.");
  writeInitTrace("InitApp finished");

  env->DeleteLocalRef(context);
  env->DeleteLocalRef(ctxClass);
  env->DeleteLocalRef(parentLoader);
  env->DeleteLocalRef(injectClassName);
  return kRunSuccess;
}

// ---------------------------------------------------------------------------
// doRun  (sub_2A38)
// ---------------------------------------------------------------------------
extern "C" __attribute__((visibility("default"))) uint64_t doRun(JavaVM **vmPtr, const char *arg) {
  writeInitTrace("doRun entered arg=%s", arg ? arg : "<null>");
  if (gInitLoaded) {
    KLOGE(kLogTag, "-- Already loaded");
    writeInitTrace("doRun aborted: already loaded");
    return kRunAlreadyLoaded;
  }
  gInitLoaded = true;

  if (!vmPtr) {
    KLOGE(kLogTag, "JavaVM** == NULL");
    writeInitTrace("JavaVM** == NULL");
    gInitLoaded = false;
    return kRunNullVmPtr;
  }
  JavaVM *vm = *vmPtr;
  if (!vm) {
    KLOGE(kLogTag, "JavaVM* == NULL");
    writeInitTrace("JavaVM* == NULL");
    gInitLoaded = false;
    return kRunNullVm;
  }

  JNIEnv *env = nullptr;
  if (vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
    KLOGE(kLogTag, "AttachCurrentThread (main) != JNI_OK");
    writeInitTrace("AttachCurrentThread failed");
    gInitLoaded = false;
    return kRunAttachFailed;
  }
  writeInitTrace("AttachCurrentThread ok env=%p", env);
  uint64_t result = init(env, sessionHookerPathFromArg(arg));
  writeInitTrace("doRun finished result=0x%llx", (unsigned long long)result);
  if (result != kRunSuccess) {
    gInitLoaded = false;
  }
  return result;
}
