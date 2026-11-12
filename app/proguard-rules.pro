# Proguard rules for AdBlock Max
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends android.app.Service
-keep public class * extends android.app.Activity
-keep public class * extends android.content.BroadcastReceiver
-keep class com.max.adblock.** { *; }
