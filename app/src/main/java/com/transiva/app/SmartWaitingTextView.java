package com.transiva.app;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Locale;
/** Compact shared card. Server snapshots own billing; monotonic clock only animates the display. */
public final class SmartWaitingTextView extends LinearLayout {
    private JSONObject state;
    private long sampledAt;
    private boolean terminal;
    private final TextView title, clock, detail;
    private final Runnable tick=new Runnable(){public void run(){render();if(isAttachedToWindow()&&!terminal&&state!=null&&state.optJSONObject("active")!=null)postDelayed(this,1000);}};
    public SmartWaitingTextView(Context context){
        super(context);setOrientation(VERTICAL);setPadding(dp(12),dp(10),dp(12),dp(10));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xFFEAF4FF);bg.setCornerRadius(dp(14));bg.setStroke(dp(1),0xFFD4E7FF);setBackground(bg);
        title=label(context,11,0xFF52708F);clock=label(context,20,0xFF1267C8);clock.setTypeface(null,android.graphics.Typeface.BOLD);detail=label(context,11,0xFF365572);
        addView(title,new LayoutParams(-1,-2));addView(clock,new LayoutParams(-1,-2));addView(detail,new LayoutParams(-1,-2));setVisibility(GONE);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(Context c,int size,int color){TextView v=new TextView(c);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);v.setPadding(0,dp(2),0,dp(2));return v;}
    public void bind(JSONObject value){bind(value,value==null?"":value.optString("order_status"));}
    public void bind(JSONObject value,String status){state=value;sampledAt=SystemClock.elapsedRealtime();String s=status==null?"":status.toLowerCase(Locale.US);terminal=s.equals("finished")||s.equals("completed")||s.equals("canceled")||s.equals("cancelled")||s.equals("cancel");removeCallbacks(tick);if(s.startsWith("cancel")){state=null;}render();if(isAttachedToWindow()&&!terminal&&state!=null&&state.optJSONObject("active")!=null)postDelayed(tick,1000);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();removeCallbacks(tick);post(tick);}
    @Override protected void onDetachedFromWindow(){removeCallbacks(tick);super.onDetachedFromWindow();}
    private String money(long n){return "Rp"+java.text.NumberFormat.getNumberInstance(new Locale("id","ID")).format(n);}
    private void render(){
        JSONArray sessions=state==null?null:state.optJSONArray("sessions");if(sessions==null||sessions.length()==0){setVisibility(GONE);return;}
        setVisibility(VISIBLE);JSONObject active=terminal?null:state.optJSONObject("active");long fee=state.optLong("total_fee");
        if(active!=null){
            long elapsed=active.optLong("elapsed_seconds")+Math.min(30,Math.max(0,(SystemClock.elapsedRealtime()-sampledAt)/1000)),free=active.optLong("free_seconds",300),rate=active.optLong("rate_per_minute",500);
            long remaining=Math.max(0,free-elapsed);fee=Math.max(0,fee-active.optLong("fee"))+((Math.max(0,elapsed-free)+59)/60)*rate;
            title.setText("SMART WAITING  •  "+active.optString("label")+((SystemClock.elapsedRealtime()-sampledAt)>30000?" • Menunggu sinkronisasi":""));
            clock.setText(remaining>0?String.format(Locale.US,"%02d:%02d gratis",remaining/60,remaining%60):money(fee)+" biaya tunggu");
            detail.setText(money(rate)+"/menit setelah 5 menit\nTunggu "+money(fee)+"  •  Estimasi total "+money(state.optLong("base_total")+fee));
        }else{
            title.setText("SMART WAITING  •  "+(terminal?"Selesai":"Dihentikan"));clock.setText(money(fee)+" biaya tunggu");
            detail.setText("Total "+money(state.optLong("base_total")+fee)+"  •  "+sessions.length()+" titik tunggu");
        }
    }
}
