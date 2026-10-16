package com.transiva.app;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.webrtc.*;
import org.webrtc.audio.JavaAudioDeviceModule;
import static org.junit.Assert.*;

/** Runs against the minified Play release, not a debug copy. No production login or server call. */
@RunWith(AndroidJUnit4.class)
public class ReleaseWebRtcSmokeTest {
    @Test public void jniOnLoadEntryPointsKeepTheirNativeNames() throws Exception {
        Class<?> init=Class.forName("org.jni_zero.JniInit");
        assertNotNull(init.getDeclaredMethod("init"));
        assertNotNull(init.getDeclaredMethod("crashIfMultiplexingMisaligned",long.class,long.class));
        Class<?> util=Class.forName("org.jni_zero.JniUtil");
        assertNotNull(util.getDeclaredMethod("mapToArray",java.util.Map.class));
        assertNotNull(util.getDeclaredMethod("arrayToMap",Object[].class));
    }

    @Test public void releaseJniCreatesAudioPeerAndSdp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WebRtcNativeRuntime.initialize(context);
        WebRtcNativeRuntime.initialize(context); // A second call must be harmless.
        JavaAudioDeviceModule adm = JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(false).setUseHardwareNoiseSuppressor(false).createAudioDeviceModule();
        PeerConnectionFactory factory = null; PeerConnection peer = null, verifier = null;
        AudioSource source = null; AudioTrack track = null;
        try {
            factory = PeerConnectionFactory.builder().setAudioDeviceModule(adm).createPeerConnectionFactory();
            assertNotNull(factory);
            PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(Collections.emptyList());
            config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
            peer = factory.createPeerConnection(config,new Observer());
            assertNotNull(peer);
            source = factory.createAudioSource(new MediaConstraints());
            track = factory.createAudioTrack("release_audio",source); track.setEnabled(false);
            assertNotNull(peer.addTrack(track,Collections.singletonList("release_smoke")));
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<String> result = new AtomicReference<>(), error = new AtomicReference<>();
            peer.createOffer(new SdpObserver() {
                public void onCreateSuccess(SessionDescription sdp) { result.set(sdp.description); done.countDown(); }
                public void onCreateFailure(String reason) { error.set(reason); done.countDown(); }
                public void onSetSuccess() {}
                public void onSetFailure(String reason) {}
            },new MediaConstraints());
            assertTrue("JNI SDP callback timed out",done.await(20,TimeUnit.SECONDS));
            assertNull(error.get()); assertNotNull(result.get());
            assertTrue("Audio offer missing",result.get().contains("m=audio"));
            verifier=factory.createPeerConnection(config,new Observer());assertNotNull(verifier);
            CountDownLatch parsed=new CountDownLatch(1);error.set(null);
            verifier.setRemoteDescription(new SdpObserver(){public void onCreateSuccess(SessionDescription d){}public void onCreateFailure(String reason){}public void onSetSuccess(){parsed.countDown();}public void onSetFailure(String reason){error.set(reason);parsed.countDown();}},new SessionDescription(SessionDescription.Type.OFFER,result.get()+CallRecoveryPolicy.CAPABILITY+"\r\n"));
            assertTrue("Advertised SDP parse timed out",parsed.await(20,TimeUnit.SECONDS));assertNull("Capability broke native SDP parsing",error.get());
        } finally {
            if(verifier!=null)verifier.dispose();if(peer!=null)peer.dispose(); if(track!=null)track.dispose();
            if(source!=null)source.dispose(); if(factory!=null)factory.dispose(); adm.release();
        }
    }
    @Test public void releaseVideoCodecsAndEglInitialize() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WebRtcNativeRuntime.initialize(context);
        WebRtcNativeRuntime.initialize(context); // A second call must be harmless.
        EglBase egl = EglBase.create(); PeerConnectionFactory factory = null;
        try {
            DefaultVideoEncoderFactory encoder = new DefaultVideoEncoderFactory(egl.getEglBaseContext(),true,true);
            DefaultVideoDecoderFactory decoder = new DefaultVideoDecoderFactory(egl.getEglBaseContext());
            assertTrue("No video encoder",encoder.getSupportedCodecs().length>0);
            assertTrue("No video decoder",decoder.getSupportedCodecs().length>0);
            factory=PeerConnectionFactory.builder().setVideoEncoderFactory(encoder).setVideoDecoderFactory(decoder).createPeerConnectionFactory();
            assertNotNull(factory);
        } finally { if(factory!=null)factory.dispose(); egl.release(); }
    }
    private static final class Observer implements PeerConnection.Observer {
        public void onSignalingChange(PeerConnection.SignalingState s) {}
        public void onIceConnectionChange(PeerConnection.IceConnectionState s) {}
        public void onIceConnectionReceivingChange(boolean b) {}
        public void onIceGatheringChange(PeerConnection.IceGatheringState s) {}
        public void onIceCandidate(IceCandidate c) {}
        public void onIceCandidatesRemoved(IceCandidate[] c) {}
        public void onAddStream(MediaStream s) {}
        public void onRemoveStream(MediaStream s) {}
        public void onDataChannel(DataChannel c) {}
        public void onRenegotiationNeeded() {}
    }
}
