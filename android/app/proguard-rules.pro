# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Jsoup uses some optional XML APIs that aren't on Android
-dontwarn org.jsoup.**
-dontwarn javax.xml.**

# Room: keep the generated DAO impls and entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Data classes (kept for Compose previews and reflection)
-keep class com.affiliatemonitor.app.data.** { *; }
