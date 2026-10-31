package com.transiva.app;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.SearchByTextRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/** Nearby first; wider autocomplete is used only when local searches have no match. */
final class TransportPlaceSearch {
    interface Callback {
        void onResults(List<TransportSearchRanking.Result> rows, boolean wider, boolean complete);
        void onFailure();
    }
    private static final int LOCAL_METERS = 12000;
    private final PlacesClient client;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String, Cached> cache = new LinkedHashMap<String, Cached>(24, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Cached> e) { return size() > 24; }
    };
    private Session active;
    private static final class Cached {
        final List<TransportSearchRanking.Result> rows;
        final boolean wider;
        final long savedAt = SystemClock.elapsedRealtime();
        Cached(List<TransportSearchRanking.Result> rows, boolean wider) { this.rows = new ArrayList<>(rows); this.wider = wider; }
    }
    TransportPlaceSearch(PlacesClient client) { this.client = client; }

    void cancel() { if (active != null) active.cancelled = true; active = null; handler.removeCallbacksAndMessages(null); }

    void search(String query, double lat, double lng, AutocompleteSessionToken token, Callback callback) {
        cancel();
        Session s = new Session(query, lat, lng, token, callback);
        active = s;
        Cached saved = cache.get(s.key);
        if (saved != null && SystemClock.elapsedRealtime() - saved.savedAt <= 120000L) {
            s.finished = true;
            callback.onResults(new ArrayList<>(saved.rows), saved.wider, true);
            return;
        }
        handler.postDelayed(() -> {
            if (!s.alive() || s.finished) return;
            List<TransportSearchRanking.Result> rows = s.merged();
            s.finished = true;
            if (!rows.isEmpty()) callback.onResults(rows, false, true);
            else callback.onFailure();
        }, 8000L);
        s.autocomplete(false);
        // Wait for a stable query before the extra Text Search request. No call per keystroke.
        if (s.textDone) return;
        handler.postDelayed(s::textSearch, 500L);
    }

    private final class Session {
        final String query, key;
        final double lat, lng;
        final AutocompleteSessionToken token;
        final Callback callback;
        final boolean local;
        final List<TransportSearchRanking.Result> predictions = new ArrayList<>(), places = new ArrayList<>();
        boolean predictionDone, textDone, cancelled, finished, widening;
        boolean predictionSuccess, textSuccess;
        Session(String query, double lat, double lng, AutocompleteSessionToken token, Callback callback) {
            this.query = query.trim(); this.lat = lat; this.lng = lng; this.token = token; this.callback = callback;
            key = TransportSearchRanking.cacheKey(query, lat, lng);
            local = TransportSearchRanking.valid(lat, lng);
            textDone = !local || query.trim().length() < 4;
        }
        boolean alive() { return active == this && !cancelled && !finished; }
        RectangularBounds bounds(double radius) {
            double dlat = radius / 111320d;
            double dlng = dlat / Math.max(.1, Math.abs(Math.cos(Math.toRadians(lat))));
            return RectangularBounds.newInstance(new LatLng(Math.max(-89.9, lat - dlat), Math.max(-179.9, lng - dlng)),
                    new LatLng(Math.min(89.9, lat + dlat), Math.min(179.9, lng + dlng)));
        }
        List<TransportSearchRanking.Result> merged() {
            return TransportSearchRanking.merge(predictions, places, local && !widening ? LOCAL_METERS : 0);
        }
        void autocomplete(boolean wider) {
            if (!alive()) return;
            try {
                FindAutocompletePredictionsRequest.Builder b = FindAutocompletePredictionsRequest.builder()
                        .setQuery(query).setCountries(java.util.Arrays.asList("ID")).setSessionToken(token);
                if (local) {
                    b.setOrigin(new LatLng(lat, lng));
                    if (wider) b.setLocationBias(bounds(50000d));
                    else b.setLocationRestriction(bounds(LOCAL_METERS));
                }
                client.findAutocompletePredictions(b.build()).addOnSuccessListener(response -> {
                    if (!alive()) return;
                    predictions.clear();
                    for (AutocompletePrediction p : response.getAutocompletePredictions())
                        predictions.add(new TransportSearchRanking.Result(p.getPlaceId(), p.getPrimaryText(null).toString(),
                                p.getSecondaryText(null).toString(), Double.NaN, Double.NaN, p.getDistanceMeters()));
                    predictionDone = true; predictionSuccess = true;
                    if (wider) finish(true); else publishLocal();
                }).addOnFailureListener(error -> {
                    if (!alive()) return;
                    predictionDone = true;
                    if (wider) failOrFinish(); else publishLocal();
                });
            } catch (Exception ignored) {
                predictionDone = true;
                if (wider) failOrFinish(); else publishLocal();
            }
        }
        void textSearch() {
            if (!alive()) return;
            try {
                List<Place.Field> fields = java.util.Arrays.asList(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION);
                SearchByTextRequest request = SearchByTextRequest.builder(query, fields)
                        .setLocationRestriction(bounds(LOCAL_METERS))
                        .setRankPreference(SearchByTextRequest.RankPreference.DISTANCE).setMaxResultCount(10).build();
                client.searchByText(request).addOnSuccessListener(response -> {
                    if (!alive()) return;
                    for (Place p : response.getPlaces()) {
                        LatLng point = p.getLocation();
                        if (point == null || !TransportSearchRanking.valid(point.latitude, point.longitude)) continue;
                        places.add(new TransportSearchRanking.Result(p.getId(), p.getDisplayName(), p.getFormattedAddress(), point.latitude, point.longitude,
                                TransportSearchRanking.distance(lat, lng, point.latitude, point.longitude)));
                    }
                    textDone = true; textSuccess = true; publishLocal();
                }).addOnFailureListener(error -> { if (alive()) { textDone = true; publishLocal(); } });
            } catch (Exception ignored) { textDone = true; publishLocal(); }
        }
        void publishLocal() {
            if (!alive()) return;
            List<TransportSearchRanking.Result> rows = merged();
            if (predictionDone && textDone) {
                if (!rows.isEmpty()) { finish(false); return; }
                if (!predictionSuccess && !textSuccess) { failOrFinish(); return; }
                widening = true;
                autocomplete(true);
            } else if (!rows.isEmpty()) callback.onResults(rows, false, false);
        }
        void finish(boolean wider) {
            if (!alive()) return;
            List<TransportSearchRanking.Result> rows = merged();
            finished = true;
            if (!rows.isEmpty()) cache.put(key, new Cached(rows, wider));
            callback.onResults(rows, wider, true);
        }
        void failOrFinish() {
            if (!alive()) return;
            if (!merged().isEmpty()) { finish(widening); return; }
            finished = true; callback.onFailure();
        }
    }
}
