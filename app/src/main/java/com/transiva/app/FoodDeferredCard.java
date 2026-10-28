package com.transiva.app;

import android.content.Context;
import android.view.View;

/** Lightweight row description; the heavy card tree is created only when RecyclerView binds it. */
final class FoodDeferredCard extends View {
    interface Factory { View create(); }
    final String key,fingerprint;final Factory factory;
    FoodDeferredCard(Context context,String key,String fingerprint,Factory factory){super(context);this.key=key;this.fingerprint=fingerprint;this.factory=factory;IncrementalLinearRenderer.mark(this,key,fingerprint);}
}
