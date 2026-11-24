package com.transiva.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

public final class TransSendCourierDirectoryActivity extends CustomerAdaptiveActivity {
    private static final int TIVA_REQUEST=725;
    private Bundle tivaDraft;
    private static final int LOCATION_PERMISSION = 41;
    private static final String TITLE = "Trans Simpel";
    private static final Locale INDONESIAN = new Locale("id", "ID");

    private final Handler main = new Handler(Looper.getMainLooper());
    private final CustomerLifecycleNetworkScope network = new CustomerLifecycleNetworkScope();
    private LinearLayout results;
    private TextView status;
    private ProgressBar progress;
    private Button refresh;
    private Button nextPage;
    private JSONObject pageCursor;
    private boolean pageLoading;
    private final java.util.Set<Integer> shown = new java.util.HashSet<>();
    private CancellationTokenSource locationRequest;
    private Location directoryLocation;
    private LinearLayout activeList;
    private String activitySnapshot;
    private LinearLayout syncStatus;
    private boolean readingActivity;
    private int activityMutation;
    private String followingActivity;
    private String followContact = "";
    private boolean visible;
    private boolean contactBusy;
    private final Runnable contactPoll = this::refreshContact;

    static void open(Context context) {
        context.startActivity(new Intent(context, TransSendCourierDirectoryActivity.class));
    }

