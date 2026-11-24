package com.transiva.app;

import android.app.Activity;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import org.json.JSONObject;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Inline address search. The chosen result moves the existing map before confirmation. */
final class TransSendInlinePointSearch {
    interface Selection { void picked(double lat,double lng,String address); }

    private final Activity host;
    private final CustomerLifecycleNetworkScope network;
    private final Selection selection;
    private final LatLng center;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LinearLayout panel, results;
    private final ScrollView scroll;
    private final EditText input;
    private PlacesClient places;
    private TransportPlaceSearch placesSearch;
    private final AutocompleteSessionToken token=AutocompleteSessionToken.newInstance();
    private boolean closed;
    private int request;

    TransSendInlinePointSearch(Activity host,CustomerLifecycleNetworkScope network,LatLng center,
                              String hint,Runnable closePanel,Selection selection) {
        this.host=host;this.network=network;this.center=center;this.selection=selection;
        panel=new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0,dp(4),0,dp(4));
        LinearLayout row=new LinearLayout(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        input=new EditText(host);
        input.setSingleLine();
        input.setTextSize(13);
        input.setHint(hint);
        input.setContentDescription(hint);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(dp(10),0,dp(6),0);
        boolean dark=CustomerAppSettings.isDarkMode(host);
        android.graphics.drawable.GradientDrawable searchSurface=new android.graphics.drawable.GradientDrawable();
        searchSurface.setColor(dark?0xff18283d:0xffffffff);
        searchSurface.setCornerRadius(dp(12));
        searchSurface.setStroke(dp(1),dark?0xff45617d:0xffa9ccff);
        input.setBackground(searchSurface);
        input.setTextColor(dark?0xffeff7ff:0xff0b3a78);
        input.setHintTextColor(dark?0xffaabbd0:0xff66758a);
        row.addView(input,new LinearLayout.LayoutParams(0,dp(48),1));
        Button hide=new Button(host);
        hide.setAllCaps(false);
        hide.setText("Peta");
        hide.setOnClickListener(v->closePanel.run());
        row.addView(hide,new LinearLayout.LayoutParams(-2,dp(48)));
        panel.addView(row);
        scroll=new ScrollView(host);
        results=new LinearLayout(host);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,dp(116)));
        scroll.setVisibility(View.GONE);
        try {
            if (!Places.isInitialized()) {
                String key=host.getString(R.string.google_maps_key).trim();
                if (!key.isEmpty()) Places.initializeWithNewPlacesApiEnabled(host.getApplicationContext(),key);
            }
            if (Places.isInitialized()) {
                places=Places.createClient(host);
                placesSearch=new TransportPlaceSearch(places);
            }
        } catch (RuntimeException ignored) { }
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {}
            public void afterTextChanged(Editable s) { schedule(s.toString().trim(),300); }
        });
        input.setOnEditorActionListener((v,id,event)->{
            if(id!=EditorInfo.IME_ACTION_SEARCH)return false;
            schedule(input.getText().toString().trim(),0);
            hideKeyboard();
            return true;
        });
    }

    View view(){return panel;}

    void close() {
        closed=true;
        request++;
        main.removeCallbacksAndMessages(null);
        if(placesSearch!=null)placesSearch.cancel();
        if(input.hasFocus())hideKeyboard();
    }

    private void hideKeyboard() {
        InputMethodManager keyboard=(InputMethodManager)host.getSystemService(Context.INPUT_METHOD_SERVICE);
        if(keyboard!=null)keyboard.hideSoftInputFromWindow(input.getWindowToken(),0);
        input.clearFocus();
    }

    private void schedule(String query,long delay) {
        int ticket=++request;
        main.removeCallbacksAndMessages(null);
        if(placesSearch!=null)placesSearch.cancel();
        results.removeAllViews();
        scroll.setVisibility(query.length()>=3?View.VISIBLE:View.GONE);
        if(query.length()>=3) main.postDelayed(()->search(query,ticket),delay);
    }

    private boolean current(int ticket) {
        return !closed && ticket==request && !host.isFinishing() && !host.isDestroyed();
    }

    private void search(String query,int ticket) {
        if(!current(ticket)) return;
        showStatus("Mencari lokasi…");
        String link=TransportMapsInput.findLink(query);
        double[] coordinate=TransportMapsInput.coordinates(link.isEmpty()?query:link);
        if (coordinate!=null && TransportSearchRanking.valid(coordinate[0],coordinate[1])) {
            selection.picked(coordinate[0],coordinate[1],"");
            return;
        }
        if(!link.isEmpty()||placesSearch==null){
            searchFallback(query,link,ticket);
            return;
        }
        placesSearch.search(query,center.latitude,center.longitude,token,new TransportPlaceSearch.Callback(){
            public void onResults(List<TransportSearchRanking.Result> matches,boolean wider,boolean complete){
                if(!current(ticket))return;
                if(matches.isEmpty()){if(complete)searchFallback(query,"",ticket);return;}
                results.removeAllViews();
                for(TransportSearchRanking.Result match:matches) addResult(match.name,match.address,()->pickResult(match,ticket));
            }
            public void onFailure(){ if(current(ticket))searchFallback(query,"",ticket); }
        });
    }

    private void pickResult(TransportSearchRanking.Result match,int ticket) {
        if(!current(ticket))return;
        if(match.hasCoordinates()) {
            selection.picked(match.latitude,match.longitude,address(match.name,match.address));
            return;
        }
        final int pick=++request;
        placesSearch.cancel();
        showStatus("Menyiapkan titik…");
        FetchPlaceRequest ask=FetchPlaceRequest.builder(match.id,Arrays.asList(Place.Field.LOCATION,Place.Field.FORMATTED_ADDRESS))
                .setSessionToken(token).build();
        places.fetchPlace(ask).addOnSuccessListener(response->{
            if(!current(pick))return;
            LatLng point=response.getPlace().getLocation();
            if(point==null)showStatus("Lokasi belum ditemukan. Geser peta atau cari lagi.");
            else selection.picked(point.latitude,point.longitude,address(match.name,response.getPlace().getFormattedAddress()));
        }).addOnFailureListener(error->{if(current(pick))showStatus("Lokasi belum ditemukan. Geser peta atau cari lagi.");});
    }

    private void searchFallback(String query,String link,int ticket) {
        network.execute(()->{
            double[] coordinates=null;
            String label="";
            try {
                if(!link.isEmpty()) {
                    JSONObject response=TransivaHttpRepository.postJson(host,ApiConfig.server("resolve_google_maps.php"),
                            new JSONObject().put("url",link),10000);
                    coordinates=TransportMapsInput.coordinates(response.optString("url",link));
                } else {
                    List<Address> found=new Geocoder(host,new Locale("id","ID")).getFromLocationName(query,5,
                            Math.max(-90,center.latitude-.5),Math.max(-180,center.longitude-.5),
                            Math.min(90,center.latitude+.5),Math.min(180,center.longitude+.5));
                    if(found!=null && !found.isEmpty()) {
                        Address first=found.get(0);
                        coordinates=new double[]{first.getLatitude(),first.getLongitude()};
                        label=address(query,first.getAddressLine(0));
                    }
                }
            } catch(Exception ignored) { }
            final double[] point=coordinates;
            final String name=label;
            network.post(main,()->{
                if(!current(ticket))return;
                if(point==null || !TransportSearchRanking.valid(point[0],point[1])) {
                    showStatus("Lokasi belum ditemukan. Geser peta atau cari lagi.");
                } else if(!link.isEmpty()) selection.picked(point[0],point[1],"");
                else {
                    results.removeAllViews();
                    addResult(query,name,()->selection.picked(point[0],point[1],name));
                }
            });
        },()->main.post(()->{if(current(ticket))showStatus("Pencarian sedang sibuk. Coba lagi.");}));
    }

    private void showStatus(String message) {
        results.removeAllViews();
        TextView status=new TextView(host);
        status.setText(message);
        status.setPadding(dp(8),dp(8),dp(8),dp(8));
        results.addView(status);
    }

    private void addResult(String title,String subtitle,Runnable action) {
        Button row=new Button(host);
        row.setAllCaps(false);
        row.setMinHeight(dp(48));
        row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        row.setTextSize(12);
        row.setText(subtitle==null || subtitle.isEmpty() || subtitle.equals(title)?title:title+"\n"+subtitle);
        row.setOnClickListener(v->action.run());
        results.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }

    private static String address(String name,String full){return full==null||full.trim().isEmpty()?name:full;}
    private int dp(int value){return Math.round(value*host.getResources().getDisplayMetrics().density);}
}
