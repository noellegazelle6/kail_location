plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.kail.location"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kail.location"
        minSdk = 27
        targetSdk = 36
        versionCode = 39
        versionName = "1.6.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        buildConfigField("String", "ADMIN_API_URL", "\"https://adminkaillocation.kaillocation.xyz/admin-api\"")
        buildConfigField("String", "APP_API_URL", "\"https://adminkaillocation.kaillocation.xyz/app-api\"")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
        prefab = true
        aidl = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libs")
        }
    }
}

// Copy kail_inject executable from CMake build output into merged native libs,
// renaming to .so suffix so AGP packages it into APK lib/<abi>/libkail_inject.so
tasks.whenTaskAdded {
    if (name == "mergeDebugNativeLibs") {
        doFirst {
            val cmakeDebugDir = file("${buildDir}/intermediates/cxx/Debug")
            if (cmakeDebugDir.exists()) {
                cmakeDebugDir.listFiles()?.forEach { hashDir ->
                    if (hashDir.isDirectory) {
                        val arm64Exe = file("${hashDir.absolutePath}/obj/arm64-v8a/kail_inject")
                        if (arm64Exe.exists()) {
                            copy {
                                from(arm64Exe)
                                into("${buildDir}/intermediates/merged_native_libs/debug/out/lib/arm64-v8a/")
                                rename { "libkail_inject.so" }
                            }
                        }
                        val armExe = file("${hashDir.absolutePath}/obj/armeabi-v7a/kail_inject")
                        if (armExe.exists()) {
                            copy {
                                from(armExe)
                                into("${buildDir}/intermediates/merged_native_libs/debug/out/lib/armeabi-v7a/")
                                rename { "libkail_inject.so" }
                            }
                        }
                    }
                }
            }
        }
    }
    if (name == "mergeReleaseNativeLibs") {
        doFirst {
            val cmakeReleaseDir = file("${buildDir}/intermediates/cxx/RelWithDebInfo")
            if (!cmakeReleaseDir.exists()) {
                val altDir = file("${buildDir}/intermediates/cxx/Release")
                if (altDir.exists()) {
                    altDir.listFiles()?.forEach { hashDir ->
                        if (hashDir.isDirectory) {
                            val arm64Exe = file("${hashDir.absolutePath}/obj/arm64-v8a/kail_inject")
                            if (arm64Exe.exists()) {
                                copy {
                                    from(arm64Exe)
                                    into("${buildDir}/intermediates/merged_native_libs/release/out/lib/arm64-v8a/")
                                    rename { "libkail_inject.so" }
                                }
                            }
                            val armExe = file("${hashDir.absolutePath}/obj/armeabi-v7a/kail_inject")
                            if (armExe.exists()) {
                                copy {
                                    from(armExe)
                                    into("${buildDir}/intermediates/merged_native_libs/release/out/lib/armeabi-v7a/")
                                    rename { "libkail_inject.so" }
                                }
                            }
                        }
                    }
                }
            } else {
                cmakeReleaseDir.listFiles()?.forEach { hashDir ->
                    if (hashDir.isDirectory) {
                        val arm64Exe = file("${hashDir.absolutePath}/obj/arm64-v8a/kail_inject")
                        if (arm64Exe.exists()) {
                            copy {
                                from(arm64Exe)
                                into("${buildDir}/intermediates/merged_native_libs/release/out/lib/arm64-v8a/")
                                rename { "libkail_inject.so" }
                            }
                        }
                        val armExe = file("${hashDir.absolutePath}/obj/armeabi-v7a/kail_inject")
                        if (armExe.exists()) {
                            copy {
                                from(armExe)
                                into("${buildDir}/intermediates/merged_native_libs/release/out/lib/armeabi-v7a/")
                                rename { "libkail_inject.so" }
                            }
                        }
                    }
                }
            }
        }
    }
    if (name == "stripDebugDebugSymbols") {
        doLast {
            val mergedDir = file("${buildDir}/intermediates/merged_native_libs/debug/out/lib")
            val strippedDir = file("${buildDir}/intermediates/stripped_native_libs/debug/stripDebugDebugSymbols/out/lib")
            if (mergedDir.exists() && strippedDir.exists()) {
                listOf("arm64-v8a", "armeabi-v7a").forEach { abi ->
                    val src = file("${mergedDir.absolutePath}/$abi/libkail_inject.so")
                    if (src.exists()) {
                        copy {
                            from(src)
                            into("${strippedDir.absolutePath}/$abi/")
                            rename { "libkail_inject.so" }
                        }
                    }
                }
            }
        }
    }
    if (name == "stripReleaseDebugSymbols") {
        doLast {
            val mergedDir = file("${buildDir}/intermediates/merged_native_libs/release/out/lib")
            val strippedDir = file("${buildDir}/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib")
            if (mergedDir.exists() && strippedDir.exists()) {
                listOf("arm64-v8a", "armeabi-v7a").forEach { abi ->
                    val src = file("${mergedDir.absolutePath}/$abi/libkail_inject.so")
                    if (src.exists()) {
                        copy {
                            from(src)
                            into("${strippedDir.absolutePath}/$abi/")
                            rename { "libkail_inject.so" }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// Build a slim DEX containing only the bootstrap classes the FakeLocation
// loader needs (inject.* + lib.lhooker.*). This file is bundled in
// assets/inject.dex and RootDeployer drops it on-device as
// /data/kail-loc/libfakeloc.so.
//
// Without this, the loader's DexClassLoader had to compile/verify the host
// APK's full 33MB dex (UI / Compose / Firebase / Kotlin stdlib / Room / ...)
// inside system_server, which on Android 14 ART regularly takes >60s,
// triggering the ptrace watchdog and killing system_server.
//
// We avoid pulling in the AGP-internal D8 helper (path is unstable across
// AGP versions). Instead we look for any `classes*.dex` AGP already produced
// for this variant under .../intermediates/dex/.../mergeProjectDex.../, then
// use Android's own dexdump-like surgery via a minimal in-Java helper:
// pack only the classes we want into an InMemoryDexClassLoader-compatible
// dex via a Java-class -> dex compile in a temp folder using whichever d8
// jar is already on the AGP build classpath.
// ============================================================================
val injectClassPrefixes = listOf(
    "com/kail/location/inject/",
    "com/kail/location/lib/lhooker/"
)

fun findD8Jar(): java.io.File? {
    val sdkDir = android.sdkDirectory
    val candidates = mutableListOf<java.io.File>()
    candidates.add(file("${sdkDir.absolutePath}/cmdline-tools/latest/lib/r8.jar"))
    candidates.add(file("${sdkDir.absolutePath}/cmdline-tools/latest/lib/d8.jar"))
    fileTree("${sdkDir.absolutePath}/cmdline-tools").matching {
        include("**/lib/r8.jar")
        include("**/lib/d8.jar")
    }.forEach { candidates.add(it) }
    val gradleHome = gradle.gradleUserHomeDir
    fileTree(gradleHome) {
        include("caches/**/r8-*.jar")
        include("caches/**/r8/**/r8.jar")
    }.forEach { candidates.add(it) }
    return candidates.firstOrNull { it.exists() && it.length() > 0 }
}

androidComponents {
    onVariants { variant ->
        val variantNameCap = variant.name.replaceFirstChar { it.uppercase() }
        val outDir = layout.buildDirectory.dir("intermediates/inject_dex/${variant.name}")
        val outFile = outDir.map { it.file("inject.dex") }

        val buildSlimDex = tasks.register("build${variantNameCap}InjectSlimDex") {
            group = "kail"
            description = "Build slim DEX with only inject + lhooker classes for FakeLocation loader."

            dependsOn("compile${variantNameCap}JavaWithJavac")
            dependsOn("compile${variantNameCap}Kotlin")

            doLast {
                val r8Jar = findD8Jar()
                if (r8Jar == null) {
                    logger.warn("kail: r8.jar not found in gradle caches; slim dex skipped (host APK will be loaded as fallback).")
                    return@doLast
                }

                outDir.get().asFile.mkdirs()

                val classDirs = listOf(
                    file("${buildDir}/intermediates/javac/${variant.name}/classes"),
                    file("${buildDir}/intermediates/javac/${variant.name}/compile${variantNameCap}JavaWithJavac/classes"),
                    file("${buildDir}/tmp/kotlin-classes/${variant.name}")
                ).filter { it.exists() }

                if (classDirs.isEmpty()) {
                    logger.warn("kail: no class directories found for ${variant.name}; slim dex skipped.")
                    return@doLast
                }

                val staging = file("${buildDir}/tmp/inject_slim_${variant.name}")
                staging.deleteRecursively()
                staging.mkdirs()

                var copied = 0
                classDirs.forEach { root ->
                    fileTree(root).forEach { f ->
                        if (!f.name.endsWith(".class")) return@forEach
                        val rel = f.toRelativeString(root).replace(File.separatorChar, '/')
                        if (injectClassPrefixes.any { rel.startsWith(it) }) {
                            val dst = file("${staging.absolutePath}/$rel")
                            dst.parentFile.mkdirs()
                            f.copyTo(dst, overwrite = true)
                            copied++
                        }
                    }
                }

                if (copied == 0) {
                    logger.warn("kail: no inject/lhooker classes copied; slim dex skipped.")
                    return@doLast
                }

                // D8 wants a jar (or .class with --classpath). Pack the staging
                // tree into a jar first.
                val stagingJar = file("${buildDir}/tmp/inject_slim_${variant.name}.jar")
                if (stagingJar.exists()) stagingJar.delete()
                ant.invokeMethod("jar", mapOf(
                    "destfile" to stagingJar.absolutePath,
                    "basedir" to staging.absolutePath
                ))

                logger.lifecycle("kail: building slim inject dex from $copied .class files via $r8Jar (jar=${stagingJar.length()} bytes)")

                val androidJarCandidate = file("${android.sdkDirectory}/platforms/android-${android.compileSdk}/android.jar")
                val libJars = mutableListOf<java.io.File>()
                if (androidJarCandidate.exists()) libJars.add(androidJarCandidate)
                // Provide the rest of the app's compiled classes as classpath
                // (NOT program input) so D8 can resolve references while
                // desugaring nestmate access. Only the staged inject/lhooker
                // classes become actual dex output.
                val cpJars = mutableListOf<java.io.File>()
                listOf(
                    file("${buildDir}/intermediates/javac/${variant.name}/classes"),
                    file("${buildDir}/intermediates/javac/${variant.name}/compile${variantNameCap}JavaWithJavac/classes"),
                    file("${buildDir}/tmp/kotlin-classes/${variant.name}")
                ).filter { it.exists() }.forEach { cpJars.add(it) }

                val cmd = mutableListOf(
                    "java", "-cp", r8Jar.absolutePath,
                    "com.android.tools.r8.D8",
                    "--debug",
                    "--min-api", "27",
                    "--output", outDir.get().asFile.absolutePath
                )
                libJars.forEach {
                    cmd.add("--lib")
                    cmd.add(it.absolutePath)
                }
                cpJars.forEach {
                    cmd.add("--classpath")
                    cmd.add(it.absolutePath)
                }
                cmd.add(stagingJar.absolutePath)

                val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
                val out = proc.inputStream.bufferedReader().readText()
                val rc = proc.waitFor()
                if (rc != 0) {
                    logger.warn("kail: D8 failed (rc=$rc), output:\n$out")
                    return@doLast
                }

                val produced = file("${outDir.get().asFile.absolutePath}/classes.dex")
                val dst = outFile.get().asFile
                if (produced.exists()) {
                    // Deploy the BARE dex (not zip-wrapped): the on-device
                    // loader uses InMemoryDexClassLoader(ByteBuffer), which
                    // wants raw .dex bytes, and that path needs no on-disk
                    // oat (so no SELinux-denied write inside system_server).
                    produced.copyTo(dst, overwrite = true)
                    produced.delete()
                    val sourceAssets = file("src/main/assets")
                    sourceAssets.mkdirs()
                    dst.copyTo(file("${sourceAssets.absolutePath}/inject.dex"), overwrite = true)
                    val mergedAssets = file("${buildDir}/intermediates/assets/${variant.name}/merge${variantNameCap}Assets")
                    if (mergedAssets.exists()) {
                        dst.copyTo(file("${mergedAssets.absolutePath}/inject.dex"), overwrite = true)
                    }
                    val mergedAssetsAlt = file("${buildDir}/intermediates/merged_assets/${variant.name}/merge${variantNameCap}Assets/out")
                    if (mergedAssetsAlt.exists()) {
                        dst.copyTo(file("${mergedAssetsAlt.absolutePath}/inject.dex"), overwrite = true)
                    }
                    logger.lifecycle("kail: slim inject dex (bare) ready: ${dst.absolutePath} (${dst.length()} bytes, was ${copied} classes)")
                } else {
                    logger.warn("kail: D8 ran but classes.dex not produced; output was:\n$out")
                }
            }
        }

        tasks.whenTaskAdded {
            if (name == "merge${variantNameCap}Assets" || name == "package${variantNameCap}Assets") {
                dependsOn(buildSlimDex)
                doLast {
                    val src = outFile.get().asFile
                    if (!src.exists()) return@doLast
                    // Drop the slim dex into both the input source-set assets
                    // (so it's picked up by the assets merger on incremental
                    // builds) and the merged output (in case the merger has
                    // already run).
                    val sourceAssets = file("src/main/assets")
                    sourceAssets.mkdirs()
                    src.copyTo(file("${sourceAssets.absolutePath}/inject.dex"), overwrite = true)
                    val mergedAssets = file("${buildDir}/intermediates/assets/${variant.name}/merge${variantNameCap}Assets")
                    if (mergedAssets.exists()) {
                        src.copyTo(file("${mergedAssets.absolutePath}/inject.dex"), overwrite = true)
                    }
                    val mergedAssetsAlt = file("${buildDir}/intermediates/merged_assets/${variant.name}/merge${variantNameCap}Assets/out")
                    if (mergedAssetsAlt.exists()) {
                        src.copyTo(file("${mergedAssetsAlt.absolutePath}/inject.dex"), overwrite = true)
                    }
                    logger.lifecycle("kail: dropped slim dex into assets (${src.length()} bytes)")
                }
            }
        }
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.preference)
    implementation(libs.material)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.noties.markwon:core:4.6.2")

    // ShadowHook
    implementation("com.bytedance.android:shadowhook:1.0.9")

    // Dobby
    implementation("io.github.vvb2060.ndk:dobby:1.2")

    // Compose dependencies (keep them for future use or mixed usage)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("com.google.zxing:core:3.5.1")
    implementation("androidx.lifecycle:lifecycle-process:2.8.3")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    val room_version = "2.7.0"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // NewBlackbox sandbox core module
    implementation(project(":NewBlackbox:Bcore"))
}
