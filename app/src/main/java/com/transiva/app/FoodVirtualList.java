package com.transiva.app;

import android.content.Context;
import android.os.Parcelable;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;

/** Recycles visible card trees while keeping the original programmatic header and story rail. */
final class FoodVirtualList extends RecyclerView {
    private static final class Entry {
        final String key,fingerprint;final FoodDeferredCard.Factory factory;
        Entry(String key,String fingerprint,FoodDeferredCard.Factory factory){this.key=key;this.fingerprint=fingerprint;this.factory=factory;}
    }
    private static final class Holder extends ViewHolder {
        final FrameLayout frame;String key="",fingerprint="";
        Holder(FrameLayout frame){super(frame);this.frame=frame;}
    }
    private final LinearLayoutManager manager;
    private final Rows adapter=new Rows();private final Runnable changed;
    private final Map<String,Long> ids=new HashMap<>();private long sequence;
    private final LinkedHashMap<String,Parcelable> states=new LinkedHashMap<>();
    private String screen="";private boolean presented,disposed;private int left,top,right,bottom;
    FoodVirtualList(Context context,Runnable changed){
        super(context);this.changed=changed;manager=new LinearLayoutManager(context);setLayoutManager(manager);setAdapter(adapter);setItemAnimator(null);setNestedScrollingEnabled(false);setClipToPadding(false);setSaveEnabled(false);
    }
    interface Quantities{int get(int menuId);}
    void updateQuantities(Quantities values){
        if(!presented)return;List<Entry> next=new ArrayList<>(adapter.rows);List<Integer> dirty=new ArrayList<>();
        for(int i=0;i<next.size();i++){Entry entry=next.get(i);if(!entry.key.startsWith("menu:"))continue;
            int id;try{id=Integer.parseInt(entry.key.substring(5));}catch(Exception ignored){continue;}
            int last=entry.fingerprint.lastIndexOf(':');if(last<0)continue;String fingerprint=entry.fingerprint.substring(0,last+1)+values.get(id);
            if(!fingerprint.equals(entry.fingerprint)){next.set(i,new Entry(entry.key,fingerprint,entry.factory));dirty.add(i);}
        }
        adapter.rows=next;for(int position:dirty)adapter.notifyItemChanged(position,values.get(Integer.parseInt(next.get(position).key.substring(5))));
    }
    boolean isPresented(){return presented;}
    void dispose(){disposed=true;setAdapter(null);removeCallbacks(changed);adapter.rows=Collections.emptyList();ids.clear();states.clear();presented=false;}
    void present(ScrollView scroller,LinearLayout header,List<View> cards,String key,int l,int t,int r,int b){
        boolean switching=!presented||!key.equals(screen);if(switching)saveState();screen=key;left=l;top=t;right=r;bottom=b;
        List<Entry> entries=new ArrayList<>();entries.add(new Entry("header:"+key,"header",()->header));
        int index=0;
        for(View view:cards){
            if(view instanceof FoodDeferredCard){FoodDeferredCard card=(FoodDeferredCard)view;entries.add(new Entry(card.key,card.fingerprint,card.factory));}
            else {final View fixed=view;String fallback="fixed:"+(index++);entries.add(new Entry(IncrementalLinearRenderer.key(view,fallback),IncrementalLinearRenderer.fingerprint(view,view instanceof TextView?((TextView)view).getText().toString():fallback),()->fixed));}
        }
        header.setPadding(0,top,0,0);setPadding(left,0,right,bottom);
        if(getParent()!=scroller){detach(header);scroller.removeAllViews();scroller.setFillViewport(true);scroller.addView(this,new ScrollView.LayoutParams(-1,-1));}
        presented=true;adapter.submit(entries);
        if(switching){Parcelable saved=states.get(key);if(saved!=null)manager.onRestoreInstanceState(saved);else manager.scrollToPositionWithOffset(0,0);}
    }
    void leave(ScrollView scroller,LinearLayout header){
        if(!presented)return;saveState();presented=false;detach(header);if(getParent()==scroller)scroller.removeView(this);adapter.submit(Collections.emptyList());
        if(header.getParent()!=scroller){detach(header);scroller.addView(header,new ScrollView.LayoutParams(-1,-2));}
        header.setPadding(left,top,right,bottom);scroller.setFillViewport(false);
    }
    void bottomPadding(int l,int t,int r,int b,LinearLayout header){left=l;top=t;right=r;bottom=b;setPadding(l,0,r,b);header.setPadding(0,t,0,0);}
    private void saveState(){if(presented&&!screen.isEmpty()){states.put(screen,manager.onSaveInstanceState());while(states.size()>16)states.remove(states.keySet().iterator().next());}}
    private static void detach(View view){if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);}
    static List<View> take(LinearLayout container,int first){List<View> views=new ArrayList<>();for(int i=first;i<container.getChildCount();i++)views.add(container.getChildAt(i));for(int i=container.getChildCount()-1;i>=first;i--)container.removeViewAt(i);return views;}
    private final class Rows extends Adapter<Holder> {
        List<Entry> rows=Collections.emptyList();Rows(){setHasStableIds(true);}
        void submit(List<Entry> next){
            List<Entry> old=rows;
            DiffUtil.DiffResult diff=DiffUtil.calculateDiff(new DiffUtil.Callback(){public int getOldListSize(){return old.size();}public int getNewListSize(){return next.size();}public boolean areItemsTheSame(int a,int b){return old.get(a).key.equals(next.get(b).key);}public boolean areContentsTheSame(int a,int b){return old.get(a).fingerprint.equals(next.get(b).fingerprint);}},false);
            rows=next;diff.dispatchUpdatesTo(this);
        }
        @Override public long getItemId(int position){String key=rows.get(position).key;Long id=ids.get(key);if(id==null){id=++sequence;ids.put(key,id);}return id;}
        @Override public int getItemCount(){return rows.size();}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){FrameLayout frame=new FrameLayout(parent.getContext());frame.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new Holder(frame);}
        @Override public void onBindViewHolder(Holder holder,int position){
            Entry entry=rows.get(position);if(holder.key.equals(entry.key)&&holder.fingerprint.equals(entry.fingerprint)&&holder.frame.getChildCount()>0)return;
            holder.frame.removeAllViews();View view=entry.factory.create();detach(view);FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(-1,-2);
            if(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams margin=(ViewGroup.MarginLayoutParams)view.getLayoutParams();params.setMargins(margin.leftMargin,margin.topMargin,margin.rightMargin,margin.bottomMargin);}
            holder.frame.addView(view,params);holder.key=entry.key;holder.fingerprint=entry.fingerprint;if(!disposed)post(changed);
        }
        @Override public void onBindViewHolder(Holder holder,int position,List<Object> payloads){
            if(!payloads.isEmpty()&&holder.key.equals(rows.get(position).key)&&holder.frame.getChildCount()>0){
                Object payload=payloads.get(payloads.size()-1);if(payload instanceof Integer){setQuantity(holder.frame,(Integer)payload);holder.fingerprint=rows.get(position).fingerprint;return;}
            }
            onBindViewHolder(holder,position);
        }
        private void setQuantity(View view,int value){
            if(view instanceof TextView && view.getTag() instanceof String && ((String)view.getTag()).startsWith("qty:"))((TextView)view).setText(String.valueOf(value));
            if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)setQuantity(group.getChildAt(i),value);}
        }
        @Override public void onViewRecycled(Holder holder){holder.frame.removeAllViews();holder.key="";holder.fingerprint="";if(!disposed)post(changed);}
    }
}
