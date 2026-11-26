# Project-specific R8 / ProGuard rules for Bunny Memo

# Preserve source file & line numbers for crash diagnostics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Kotlin metadata & annotations used by Room, Compose, and Firestore serialization
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,Signature,InnerClasses,EnclosingMethod

# Keep planner data models, Room entities, enums, and Firestore DTOs
-keep class com.example.data.** { *; }

# Keep Android components declared in AndroidManifest.xml (widgets, receivers, services)
-keep class com.example.widgets.** { *; }
-keep class com.example.notifications.** { *; }
-keep class com.example.focus.** { *; }

# Firebase Firestore & Auth reflection safety
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName *;
}
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# AndroidX Credentials / Google ID
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
