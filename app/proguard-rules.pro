# R8 / ProGuard rules for Velo

# CRITICAL FIX: R8 class merging breaks youtubedl-android's ZipUtils/Python initialization.
# It merges concrete classes into abstract ones that the library then tries to instantiate.
-optimizations !class/merging/*

# yt-dlp / youtubedl-android (junkfood02 fork)
# We must keep these packages completely untouched by shrinking or optimization.
-keep class com.yausername.** { *; }
-keep interface com.yausername.** { *; }
-keep class com.github.yausername.** { *; }
-keep interface com.github.yausername.** { *; }
-keep class io.github.junkfood02.** { *; }
-keep interface io.github.junkfood02.** { *; }

# Prevent R8 from merging classes in these packages
-dontwarn com.yausername.**
-dontwarn io.github.junkfood02.**

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Coil
-dontwarn coil.**

# Keep data models
-keep class com.velo.app.data.model.** { *; }

-dontwarn java.beans.**
