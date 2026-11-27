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
    private boolean compact;
    private boolean tripPresentation;
    private final TextView title, clock, detail;
    private final Runnable tick=new Runnable(){public void run(){render();if(isAttachedToWindow()&&!terminal&&state!=null&&state.optJSONObject("active")!=null)postDelayed(this,1000);}};
    public SmartWaitingTextView(Context context){
        super(context);setOrientation(VERTICAL);setPadding(dp(9),dp(6),dp(9),dp(6));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xFFEAF4FF);bg.setCornerRadius(dp(14));bg.setStroke(dp(1),0xFFD4E7FF);setBackground(bg);
        title=label(context,10,0xFF52708F);clock=label(context,14,0xFF1267C8);clock.setTypeface(null,android.graphics.Typeface.BOLD);detail=label(context,11,0xFF365572);
        addView(title,new LayoutParams(-1,-2));addView(clock,new LayoutParams(-1,-2));addView(detail,new LayoutParams(-1,-2));setVisibility(GONE);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(Context c,int size,int color){TextView v=new TextView(c);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);v.setPadding(0,dp(1),0,dp(1));return v;}
    public void setCompactPresentation(boolean value){compact=value;render();}
    public void setTripPresentation() {
        compact = true;
        tripPresentation = true;
        removeAllViews();
        LinearLayout header = new LinearLayout(getContext());
        boolean stack = getResources().getConfiguration().screenWidthDp < 340
                || getResources().getConfiguration().fontScale > 1.2f;
        header.setOrientation(stack ? VERTICAL : HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.addView(title, new LayoutParams(stack ? -1 : 0, -2, stack ? 0 : 1));
        header.addView(clock, new LayoutParams(-2, -2));
        addView(header, new LayoutParams(-1, -2));
        addView(detail, new LayoutParams(-1, -2));
        render();
    }
    public void bind(JSONObject value){bind(value,value==null?"":value.optString("order_status"));}
    public void bind(JSONObject value,String status){state=value;sampledAt=SystemClock.elapsedRealtime();String s=status==null?"":status.toLowerCase(Locale.US);terminal=s.equals("finished")||s.equals("completed")||s.equals("canceled")||s.equals("cancelled")||s.equals("cancel");removeCallbacks(tick);if(s.startsWith("cancel")){state=null;}render();if(isAttachedToWindow()&&!terminal&&state!=null&&state.optJSONObject("active")!=null)postDelayed(tick,1000);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();removeCallbacks(tick);post(tick);}
    @Override protected void onDetachedFromWindow(){removeCallbacks(tick);super.onDetachedFromWindow();}
    private String money(long n){return "Rp"+java.text.NumberFormat.getNumberInstance(new Locale("id","ID")).format(n);}
    private void render(){
        JSONArray sessions=state==null?null:state.optJSONArray("sessions");if(sessions==null||sessions.length()==0){setVisibility(GONE);return;}
        JSONObject active=terminal?null:state.optJSONObject("active");long fee=state.optLong("total_fee");
        if(compact && active==null && fee<=0){setVisibility(GONE);return;}
        setVisibility(VISIBLE);
        if(active!=null){
            long elapsed=active.optLong("elapsed_seconds")+Math.min(30,Math.max(0,(SystemClock.elapsedRealtime()-sampledAt)/1000)),free=active.optLong("free_seconds"),rate=active.optLong("rate_per_minute");
            long remaining=Math.max(0,free-elapsed);fee=Math.max(0,fee-active.optLong("fee"))+((Math.max(0,elapsed-free)+59)/60)*rate;
            String waitingLabel = active.optString("label");
            boolean stale = SystemClock.elapsedRealtime() - sampledAt > 30000;
            title.setText((tripPresentation ? "Tunggu · " : "SMART WAITING  •  ") + waitingLabel
                    + (stale ? " · Menunggu sinkronisasi" : ""));
            boolean dark=CustomerAppSettings.isDarkMode(getContext());
            int accent=WaitingClock.color(elapsed,free,dark);
            GradientDrawable background=new GradientDrawable();
            background.setColor(dark?0xff13273a:0xfff1f7fd);
            background.setCornerRadius(dp(14));
            background.setStroke(dp(1),accent);
            setBackground(background);
            title.setTextColor(dark?0xffbad0e8:0xff465e76);
            detail.setTextColor(dark?0xffd4e4f2:0xff365572);
            clock.setTextColor(accent);
            clock.setText(WaitingClock.text(elapsed,free));
            if (tripPresentation) {
                String freeLabel = free % 60 == 0 ? (free / 60) + " mnt" : (free / 60) + " mnt " + (free % 60) + " dtk";
                String paidWaiting = fee > 0 ? " · Biaya tunggu " + money(fee) : "";
                detail.setText(money(rate) + "/mnt setelah " + freeLabel + " gratis" + paidWaiting
                        + " · Est. total " + money(state.optLong("base_total") + fee));
            } else {
                detail.setText(money(rate)+"/menit setelah "+(free/60)+" menit "+(free%60)+" detik gratis\nTunggu "+money(fee)+"  •  Estimasi total "+money(state.optLong("base_total")+fee));
            }
        }else{
            clock.setTextColor(CustomerAppSettings.isDarkMode(getContext())?0xffadd2ff:0xff1267c8);
            title.setText("Waktu tunggu · "+(terminal?"Selesai":"Dijeda"));clock.setText(money(fee)+" biaya tunggu");
            detail.setText("Total "+money(state.optLong("base_total")+fee)+"  •  "+sessions.length()+" titik tunggu");
        }
    }
}
