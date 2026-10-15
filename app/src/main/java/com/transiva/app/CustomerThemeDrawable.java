package com.transiva.app;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.*;
import android.view.View;

/** Never edits an ImageView's image. Only private copies of UI backgrounds are recolored. */
final class CustomerThemeDrawable {
    private CustomerThemeDrawable() { }
    static Drawable copy(Drawable source) {
        if(source==null)return null;
        Drawable.ConstantState state=source.getConstantState();
        if(state==null)return null;
        Drawable result=state.newDrawable().mutate();
        result.setBounds(new Rect(source.getBounds()));result.setState(source.getState().clone());
        return result;
    }
    static int sample(Drawable source) {
        if(source==null)return 0;
        Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
        Rect before=new Rect(source.getBounds());
        try {source.setBounds(0,0,64,64);source.draw(new Canvas(b));return b.getPixel(32,32);}
        catch(RuntimeException ignored){return 0;}
        finally {source.setBounds(before);b.recycle();}
    }
    static void dark(Drawable d,View host,int depth) {
        if(d==null)return;
        if(d instanceof BitmapDrawable || d instanceof VectorDrawable)return;
        if(d instanceof RippleDrawable) {
            RippleDrawable ripple=(RippleDrawable)d;
            ripple.setColor(ColorStateList.valueOf(0x3378baff));
            for(int i=0;i<ripple.getNumberOfLayers();i++)
                if(ripple.getId(i)!=android.R.id.mask)dark(ripple.getDrawable(i),host,depth);
        } else if(d instanceof InsetDrawable)dark(((InsetDrawable)d).getDrawable(),host,depth);
        else if(d instanceof LayerDrawable) {
            LayerDrawable layers=(LayerDrawable)d;
            for(int i=0;i<layers.getNumberOfLayers();i++)dark(layers.getDrawable(i),host,depth);
        } else if(d instanceof StateListDrawable) {
            int[] old=d.getState().clone();
            int[][] probes={{android.R.attr.state_enabled,android.R.attr.state_focused},
                    {android.R.attr.state_enabled,android.R.attr.state_pressed},
                    {android.R.attr.state_enabled,android.R.attr.state_checked},
                    {android.R.attr.state_enabled,android.R.attr.state_selected},{android.R.attr.state_enabled},{}};
            for(int[] state:probes){d.setState(state);Drawable current=d.getCurrent();if(current!=d)dark(current,host,depth);}
            d.setState(old);
        } else if(d instanceof GradientDrawable) {
            int color=sample(d), mapped=CustomerThemeColors.surface(color,depth);
            if(color!=mapped) {
                GradientDrawable shape=(GradientDrawable)d;shape.setTintList(null);shape.clearColorFilter();shape.setColor(mapped);
                int border=CustomerThemeColors.BORDER;
                for(int state:d.getState())if(state==android.R.attr.state_focused && host instanceof android.widget.EditText)border=CustomerThemeColors.ACCENT;
                shape.setStroke(Math.max(1,Math.round(host.getResources().getDisplayMetrics().density)),border);
            } else {
                GradientDrawable shape=(GradientDrawable)d;
                if(android.os.Build.VERSION.SDK_INT>=24 && shape.getColors()!=null){
                    int[] colors=shape.getColors().clone();boolean changed=false;
                    for(int i=0;i<colors.length;i++){int original=colors[i];colors[i]=CustomerThemeColors.brand(original);changed|=colors[i]!=original;}
                    if(changed){shape.setTintList(null);shape.clearColorFilter();shape.setColors(colors);}
                } else if(CustomerThemeColors.brand(color)!=color){shape.setTintList(null);shape.clearColorFilter();shape.setColor(CustomerThemeColors.brand(color));}
            }
        } else if(d instanceof ColorDrawable) {
            ColorDrawable solid=(ColorDrawable)d;int old=sample(d), next=CustomerThemeColors.brand(CustomerThemeColors.surface(old,depth));
            if(old!=next){solid.setTintList(null);solid.clearColorFilter();solid.setColor(next);}
        } else {
            int color=sample(d), mapped=CustomerThemeColors.surface(color,depth);
            if(mapped!=color)d.setTint(mapped); // Native wrappers retain their original rounded outline.
        }
    }
}
