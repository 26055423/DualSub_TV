# 开发环境交接文档

## 背景

本项目在 Windows 上无法编译 Media3 原生扩展（FFmpeg / AV1），需要在 **macOS + Apple Silicon** 上进行。

---

## 当前 Mac 环境（已配置完毕）

| 工具 | 路径 / 版本 |
|---|---|
| Java 17 | `/opt/homebrew/opt/openjdk@17` |
| Android SDK | `~/android-sdk` |
| NDK | `~/android-sdk/ndk/27.2.12479018` |
| CMake | `~/android-sdk/cmake/3.22.1` |
| Gradle 8.9 | `~/.gradle/wrapper/dists/gradle-8.9-bin/local/gradle-8.9/bin/gradle` |
| Gradle 8.7 | `~/.gradle/wrapper/dists/gradle-8.7-bin/544c35d6/gradle-8.7/bin/gradle` |

> **注意**：编译时必须使用 Java 17，若默认 JDK 版本过高，Gradle 会报 `Unsupported class file major version`，需显式指定 Java 17。

每次编译前设置环境变量：
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=~/android-sdk
```

---

## 已完成：FFmpeg 音频扩展

### 作用
解决 MKV 常见的 DTS、TrueHD、EAC3/AC3 等音频格式在电视设备上无法播放的问题（这些格式大多数电视芯片没有硬解器）。

启用的解码器：`dca`(DTS)、`truehd`、`eac3`、`ac3`、`flac`、`opus`、`vorbis`、`mp3`、`aac`

### 产物位置
```
app/libs/lib-decoder-ffmpeg-release.aar
```

### 源码位置
```
~/Project/media3-1.4.1/          ← Media3 1.4.1 源码
~/Project/ffmpeg-src/ffmpeg-6.0/ ← FFmpeg 6.0 源码（已 patch）
```

### 重新编译方法

如需重新编译（升级 FFmpeg 版本或修改启用的解码器）：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=~/android-sdk

# 第一步：编译 FFmpeg 静态库
FFMPEG_MODULE_PATH=~/Project/media3-1.4.1/libraries/decoder_ffmpeg/src/main
NDK_PATH=~/android-sdk/ndk/27.2.12479018
ENABLED_DECODERS=(dca truehd eac3 ac3 flac opus vorbis mp3 aac)

cd ${FFMPEG_MODULE_PATH}/jni && \
bash build_ffmpeg_tv.sh \
    "${FFMPEG_MODULE_PATH}" "${NDK_PATH}" "darwin-x86_64" 23 \
    "${ENABLED_DECODERS[@]}"

# 第二步：打 AAR
GRADLE=~/.gradle/wrapper/dists/gradle-8.7-bin/544c35d6/gradle-8.7/bin/gradle
cd ~/Project/media3-1.4.1 && \
$GRADLE :lib-decoder-ffmpeg:assembleRelease \
    -Pandroid.ndkVersion=27.2.12479018 --no-daemon

# 第三步：复制到项目
cp ~/Project/media3-1.4.1/libraries/decoder_ffmpeg/buildout/outputs/aar/lib-decoder-ffmpeg-release.aar \
   ~/Project/DualSub_TV/app/libs/
```

### 关键 patch 说明

FFmpeg 6.0 的 `libavutil/log.c` 里直接使用了 `stderr`，而 NDK 24+ 的 bionic libc 不再将 `stderr` 作为可链接符号导出。解决方案是 patch `log.c`，在 `__ANDROID__` 宏下把 `stderr` 引用替换成 `__android_log_print`。

patch 已应用于：`~/Project/ffmpeg-src/ffmpeg-6.0/libavutil/log.c`
原始备份在：`~/Project/ffmpeg-src/ffmpeg-6.0/libavutil/log.c.orig`

另外，macOS 文件系统大小写不敏感，FFmpeg 根目录下的 `VERSION` 文件会与 C++17 标准库的 `<version>` 头文件冲突，编译前已将其重命名为 `VERSION.bak`。

---

## 待完成：AV1 视频扩展（decoder_av1）

### 作用
为旧芯片（2020 年前，无 AV1 硬解器）提供 AV1 软解能力，基于 Google 的 libgav1。

### 为什么没做完
需要从 GitHub 下载三个依赖库，当前 Mac 网络环境无法访问 GitHub（连接超时）：

| 库 | URL |
|---|---|
| cpu_features | `https://github.com/google/cpu_features/archive/refs/heads/main.zip` |
| libgav1 | `https://github.com/google/libgav1/archive/refs/heads/main.zip` |
| abseil-cpp | `https://github.com/abseil/abseil-cpp/archive/refs/heads/master.zip` |

### 继续编译的步骤

1. 在能访问 GitHub 的网络下载上述三个 zip，放到 `~/Downloads/`
2. 解压并配置：
```bash
AV1_JNI=~/Project/media3-1.4.1/libraries/decoder_av1/src/main/jni

unzip -q ~/Downloads/cpu_features-main.zip -d /tmp/
mv /tmp/cpu_features-main ${AV1_JNI}/cpu_features

unzip -q ~/Downloads/libgav1-main.zip -d /tmp/
mv /tmp/libgav1-main ${AV1_JNI}/libgav1

unzip -q ~/Downloads/abseil-cpp-master.zip -d /tmp/
mv /tmp/abseil-cpp-master ${AV1_JNI}/libgav1/third_party/abseil-cpp
```

3. 打 AAR（CMake 会自动编译 libgav1）：
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=~/android-sdk
GRADLE=~/.gradle/wrapper/dists/gradle-8.9-bin/local/gradle-8.9/bin/gradle

cd ~/Project/media3-1.4.1 && \
$GRADLE :lib-decoder-av1:assembleRelease \
    -Pandroid.ndkVersion=27.2.12479018 --no-daemon
```

4. 复制产物：
```bash
cp ~/Project/media3-1.4.1/libraries/decoder_av1/buildout/outputs/aar/lib-decoder-av1-release.aar \
   ~/Project/DualSub_TV/app/libs/
```

5. 在 `app/build.gradle.kts` 里加依赖：
```kotlin
implementation(files("libs/lib-decoder-av1-release.aar"))
```

> `PlayerController` 已设置 `EXTENSION_RENDERER_MODE_ON`，加入 AAR 后 AV1 软解自动生效，无需改代码。

---

## 项目构建（日常开发）

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=~/android-sdk
GRADLE=~/.gradle/wrapper/dists/gradle-8.9-bin/local/gradle-8.9/bin/gradle

# debug APK
cd ~/Project/DualSub_TV && $GRADLE :app:assembleDebug --no-daemon

# 产物
# app/build/outputs/apk/debug/app-debug.apk
```

安装到电视：
```bash
adb connect <电视IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 注意事项

- `local.properties` 已写入 `sdk.dir=/Users/I500998/android-sdk`，不要提交到 Git
- `app/libs/` 目录下的 AAR 文件也不要提交到 Git（体积大，应走产物存储）
- Windows 机器可以正常开发业务逻辑代码，但编译原生扩展必须在此 Mac 上进行
