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

# The separate instrumentation APK calls this shared class by its original ABI.
# Without this rule R8 removes/renames Trace in the app, while AGP excludes the
# duplicate dependency from the test APK. AndroidJUnitRunner then crashes before
# running any test (run #680: NoClassDefFoundError androidx.tracing.Trace).
# Retain its methods too: a name-only keep still allows forceEnableAppTracing()
# and the trace-section entry points used by the runner to be removed/inlined.
-keep class androidx.tracing.Trace { *; }
# AndroidX Test's ListFuture calls this shared static entry point too. R8 can
# inline every app call and remove its holder, leaving the test APK unlinked.
-keep class androidx.concurrent.futures.CallbackToFutureAdapter { *; }

# Separate release instrumentation also calls Kotlin multifile facades. R8
# cannot infer those calls from app code, and AGP removes duplicate stdlib
# classes from the test APK. Keep only the shared entry-point families verified
# by tools/reliability/verify_release_runner.py, not the entire Kotlin runtime.
# A facade's static methods can be inherited from its __ implementation class.
-keep class kotlin.Lazy { *; }
-keep class kotlin.LazyKt** { *; }
-keep class kotlin.Result { *; }
-keep class kotlin.ResultKt { *; }
-keep class kotlin.coroutines.ContinuationKt { *; }
-keep class kotlin.coroutines.Continuation { *; }
-keep class kotlin.coroutines.jvm.internal.ContinuationImpl { *; }
-keep class kotlin.coroutines.jvm.internal.SuspendLambda { *; }
-keep class kotlin.coroutines.intrinsics.IntrinsicsKt** { *; }
-keep class kotlin.coroutines.jvm.internal.DebugProbesKt { *; }
-keep class kotlin.jvm.internal.FunctionReferenceImpl { *; }
-keep class kotlin.jvm.internal.StringCompanionObject { *; }
-keep class kotlin.jvm.internal.Intrinsics { *; }
-keep class kotlin.jvm.internal.Lambda { *; }
-keep class kotlin.jvm.internal.Ref$ObjectRef { *; }
-keep class kotlin.io.CloseableKt { *; }
-keep class kotlin.time.DurationKt { *; }
# Instrumentation's coroutine helpers use these shared APIs as well. Preserve
# constructor parameter order and dispatcher accessors across the APK boundary.
-keep class kotlinx.coroutines.Dispatchers { *; }
-keep class kotlinx.coroutines.CancellableContinuationImpl { *; }
-keep class kotlinx.coroutines.BuildersKt** { *; }
-keep class kotlinx.coroutines.ExecutorsKt { *; }
-keep class kotlinx.coroutines.Job$DefaultImpls { *; }
-keep class kotlinx.coroutines.TimeoutKt { *; }
# The paired-device test registers its receiver using this public overload.
# Other ContextCompat methods remain eligible for shrinking/optimization.
-keep class androidx.core.content.ContextCompat {
    public static android.content.Intent registerReceiver(...);
}

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

# IncomingCallNotificationTest is packaged in a separate release test APK.
# Preserve the class and member ABI it calls in the minified target app.
# Otherwise R8 can inline/rename this holder while the test DEX still refers
# to com.transiva.app.IncomingCallNotifier.channels/build by their Java names.
-keep class com.transiva.app.IncomingCallNotifier { *; }

# WorkManager creates this worker reflectively after process restart.
-keep class com.transiva.app.FcmRegistrationWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }

-keep class com.transiva.app.CallDeliveryReceiptWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }
