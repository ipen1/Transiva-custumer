package com.transiva.app;
import android.app.Activity;
import android.view.View;
/** One synchronous preference commit and an in-place update of every live window. */
public final class CustomerThemeTransition {
 private CustomerThemeTransition(){}
 public static void switchTheme(Activity a,boolean dark){if(a!=null&&!a.isFinishing()&&!a.isDestroyed())CustomerAppSettings.setDarkMode(a,dark);}
 public static void refreshAppliedTheme(Activity a){CustomerAppSettings.apply(a);}
 public static void playEnter(Activity a){if(a==null)return;View v=a.findViewById(android.R.id.content);if(v!=null){v.animate().cancel();v.setAlpha(1f);v.setTranslationY(0f);}CustomerAppSettings.apply(a);}
}
