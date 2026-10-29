package com.transiva.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

/** Uses the supplied artwork. Decode a small display bitmap instead of the full 1280px asset. */
public final class PoinTransivaUi {
    private static Bitmap icon;
    private PoinTransivaUi() {}
    public static ImageView icon(Context c,int sizeDp){
        if(icon==null){synchronized(PoinTransivaUi.class){if(icon==null){BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=4;options.inScaled=false;icon=BitmapFactory.decodeResource(c.getResources(),R.drawable.poin_transiva,options);}}}
        ImageView image=new ImageView(c);image.setImageBitmap(icon);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription("Poin Transiva");image.setAdjustViewBounds(true);image.setMinimumWidth((int)(sizeDp*c.getResources().getDisplayMetrics().density+.5f));return image;
    }
}
