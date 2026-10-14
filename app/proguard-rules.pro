# Ktor / Netty need some classes kept
-keep class io.ktor.** { *; }
-keep class io.netty.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *; }
-dontwarn io.netty.**
-dontwarn org.slf4j.**
