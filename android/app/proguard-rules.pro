# Keep OkHttp platform-specific classes silent under R8.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Google Tink (via androidx.security:security-crypto) references ErrorProne
# annotations that are compile-time only and stripped from the runtime classpath.
-dontwarn com.google.errorprone.annotations.**

# Microsoft SignalR client + its transitive deps (Gson reflection, RxJava, slf4j).
# The hub payload is deserialized reflectively, so the classes must survive R8.
-keep class com.microsoft.signalr.** { *; }
-dontwarn com.microsoft.signalr.**
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
-dontwarn io.reactivex.rxjava3.**
-dontwarn org.slf4j.**

# Shizuku: the library binds our UserService reflectively and the AIDL Stub is
# invoked across processes, so both must survive R8. The user service also reflects
# into the hidden android.content.IClipboard API.
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-keep class com.chloemlla.syncclipboard.mobile.shizuku.** { *; }
-keep interface com.chloemlla.syncclipboard.mobile.shizuku.IShizukuUserService { *; }
