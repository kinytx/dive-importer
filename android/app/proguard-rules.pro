# DivePlan Importer · ProGuard / R8 rules
# 大部分常见依赖（OkHttp / Compose / Hilt / Room / Moshi）已自带 consumer rules，
# 这里只放本工程额外需要保留的。

# Moshi 反射要保留 @JsonClass 注解
-keep,allowobfuscation,allowshrinking @com.squareup.moshi.JsonClass class **

# 数据类（dump payload / wire 上报）— 后续 P4/P5 加上 @Keep 或在此显式 keep
