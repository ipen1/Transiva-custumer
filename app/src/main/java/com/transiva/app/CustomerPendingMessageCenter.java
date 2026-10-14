package com.transiva.app;
import android.app.*;
import android.widget.*;
import android.os.*;
import org.json.*;
import java.util.*;

/** All pending text, voice and photo operations for the signed-in account. */
final class CustomerPendingMessageCenter {
    static void open(Activity activity){
        CustomerMessageApi.initialize(activity);
        CustomerSessionGuard session=CustomerSessionGuard.capture(activity);
        Handler main=new Handler(Looper.getMainLooper());
        ScrollView scroll=new ScrollView(activity);LinearLayout rows=new LinearLayout(activity);rows.setOrientation(LinearLayout.VERTICAL);int pad=(int)(16*activity.getResources().getDisplayMetrics().density);rows.setPadding(pad,pad,pad,pad);scroll.addView(rows);
        final android.media.MediaPlayer[] player={null};
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Pesan tertunda").setView(scroll).setPositiveButton("Tutup",null).create();dialog.show();dialog.setOnDismissListener(d->{if(player[0]!=null){player[0].release();player[0]=null;}});
        TextView loading=new TextView(activity);loading.setText("Memuat pesan tersimpan...");rows.addView(loading);
        TransivaNetworkExecutor.execute(()->{
            try{
                CustomerPendingChatStore store=new CustomerPendingChatStore(activity,session);List<JSONObject> operations=store.list();final Map<String,java.io.File> attachments=new HashMap<>();for(JSONObject op:operations)if(!"text".equals(op.optString("kind")))attachments.put(op.optString("client_message_id"),store.attachment(op));
                main.post(()->{
                    if(activity.isFinishing()||activity.isDestroyed()||!session.matches(activity)||!dialog.isShowing())return;
                    rows.removeAllViews();
                    if(operations.isEmpty()){TextView empty=new TextView(activity);empty.setText("Tidak ada pesan tertunda.");rows.addView(empty);}
                    for(JSONObject op:operations){
                        LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(0,pad,0,pad);rows.addView(card);
                        TextView label=new TextView(activity);String kind=op.optString("kind","text");
                        String preview="image".equals(kind)?"📷 Foto tersimpan di perangkat":"voice".equals(kind)?"🎤 Voice • "+op.optLong("duration_ms")/1000+" detik":op.optString("message");
                        label.setText("Order "+op.optString("order_id",op.optString("room_id"))+"\n"+preview+"\nBelum terkonfirmasi");label.setTextIsSelectable(true);card.addView(label);
                        if(!"text".equals(kind)){
                            Button inspect=new Button(activity);inspect.setAllCaps(false);inspect.setText("image".equals(kind)?"Lihat foto tersimpan":"Putar voice tersimpan");card.addView(inspect);
                            inspect.setOnClickListener(v->{java.io.File attachment=attachments.get(op.optString("client_message_id"));if(attachment==null||!attachment.isFile()){Toast.makeText(activity,"Lampiran sudah dikirim atau tidak tersedia",Toast.LENGTH_SHORT).show();return;}
                                if("voice".equals(kind)){try{if(player[0]!=null)player[0].release();android.media.MediaPlayer audio=new android.media.MediaPlayer();player[0]=audio;audio.setDataSource(attachment.getAbsolutePath());audio.setOnPreparedListener(p->{if(dialog.isShowing())p.start();});audio.setOnCompletionListener(p->inspect.setText("Putar voice tersimpan"));audio.prepareAsync();inspect.setText("Memutar voice...");}catch(Exception e){Toast.makeText(activity,"Voice belum dapat diputar",Toast.LENGTH_SHORT).show();}}
                                else TransivaNetworkExecutor.execute(()->{android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inSampleSize=2;android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(attachment.getAbsolutePath(),options);main.post(()->{if(!dialog.isShowing()||!session.matches(activity))return;ImageView image=new ImageView(activity);image.setAdjustViewBounds(true);image.setImageBitmap(bitmap);new AlertDialog.Builder(activity).setTitle("Foto tertunda").setView(image).setPositiveButton("Tutup",null).show();});});
                            });
                        }

                        Button retry=new Button(activity);retry.setAllCaps(false);retry.setText("Periksa / kirim ulang");card.addView(retry);
                        retry.setOnClickListener(v->{retry.setEnabled(false);retry.setText("Memeriksa...");
                            TransivaNetworkExecutor.execute(()->{
                                String result;boolean success=false;
                                try{JSONObject response=new CustomerPendingChatStore(activity,session).dispatch(op);success=response.optBoolean("success");result=response.optString("message",success?"Terkirim":"Masih tertunda");}catch(Exception e){result=e.getMessage();}
                                final boolean ok=success;final String text=result;
                                main.post(()->{if(activity.isFinishing()||activity.isDestroyed()||!session.matches(activity)||!dialog.isShowing())return;label.setText(label.getText()+"\n"+text);retry.setText(ok?"Terkirim":"Kirim ulang");retry.setEnabled(!ok);});
                            },()->main.post(()->{if(dialog.isShowing()){retry.setEnabled(true);retry.setText("Kirim ulang");}}));
                        });
                    }
                });
            }catch(Exception e){main.post(()->{if(dialog.isShowing()&&session.matches(activity))loading.setText("Pesan belum dapat dibaca. Coba kembali.");});}
        },()->main.post(()->{if(dialog.isShowing())loading.setText("Aplikasi sedang sibuk. Coba kembali.");}));
    }
}
