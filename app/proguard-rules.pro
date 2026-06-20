# ThermaGuard — ProGuard / R8 rules v3.9.26

# ── Keep: Room entities y DAOs ─────────────────────────────────────────────
-keep class com.jeissonalberto.thermaguard.data.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# ── Keep: data classes usadas en serialización ─────────────────────────────
-keepclassmembers class com.jeissonalberto.thermaguard.** {
    public <init>(...);
    public ** component*();
    public ** copy(...);
}

# ── Kotlin coroutines ───────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── Compose ─────────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Lottie ──────────────────────────────────────────────────────────────────
-keep class com.airbnb.lottie.** { *; }

# ── WorkManager ─────────────────────────────────────────────────────────────
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

# ── Eliminar logs en release (reducción de tamaño) ─────────────────────────
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

# ── Optimización agresiva R8 ────────────────────────────────────────────────
-optimizationpasses 5
-allowaccessmodification
-repackageclasses 'tg'
