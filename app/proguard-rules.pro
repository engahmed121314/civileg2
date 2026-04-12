# ProGuard rules for Civil EG App

# Keep model classes
-keep class com.civileg.app.db.** { *; }
-keep class com.civileg.app.model.** { *; }

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep iText
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent

# Keep Kotlin metadata
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
-keepattributes *Annotation*