    static View homeCard(Context context) {
        FrameLayout card = new FrameLayout(context);
        card.setBackground(CustomerUiPrimitives.round("#082B81", CustomerUiPrimitives.dp(context, 19)));
        card.setClipToOutline(true);
        ImageView art = new ImageView(context);
        art.setImageResource(R.drawable.trans_simpel_banner);
        art.setAdjustViewBounds(true);
        art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(art, new FrameLayout.LayoutParams(-1, -2));
        card.setContentDescription("Trans Simpel. Chat driver dulu, baru pesan. Buka Trans Simpel.");
        card.setFocusable(true);
        card.setOnClickListener(v -> open(context));
        return card;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state!=null) tivaDraft=state.getBundle(TivaSimpelActivity.EXTRA_DRAFT);
        buildScreen();
        followContact = state == null ? getIntent().getStringExtra(TransSendContactClient.EXTRA_CONTACT)
                : state.getString(TransSendContactClient.EXTRA_CONTACT, "");
        if (followContact == null) followContact = "";
        if (hasLocationPermission()) loadCouriers();
        else showLocationPrompt();
    }

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        refreshContact();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String id = intent.getStringExtra(TransSendContactClient.EXTRA_CONTACT);
        if (id != null && !id.isEmpty()) {
            followContact = id;
            refreshContact();
        }
    }

    @Override protected void onPause() {
        visible = false;
        main.removeCallbacks(contactPoll);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBundle(TivaSimpelActivity.EXTRA_DRAFT,tivaDraft);
        out.putString(TransSendContactClient.EXTRA_CONTACT, followContact);
    }

    @Override protected void onDestroy() {
        if (locationRequest != null) locationRequest.cancel();
        network.destroy();
        super.onDestroy();
    }

    private void buildScreen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(2), dp(14), dp(2));
        header.setBackgroundColor(Color.WHITE);
        page.addView(header, new LinearLayout.LayoutParams(-1, -2));

        Button back = new Button(this);
        back.setText("←");
        back.setTextSize(24);
        back.setTextColor(Color.parseColor("#075BB8"));
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setContentDescription("Kembali");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = label(TITLE, 18, "#103769", true);
        title.setPaddingRelative(dp(4), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(8), dp(14), 0);
        page.addView(body, new LinearLayout.LayoutParams(-1, -2));

        boolean stacked = getResources().getConfiguration().screenWidthDp < 330
                || getResources().getConfiguration().fontScale > 1.2f;
        LinearLayout directoryHeader = new LinearLayout(this);
        directoryHeader.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        directoryHeader.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(directoryHeader, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.addView(label("Driver di regional Anda", 15, "#103769", true));
        status = label("", 12, "#315678", false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        heading.addView(status);
        directoryHeader.addView(heading, new LinearLayout.LayoutParams(stacked ? -1 : 0, -2, stacked ? 0 : 1));

        refresh = new Button(this);
        refresh.setAllCaps(false);
        refresh.setTextSize(12);
        refresh.setMinHeight(dp(48));
        refresh.setMinimumHeight(dp(48));
        refresh.setMinimumWidth(dp(48));
        refresh.setMinWidth(dp(48));
        refresh.setPadding(dp(9), dp(3), dp(9), dp(3));
        refresh.setTextColor(Color.parseColor("#075BB8"));
        refresh.setBackground(CustomerUiPrimitives.round("#E6F2FF",dp(12)));
        refresh.setOnClickListener(v -> {
            if (hasLocationPermission()) loadCouriers();
            else requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION);
        });
        LinearLayout.LayoutParams refreshSpace = new LinearLayout.LayoutParams(-2, -2);
        if (stacked) refreshSpace.topMargin = dp(4);
        else refreshSpace.setMarginStart(dp(8));
        directoryHeader.addView(refresh, refreshSpace);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        body.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(dp(14), dp(6), dp(14), dp(14));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        activeList=new LinearLayout(this);
        activeList.setOrientation(LinearLayout.VERTICAL);
        activeList.setPadding(dp(16),dp(8),dp(16),0);
        activeList.setVisibility(View.GONE);
        syncStatus=new LinearLayout(this);
        syncStatus.setGravity(Gravity.CENTER_VERTICAL);
        syncStatus.setPadding(dp(16),0,dp(16),0);
        syncStatus.setVisibility(View.GONE);
        syncStatus.addView(label("Belum tersinkron",13,"#805009",true),new LinearLayout.LayoutParams(0,-2,1));
        Button retry=new Button(this);
        retry.setAllCaps(false);
        retry.setText("Coba lagi");
        retry.setMinHeight(dp(48));
        retry.setOnClickListener(v->refreshContact());
        syncStatus.addView(retry,new LinearLayout.LayoutParams(-2,-2));
        list.addView(syncStatus,new LinearLayout.LayoutParams(-1,-2));
        list.addView(activeList,new LinearLayout.LayoutParams(-1,-2));
        list.addView(results, new LinearLayout.LayoutParams(-1,-2));
        nextPage = new Button(this);
        nextPage.setAllCaps(false);
        nextPage.setText("Muat Driver Lain");
        nextPage.setVisibility(View.GONE);
        nextPage.setOnClickListener(v -> fetchCouriers(directoryLocation, true));
        LinearLayout.LayoutParams nextSpace = new LinearLayout.LayoutParams(-1,-2);
        nextSpace.setMargins(dp(16),0,dp(16),dp(16));
        list.addView(nextPage,nextSpace);
        scroll.addView(list);
        setContentView(page);
        CustomerAppSettings.apply(this);
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void showLocationPrompt() {
        finishLoading("Izinkan lokasi untuk melihat kurir di sekitar Anda.", "Izinkan Lokasi");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode != LOCATION_PERMISSION) return;
        if (hasLocationPermission()) loadCouriers();
        else finishLoading("Akses lokasi diperlukan. Anda juga bisa mengaktifkannya di pengaturan aplikasi.", "Izinkan Lokasi");
    }

    private void loadCouriers() {
        results.removeAllViews();
        shown.clear();
        pageCursor = null;
        nextPage.setVisibility(View.GONE);
        status.setText("Mencari lokasi Anda…");
        refresh.setText("Perbarui Daftar");
        refresh.setEnabled(false);
        progress.setVisibility(View.VISIBLE);

        locationRequest = new CancellationTokenSource();
        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(60_000L)
                .setDurationMillis(8_000L)
                .build();
        try {
            LocationServices.getFusedLocationProviderClient(this)
                    .getCurrentLocation(request, locationRequest.getToken())
                    .addOnSuccessListener(location -> {
                        if (isFinishing() || network.isDestroyed()) return;
                        if (location == null) showLocationUnavailable();
                        else fetchCouriers(location, false);
                    })
                    .addOnFailureListener(error -> showLocationUnavailable());
        } catch (SecurityException error) {
            showLocationPrompt();
        }
    }

    private void showLocationUnavailable() {
        if (isFinishing() || network.isDestroyed()) return;
        finishLoading("Lokasi belum ditemukan. Aktifkan layanan lokasi lalu coba lagi.", "Coba Lagi");
    }

    private void fetchCouriers(Location location, boolean append) {
        if (location == null || pageLoading || (append && pageCursor == null)) return;
        directoryLocation = location;
        JSONObject cursor = append ? pageCursor : null;
        pageLoading = true;
        nextPage.setEnabled(false);
        refresh.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        if (!append) status.setText("Memuat driver di regional…");
        network.execute(() -> {
            try {
                JSONObject point = new JSONObject().put("service","trans_simpel").put("queue_capable",true)
                        .put("lat", location.getLatitude()).put("lng", location.getLongitude());
                if (cursor != null) point.put("after_band",cursor.getInt("after_band"))
                        .put("after_id",cursor.getInt("after_id"));
                JSONObject response = TransivaHttpRepository.postJson(this,
                        ApiConfig.server("customer_transsend_couriers.php"), point, 7000);
                JSONArray couriers = response.getJSONArray("couriers");
                network.post(main, () -> showCouriers(couriers,response.optString("region"),
                        response.optJSONObject("next")));
            } catch (Exception error) {
                String message = error instanceof TransivaHttpRepository.ServerResponseException
                        ? error.getMessage() : "Daftar driver belum dapat dimuat.";
                network.post(main, () -> pageFailed(message,append));
            }
        }, () -> network.post(main, () -> pageFailed("Daftar driver belum dapat dimuat.",append)));
    }

    private void pageFailed(String message, boolean append) {
        pageLoading = false;
        nextPage.setEnabled(true);
        if (append) Toast.makeText(this,message,Toast.LENGTH_SHORT).show();
        finishLoading(append ? status.getText().toString() : message, append ? "Perbarui Daftar" : "Coba Lagi");
    }

    private void showCouriers(JSONArray couriers, String region, JSONObject next) {
        for (int i = 0; i < couriers.length(); i++) {
            JSONObject courier = couriers.optJSONObject(i);
            if (courier != null && shown.add(courier.optInt("id"))) addCourier(courier);
        }
        pageLoading = false;
        pageCursor = next;
        nextPage.setVisibility(next == null ? View.GONE : View.VISIBLE);
        nextPage.setEnabled(true);
        finishLoading(shown.isEmpty() ? "Belum ada driver tersedia di " + region + "."
                : region + " · Jarak terdekat", "Perbarui");
        CustomerAppSettings.apply(this);
    }

    private void addCourier(JSONObject courier) {
        String name = courier.optString("name", "Kurir Transiva");
        String state = courier.optString("driver_state", "idle");
        boolean idle = "idle".equals(state);
        boolean busy = "busy".equals(state);
        boolean offline = !idle && !busy && "offline".equals(courier.optString("driver_presence"));
        boolean dark = CustomerAppSettings.isDarkMode(this);

        String marker = idle ? "Standby" : busy ? "Sibuk" : offline ? "Offline" : "Tidak tersedia";
        String accent = idle ? (dark ? "#8EE2BB" : "#116444")
                : busy ? (dark ? "#FCD387" : "#815009") : (dark ? "#C5D0DD" : "#485568");
        String background = idle ? (dark ? "#142C29" : "#F1FBF6")
                : busy ? (dark ? "#332A1D" : "#FFF8EB") : (dark ? "#222B38" : "#F0F3F7");
        String border = idle ? (dark ? "#31584B" : "#C0E5D3")
                : busy ? (dark ? "#655239" : "#EED29D") : (dark ? "#3C4758" : "#D6DFEA");
        String pill = idle ? (dark ? "#214839" : "#D9F3E5")
                : busy ? (dark ? "#554125" : "#FBE7B9") : (dark ? "#313D4D" : "#E1E7EF");

        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(CustomerUiPrimitives.roundStroke(this, background, border, dp(13), 1));
        frame.setClipToOutline(true);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPaddingRelative(dp(13), dp(5), dp(8), dp(5));
        card.setMinimumHeight(dp(58));
        card.setGravity(Gravity.CENTER_VERTICAL);
        frame.addView(card, new FrameLayout.LayoutParams(-1, -2));
        View stripe = new View(this);
        stripe.setBackgroundColor(Color.parseColor(idle ? "#27A45E" : busy ? "#E6A326" : "#87939F"));
        stripe.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        frame.addView(stripe, new FrameLayout.LayoutParams(dp(4), -1, Gravity.START));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBaselineAligned(false);
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout avatar = new FrameLayout(this);
        avatar.setBackground(CustomerUiPrimitives.round(dark ? "#26354A" : "#E5EFFF", dp(19)));
        avatar.setClipToOutline(true);
        TextView initials = label(CallPeerIdentity.initials(name), 13, dark ? "#BED9FF" : "#075BB8", true);
        initials.setGravity(Gravity.CENTER);
        initials.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        avatar.addView(initials, new FrameLayout.LayoutParams(-1, -1));
        String photo = CallPeerIdentity.photoUrl(courier.optString("photo"));
        if (!photo.isEmpty()) {
            ImageView image = new ImageView(this);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            avatar.addView(image, new FrameLayout.LayoutParams(-1, -1));
            RemoteImageLoader.loadCenterCrop(image, photo, 0);
        }
        row.addView(avatar, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPaddingRelative(dp(8), 0, dp(4), 0);
        TextView driverName = label(name, 14, dark ? "#F1F5FB" : "#112C4B", true);
        driverName.setMaxLines(2);
        driverName.setEllipsize(TextUtils.TruncateAt.END);
        details.addView(driverName);

        details.addView(label(courierMetadata(courier), 12, dark ? "#C0CCDB" : "#425A73", false));
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));

        boolean stacked = getResources().getConfiguration().fontScale > 1.25f
                || getResources().getConfiguration().screenWidthDp < 330;
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setOrientation(stacked ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        if (stacked) {
            actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            card.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        } else {
            View divider = new View(this);
            divider.setBackgroundColor(Color.parseColor(border));
            row.addView(divider, new LinearLayout.LayoutParams(dp(1), dp(30)));
            LinearLayout.LayoutParams actionColumn = new LinearLayout.LayoutParams(dp(86), -2);
            actionColumn.setMarginStart(dp(7));
            row.addView(actions, actionColumn);
        }
        TextView badge = label((idle ? "✓ " : busy ? "◷ " : offline ? "○ " : "") + marker, 11, accent, true);
        badge.setContentDescription(marker);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(6), dp(1), dp(6), dp(1));
        badge.setBackground(CustomerUiPrimitives.round(pill, dp(9)));
        actions.addView(badge, new LinearLayout.LayoutParams(stacked ? -2 : -1, -2));

        if (idle || busy) {
            Button contact = new Button(this);
            contact.setAllCaps(false);
            contact.setText(busy ? "Antre" : "Pilih");
            contact.setContentDescription((busy ? "Antre dengan " : "Pilih ") + name);
            contact.setTextSize(12);
            contact.setTextColor(Color.WHITE);
            contact.setMinHeight(dp(48));
            contact.setMinimumHeight(dp(48));
            contact.setMinWidth(dp(58));
            contact.setMinimumWidth(dp(58));
            contact.setPadding(dp(9), dp(4), dp(9), dp(4));
            contact.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                    CustomerUiPrimitives.round(busy ? "#8B510A" : "#075CC5", dp(10)), null));
            contact.setOnClickListener(v -> selectDriver(courier));

            LinearLayout.LayoutParams actionSpace = new LinearLayout.LayoutParams(stacked ? -2 : -1, -2);
            if (stacked) actionSpace.setMarginStart(dp(6));
            else actionSpace.topMargin = dp(3);
            actions.addView(contact, actionSpace);
        }

        LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(-1, -2);
        spacing.bottomMargin = dp(5);
        results.addView(frame, spacing);
    }

    private String courierMetadata(JSONObject courier) {
        int distance = courier.optInt("distance_band_km", -1);
        String distanceText = distance < 0 ? "Jarak belum tersedia"
                : distance == 0 ? "< 1 km" : distance + "–" + (distance + 1) + " km";
        double rating = courier.optDouble("rating");
        if (rating < 1 || rating > 5 || Double.isNaN(rating)) return distanceText;
        NumberFormat decimal = NumberFormat.getNumberInstance(INDONESIAN);
        decimal.setMinimumFractionDigits(1);
        decimal.setMaximumFractionDigits(1);
        return distanceText + " · ★ " + decimal.format(rating);
    }

    private void selectDriver(JSONObject courier) {
        if (directoryLocation == null) return;
        startActivityForResult(new Intent(this, TivaSimpelActivity.class)
                .putExtra("driver_id", courier.optInt("id"))
                .putExtra("driver_name", courier.optString("name"))
                .putExtra("driver_busy","busy".equals(courier.optString("driver_state")))
                .putExtra("lat", directoryLocation.getLatitude())
                .putExtra("lng", directoryLocation.getLongitude())
                .putExtra(TivaSimpelActivity.EXTRA_DRAFT,tivaDraft),TIVA_REQUEST);
        tivaDraft=null;
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request==TIVA_REQUEST && result==RESULT_OK && data!=null) {
            tivaDraft=data.getBundleExtra(TivaSimpelActivity.EXTRA_DRAFT);
            if (tivaDraft!=null) Toast.makeText(this,"Draf tersimpan. Pilih driver untuk melanjutkan.",Toast.LENGTH_SHORT).show();
        }
    }

    private interface Payload { JSONObject body() throws Exception; }

    private void runContactAction(Payload payload, boolean follow) {
        if (contactBusy) {
            main.removeCallbacks(contactPoll);
            main.postDelayed(()->{if (visible) runContactAction(payload,follow);},300);
            return;
        }
        contactBusy = true;
        activityMutation++;
        network.execute(() -> {
            try {
                JSONObject entry = TransSendContactClient.contact(this, payload.body()).getJSONObject("contact");
                network.post(main, () -> {
                    contactBusy = false;
                    followContact = follow ? entry.optString("id") : "";
                    presentFollowedContact(entry);
                    refreshContact();
                    scheduleContactPoll();
                });
            } catch (Exception error) {
                network.post(main, () -> contactFailed(error));
            }
        }, () -> network.post(main, () -> contactFailed(null)));
    }

    private void refreshContact() {
        if (!visible) return;
        fetchFollowedContact();
        if (!readingActivity && !contactBusy) {
            readingActivity=true;
            int mutation=activityMutation;
            network.execute(()->{
                try {
                    JSONObject response=TransSendContactClient.contact(this,TransSendContactClient.action("active_list",""));
                    network.post(main,()->finishActivityRead(response,mutation));
                } catch (Exception error) {
                    network.post(main,()->finishActivityRead(null,mutation));
                }
            },()->network.post(main,()->finishActivityRead(null,mutation)));
        }
        scheduleContactPoll();
    }

    private void finishActivityRead(JSONObject response,int mutation) {
        readingActivity=false;
        if (!visible || contactBusy) return;
        if (mutation!=activityMutation) { refreshContact(); return; }
        boolean synced=response!=null && response.optBoolean("success")
                && response.optJSONArray("contacts")!=null && response.optJSONArray("orders")!=null;
        syncStatus.setVisibility(synced?View.GONE:View.VISIBLE);
        if (synced) renderActivity(response);
    }

    private void fetchFollowedContact() {
        String id=followContact;
        if (id.isEmpty() || id.equals(followingActivity) || contactBusy) return;
        followingActivity=id;
        network.execute(()->{
            try {
                JSONObject entry=TransSendContactClient.contact(this,
                        TransSendContactClient.action("get",id)).optJSONObject("contact");
                network.post(main,()->finishFollowedRead(id,entry,false));
            } catch (Exception error) {
                boolean missing=error instanceof TransivaHttpRepository.ServerResponseException
                        && ((TransivaHttpRepository.ServerResponseException)error).status==404;
                network.post(main,()->finishFollowedRead(id,null,missing));
            }
        },()->network.post(main,()->finishFollowedRead(id,null,false)));
    }

    private void finishFollowedRead(String id,JSONObject entry,boolean missing) {
        if (id.equals(followingActivity)) followingActivity=null;
        if (!visible || contactBusy || !id.equals(followContact)) return;
        if (missing) followContact="";
        else presentFollowedContact(entry);
    }

    private void presentFollowedContact(JSONObject entry) {
        if (entry==null || !entry.optString("id").equals(followContact)) return;
        String state=entry.optString("state");
        if ("pending".equals(state)) return;
        followContact="";
        if (visible && ("accepted".equals(state) || "ordered".equals(state))) {
            TransSendContactClient.openChat(this,entry);
        } else if (visible && "rejected".equals(state)) {
            Toast.makeText(this,"Driver belum bisa menerima permintaan.",Toast.LENGTH_SHORT).show();
        }
    }

    private void renderActivity(JSONObject response) {
        JSONArray contacts=response.optJSONArray("contacts");
        JSONArray orders=response.optJSONArray("orders");
        String snapshot=String.valueOf(contacts)+String.valueOf(orders);
        if (snapshot.equals(activitySnapshot)) return;
        activitySnapshot=snapshot;
        activeList.removeAllViews();
        boolean hasContacts=contacts!=null && contacts.length()>0;
        boolean hasOrders=orders!=null && orders.length()>0;
        activeList.setVisibility(hasContacts || hasOrders?View.VISIBLE:View.GONE);
        if (!hasContacts && !hasOrders) return;
        activeList.addView(label("Trans Simpel aktif",17,"#103769",true));
        if (hasContacts) for (int i=0;i<contacts.length();i++) {
            JSONObject entry=contacts.optJSONObject(i);
            if (entry==null) continue;
            boolean accepted="accepted".equals(entry.optString("state"));
            String state=entry.optBoolean("waiting_driver")?"Menunggu driver menyelesaikan pesanan aktif"
                    :accepted?"Chat siap dibuka":"Menunggu jawaban driver";
            LinearLayout row=activityCard(entry.optString("peer_name","Driver"),state);
            if (accepted) addActivityAction(row,"Buka chat",false,()->TransSendContactClient.openChat(this,entry));
            addActivityAction(row,"Batalkan",true,()->new TransivaAlertDialogBuilder(this)
                    .setTitle("Batalkan permintaan?")
                    .setNegativeButton("Kembali",null)
                    .setPositiveButton("Batalkan",(d,w)->runContactAction(
                            ()->TransSendContactClient.action("cancel",entry.optString("id")),false)).show());
        }
        if (hasOrders) for (int i=0;i<orders.length();i++) {
            JSONObject order=orders.optJSONObject(i);
            if (order==null) continue;
            String service=order.optString("service_name");
            String state=order.optBoolean("cancel_pending")?"Menunggu persetujuan pembatalan dari toko"
                    :OrderStatusPresentation.label(order.optString("status"),service);
            LinearLayout row=activityCard(service+" · #"+order.optString("order_id"),state);
            addActivityAction(row,"Lacak",false,()->openActiveOrder(order));
            JSONObject chat=order.optJSONObject("chat");
            if (chat!=null) addActivityAction(row,"Chat",false,()->{
                if (chat.has("source")) TransSendContactClient.openAssignedOrderChat(this,chat);
                else TransSendContactClient.openChat(this,chat);
            });
            if (order.optBoolean("can_cancel")) {
                boolean ask=order.optBoolean("needs_merchant");
                addActivityAction(row,ask?"Ajukan batal":"Batalkan",true,()->confirmOrderCancellation(order,ask));
            }
            JSONObject previousChat=order.optJSONObject("previous_chat");
            if (previousChat!=null) {
                LinearLayout historyRow=new LinearLayout(this);
                ((LinearLayout)row.getParent()).addView(historyRow);
                addActivityAction(historyRow,"Riwayat chat driver sebelumnya",false,
                        ()->TransSendContactClient.openChatHistory(this,previousChat));
            }
        }
    }

    private LinearLayout activityCard(String title,String state) {
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12),dp(10),dp(12),dp(5));
        card.setBackground(CustomerUiPrimitives.round("#FFFFFF",dp(16)));
        LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-1,-2);
        space.topMargin=dp(8);
        activeList.addView(card,space);
        card.addView(label(title,15,"#103769",true));
        card.addView(label(state,13,"#315678",false));
        LinearLayout row=new LinearLayout(this);
        card.addView(row);
        return row;
    }

    private void addActivityAction(LinearLayout row,String title,boolean destructive,Runnable action) {
        Button button=new Button(this);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(48));
        button.setPadding(dp(5),0,dp(5),0);
        button.setTextSize(13);
        button.setText(title);
        button.setTextColor(Color.parseColor(destructive?"#9B2441":"#075BB8"));
        button.setBackground(CustomerUiPrimitives.round(destructive?"#FFF0F3":"#EAF4FF",dp(11)));
        LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(0,-2,1);
        space.setMargins(0,dp(5),dp(4),dp(4));
        row.addView(button,space);
        button.setOnClickListener(v->action.run());
    }

    private void openActiveOrder(JSONObject order) {
        String id=order.optString("order_id");
        String source=order.optString("source");
        String service=order.optString("service_name");
        String status=order.optString("status");
        Class<?> screen="TransFood".equals(service)?CustomerFoodOrderStatusActivity.class
                : ("offered".equals(status) || "pending".equals(status))?SearchDriverActivity.class:CustomerTripActivity.class;
        startActivity(new Intent(this,screen).putExtra("order_id",id).putExtra("source",source)
                .putExtra("order_source",source).putExtra("order_type",service).putExtra("status",status));
    }

    private void confirmOrderCancellation(JSONObject order,boolean ask) {
        new TransivaAlertDialogBuilder(this)
                .setTitle(ask?"Ajukan pembatalan ke toko?":"Batalkan pesanan?")
                .setNegativeButton("Kembali",null)
                .setPositiveButton(ask?"Ajukan":"Batalkan",(d,w)->cancelActiveOrder(order)).show();
    }

    private void cancelActiveOrder(JSONObject order) {
        if (contactBusy) {
            main.removeCallbacks(contactPoll);
            main.postDelayed(()->{if (visible) cancelActiveOrder(order);},300);
            return;
        }
        contactBusy=true;
        activityMutation++;
        network.execute(()->{
            try {
                JSONObject data=new JSONObject().put("order_id",order.optString("order_id"))
                        .put("table",order.optString("source")).put("food_cancel_request_supported",true)
                        .put("trans_simpel_list",true);
                JSONObject response=TransivaHttpRepository.postJson(this,ApiConfig.server("cancel_order.php"),data,15000);
                network.post(main,()->{
                    contactBusy=false;
                    if (visible) {
                        Toast.makeText(this,response.optString("message"),Toast.LENGTH_LONG).show();
                        refreshContact();
                    }
                });
            } catch (Exception error) {
                network.post(main,()->contactFailed(error));
            }
        },()->network.post(main,()->contactFailed(null)));
    }

    private void contactFailed(Exception error) {
        contactBusy = false;
        if (visible) {
            String message = error instanceof TransivaHttpRepository.ServerResponseException
                    ? error.getMessage() : "Permintaan belum dapat diproses. Coba lagi.";
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
        scheduleContactPoll();
    }

    private void scheduleContactPoll() {
        main.removeCallbacks(contactPoll);
        if (visible) main.postDelayed(contactPoll,followContact.isEmpty()?7000:3000);
    }

    private void finishLoading(String message, String action) {
        status.setText(message);
        boolean compactRefresh = action.startsWith("Perbarui");
        refresh.setText(compactRefresh ? "↻" : action);
        refresh.setTextSize(compactRefresh ? 22 : 12);
        refresh.setContentDescription(compactRefresh ? "Perbarui daftar driver" : action);
        refresh.setEnabled(true);
        progress.setVisibility(View.GONE);
    }

    private int dp(int value) {
        return CustomerUiPrimitives.dp(this, value);
    }

    private TextView label(String value, int size, String color, boolean bold) {
        return CustomerUiPrimitives.text(this, value, size, color, bold);
    }
}
