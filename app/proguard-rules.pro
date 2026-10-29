-keepattributes *Annotation*,InnerClasses,Signature
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.nhlfantasygm.**$$serializer { *; }
-keepclassmembers class com.nhlfantasygm.** {
    *** Companion;
}
-keepclasseswithmembers class com.nhlfantasygm.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
