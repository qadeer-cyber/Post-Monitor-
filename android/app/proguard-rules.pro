# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Moshi Kotlin codegen friendliness
-keep class kotlin.Metadata { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}

# Data classes (so Moshi reflection can find them)
-keep class com.affiliatemonitor.app.data.** { *; }
