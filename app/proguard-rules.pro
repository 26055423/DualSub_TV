# 默认规则；当前 release 未开启混淆，此处保留以备后续启用。

-keepattributes SourceFile,LineNumberTable

# ---------------------------------------------------------------- smbj / BouncyCastle
#
# smbj 用 BouncyCastle 提供 NTLM 所需的 MD4 等摘要算法，
# 而 BouncyCastleProvider 是通过反射按名字加载内部实现类的，
# 一旦被 R8 混淆或裁剪，运行时会抛
#   RuntimeException: BouncyCastle provider not available
#   NoSuchAlgorithmException: no such algorithm: MD4 for provider BC
# 因此整个包都必须保留。
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# smbj 的事件总线
-keep class net.engio.mbassy.** { *; }
-dontwarn net.engio.mbassy.**

# smbj 的公开 API（反射与序列化场景）
-keep class com.hierynomus.** { *; }
-dontwarn com.hierynomus.**

# 可选依赖：Kerberos/GSSAPI 与 slf4j 绑定在 Android 上不存在
-dontwarn org.ietf.jgss.**
-dontwarn javax.security.auth.**
-dontwarn org.slf4j.**
-dontwarn org.apache.**
