package com.transiva.app;

import android.view.View;
import android.widget.LinearLayout;
import java.util.*;

/** Reconciles a detached render plan in one UI frame; unchanged keyed cards stay attached. */
final class IncrementalLinearRenderer {
    private static final class Stamp {
        final String key,value;
        Stamp(String key,String value){this.key=key;this.value=value;}
    }
    static String key(View view,String fallback){return view.getTag() instanceof Stamp?((Stamp)view.getTag()).key:fallback;}
    static String fingerprint(View view,String fallback){return view.getTag() instanceof Stamp?((Stamp)view.getTag()).value:fallback;}
    static void mark(View view,String key,String fingerprint){view.setTag(new Stamp(key,fingerprint));}
    private static final Map<LinearLayout,Map<String,View>> INDEX=new IdentityHashMap<>();
    static void begin(LinearLayout live){Map<String,View> index=new HashMap<>();for(int i=0;i<live.getChildCount();i++){View v=live.getChildAt(i);if(v.getTag() instanceof Stamp)index.put(((Stamp)v.getTag()).key,v);}INDEX.put(live,index);}
    static void end(LinearLayout live){INDEX.remove(live);}
    static boolean unchanged(LinearLayout live,String key,String value){
        if(live==null)return false;
        Map<String,View> index=INDEX.get(live);
        if(index!=null){View v=index.get(key);return v!=null&&value.equals(((Stamp)v.getTag()).value);}
        for(int i=0;i<live.getChildCount();i++){Object tag=live.getChildAt(i).getTag();if(tag instanceof Stamp){Stamp stamp=(Stamp)tag;if(key.equals(stamp.key)&&value.equals(stamp.value))return true;}}
        return false;
    }
    static void apply(LinearLayout live,LinearLayout plan,int start){
        Map<String,View> old=new HashMap<>();
        for(int i=start;i<live.getChildCount();i++){View v=live.getChildAt(i);if(v.getTag() instanceof Stamp)old.put(((Stamp)v.getTag()).key,v);}
        List<View> wanted=new ArrayList<>();
        List<View> planned=new ArrayList<>();
        for(int i=plan.getChildCount()-1;i>=0;i--){View view=plan.getChildAt(i);plan.removeViewAt(i);planned.add(view);}
        Collections.reverse(planned);
        for(View fresh:planned){View chosen=fresh;
            if(fresh.getTag() instanceof Stamp){Stamp stamp=(Stamp)fresh.getTag();View previous=old.get(stamp.key);
                if(previous!=null&&stamp.value.equals(((Stamp)previous.getTag()).value))chosen=previous;
            }
            wanted.add(chosen);
        }
        Set<View> keep=new HashSet<>(wanted);
        for(int i=live.getChildCount()-1;i>=start;i--)if(!keep.contains(live.getChildAt(i)))live.removeViewAt(i);
        for(int i=0;i<wanted.size();i++){
            View v=wanted.get(i);int index=start+i;
            if(index<live.getChildCount()&&live.getChildAt(index)==v)continue;
            if(v.getParent()==live)live.removeView(v);
            live.addView(v,index);
        }
    }
}
