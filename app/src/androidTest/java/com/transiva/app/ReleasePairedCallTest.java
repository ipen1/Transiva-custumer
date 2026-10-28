package com.transiva.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Base64;
import android.util.Rational;
import android.widget.FrameLayout;
import androidx.core.content.ContextCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.webrtc.*;
import org.webrtc.audio.JavaAudioDeviceModule;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/** Real minified SDK transport across TWO devices; fixture signaling travels only through local ADB. */
@androidx.annotation.RequiresApi(36)
@RunWith(AndroidJUnit4.class)
public class ReleasePairedCallTest {
    private Instrumentation instrumentation;
    private PeerConnection peer;private PeerConnectionFactory factory;private EglBase egl;
    private JavaAudioDeviceModule adm;private AudioSource audioSource;private org.webrtc.AudioTrack audio;
    private VideoSource videoSource;private VideoTrack video,remoteVideo;private CallTextureRenderer renderer;
    private WebRtcCallActivity activity;private DataChannel data;
    private String nonce,action;private boolean caller;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final CountDownLatch go=new CountDownLatch(1),finish=new CountDownLatch(1),release=new CountDownLatch(1);
    private final AtomicInteger frames=new AtomicInteger(),restartFrames=new AtomicInteger(-1);
    private final AtomicBoolean connected=new AtomicBoolean(),pong=new AtomicBoolean();
    private final AtomicReference<Throwable> error=new AtomicReference<>();
    private final List<IceCandidate> pending=new ArrayList<>();private boolean remoteSet;
    private ScheduledExecutorService capture;
    private AudioManager audioManager;private int originalAudioMode;
    // API 36 fresh installs can reach the test before AudioFlinger knows the UID.
    // Probe actual capture BEFORE constructing WebRTC's one-shot native recorder.
    static int awaitAudioReady(Context context){
        if(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Paired audio test needs RECORD_AUDIO permission");
        int minimum=AudioRecord.getMinBufferSize(48000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
        if(minimum<=0)throw new AssertionError("48 kHz mono capture unsupported: "+minimum);
        long deadline=SystemClock.elapsedRealtime()+30000;int attempts=0;String last="not initialized";
        while(SystemClock.elapsedRealtime()<deadline){
            AudioRecord probe=null;attempts++;
            try{
                probe=new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,48000,
                        AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(minimum*2,1920));
                if(probe.getState()==AudioRecord.STATE_INITIALIZED){
                    probe.startRecording();
                    if(probe.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING){
                        byte[] pcm=new byte[960];long readDeadline=Math.min(deadline,SystemClock.elapsedRealtime()+1000);
                        while(SystemClock.elapsedRealtime()<readDeadline){
                            int count=probe.read(pcm,0,pcm.length,AudioRecord.READ_NON_BLOCKING);
                            if(count>0)return attempts;
                            if(count<0){last="AudioRecord read error "+count;break;}
                            last="AudioRecord produced no PCM";SystemClock.sleep(20);
                        }
                    }else last="AudioRecord did not enter recording state";
                }else last="AudioRecord state uninitialized (AudioFlinger UID registration may still be pending)";
            }catch(SecurityException denied){throw denied;
            }catch(IllegalArgumentException|IllegalStateException transientAudio){last=transientAudio.toString();
            }finally{
                if(probe!=null){try{if(probe.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)probe.stop();}finally{probe.release();}}
            }
            SystemClock.sleep(250);
        }
        throw new AssertionError("Audio capture not ready after 30s / "+attempts+" attempts: "+last);
    }
    private long inboundAudioPackets()throws InterruptedException{
        CountDownLatch complete=new CountDownLatch(1);AtomicLong packets=new AtomicLong();
        peer.getStats(report->{for(RTCStats stat:report.getStatsMap().values()){
            Map<String,Object> members=stat.getMembers();Object kind=members.get("kind");if(kind==null)kind=members.get("mediaType");
            if("inbound-rtp".equals(stat.getType())&&"audio".equals(String.valueOf(kind))){Object n=members.get("packetsReceived");if(n instanceof Number)packets.addAndGet(((Number)n).longValue());}
        }complete.countDown();});
        assertTrue("Audio stats callback timed out",complete.await(5,TimeUnit.SECONDS));return packets.get();
    }
    private long awaitInboundAudio(long previous)throws InterruptedException{
        long deadline=SystemClock.elapsedRealtime()+10000;long received=0;
        do{if(error.get()!=null)throw new AssertionError(error.get());received=inboundAudioPackets();if(received>previous)return received;SystemClock.sleep(200);}while(SystemClock.elapsedRealtime()<deadline);
        throw new AssertionError("No inbound audio progress: previous="+previous+", received="+received);
    }
    private void event(String type){event(type,new JSONObject());}
    private void event(String type,JSONObject value){try{value.put("type",type);Bundle b=new Bundle();b.putString("pair_event",Base64.encodeToString(value.toString().getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP));instrumentation.sendStatus(2,b);}catch(Exception e){error.set(e);}}
    private void publish(String type,String sdp){try{event(type,new JSONObject().put("sdp",sdp));}catch(Exception e){error.set(e);}}
    private SdpObserver description(String type){return new SdpObserver(){public void onCreateSuccess(SessionDescription d){peer.setLocalDescription(new EmptySdp(){@Override public void onSetSuccess(){publish(type,d.description+CallRecoveryPolicy.CAPABILITY+"\r\n");}},d);}public void onCreateFailure(String s){error.set(new AssertionError(s));}public void onSetSuccess(){}public void onSetFailure(String s){error.set(new AssertionError(s));}};}
    private class EmptySdp implements SdpObserver{public void onCreateSuccess(SessionDescription d){}public void onCreateFailure(String s){error.set(new AssertionError(s));}public void onSetSuccess(){}public void onSetFailure(String s){error.set(new AssertionError(s));}}
    private void channel(DataChannel channel){data=channel;channel.registerObserver(new DataChannel.Observer(){
        public void onBufferedAmountChange(long n){}
        public void onStateChange(){if(channel.state()==DataChannel.State.OPEN)channel.send(new DataChannel.Buffer(ByteBuffer.wrap("ping".getBytes(StandardCharsets.UTF_8)),false));}
        public void onMessage(DataChannel.Buffer b){pong.set(true);if(b.data.remaining()==4)channel.send(new DataChannel.Buffer(ByteBuffer.wrap("ack".getBytes(StandardCharsets.UTF_8)),false));}
    });}
    private void command(JSONObject value)throws Exception{
        String type=value.optString("type");
        if("go".equals(type)){go.countDown();return;}
        if("finish".equals(type)){finish.countDown();return;}
        if("release".equals(type)){release.countDown();return;}
        if("restart".equals(type)){restartFrames.set(frames.get());peer.restartIce();peer.createOffer(description("offer"),new MediaConstraints());return;}
        if("pip".equals(type)){activity.enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(3,4)).build());ui.postDelayed(()->{try{event("pip",new JSONObject().put("active",activity.isInPictureInPictureMode()));}catch(Exception e){error.set(e);}},1000);return;}
        if("lock_state".equals(type)){event("lock",new JSONObject().put("active",((KeyguardManager)activity.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked()));return;}
        if(type.startsWith("network_")){
            android.net.ConnectivityManager manager=(android.net.ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkCapabilities capabilities=manager.getNetworkCapabilities(manager.getActiveNetwork());
            String transport=capabilities!=null&&capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)?"wifi":capabilities!=null&&capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)?"cellular":"other";
            event(type,new JSONObject().put("transport",transport));return;
        }
        if("candidate".equals(type)){IceCandidate candidate=new IceCandidate(value.optString("mid"),value.optInt("line"),value.getString("candidate"));if(remoteSet)peer.addIceCandidate(candidate);else pending.add(candidate);return;}
        if("offer".equals(type)||"answer".equals(type)){
            if("offer".equals(type)&&remoteSet)restartFrames.set(frames.get());
            remoteSet=false;
            peer.setRemoteDescription(new EmptySdp(){@Override public void onSetSuccess(){ui.post(()->{remoteSet=true;for(IceCandidate c:pending)peer.addIceCandidate(c);pending.clear();if("offer".equals(type))peer.createAnswer(description("answer"),new MediaConstraints());});}},new SessionDescription("offer".equals(type)?SessionDescription.Type.OFFER:SessionDescription.Type.ANSWER,value.getString("sdp")));
        }
    }
    @Test public void pairedReleaseMediaSurvivesNetworkPipAndLock()throws Exception{
        instrumentation=InstrumentationRegistry.getInstrumentation();Bundle args=InstrumentationRegistry.getArguments();
        nonce=args.getString("pair_nonce","");Assume.assumeTrue("Run via paired_release_gate.py",!nonce.isEmpty());
        action="com.transiva.test.PAIR_"+nonce;caller="caller".equals(args.getString("pair_role"));
        Context context=instrumentation.getTargetContext();BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(!nonce.equals(i.getStringExtra("nonce")))return;try{command(new JSONObject(new String(Base64.decode(i.getStringExtra("payload"),Base64.DEFAULT),StandardCharsets.UTF_8)));}catch(Exception e){error.set(e);}}};
        boolean registered=false;
        try{
            WebRtcNativeRuntime.initialize(context);egl=EglBase.create();
            activity=(WebRtcCallActivity)instrumentation.startActivitySync(new Intent(context,WebRtcCallActivity.class).putExtra("call_type","video").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            instrumentation.runOnMainSync(()->{try{renderer=new CallTextureRenderer(activity);renderer.init(egl.getEglBaseContext(),null);FrameLayout stage=(FrameLayout)ReleaseCallLifecycleTest.field(activity,"stage");stage.addView(renderer,new FrameLayout.LayoutParams(-1,-1));}catch(Exception e){throw new AssertionError(e);}});
            audioManager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);originalAudioMode=audioManager.getMode();
            instrumentation.runOnMainSync(()->audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION));
            int audioAttempts=awaitAudioReady(context);event("audio_ready",new JSONObject().put("attempts",audioAttempts).put("sample_rate",48000));
            adm=JavaAudioDeviceModule.builder(context).setSampleRate(48000)
                    .setUseHardwareAcousticEchoCanceler(false).setUseHardwareNoiseSuppressor(false)
                    .setAudioRecordErrorCallback(new JavaAudioDeviceModule.AudioRecordErrorCallback(){
                        public void onWebRtcAudioRecordInitError(String message){error.compareAndSet(null,new AssertionError("Audio capture init: "+message));}
                        public void onWebRtcAudioRecordStartError(JavaAudioDeviceModule.AudioRecordStartErrorCode code,String message){error.compareAndSet(null,new AssertionError("Audio capture start: "+code+": "+message));}
                        public void onWebRtcAudioRecordError(String message){error.compareAndSet(null,new AssertionError("Audio capture: "+message));}
                    }).createAudioDeviceModule();
            factory=PeerConnectionFactory.builder().setAudioDeviceModule(adm).setVideoEncoderFactory(new DefaultVideoEncoderFactory(egl.getEglBaseContext(),true,true)).setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext())).createPeerConnectionFactory();
            PeerConnection.IceServer turn=PeerConnection.IceServer.builder(args.getString("pair_turn")).setUsername("ci").setPassword(args.getString("pair_turn_password")).createIceServer();
            PeerConnection.RTCConfiguration config=new PeerConnection.RTCConfiguration(Collections.singletonList(turn));config.sdpSemantics=PeerConnection.SdpSemantics.UNIFIED_PLAN;config.iceTransportsType=PeerConnection.IceTransportsType.RELAY;
            peer=factory.createPeerConnection(config,new PeerConnection.Observer(){
                public void onSignalingChange(PeerConnection.SignalingState s){}public void onIceConnectionReceivingChange(boolean b){}public void onIceGatheringChange(PeerConnection.IceGatheringState s){}
                public void onIceConnectionChange(PeerConnection.IceConnectionState s){connected.set(s==PeerConnection.IceConnectionState.CONNECTED||s==PeerConnection.IceConnectionState.COMPLETED);}
                public void onIceCandidate(IceCandidate c){try{event("candidate",new JSONObject().put("candidate",c.sdp).put("mid",c.sdpMid).put("line",c.sdpMLineIndex));}catch(Exception e){error.set(e);}}
                public void onIceCandidatesRemoved(IceCandidate[] c){}public void onAddStream(MediaStream s){}public void onRemoveStream(MediaStream s){}public void onRenegotiationNeeded(){}
                public void onDataChannel(DataChannel c){channel(c);}
                public void onTrack(RtpTransceiver t){track(t.getReceiver().track());}public void onAddTrack(RtpReceiver r,MediaStream[] streams){track(r.track());}
                private void track(MediaStreamTrack t){if(t instanceof VideoTrack)ui.post(()->{if(remoteVideo!=null)return;remoteVideo=(VideoTrack)t;remoteVideo.addSink(frame->frames.incrementAndGet());remoteVideo.addSink(renderer);});}
            });assertNotNull(peer);
            audioSource=factory.createAudioSource(new MediaConstraints());audio=factory.createAudioTrack("ci_audio",audioSource);peer.addTrack(audio,Collections.singletonList("ci"));
            videoSource=factory.createVideoSource(false);video=factory.createVideoTrack("ci_video",videoSource);peer.addTrack(video,Collections.singletonList("ci"));videoSource.getCapturerObserver().onCapturerStarted(true);
            capture=Executors.newSingleThreadScheduledExecutor();capture.scheduleAtFixedRate(()->{JavaI420Buffer buffer=JavaI420Buffer.allocate(160,120);for(int n=0;n<buffer.getDataY().capacity();n++)buffer.getDataY().put(n,(byte)160);for(int n=0;n<buffer.getDataU().capacity();n++)buffer.getDataU().put(n,(byte)128);for(int n=0;n<buffer.getDataV().capacity();n++)buffer.getDataV().put(n,(byte)128);VideoFrame frame=new VideoFrame(buffer,0,System.nanoTime());videoSource.getCapturerObserver().onFrameCaptured(frame);frame.release();},0,100,TimeUnit.MILLISECONDS);
            if(caller)channel(peer.createDataChannel("ci",new DataChannel.Init()));
            ContextCompat.registerReceiver(context,receiver,new IntentFilter(action),ContextCompat.RECEIVER_EXPORTED);registered=true;event("ready",new JSONObject().put("protocol_version",2));
            if(caller){assertTrue(go.await(60,TimeUnit.SECONDS));instrumentation.runOnMainSync(()->peer.createOffer(description("offer"),new MediaConstraints()));}
            long deadline=SystemClock.elapsedRealtime()+70000;
            while((!connected.get()||!pong.get()||frames.get()<3)&&error.get()==null&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);
            if(error.get()!=null)throw new AssertionError(error.get());assertTrue("Peer did not connect",connected.get());assertTrue("No data acknowledgement",pong.get());assertTrue("No decoded remote video",frames.get()>=3);long audioBeforeTransition=awaitInboundAudio(0);event("media");
            assertTrue("Host did not finish network/PiP/lock phase",finish.await(100,TimeUnit.SECONDS));
            if(error.get()!=null)throw new AssertionError(error.get());assertTrue("Media not reconnected after transition",connected.get());assertTrue("Media did not continue after ICE restart",restartFrames.get()>=0&&frames.get()>restartFrames.get()+3);
            long receivedAudio=awaitInboundAudio(audioBeforeTransition);
            event("done",new JSONObject().put("video_frames",frames.get()).put("audio_packets",receivedAudio));
            // done acknowledges verified media, not permission to close this peer.
            // The other device may still be collecting stats/asserting connected.
            // Keep media alive until the host has received BOTH acknowledgements.
            assertTrue("Host did not release both verified peers",release.await(30,TimeUnit.SECONDS));
        }finally{
            if(registered)context.unregisterReceiver(receiver);
            if(capture!=null){capture.shutdownNow();capture.awaitTermination(5,TimeUnit.SECONDS);}if(videoSource!=null)videoSource.getCapturerObserver().onCapturerStopped();
            if(remoteVideo!=null&&renderer!=null)remoteVideo.removeSink(renderer);if(data!=null){data.unregisterObserver();data.close();data.dispose();}if(peer!=null){peer.close();peer.dispose();}
            instrumentation.runOnMainSync(()->{if(renderer!=null)renderer.release();if(activity!=null)activity.finish();});
            if(video!=null)video.dispose();if(videoSource!=null)videoSource.dispose();if(audio!=null)audio.dispose();if(audioSource!=null)audioSource.dispose();if(factory!=null)factory.dispose();if(adm!=null)adm.release();if(egl!=null)egl.release();
            if(audioManager!=null)instrumentation.runOnMainSync(()->audioManager.setMode(originalAudioMode));
        }
    }
}
