# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.maccms.mobile.data.** { *** Companion; }
-keepclasseswithmembers class com.maccms.mobile.data.** { kotlinx.serialization.KSerializer serializer(...); }
