# JNI looks up WebRTC classes, fields and callbacks by exact Java names.
-keep class org.webrtc.** { *; }
-keep class * implements org.webrtc.PeerConnection$Observer { *; }
-keep class * implements org.webrtc.SdpObserver { *; }
-keep class * implements org.webrtc.VideoSink { *; }
# Preserve the media session and its nested callbacks after the previous release crash.
-keep class com.transiva.app.WebRtcSessionEngine { *; }
-keep class com.transiva.app.WebRtcSessionEngine$* { *; }
-keep class com.transiva.app.WebRtcCallForegroundService { *; }
-keep class com.transiva.app.TransivaCallUiActivity { *; }
-keep class com.transiva.app.TransivaCallUiActivity$* { *; }
# Dashboard uses Class.forName for the balance-history screen.
-keep class com.transiva.app.CustomerBalanceHistoryActivity { *; }
# Lifecycle ownership and screen-name diagnostics use the application package/name.
-keeppackagenames com.transiva.app
-keepnames class * extends android.app.Activity
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
    native <methods>;
}
# Firebase and other SDKs supply their own consumer rules. Avoid blanket SDK keeps.

-keep class com.transiva.app.WebRtcNativeRuntime { *; }

# WebRTC 144 also calls these classes directly from libjingle_peerconnection_so.so.
# Keeping org.webrtc alone does not protect JNI_OnLoad's org/jni_zero/JniInit lookup.
-keep class org.jni_zero.** { *; }
-keepclasseswithmembers,includedescriptorclasses class * {
    @org.jni_zero.CalledByNative <methods>;
}
-keepclasseswithmembers,includedescriptorclasses class * {
    @org.jni_zero.CalledByNativeUnchecked <methods>;
}
-keepclasseswithmembers,includedescriptorclasses class * {
    @org.jni_zero.AccessedByNative <fields>;
}
