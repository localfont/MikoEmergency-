# Add project specific ProGuard rules here.
-keep class com.miko.emergency.model.** { *; }
-keep class com.miko.emergency.mesh.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Gson
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
