# 默认 ProGuard 规则文件（release 未开启混淆，此处可留空）
# 若日后开启混淆，需要保留：
# - okhttp3 相关类
# - org.json 由系统提供
-keep class com.squareup.okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
