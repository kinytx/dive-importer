# DivePlan Importer · ProGuard / R8 rules
# 大部分常见依赖（OkHttp / Compose / Hilt / Room / Moshi / CameraX / MLKit）已自带 consumer rules，
# 这里只放本工程额外需要保留的规则。

# Moshi 反射要保留 @JsonClass 注解（代码生成 adapter）
-keep,allowobfuscation,allowshrinking @com.squareup.moshi.JsonClass class **

# 保留 Moshi DTO 字段（防止 R8 把 JSON 映射字段缩短后 Moshi 找不到）
-keepclassmembers class cn.diveplan.importer.model.** { *; }
-keepclassmembers class cn.diveplan.importer.ui.bind.BindRequest { *; }
-keepclassmembers class cn.diveplan.importer.ui.bind.BindResponse { *; }

# Hilt 生成代码（Hilt 自带 consumer rules，这里是保险）
-keep class * extends dagger.hilt.android.internal.managers.** { *; }
-dontwarn dagger.hilt.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# MLKit Barcode（混淆后保留入口）
-keep class com.google.mlkit.** { *; }

# EncryptedSharedPreferences（Tink 库）
-keepclassmembers class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
