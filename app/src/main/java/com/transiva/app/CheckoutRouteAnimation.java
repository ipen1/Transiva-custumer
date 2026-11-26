package com.transiva.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Point;
import android.location.Location;
import android.view.animation.LinearInterpolator;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;

import java.util.ArrayList;
import java.util.List;

/** A short, interruptible reveal over the confirmed directions geometry. */
final class CheckoutRouteAnimation {
    private static final int START_CUE_MS = 330;
    private static final int DESTINATION_CUE_MS = 640;
    private static final int MAX_PREVIEW_VERTICES = 1800;
    private static final int[] PICKUP_COLORS = {0xFF73F7D0, 0xFF24D4A5};
    private static final int[] STOP_COLORS = {0xFFFFE7A8, 0xFFFFB34F};
    private static final int[] DESTINATION_COLORS = {0xFFFFB3CB, 0xFFFF668C};

    private final GoogleMap map;
    private final Polyline route;
    private final Polyline casing;
    private final List<LatLng> fullRoute;
    private final List<LatLng> points = new ArrayList<>();
    private final List<Marker> markers;
    private final LatLng[] anchors;
    private final Circle[] pulses;
    private final float[] distance;
    private final float[] arrival;
    private final float density;
    private final CameraUpdate overview;

    private Polyline aura, trail, spark;
    private Circle head;
    private ValueAnimator timeline;
    private boolean active = true;
    private boolean finalRouteShown;

