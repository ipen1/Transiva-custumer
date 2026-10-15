package com.transiva.app;
import android.content.Context;
import android.webkit.WebView;
/** Update the existing map document in place; keep markers, camera, and order state. */
public final class CustomerThemedWebView extends WebView implements CustomerAppSettings.ThemeAware {
 private Boolean applied;
 public CustomerThemedWebView(Context context){super(context);CustomerAppSettings.preserveArtwork(this);}
 public void refreshCustomerTheme(){applied=null;onCustomerThemeChanged(CustomerAppSettings.isDarkMode(getContext()));}
 @Override public void onCustomerThemeChanged(boolean dark){
  if(applied!=null&&applied==dark)return;applied=dark;
  setBackgroundColor(dark?CustomerAppSettings.DARK_PAGE:0xfff5f8fd);
  String bg=dark?"#122235":"#ffffff",fg=dark?"#eef5ff":"#17324d";
  String tiles=dark?"https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png":"https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png";
  evaluateJavascript("(function(){var s=document.getElementById('transiva-theme');if(!s){s=document.createElement('style');s.id='transiva-theme';document.head.appendChild(s);}s.textContent='body,.leaflet-popup-content-wrapper,.leaflet-popup-tip{background:"+bg+";color:"+fg+";}';if(window.transivaThemeTiles){window.transivaThemeTiles.setUrl('"+tiles+"');}})();",null);
 }
}
