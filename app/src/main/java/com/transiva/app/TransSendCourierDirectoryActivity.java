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
import android.text.format.DateUtils;
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
    private Button contactStatus;
    private JSONObject activeContact;
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
        header.setPadding(dp(12), dp(8), dp(16), dp(8));
        header.setBackgroundColor(Color.WHITE);
        page.addView(header, new LinearLayout.LayoutParams(-1, -2));

        Button back = new Button(this);
        back.setText("‹");
        back.setTextSize(24);
        back.setTextColor(Color.parseColor("#075BB8"));
        back.setBackground(CustomerUiPrimitives.round("#EAF4FF",dp(15)));
        back.setContentDescription("Kembali");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = label(TITLE, 18, "#103769", true);
        title.setPaddingRelative(dp(8), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(16), dp(16), 0);
        page.addView(body, new LinearLayout.LayoutParams(-1, -2));
        body.addView(label("Driver di regional Anda", 17, "#103769", true));
        status = label("", 14, "#315678", false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setPadding(0, dp(6), 0, dp(8));
        body.addView(status);
        refresh = new Button(this);
        refresh.setAllCaps(false);
        refresh.setMinHeight(dp(44));
        refresh.setPadding(dp(14),dp(6),dp(14),dp(6));
        refresh.setTextColor(Color.parseColor("#075BB8"));
        refresh.setBackground(CustomerUiPrimitives.round("#E6F2FF",dp(14)));
        refresh.setOnClickListener(v -> {
            if (hasLocationPermission()) loadCouriers();
            else requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION);
        });
        body.addView(refresh, new LinearLayout.LayoutParams(-2, -2));
        contactStatus = new Button(this);
        contactStatus.setAllCaps(false);
        contactStatus.setMinHeight(dp(46));
        contactStatus.setTextColor(Color.WHITE);
        contactStatus.setBackground(CustomerUiPrimitives.gradient("#0759BF","#087AF2",dp(14)));
        contactStatus.setVisibility(View.GONE);
        contactStatus.setOnClickListener(v -> showContactActions());
        LinearLayout.LayoutParams chatSpace = new LinearLayout.LayoutParams(-1,-2);
        chatSpace.topMargin = dp(8);
        body.addView(contactStatus,chatSpace);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        body.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(dp(16), dp(8), dp(16), dp(16));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
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
                JSONObject point = new JSONObject().put("service","trans_simpel")
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
                : region + " · Diurutkan dari jarak terdekat", "Perbarui Daftar");
        CustomerAppSettings.apply(this);
    }

    private void addCourier(JSONObject courier) {
        String name = courier.optString("name", "Kurir Transiva");
        int distance = courier.optInt("distance_band_km");
        double rating = courier.optDouble("rating");
        String ratingText = "Belum ada rating";
        if (rating >= 1 && rating <= 5) {
            NumberFormat decimal = NumberFormat.getNumberInstance(INDONESIAN);
            decimal.setMinimumFractionDigits(1);
            decimal.setMaximumFractionDigits(1);
            ratingText = "★ " + decimal.format(rating);
        }
        String distanceText = distance == 0 ? "Kurang dari 1 km" : distance + "–" + (distance + 1) + " km";

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(CustomerUiPrimitives.roundStroke(this, "#FFFFFF", "#D4E6FA", dp(16), 1));

        FrameLayout avatar = new FrameLayout(this);
        avatar.setBackground(CustomerUiPrimitives.round("#EAF4FF", dp(24)));
        avatar.setClipToOutline(true);
        TextView initials = label(CallPeerIdentity.initials(name), 18, "#075BB8", true);
        initials.setGravity(Gravity.CENTER);
        avatar.addView(initials, new FrameLayout.LayoutParams(-1, -1));
        String photo = CallPeerIdentity.photoUrl(courier.optString("photo"));
        if (!photo.isEmpty()) {
            ImageView image = new ImageView(this);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            avatar.addView(image, new FrameLayout.LayoutParams(-1, -1));
            RemoteImageLoader.loadCenterCrop(image, photo, 0);
        }
        row.addView(avatar, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPaddingRelative(dp(12), 0, 0, 0);
        details.addView(label(name, 16, "#103769", true));
        details.addView(label(distanceText + "  ·  " + ratingText, 14, "#315678", false));
        long lastOrder = courier.optLong("last_order_epoch");
        CharSequence ago = lastOrder > 0 ? DateUtils.getRelativeTimeSpanString(lastOrder * 1000L)
                : "Belum ada catatan";
        details.addView(label("Terakhir menerima: " + ago, 12, "#315678", false));
        Button contact = new Button(this);
        contact.setAllCaps(false);
        contact.setText("Pilih Driver");
        contact.setTextColor(Color.WHITE);
        contact.setMinHeight(dp(46));
        contact.setPadding(dp(14), dp(8), dp(14), dp(8));
        contact.setBackground(CustomerUiPrimitives.gradient("#0759BF", "#087AF2", dp(13)));
        contact.setOnClickListener(v -> selectDriver(courier));
        LinearLayout.LayoutParams actionSpace = new LinearLayout.LayoutParams(-2, -2);
        actionSpace.topMargin = dp(6);
        details.addView(contact, actionSpace);
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(-1, -2);
        spacing.bottomMargin = dp(8);
        results.addView(row, spacing);
    }

    private void selectDriver(JSONObject courier) {
        if (directoryLocation == null) return;
        startActivity(new Intent(this, TivaSimpelActivity.class)
                .putExtra("driver_id", courier.optInt("id"))
                .putExtra("driver_name", courier.optString("name"))
                .putExtra("lat", directoryLocation.getLatitude())
                .putExtra("lng", directoryLocation.getLongitude()));
    }

    private void showContactActions() {
        if (activeContact == null || contactBusy) return;
        JSONObject entry = activeContact;
        String state = entry.optString("state");
        if ("ordered".equals(state)) {
            TransSendContactClient.openChat(this, entry);
            return;
        }
        android.app.AlertDialog dialog = new TransivaAlertDialogBuilder(this)
                .setTitle(entry.optString("peer_name", "Kurir TransSend"))
                .setMessage("accepted".equals(state) ? "Chat sudah siap dibuka."
                        : "Menunggu konfirmasi kurir. Permintaan berlaku 90 detik.")
                .setNegativeButton("Tutup", null)
                .setNeutralButton("Batalkan Permintaan", (d,w) -> runContactAction(
                        () -> TransSendContactClient.action("cancel", entry.optString("id")), false))
                .setPositiveButton("accepted".equals(state) ? "Buka Chat" : "Perbarui",
                        (d,w) -> {
                            if ("accepted".equals(state)) TransSendContactClient.openChat(this, entry);
                            else refreshContact();
                        }).create();
        dialog.show();
    }

    private interface Payload { JSONObject body() throws Exception; }

    private void runContactAction(Payload payload, boolean follow) {
        if (contactBusy) return;
        contactBusy = true;
        network.execute(() -> {
            try {
                JSONObject entry = TransSendContactClient.contact(this, payload.body()).getJSONObject("contact");
                network.post(main, () -> {
                    contactBusy = false;
                    followContact = follow ? entry.optString("id") : "";
                    presentContact(entry);
                    scheduleContactPoll();
                });
            } catch (Exception error) {
                network.post(main, () -> contactFailed(error));
            }
        }, () -> network.post(main, () -> contactFailed(null)));
    }

    private void refreshContact() {
        if (!visible || contactBusy) return;
        contactBusy = true;
        String id = followContact;
        network.execute(() -> {
            try {
                JSONObject response = TransSendContactClient.contact(this,
                        TransSendContactClient.action(id.isEmpty() ? "list" : "get", id));
                JSONObject entry = response.optJSONObject("contact");
                JSONArray list = response.optJSONArray("contacts");
                if (entry == null && list != null) entry = list.optJSONObject(0);
                JSONObject result = entry;
                network.post(main, () -> {
                    contactBusy = false;
                    if (visible) presentContact(result);
                    scheduleContactPoll();
                });
            } catch (Exception error) {
                network.post(main, () -> {
                    contactBusy = false;
                    if (error instanceof TransivaHttpRepository.ServerResponseException) {
                        int statusCode = ((TransivaHttpRepository.ServerResponseException) error).status;
                        if (statusCode == 404) followContact = "";
                    }
                    scheduleContactPoll();
                });
            }
        }, () -> network.post(main, () -> { contactBusy = false; scheduleContactPoll(); }));
    }

    private void presentContact(JSONObject entry) {
        activeContact = entry;
        String state = entry == null ? "" : entry.optString("state");
        if ("accepted".equals(state) || "ordered".equals(state)) {
            contactStatus.setText("Buka Chat · " + entry.optString("peer_name"));
            contactStatus.setVisibility(View.VISIBLE);
            if (visible && followContact.equals(entry.optString("id"))) {
                followContact = "";
                TransSendContactClient.openChat(this, entry);
            }
        } else if ("pending".equals(state)) {
            contactStatus.setText("Menunggu jawaban · " + entry.optString("peer_name"));
            contactStatus.setVisibility(View.VISIBLE);
            followContact = entry.optString("id");
        } else {
            contactStatus.setVisibility(View.GONE);
            if (!followContact.isEmpty() && !state.isEmpty()) {
                String message = "rejected".equals(state) ? "Kurir menolak permintaan Anda." : "Waktu permintaan sudah habis.";
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
            followContact = "";
        }
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
        if (visible && !followContact.isEmpty()) main.postDelayed(contactPoll, 3000);
    }

    private void finishLoading(String message, String action) {
        status.setText(message);
        refresh.setText(action);
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
