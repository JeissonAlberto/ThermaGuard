# ThermaGuard ProGuard Rules
-keep class com.jeissonalberto.thermaguard.data.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.* class * { *; }
