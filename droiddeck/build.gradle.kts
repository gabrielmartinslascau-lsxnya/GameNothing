plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.droiddeck.launcher"
    compileSdk = 36
    ndkVersion = "27.3.13750724"

    defaultConfig {
        minSdk = 26
        // targetSdk = 28 is inherited from the host application's modern flavor

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DDROIDDECK_VITURE_SDK_DIR=",
                    "-DDROIDDECK_UVC_DIR="
                )
            }
        }

        buildConfigField("int", "VERSION_CODE", "19")
        buildConfigField("String", "VERSION_NAME", "\"0.7.1\"")
        buildConfigField("String", "BUILD_LABEL", "\"GameNothing Embedded DroidDeck v0.7.1\"")
        buildConfigField("String", "SOURCE_COMMIT", "\"d1c68f1\"")
        buildConfigField("int", "PR_NUMBER", "0")
        buildConfigField("String", "PR_TITLE", "\"\"")
        buildConfigField("String", "BUILD_BRANCH", "\"main\"")
        buildConfigField("boolean", "CI_BUILD", "false")
        buildConfigField("String", "RELEASE_SIGNER", "\"\"")
        buildConfigField("long", "COMMIT_TIME", "1731338400000L")
        buildConfigField("String", "VARIANT_SUFFIXES", "\"standard=\"")
        buildConfigField("String", "SGDB_API_KEY", "\"\"")
        buildConfigField("String", "DESKTOP_KDE_TAG", "\"desktop-kde-r3\"")
        buildConfigField("String", "DESKTOP_KDE_URL", "\"https://github.com/Droid-Deck/DroidDeck-Components/releases/download/desktop-kde-r3/desktop-kde.tar.zst\"")
        buildConfigField("String", "DESKTOP_KDE_SHA256", "\"8b7142fcec8d86143ae39fc14ffc86907d30d9a5b5a45f49cb1b55109d6fc4eb\"")
        buildConfigField("long", "DESKTOP_KDE_SIZE", "972598429L")

        buildConfigField("String", "PACK_PROOT_SHA256", "\"5a2b7284e6357f88a14a790abd54e61effbe5f202c63b6aaba9b974c559649c3\"")
        buildConfigField("long", "PACK_PROOT_SIZE", "96643L")
        buildConfigField("String", "PACK_DIRECTAUDIO_SHA256", "\"fc3deada9eb56deca7a8f930c193ad9dbcf2a2c0f4fe094edcf905a82550b33f\"")
        buildConfigField("long", "PACK_DIRECTAUDIO_SIZE", "22583L")
        buildConfigField("String", "PACK_TRACKING_SHA256", "\"968fcb3ff812c26d7f8421a2584cf42498458004c08c6f28900b09c0f4432415\"")
        buildConfigField("long", "PACK_TRACKING_SIZE", "25576818L")
        buildConfigField("String", "PACK_XBOX_SHA256", "\"24cb41e2e9901fd269471d9270f1699731b73f26a8e4e80eec6bdf88eadf0ac2\"")
        buildConfigField("long", "PACK_XBOX_SIZE", "14259109L")
        buildConfigField("String", "PACK_COMET_SHA256", "\"73cca25e9c2b9506737ff6c9714d20c74b2a2bd5036fdfbbd4d9d8c1942a103a\"")
        buildConfigField("long", "PACK_COMET_SIZE", "4187027L")
        buildConfigField("String", "PACK_BUTLER_SHA256", "\"f09fe478841a79a43267fce9050235c34207020274b8f62afe811dbe7bb725cf\"")
        buildConfigField("long", "PACK_BUTLER_SIZE", "7855451L")
        buildConfigField("String", "PACK_POWERVR_SHA256", "\"e695d3a8efd5edce13db3236a94bb5181286c6517cbfd60f99eda76d2997033b\"")
        buildConfigField("long", "PACK_POWERVR_SIZE", "33600870L")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
        prefab = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            excludes += listOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += listOf(
                "**/libffi.so",
                "**/libwayland-server.so",
                "**/libpulse.so",
                "**/libsndfile.so",
                "**/libpulseaudio.so",
                "**/libpulsecore-13.0.so",
                "**/libpulsecommon-13.0.so",
                "**/libltdl.so",
                "**/libc++_shared.so",
                "**/libtermux.so",
                "**/libhook_impl.so",
                "**/libmain_hook.so",
                "**/libfile_redirect_hook.so",
                "**/libgsl_alloc_hook.so"
            )
        }
    }
}

val stageSessionScripts by tasks.registering(Copy::class) {
    from(file("tools/linuxfs/overlay")) {
        include("usr/local/bin/droiddeck-*", "usr/local/bin/steam-compatibility", "usr/bin/**")
    }
    from(file("tools/linuxfs/desktop")) {
        into("usr/local/bin")
    }
    from(file("tools/linuxfs/innoextract/linuxfs"))
    into(file("src/main/assets/linuxfs"))
}

val stageAppLinuxfs by tasks.registering(Copy::class) {
    dependsOn(stageSessionScripts)
    from(file("src/main/assets/linuxfs"))
    into(rootProject.file("app/src/main/assets/linuxfs"))
}

tasks.named("preBuild").configure {
    dependsOn(stageSessionScripts, stageAppLinuxfs)
}

configurations.all {
    exclude(group = "com.google.protobuf", module = "protobuf-javalite")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlin.coroutines)
    implementation(libs.zstd.jni) { artifact { type = "aar" } }
    implementation(libs.apache.compress)
    implementation(libs.xz)
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("com.flyfishxu:kadb-android:1.2.1")
    implementation("com.termux.termux-app:terminal-view:0.118.0")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("io.github.webrtc-sdk:android:125.6422.07")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.khronos.openxr:openxr_loader_for_android:1.1.49")
    implementation("org.opencv:opencv:4.13.0")
    implementation("com.google.mediapipe:tasks-vision:1.0.0") {
        exclude(group = "com.google.auto.value", module = "auto-value")
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }
}
