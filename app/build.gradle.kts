plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dualsub.tv"
    // androidx.tv:tv-foundation:1.0.0 要求 compileSdk >= 35 且 minSdk >= 23
    compileSdk = 35
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.dualsub.tv"
        minSdk = 23
        // 保持 34：升到 35 会引入强制 edge-to-edge 等行为变更，与本次目标无关
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // BouncyCastle 会与 jspecify 等 jar 争夺同一个 OSGI 清单文件路径，
            // 不做 pickFirst 会在打包阶段直接失败。
            pickFirsts += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "META-INF/*.version"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose 与 TV Compose（版本已在 version catalog 中显式对齐）
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.foundation)

    // 播放引擎
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)
    // FFmpeg 软解扩展（本地编译）：解决 DTS / TrueHD / EAC3 / AC3 等音频格式
    // 编译方式见 ~/Project/media3-1.4.1/libraries/decoder_ffmpeg/src/main/jni/build_ffmpeg_tv.sh
    implementation(files("libs/lib-decoder-ffmpeg-release.aar"))

    // 局域网 SMB（SMB2/SMB3，纯 Java）：浏览与播放
    implementation(libs.smbj) {
        // smbj 会传递 bcprov-jdk18on，它与下面显式引入的 jdk15to18 变体有大量同名类，
        // 不排除就会在 :app:mergeDebugJavaResource 报 Duplicate class。
        exclude(group = "org.bouncycastle")
    }
    // 只用来列举服务器上的共享 —— smbj 没有暴露 SRVSVC 的 NetShareEnumAll，
    // 而 jcifs-ng 对 `smb://host/` 调 listFiles() 得到的正是共享清单。
    implementation(libs.jcifs.ng) {
        // 同样会传递 bcprov-jdk18on，理由同上
        exclude(group = "org.bouncycastle")
    }
    // NTLM 需要 MD4，由 BouncyCastle 提供（Android 内置的那个包名不同、也不完整）。
    // 选 jdk15to18 变体：不依赖 java.lang.invoke，在 minSdk 23 的设备上更安全。
    implementation(libs.bcprov)

    // 持久化与协程
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.ui.tooling)
}
