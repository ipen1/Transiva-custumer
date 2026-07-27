# Transiva Customer release rules
# Keep model fields accessed reflectively through org.json only when required by libraries.
-keepattributes *Annotation*
-dontwarn org.maplibre.**

# WebRTC JNI classes
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
