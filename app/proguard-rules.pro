# ============================================================
# Civil Engineer Pro — Enhanced ProGuard/R8 Rules
# Developer: Eng. Ahmed Magdy
# ============================================================

# --- Anti-Tampering & Code Protection ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes InnerClasses,EnclosingMethod

# Obfuscate aggressively — remove debug info from release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
-assumenosideeffects class kotlin.io.TextStreamsKt {
    public static *** println(...);
}

# --- Room Database ---
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class com.civileg.app.db.** { *; }
-keep class com.civileg.app.db.entities.** { *; }
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface *
-keepclassmembers @androidx.room.Dao interface * { *; }
-keep @androidx.room.TypeConverter class *
-keepclassmembers @androidx.room.TypeConverter class * { *; }

# --- Hilt / Dagger ---
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-dontwarn dagger.hilt.internal.**

# --- Data Classes (Gson Serialization) ---
-keep class com.civileg.app.db.** { *; }
-keep class com.civileg.app.model.** { *; }
-keep class com.civileg.app.domain.entities.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# --- iText PDF ---
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# --- MPAndroidChart ---
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# --- Glide ---
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** { **[] $VALUES; public *; }
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder { *** *; }

# --- Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- Compose ---
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# --- Navigation ---
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# --- Prevent removal of enum values ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Kotlin Serialization & Metadata ---
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
-keep class kotlin.Metadata { *; }

# --- AndroidX ---
-keep class androidx.** { *; }
-dontwarn androidx.**

# --- Security: Keep application and model classes ---
-keep class com.civileg.app.** { *; }