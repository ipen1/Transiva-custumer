# Transiva Customer release rules
# Keep model fields accessed reflectively through org.json only when required by libraries.
-keepattributes *Annotation*
-dontwarn org.maplibre.**

# WebRTC JNI classes
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Transiva stability / reflection keep rules. Safe to keep even while R8 is disabled.
-keep class com.transiva.app.**$*JavascriptInterface* { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
