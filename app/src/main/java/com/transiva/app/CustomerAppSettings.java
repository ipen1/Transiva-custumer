package com.transiva.app;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Reversible, in-place theme updates. No Activity recreation, request replay, or form reset. */
public final class CustomerAppSettings {
    private static final String PREF="customer_app_settings", KEY_DARK="dark_mode", KEY_THEME="theme_mode", KEY_VIBRATE="vibration_enabled";
    public static final int DARK_PAGE=CustomerThemeColors.PAGE, DARK_CARD=CustomerThemeColors.CARD,
            DARK_CARD_SOFT=CustomerThemeColors.SOFT,DARK_BORDER=CustomerThemeColors.BORDER,
            DARK_TEXT=CustomerThemeColors.TEXT,DARK_MUTED=CustomerThemeColors.MUTED;
    public interface ThemeAware {void onCustomerThemeChanged(boolean dark);}
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity,Boolean> ACTIVITIES=new WeakHashMap<>();
    private static final WeakHashMap<View,Snapshot> VIEWS=new WeakHashMap<>();
    private static final WeakHashMap<View,Watcher> ROOTS=new WeakHashMap<>();
    private static final WeakHashMap<View,Boolean> ARTWORK=new WeakHashMap<>();
    private static boolean installed;
    private CustomerAppSettings(){}
    private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    public static String themeMode(Context c){return prefs(c).getString(KEY_THEME,"system");}
    public static boolean isDarkMode(Context c){
        String mode=themeMode(c);
        if ("dark".equals(mode)) return true;
        if ("light".equals(mode)) return false;
        return (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
    public static void setThemeMode(Context c,String mode){
        if (!"light".equals(mode) && !"dark".equals(mode)) mode="system";
        prefs(c).edit().putString(KEY_THEME,mode).remove(KEY_DARK).apply();
        refreshThemes();
    }
    private static void refreshThemes(){
        onMain(()->{
            for(Activity a:new ArrayList<>(ACTIVITIES.keySet()))if(a!=null&&!a.isFinishing()&&!a.isDestroyed())apply(a);
            for(View root:new ArrayList<>(ROOTS.keySet()))if(root!=null){boolean dark=isDarkMode(root.getContext());paint(root,dark,0,dark?DARK_PAGE:0xffffffff);}
        });
    }
    public static boolean isVibrationEnabled(Context c){return prefs(c).getBoolean(KEY_VIBRATE,true);}
    public static void setVibrationEnabled(Context c,boolean value){prefs(c).edit().putBoolean(KEY_VIBRATE,value).apply();}
    public static void setDarkMode(Context context,boolean dark){
        setThemeMode(context,dark?"dark":"light");
    }
    public static void preserveArtwork(View view){if(view!=null)ARTWORK.put(view,true);}
    public static void install(Application app){
        if(installed)return;installed=true;
        app.registerComponentCallbacks(new android.content.ComponentCallbacks(){
            public void onConfigurationChanged(android.content.res.Configuration config){ MAIN.post(CustomerAppSettings::refreshThemes); }
            public void onLowMemory(){}
        });
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity a,Bundle state){if(owned(a))apply(a);}
            public void onActivityStarted(Activity a){if(owned(a))apply(a);}
            public void onActivityResumed(Activity a){if(owned(a))apply(a);}
            public void onActivityPaused(Activity a){}
            public void onActivityStopped(Activity a){}
            public void onActivitySaveInstanceState(Activity a,Bundle b){}
            public void onActivityDestroyed(Activity a){
                ACTIVITIES.remove(a);View root=a.findViewById(android.R.id.content);Watcher w=ROOTS.remove(root);
                if(w!=null)w.detach();
            }
        });
    }
    private static boolean owned(Activity a){return a.getClass().getName().startsWith("com.transiva.app.");}
    public static void apply(Activity a){
        if(a==null||a.isFinishing()||a.isDestroyed())return;
        onMain(()->{
            boolean dark=isDarkMode(a);ACTIVITIES.put(a,dark);
            CustomerWindowPolicy.install(a);
            CustomerWindowPolicy.status(a.getWindow(), dark?0xff05101d:0xff0b7cff);
            CustomerWindowPolicy.navigation(a.getWindow(), dark?0xff05101d:0xff071426);
            a.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(dark?DARK_PAGE:CustomerThemeColors.LIGHT_PAGE));
            View decor=a.getWindow().getDecorView();
            CustomerWindowPolicy.appearance(a.getWindow(), CustomerWindowPolicy.appearance(a.getWindow())&~CustomerWindowPolicy.LIGHT_STATUS);
            View root=a.findViewById(android.R.id.content);
            if(root!=null){watch(root);paint(root,dark,0,dark?DARK_PAGE:0xffffffff);}
            if(a instanceof TransivaCallUiActivity)((TransivaCallUiActivity)a).applyCallBars();
        });
    }
    public static void applyToView(Context c,View root){
        if(root==null)return;
        onMain(()->{
            // Callers may pass getRootView(). Keep the OS window/decor outside the theme engine.
            View content=root.findViewById(android.R.id.content);
            View target=content!=null?content:root;
            watch(target);boolean dark=isDarkMode(c);paint(target,dark,0,dark?DARK_PAGE:0xffffffff);
        });
    }
    private static void onMain(Runnable r){if(Looper.myLooper()==Looper.getMainLooper())r.run();else MAIN.post(r);}
    private static void watch(View root){if(!ROOTS.containsKey(root)){Watcher w=new Watcher(root);ROOTS.put(root,w);root.getViewTreeObserver().addOnPreDrawListener(w);}}
    private static final class Watcher implements ViewTreeObserver.OnPreDrawListener {
        final WeakReference<View> root;boolean painting;
        Watcher(View v){root=new WeakReference<>(v);}
        public boolean onPreDraw(){View v=root.get();if(v!=null&&!painting){painting=true;try{boolean d=isDarkMode(v.getContext());paint(v,d,0,d?DARK_PAGE:0xffffffff);}finally{painting=false;}}return true;}
        void detach(){View v=root.get();if(v!=null&&v.getViewTreeObserver().isAlive())v.getViewTreeObserver().removeOnPreDrawListener(this);}
    }
    private static final int[][] STATES={{-android.R.attr.state_enabled},{android.R.attr.state_enabled,android.R.attr.state_pressed},
            {android.R.attr.state_enabled,android.R.attr.state_focused},{android.R.attr.state_enabled,android.R.attr.state_checked},
            {android.R.attr.state_enabled,android.R.attr.state_selected},{}};
    private static ColorStateList mapped(ColorStateList source,int bg,boolean text,int depth){
        if(source==null)return null;int[] colors=new int[STATES.length];
        for(int i=0;i<colors.length;i++){int c=source.getColorForState(STATES[i],source.getDefaultColor());colors[i]=text?CustomerThemeColors.text(c,bg):CustomerThemeColors.brand(CustomerThemeColors.surface(c,depth));}
        return new ColorStateList(STATES,colors);
    }
    private static ColorStateList lightText(ColorStateList source,int bg){
        if(source==null)return null;
        int[] colors=new int[STATES.length];boolean changed=false;
        for(int i=0;i<colors.length;i++){int old=source.getColorForState(STATES[i],source.getDefaultColor());colors[i]=CustomerThemeColors.lightText(old,bg);changed|=old!=colors[i];}
        return changed?new ColorStateList(STATES,colors):source;
    }
    private static final class Snapshot {
        Drawable original;WeakReference<Drawable> applied=new WeakReference<>(null);
        boolean ready,dark;int sample,stateHash=Integer.MIN_VALUE,bgContext;
        ColorStateList text,lastText,hint,lastHint,tint,lastTint,button,lastButton,thumb,lastThumb,track,lastTrack,progress,lastProgress,indeterminate,lastIndeterminate;
    }
    private static void paint(View v,boolean dark,int depth,int inherited){
        if(v instanceof ThemeAware)((ThemeAware)v).onCustomerThemeChanged(dark);
        if(Boolean.TRUE.equals(ARTWORK.get(v)))return;
        Snapshot s=VIEWS.get(v);if(s==null){s=new Snapshot();VIEWS.put(v,s);}
        Drawable current=v.getBackground();boolean changed=!s.ready||current!=s.applied.get(), mode=!s.ready||s.dark!=dark;
        if(changed)s.original=CustomerThemeDrawable.copy(current);
        ColorStateList tint=v.getBackgroundTintList();boolean tintChanged=!s.ready||tint!=s.lastTint;
        if(tintChanged)s.tint=tint;
        if(changed||mode||tintChanged){
            Drawable copy=CustomerThemeDrawable.copy(s.original);
            if(copy!=null){copy.setState(v.getDrawableState());if(dark)CustomerThemeDrawable.dark(copy,v,depth);else CustomerThemeDrawable.light(copy,v,depth);
                int left=v.getPaddingLeft(),top=v.getPaddingTop(),right=v.getPaddingRight(),bottom=v.getPaddingBottom();
                v.setBackground(copy);
                if(left!=v.getPaddingLeft()||top!=v.getPaddingTop()||right!=v.getPaddingRight()||bottom!=v.getPaddingBottom())v.setPadding(left,top,right,bottom);
            }
            ColorStateList target=dark?mapped(s.tint,inherited,false,depth):s.tint;
            if(v.getBackgroundTintList()!=target)v.setBackgroundTintList(target);
            s.lastTint=v.getBackgroundTintList();s.applied=new WeakReference<>(v.getBackground());
            s.sample=CustomerThemeDrawable.sample(v.getBackground());s.stateHash=java.util.Arrays.hashCode(v.getDrawableState());
            v.invalidateOutline();
        } else if(s.stateHash!=java.util.Arrays.hashCode(v.getDrawableState())) {
            s.sample=CustomerThemeDrawable.sample(v.getBackground());s.stateHash=java.util.Arrays.hashCode(v.getDrawableState());
        }
        int background=CustomerThemeColors.composite(s.sample,inherited);
        if(v instanceof TextView){
            TextView t=(TextView)v;ColorStateList now=t.getTextColors();boolean fresh=!s.ready||now!=s.lastText;
            if(fresh)s.text=now;
            if(fresh||mode||s.bgContext!=background){ColorStateList target=dark?mapped(s.text,background,true,depth):lightText(s.text,background);if(target!=null)t.setTextColor(target);s.lastText=t.getTextColors();}
            if(t instanceof EditText){ColorStateList hint=t.getHintTextColors();boolean newHint=!s.ready||hint!=s.lastHint;if(newHint)s.hint=hint;
                if(newHint||mode||s.bgContext!=background){ColorStateList target=dark?mapped(s.hint,background,true,depth):lightText(s.hint,background);if(target!=null)t.setHintTextColor(target);s.lastHint=t.getHintTextColors();}}
        }
        if(v instanceof CompoundButton){
            CompoundButton button=(CompoundButton)v;ColorStateList now=button.getButtonTintList();boolean fresh=!s.ready||now!=s.lastButton;if(fresh)s.button=now;
            if(fresh||mode){button.setButtonTintList(dark?new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{CustomerThemeColors.ACCENT,0xff93a8c0}):s.button);s.lastButton=button.getButtonTintList();}
        }
        if(v instanceof Switch){Switch toggle=(Switch)v;
            boolean freshThumb=!s.ready||toggle.getThumbTintList()!=s.lastThumb, freshTrack=!s.ready||toggle.getTrackTintList()!=s.lastTrack;
            if(freshThumb)s.thumb=toggle.getThumbTintList();if(freshTrack)s.track=toggle.getTrackTintList();
            if(!s.ready)CustomerSwitchStyle.install(toggle);
            if(mode||freshThumb||freshTrack){toggle.setThumbTintList(CustomerSwitchStyle.thumb(dark));
                toggle.setTrackTintList(CustomerSwitchStyle.track(dark));
                s.lastThumb=toggle.getThumbTintList();s.lastTrack=toggle.getTrackTintList();}
        }
        if(v instanceof ProgressBar){ProgressBar p=(ProgressBar)v;
            boolean freshProgress=!s.ready||p.getProgressTintList()!=s.lastProgress, freshIndeterminate=!s.ready||p.getIndeterminateTintList()!=s.lastIndeterminate;
            if(freshProgress)s.progress=p.getProgressTintList();if(freshIndeterminate)s.indeterminate=p.getIndeterminateTintList();
            if(mode||freshProgress||freshIndeterminate){p.setProgressTintList(dark?ColorStateList.valueOf(CustomerThemeColors.ACCENT):s.progress);
                p.setIndeterminateTintList(dark?ColorStateList.valueOf(CustomerThemeColors.ACCENT):s.indeterminate);
                s.lastProgress=p.getProgressTintList();s.lastIndeterminate=p.getIndeterminateTintList();}
        }
        s.bgContext=background;s.dark=dark;s.ready=true;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)paint(g.getChildAt(i),dark,depth+1,background);}
    }
}