    CheckoutRouteAnimation(GoogleMap map, Polyline route, Polyline casing, List<LatLng> fullRoute,
                           List<Marker> markers, List<Integer> legEnds, float density, CameraUpdate overview) {
        this.map = map;
        this.route = route;
        this.casing = casing;
        this.fullRoute = fullRoute;
        this.markers = markers;
        this.density = density;
        this.overview = overview;
        anchors = new LatLng[markers.size()];
        pulses = new Circle[markers.size()];
        arrival = new float[markers.size()];
        for (int i = 0; i < anchors.length; i++) anchors[i] = markers.get(i).getPosition();

        // Bound per-frame work for long routes. Keep every leg boundary and restore exact geometry at the end.
        int stride = Math.max(1, (fullRoute.size() - 1) / MAX_PREVIEW_VERTICES + 1);
        int[] stopVertices = new int[legEnds.size()];
        int nextStop = 0;
        for (int i = 0; i < fullRoute.size(); i++) {
            boolean legEnd = nextStop < legEnds.size() && i == legEnds.get(nextStop);
            if (legEnd) stopVertices[nextStop++] = points.size();
            if (legEnd || i % stride == 0 || i == fullRoute.size() - 1) points.add(fullRoute.get(i));
        }
        distance = new float[points.size()];
        float[] step = new float[1];
        for (int i = 1; i < points.size(); i++) {
            LatLng a = points.get(i - 1), b = points.get(i);
            Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, step);
            if (!Float.isFinite(step[0])) step[0] = 0;
            distance[i] = distance[i - 1] + step[0];
        }
        for (int i = 0; i < stopVertices.length; i++) arrival[i + 1] = distance[stopVertices[i]];
        arrival[arrival.length - 1] = distance[distance.length - 1];
    }

    void start() {
        if (distance[distance.length - 1] < 1f) {
            cancel();
            return;
        }
        setLine(points.subList(0, 1));
        map.animateCamera(overview, 460, new GoogleMap.CancelableCallback() {
            @Override public void onFinish() { if (active) reveal(); }
            @Override public void onCancel() { cancel(); }
        });
    }

    private void reveal() {
        aura = effectLine(20, 0x5577D9FF, 3);
        trail = effectLine(8, 0xFF6ACFFF, 4);
        spark = effectLine(4, 0xFFE8FEFF, 5);
        head = map.addCircle(new CircleOptions().center(points.get(0)).radius(0)
                .fillColor(0xFFF4FFFF).strokeColor(0xFF3ECFFC).strokeWidth(2.5f * density).zIndex(6));
        for (int i = 0; i < anchors.length; i++) {
            pulses[i] = map.addCircle(new CircleOptions().center(anchors[i]).radius(0)
                    .strokeWidth(2.4f * density).zIndex(7).visible(false));
        }

        float total = distance[distance.length - 1];
        int cruiseMs = 1720 + 170 * (anchors.length - 2);
        int finish = START_CUE_MS + cruiseMs;
        float[] cueTimes = new float[arrival.length];
        for (int i = 1; i < cueTimes.length; i++) cueTimes[i] = START_CUE_MS + cruiseMs * arrival[i] / total;
        timeline = ValueAnimator.ofFloat(0, finish + DESTINATION_CUE_MS);
        timeline.setInterpolator(new LinearInterpolator());
        timeline.setDuration(finish + DESTINATION_CUE_MS);
        timeline.addUpdateListener(frame -> {
            float elapsed = (float) frame.getAnimatedValue();
            float end = total * Math.max(0, Math.min(1, (elapsed - START_CUE_MS) / cruiseMs));
            if (elapsed >= START_CUE_MS && elapsed < finish) {
                setLine(slice(0, end));
                aura.setPoints(slice(Math.max(0, end - total * .16f), end));
                trail.setPoints(slice(Math.max(0, end - total * .085f), end));
                spark.setPoints(slice(Math.max(0, end - total * .032f), end));
                LatLng tip = interpolate(segment(end), end);
                head.setCenter(tip);
                head.setRadius(mapRadius(tip, 4.3f));
            } else if (elapsed >= finish && !finalRouteShown) {
                showFinalRoute();
                clearTrail();
            }
            for (int i = 0; i < anchors.length; i++) {
                markerCue(i, elapsed - cueTimes[i]);
            }
        });
        timeline.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator ignored) {
                if (active) {
                    restore();
                    orbit(map.getCameraPosition(), 0);
                }
            }
        });
        timeline.start();
    }

    private void markerCue(int index, float ms) {
        boolean destination = index == anchors.length - 1;
        float duration = destination ? DESTINATION_CUE_MS : 535;
        Circle pulse = pulses[index];
        Marker marker = markers.get(index);
        LatLng anchor = anchors[index];
        if (ms < 0 || ms >= duration) {
            if (pulse.isVisible()) pulse.setVisible(false);
            if (!anchor.equals(marker.getPosition())) marker.setPosition(anchor);
            return;
        }
        float progress = ms / duration;
        float decay = 1 - progress;
        int[] colors = index == 0 ? PICKUP_COLORS : destination ? DESTINATION_COLORS : STOP_COLORS;
        pulse.setCenter(anchor);
        pulse.setRadius(mapRadius(anchor, 8 + (destination ? 32 : 25) * (1 - decay * decay)));
        pulse.setStrokeColor(withAlpha(colors[0], Math.round(220 * decay)));
        pulse.setFillColor(withAlpha(colors[1], Math.round(85 * decay)));
        if (!pulse.isVisible()) pulse.setVisible(true);

        Projection projection = map.getProjection();
        Point pixel = projection.toScreenLocation(anchor);
        float lift = (float) Math.abs(Math.sin(2 * Math.PI * progress)) * decay;
        pixel.y -= Math.round(lift * (destination ? 29 : 19) * density);
        marker.setPosition(projection.fromScreenLocation(pixel));
    }

    private double mapRadius(LatLng center, float dp) {
        double metersPerPixel = 156543.03392 * Math.cos(Math.toRadians(center.latitude))
                / Math.pow(2, map.getCameraPosition().zoom);
        return Math.max(.1, dp * density * metersPerPixel);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private Polyline effectLine(int width, int color, int z) {
        return map.addPolyline(new PolylineOptions().width(width * density).color(color).zIndex(z)
                .jointType(JointType.ROUND).startCap(new RoundCap()).endCap(new RoundCap()));
    }

    private void setLine(List<LatLng> line) {
        casing.setPoints(line);
        route.setPoints(line);
    }

    private void showFinalRoute() {
        if (finalRouteShown) return;
        setLine(fullRoute);
        finalRouteShown = true;
    }

    private List<LatLng> slice(float from, float to) {
        int start = segment(from), end = segment(to);
        List<LatLng> result = new ArrayList<>(end - start + 2);
        result.add(interpolate(start, from));
        result.addAll(points.subList(start + 1, end + 1));
        result.add(interpolate(end, to));
        return result;
    }

    private int segment(float meters) {
        int low = 0, high = distance.length - 1;
        while (high - low > 1) {
            int mid = (low + high) / 2;
            if (distance[mid] <= meters) low = mid;
            else high = mid;
        }
        return low;
    }

    private LatLng interpolate(int index, float meters) {
        LatLng a = points.get(index), b = points.get(index + 1);
        float length = distance[index + 1] - distance[index];
        if (length <= 0) return b;
        float f = Math.max(0, Math.min(1, (meters - distance[index]) / length));
        double longitude = (b.longitude - a.longitude + 540) % 360 - 180;
        return new LatLng(a.latitude + (b.latitude - a.latitude) * f, a.longitude + longitude * f);
    }

    boolean isActive() { return active; }
    boolean anchorsPickup(LatLng point) { return anchors[0].equals(point); }
    boolean anchorsDelivery(LatLng point) { return anchors[anchors.length - 1].equals(point); }

    void cancel() {
        if (!active) return;
        active = false;
        if (timeline != null) timeline.cancel();
        restore();
    }

    private void orbit(CameraPosition overview, int shot) {
        if (!active) return;
        if (shot == 3) {
            active = false;
            return;
        }
        float bearing = shot == 0 ? -25f : shot == 1 ? 28f : 7f;
        float tilt = shot == 0 ? 34f : shot == 1 ? 40f : 20f;
        // Pull back before rotating so the confirmed route remains framed.
        float zoom = Math.max(map.getMinZoomLevel(), overview.zoom - (shot == 2 ? .42f : .85f));
        CameraPosition position = new CameraPosition.Builder(overview)
                .bearing(overview.bearing + bearing).tilt(tilt).zoom(zoom).build();
        map.animateCamera(CameraUpdateFactory.newCameraPosition(position),
                shot == 0 ? 640 : shot == 1 ? 1060 : 580, new GoogleMap.CancelableCallback() {
                    @Override public void onFinish() { orbit(overview, shot + 1); }
                    @Override public void onCancel() { cancel(); }
                });
    }

    private void restore() {
        showFinalRoute();
        clearTrail();
        for (int i = 0; i < anchors.length; i++) {
            if (pulses[i] != null) {
                pulses[i].remove();
                pulses[i] = null;
            }
            if (!anchors[i].equals(markers.get(i).getPosition())) markers.get(i).setPosition(anchors[i]);
        }
    }

    private void clearTrail() {
        if (aura == null) return;
        aura.remove();
        trail.remove();
        spark.remove();
        head.remove();
        aura = trail = spark = null;
        head = null;
    }
}
