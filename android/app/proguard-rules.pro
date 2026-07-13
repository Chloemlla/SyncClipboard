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
