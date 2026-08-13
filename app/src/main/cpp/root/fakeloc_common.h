// fakeloc_common.h
//
// Shared helpers used by the reconstructed FakeLocation JNI loader libraries
// (libfakeloc_apphook / libfakeloc_init / libfakeloc_initzygote, both ABIs).
//
// The original decompiled sources each inlined their own copy of:
//   - a standard RFC 1321 MD5 implementation (getFileMD5 / MD5Init / MD5Update
//     / MD5Final),
//   - an APK integrity check (verifyApkMd5 / c5),
//   - a release-signature check (verifyReleaseSignature / sq),
//   - a small set of JNI helpers (getJavaVM, getJniEnv, getGlobalContext,
//     concatString, Jstring2CStr, getSystemClassLoader).
//
// They are factored out here so the per-target reconstructions stay readable.
// The MD5 below is the canonical algorithm; it produces the same digests the
// hand-vectorised NEON version in the decompilation produced.

#ifndef FAKELOC_COMMON_H
#define FAKELOC_COMMON_H

#include <cstdint>
#include <cstdio>
#include <cstring>

#include <fcntl.h>
#include <unistd.h>
#include <dlfcn.h>
#include <dirent.h>

#include <jni.h>
#include <android/log.h>

#include "kail_log.h"

namespace fakeloc {

static const char *kLogTag = "LINJECT.native";

// Resolve the on-disk payload .dex path at runtime by scanning for
// libfakeloc_v*.so.  This avoids baking a version-suffixed path at build time
// and eliminates the need for a standard-name symlink.
static const char *resolvePayloadPath() {
  static char buf[256];
  // First check whether we already resolved it.
  if (buf[0] != '\0') return buf;

  DIR *dir = opendir("/data/kail-loc");
  if (!dir) return "/data/kail-loc/libfakeloc.so";   // fallback

  struct dirent *ent;
  while ((ent = readdir(dir)) != nullptr) {
    if (strncmp(ent->d_name, "libfakeloc_v", 12) == 0 &&
        strcmp(ent->d_name + strlen(ent->d_name) - 3, ".so") == 0) {
      snprintf(buf, sizeof(buf), "/data/kail-loc/%s", ent->d_name);
      closedir(dir);
      return buf;
    }
  }
  closedir(dir);
  return "/data/kail-loc/libfakeloc.so";  // fallback
}
#define kPayloadPath (fakeloc::resolvePayloadPath())

// MD5 gate for the on-disk payload.
//
// The original framework checked a fixed digest of the released payload.
// For the kail rebrand we cannot bake in a digest at build time because the
// payload is rebuilt with a fresh hash each release. The empty string is
// treated as "skip" by verifyApkMd5() below; set this to the real digest in a
// release config if you need the integrity gate back.
static const char *kPayloadMd5  = "";

// Release signing certificate (DER hex). This must match
// PackageInfo.signatures[0].toCharsString() of the host package on device.
//
// 签名检验已移除：置空字符串，verifyReleaseSignature() 据此跳过所有签名比较，
// 任何签名（debug/release/自签）的宿主包都能通过校验。
// 如需恢复，填入对应证书 DER hex 即可。
static const char *kReleaseSign = "";

// Stash the debug keystore cert hex here for easy re-enabling later:
static const char *kReleaseSign_DebugKeystore =
    "308202e4308201cc020101300d06092a864886f70d01010b050030373116301406035504030c0d416e64726f"
    "69642044656275673110300e060355040a0c07416e64726f6964310b30090603550406130255533020170d32"
    "36303431323039353132355a180f32303536303430343039353132355a30373116301406035504030c0d416e"
    "64726f69642044656275673110300e060355040a0c07416e64726f6964310b3009060355040613025553308201"
    "22300d06092a864886f70d01010105000382010f003082010a0282010100eba93aafaa946096f144871949"
    "4ad610e32268b516ae8adff2468470aaca78abc9415fedd6b889bfaf5ce8fd877c7af1bc1312bcee5d544c4d1"
    "f2bf089aea8858735fe71754305a9e3caca12592553b832639962b145f0f768a24ef50ef2a1fbeb40a677aa3e"
    "56995cebe7f58e5897ff24dbb25a2f579a4b29ceb36409c23513f3d04b6ab986d3af6176f5fd6041cd1212330"
    "e0b47ff2c57043c560c88d213c2c97ba8da87edbdac8d1c59b23826985dfdf1e4a833fa591fce259ab12f57d"
    "c1b49ec83c11627fb87646beec839a81a26f26a02097d7f8b8a6e325bda16d4b17e8521c4d744040d0ddb218"
    "4b57653e5e5d1cc8a2067651fc2c99f702a97b54b150203010001300d06092a864886f70d01010b0500038201"
    "01006a11123643b8f7f2b2363d3ebf7ca8b8c08093f318cc1cc9108d51514cf6b7721c471e9e2245401bd3c0"
    "912bdcab2b10e2380938fdc282d18d10fee2d211acc4025c4fba9012e653bd1105452774463e58a863270009"
    "909ffc2aeeb253707363a16eaab4b637821deec16242bf9487685dcd1afe71d16b87fbc029c06539746634f0"
    "dfc3ba979e236a6128eef87ebebdbb34cb2f7aa4c0a903a092b102616461bffa01b0c0545842f14153e263ec"
    "6e3be3df858bc090048637c9b2128cb42c64f417b80dc219e073a72699b677db563ba69ddab6689b7974626d"
    "d058a65e780221b0e2e6a0b6ee4a097f487c7c8be762a11c711505c492a6131ab7df78639eaf";

// Owning package on device. Re-pointed at the kail consolidated app.
static const char *kPackageName = "com.kail.location";

// ===========================================================================
// MD5 (RFC 1321)
// ===========================================================================
struct Md5Ctx {
  uint32_t count[2];   // bit count (lo, hi)
  uint32_t state[4];   // A,B,C,D
  uint8_t  buffer[64];
};

inline void md5Init(Md5Ctx *c) {
  c->count[0] = c->count[1] = 0;
  c->state[0] = 0x67452301;
  c->state[1] = 0xefcdab89;
  c->state[2] = 0x98badcfe;
  c->state[3] = 0x10325476;
}

inline void md5Transform(uint32_t state[4], const uint8_t block[64]) {
  auto F = [](uint32_t x, uint32_t y, uint32_t z) { return (x & y) | (~x & z); };
  auto G = [](uint32_t x, uint32_t y, uint32_t z) { return (x & z) | (y & ~z); };
  auto H = [](uint32_t x, uint32_t y, uint32_t z) { return x ^ y ^ z; };
  auto I = [](uint32_t x, uint32_t y, uint32_t z) { return y ^ (x | ~z); };
  auto ROTL = [](uint32_t x, int n) { return (x << n) | (x >> (32 - n)); };

  uint32_t x[16];
  for (int i = 0; i < 16; ++i)
    x[i] = (uint32_t)block[i * 4] | ((uint32_t)block[i * 4 + 1] << 8) |
           ((uint32_t)block[i * 4 + 2] << 16) | ((uint32_t)block[i * 4 + 3] << 24);

  uint32_t a = state[0], b = state[1], c = state[2], d = state[3];

  static const uint32_t K[64] = {
      0xd76aa478,0xe8c7b756,0x242070db,0xc1bdceee,0xf57c0faf,0x4787c62a,0xa8304613,0xfd469501,
      0x698098d8,0x8b44f7af,0xffff5bb1,0x895cd7be,0x6b901122,0xfd987193,0xa679438e,0x49b40821,
      0xf61e2562,0xc040b340,0x265e5a51,0xe9b6c7aa,0xd62f105d,0x02441453,0xd8a1e681,0xe7d3fbc8,
      0x21e1cde6,0xc33707d6,0xf4d50d87,0x455a14ed,0xa9e3e905,0xfcefa3f8,0x676f02d9,0x8d2a4c8a,
      0xfffa3942,0x8771f681,0x6d9d6122,0xfde5380c,0xa4beea44,0x4bdecfa9,0xf6bb4b60,0xbebfbc70,
      0x289b7ec6,0xeaa127fa,0xd4ef3085,0x04881d05,0xd9d4d039,0xe6db99e5,0x1fa27cf8,0xc4ac5665,
      0xf4292244,0x432aff97,0xab9423a7,0xfc93a039,0x655b59c3,0x8f0ccc92,0xffeff47d,0x85845dd1,
      0x6fa87e4f,0xfe2ce6e0,0xa3014314,0x4e0811a1,0xf7537e82,0xbd3af235,0x2ad7d2bb,0xeb86d391};
  static const int S[64] = {
      7,12,17,22, 7,12,17,22, 7,12,17,22, 7,12,17,22,
      5, 9,14,20, 5, 9,14,20, 5, 9,14,20, 5, 9,14,20,
      4,11,16,23, 4,11,16,23, 4,11,16,23, 4,11,16,23,
      6,10,15,21, 6,10,15,21, 6,10,15,21, 6,10,15,21};

  for (int i = 0; i < 64; ++i) {
    uint32_t f;
    int g;
    if (i < 16)      { f = F(b, c, d); g = i; }
    else if (i < 32) { f = G(b, c, d); g = (5 * i + 1) & 15; }
    else if (i < 48) { f = H(b, c, d); g = (3 * i + 5) & 15; }
    else             { f = I(b, c, d); g = (7 * i) & 15; }
    uint32_t tmp = d;
    d = c;
    c = b;
    b = b + ROTL(a + f + K[i] + x[g], S[i]);
    a = tmp;
  }

  state[0] += a;
  state[1] += b;
  state[2] += c;
  state[3] += d;
}

inline void md5Update(Md5Ctx *c, const uint8_t *data, size_t len) {
  size_t index = (c->count[0] >> 3) & 0x3f;
  uint32_t bits = (uint32_t)(len << 3);
  if ((c->count[0] += bits) < bits)
    c->count[1]++;
  c->count[1] += (uint32_t)(len >> 29);

  size_t partLen = 64 - index;
  size_t i = 0;
  if (len >= partLen) {
    memcpy(&c->buffer[index], data, partLen);
    md5Transform(c->state, c->buffer);
    for (i = partLen; i + 63 < len; i += 64)
      md5Transform(c->state, &data[i]);
    index = 0;
  }
  memcpy(&c->buffer[index], &data[i], len - i);
}

inline void md5Final(Md5Ctx *c, uint8_t digest[16]) {
  static const uint8_t kPadding[64] = {0x80};
  uint8_t bits[8];
  for (int i = 0; i < 8; ++i)
    bits[i] = (uint8_t)(c->count[i >> 2] >> ((i & 3) * 8));

  size_t index = (c->count[0] >> 3) & 0x3f;
  size_t padLen = (index < 56) ? (56 - index) : (120 - index);
  md5Update(c, kPadding, padLen);
  md5Update(c, bits, 8);

  for (int i = 0; i < 4; ++i) {
    digest[i * 4]     = (uint8_t)(c->state[i]);
    digest[i * 4 + 1] = (uint8_t)(c->state[i] >> 8);
    digest[i * 4 + 2] = (uint8_t)(c->state[i] >> 16);
    digest[i * 4 + 3] = (uint8_t)(c->state[i] >> 24);
  }
}

// getFileMD5: hash the file at path into a static 33-byte hex buffer.
inline const char *getFileMD5(const char *path) {
  static char hex[33];
  int fd = open(path, O_RDONLY);
  if (fd == -1)
    return nullptr;

  Md5Ctx ctx;
  md5Init(&ctx);

  uint8_t buf[1024];
  ssize_t n;
  while ((n = read(fd, buf, sizeof(buf))) > 0) {
    md5Update(&ctx, buf, (size_t)n);
    if (n < 1024)
      break;
  }
  if (n == -1) {
    close(fd);
    return nullptr;
  }
  close(fd);

  uint8_t digest[16];
  md5Final(&ctx, digest);
  for (int i = 0; i < 16; ++i)
    snprintf(&hex[i * 2], 3, "%02x", digest[i]);
  hex[32] = 0;
  return hex;
}

// verifyApkMd5 (c5): 0 when the payload library matches the expected digest.
// An empty kPayloadMd5 is treated as "integrity check disabled" — the file
// just needs to be readable.  This is the kail-rebrand default since the
// payload is rebuilt with a fresh digest on each release.
inline int verifyApkMd5() {
  if (kPayloadMd5 == nullptr || kPayloadMd5[0] == '\0') {
    // Still verify the file exists and is readable; that catches the case
    // where /data/kail-loc/libfakeloc.so wasn't staged.
    const char *md5 = getFileMD5(kPayloadPath);
    return md5 ? 0 : -1;
  }
  const char *md5 = getFileMD5(kPayloadPath);
  return md5 ? strcmp(kPayloadMd5, md5) : -1;
}

// ===========================================================================
// JNI helpers
// ===========================================================================

// Obtain the already-created JavaVM by dlopen()ing the running runtime and
// calling JNI_GetCreatedJavaVMs.  (getJavaVM)
inline JavaVM *getJavaVM() {
  static const char *kRuntimeLibs[] = {"libart.so", "libdvm.so"};
  void *handle = nullptr;
  for (size_t i = 0; i < 2 && !handle; ++i) {
    dlerror();
    handle = dlopen(nullptr, RTLD_NOW);   // resolve against the current image
    const char *err = dlerror();
    if (err)
      KLOGI(kLogTag, "failed to load %s: %s", kRuntimeLibs[i], err);
    if (handle)
      KLOGI(kLogTag, "Android runtime loaded from %s", kRuntimeLibs[i]);
  }
  if (!handle) {
    KLOGI(kLogTag, "Failed to get jvm");
    return nullptr;
  }

  using GetCreatedFn = jint (*)(JavaVM **, jsize, jsize *);
  dlerror();
  auto getCreated = (GetCreatedFn)dlsym(handle, "JNI_GetCreatedJavaVMs");
  const char *err = dlerror();
  if (err) {
    KLOGI(kLogTag, "dlsym(\"JNI_GetCreatedJavaVMs\") failed: %s", err);
    return nullptr;
  }

  JavaVM *vm = nullptr;
  jsize count = 0;
  getCreated(&vm, 1, &count);
  if (count <= 0)
    KLOGI(kLogTag, "get_created_java_vms returned %d jvms, jvm: %p", count, (void *)vm);
  else
    KLOGI(kLogTag, "found existing jvm");
  dlclose(handle);
  return vm;
}

// get_jni_env_from_jvm: GetEnv, attaching the current thread if needed.
inline JNIEnv *getJniEnvFromJvm(JavaVM *vm) {
  if (!vm)
    return nullptr;
  KLOGI(kLogTag, "jvm->GetEnv ...");
  JNIEnv *env = nullptr;
  jint err = vm->GetEnv((void **)&env, JNI_VERSION_1_6);
  if (err == JNI_OK) {
    KLOGI(kLogTag, "jvm->GetEnv() JNI_OK");
  } else if (err == JNI_EDETACHED) {
    KLOGI(kLogTag, "JNI_EDETACHED == err");
    vm->AttachCurrentThread(&env, nullptr);
  } else {
    KLOGI(kLogTag, "jvm->GetEnv() failed");
  }
  return env;
}

inline JNIEnv *getJniEnv() {
  JavaVM *vm = getJavaVM();
  return getJniEnvFromJvm(vm);
}

// String.concat(b) on a + b, both C strings.  (concatString)
inline jstring concatString(JNIEnv *env, const char *a, const char *b) {
  jstring sa = env->NewStringUTF(a);
  jclass strClass = env->FindClass("java/lang/String");
  jmethodID concat = env->GetMethodID(strClass, "concat", "(Ljava/lang/String;)Ljava/lang/String;");
  jstring sb = env->NewStringUTF(b);
  jstring result = (jstring)env->CallObjectMethod(sa, concat, sb);
  env->DeleteLocalRef(sa);
  env->DeleteLocalRef(sb);
  return result;
}

// ActivityThread.currentActivityThread().getApplication()  (getGlobalContext)
inline jobject getGlobalContext(JNIEnv *env) {
  jclass at = env->FindClass("android/app/ActivityThread");
  jmethodID current = env->GetStaticMethodID(at, "currentActivityThread",
                                             "()Landroid/app/ActivityThread;");
  jobject thread = env->CallStaticObjectMethod(at, current);
  jmethodID getApp = env->GetMethodID(at, "getApplication", "()Landroid/app/Application;");
  jobject app = thread ? env->CallObjectMethod(thread, getApp) : nullptr;
  env->DeleteLocalRef(at);
  if (thread)
    env->DeleteLocalRef(thread);
  return app;
}

// ClassLoader.getSystemClassLoader()  (getSystemClassLoader)
inline jobject getSystemClassLoader(JNIEnv *env) {
  KLOGI(kLogTag, "getSystemClassLoader is Executing...");
  jclass cl = env->FindClass("java/lang/ClassLoader");
  jmethodID m = env->GetStaticMethodID(cl, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
  jobject loader = env->CallStaticObjectMethod(cl, m);
  KLOGI(kLogTag, "getSystemClassLoader is finished!!");
  env->DeleteLocalRef(cl);
  return loader;
}

// verifyReleaseSignature (sq): 0 when the host package is signed with the
// expected release certificate.  Returns -2 when no context is available and
// -1 on signature mismatch.  An empty kReleaseSign skips the comparison so
// debug builds work without a baked-in cert hash.
inline int verifyReleaseSignature(JNIEnv *env) {
  if (kReleaseSign == nullptr || kReleaseSign[0] == '\0') {
    return 0;
  }
  jobject context = getGlobalContext(env);
  if (!context)
    return -2;

  jclass ctxClass = env->FindClass("android/content/Context");
  jclass pmClass  = env->FindClass("android/content/pm/PackageManager");
  jclass piClass  = env->FindClass("android/content/pm/PackageInfo");

  jmethodID getPm = env->GetMethodID(ctxClass, "getPackageManager",
                                     "()Landroid/content/pm/PackageManager;");
  jobject pm = env->CallObjectMethod(context, getPm);

  jmethodID getPkgInfo = env->GetMethodID(
      pmClass, "getPackageInfo",
      "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
  jstring pkg = env->NewStringUTF(kPackageName);
  jobject info = env->CallObjectMethod(pm, getPkgInfo, pkg, 64 /*GET_SIGNATURES*/);
  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    env->DeleteLocalRef(ctxClass);
    env->DeleteLocalRef(pmClass);
    env->DeleteLocalRef(piClass);
    return -1;
  }

  jfieldID sigField = env->GetFieldID(piClass, "signatures", "[Landroid/content/pm/Signature;");
  jobjectArray sigs = (jobjectArray)env->GetObjectField(info, sigField);
  jobject sig0 = env->GetObjectArrayElement(sigs, 0);
  jclass sigClass = env->GetObjectClass(sig0);
  jmethodID toChars = env->GetMethodID(sigClass, "toCharsString", "()Ljava/lang/String;");
  jstring sigStr = (jstring)env->CallObjectMethod(sig0, toChars);
  const char *sigChars = env->GetStringUTFChars(sigStr, nullptr);

  int result = strcmp(sigChars, kReleaseSign);

  env->ReleaseStringUTFChars(sigStr, sigChars);
  env->DeleteLocalRef(ctxClass);
  env->DeleteLocalRef(pmClass);
  env->DeleteLocalRef(piClass);
  return result ? -1 : 0;
}

}  // namespace fakeloc

#endif  // FAKELOC_COMMON_H
