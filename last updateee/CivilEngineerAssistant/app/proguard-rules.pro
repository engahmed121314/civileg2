# Civil Engineer Assistant - ProGuard Rules

# Keep Kotlin metadata
-keepattributes *Annotation*
-keep class kotlin.Metadata { *; }

# Keep Compose
-keep class androidx.compose.** { *; }

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * { @androidx.room.* <methods>; }

# Keep Models
-keep class com.civilengineer.assistant.models.** { *; }

# Keep iText PDF
-keep class com.itextpdf.** { *; }

# Keep Gson
-keepattributes Signature
-keep class com.google.gson.** { *; }

# General
-keepclassmembers class * implements android.os.Parcelable { *; }
-keepclassmembers class * implements java.io.Serializable { *; }
