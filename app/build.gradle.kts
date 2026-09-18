import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        // 把**构建时刻**注入 BuildConfig，界面上会显示出来。
        // 真机来回调试时最耗时的一环就是「确认装的是不是最新版」—— 之前有好几轮
        // 都在拿旧包的日志分析问题。有这行就能一眼判断，不必再靠猜。
        buildConfigField(
            "String",
            "BUILD_TIME",
            "\"${SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\""
        )
        // libVLC 的 AAR 打包了全部 4 个 ABI 的 .so（AAR 本身就 83MB），不过滤的话
        // debug APK 会从 21MB 涨到 210MB。电视是 arm64，其余三个 ABI 纯属浪费。
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // 电脑上的 Android 模拟器是 x86_64，而 defaultConfig 只留了 arm64-v8a，
            // 缺了 x86_64 的话 libVLC 的 native 库装不进去、APP 起不来。
            // 只给 debug 追加，release 仍只保留 arm64-v8a（电视真机）。
            ndk {
                abiFilters += listOf("x86_64")
            }
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
        // 上面用 buildConfigField 写了 BUILD_TIME，必须同时打开这项才会生成 BuildConfig
        buildConfig = true
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

    // 播放引擎：libVLC（内置完整 FFmpeg）
    //
    // 选它而不是 Media3/ExoPlayer 的原因：RM / RMVB 这类老格式在 Media3 架构下无解 ——
    // Media3 没有 RealMedia 的 Extractor，而 Media3 的 FFmpeg 扩展只提供解码器、
    // 不做容器解析，所以「自己写 Extractor」也会卡在没有解码器上。VLC 两样都有。
    implementation(libs.libvlc.all)

    // Media3 保留：仍用于格式/编码信息读取等周边能力
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)
    // FFmpeg 软解扩展（本地编译）：解决 DTS / TrueHD / EAC3 / AC3 等音频格式。
    // 改用 libVLC 播放后这个 AAR 对播放链路已不是必需（VLC 自带完整 FFmpeg），
    // 暂时保留以免影响其它引用点的编译。
    implementation(files("libs/lib-decoder-ffmpeg-release.aar"))

    // 局域网 SMB（SMB2/SMB3，纯 Java）：浏览与列目录
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

    // 网盘集成：HTTP 客户端 + 二维码生成 + WebDAV
    implementation(libs.okhttp)
    implementation(libs.zxing.core)
    implementation(libs.sardine.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.test:runner:1.6.2")

    debugImplementation(libs.androidx.ui.tooling)
}
