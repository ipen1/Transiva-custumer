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
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;

import java.util.ArrayList;
import java.util.List;

/** One checkout preview. The permanent route remains owned by the map view. */
final class CheckoutRouteAnimation {
    private final GoogleMap map;
    private final Polyline route;
    private final List<LatLng> points;
    private final List<Marker> markers;
    private final LatLng[] anchors;
    private final float[] distance, stops;
    private final float density;
    private final CameraUpdate overview;
    private Polyline glow, beam;
    private ValueAnimator timeline;
    private boolean active = true;

    CheckoutRouteAnimation(GoogleMap map, Polyline route, List<LatLng> points,
                           List<Marker> markers, List<Integer> legEnds, float density,
                           CameraUpdate overview) {
        this.map = map;
        this.route = route;
        this.points = points;
        this.markers = markers;
        this.density = density;
        this.overview = overview;
        anchors = new LatLng[markers.size()];
        for (int i = 0; i < anchors.length; i++) anchors[i] = markers.get(i).getPosition();
        distance = new float[points.size()];
        float[] step = new float[1];
        for (int i = 1; i < points.size(); i++) {
            LatLng a = points.get(i - 1), b = points.get(i);
            Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, step);
            distance[i] = distance[i - 1] + step[0];
        }
        stops = new float[legEnds.size()];
        for (int i = 0; i < stops.length; i++) stops[i] = distance[legEnds.get(i)];
    }

    void start() {
        if (distance[distance.length - 1] < 1f) {
            cancel();
            return;
        }
        route.setPoints(points.subList(0, 1));
        glow = effectLine(15, 0x7739C5FF, 3);
        beam = effectLine(4, 0xFFE7FBFF, 4);
        map.animateCamera(overview, 420, new GoogleMap.CancelableCallback() {
            @Override public void onFinish() { if (active) reveal(); }
            @Override public void onCancel() { cancel(); }
        });
    }

    private Polyline effectLine(int width, int color, int z) {
        return map.addPolyline(new PolylineOptions().width(width * density).color(color).zIndex(z)
                .jointType(JointType.ROUND).startCap(new RoundCap()).endCap(new RoundCap()));
    }

    private void reveal() {
        float total = distance[distance.length - 1];
        int drawMs = 1500 + 180 * stops.length;
        timeline = ValueAnimator.ofFloat(0, 220 + drawMs + 560);
        timeline.setDuration(220 + drawMs + 560L);
        timeline.setInterpolator(new LinearInterpolator());
        timeline.addUpdateListener(frame -> {
            float ms = (float) frame.getAnimatedValue();
            float progress = Math.min(1f, Math.max(0f, (ms - 220) / drawMs));
            float end = total * progress;
            if (ms >= 220 && ms <= 220 + drawMs) {
                route.setPoints(slice(0, end));
                List<LatLng> tail = slice(Math.max(0, end - total * .12f), end);
                glow.setPoints(tail);
                beam.setPoints(tail);
            } else if (ms > 220 + drawMs && glow != null) {
                route.setPoints(points);
                clearBeam();
            }
            bounce(0, ms, 490, 10);
            for (int i = 0; i < stops.length; i++)
                bounce(i + 1, ms - 220 - drawMs * stops[i] / total, 420, 9);
            bounce(markers.size() - 1, ms - 220 - drawMs, 560, 19);
        });
        timeline.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator ignored) {
                if (active) orbit(map.getCameraPosition(), 0);
            }
        });
        timeline.start();
    }

    private void bounce(int index, float ms, int duration, int height) {
        if (ms <= 0 || ms >= duration) {
            if (!anchors[index].equals(markers.get(index).getPosition()))
                markers.get(index).setPosition(anchors[index]);
            return;
        }
        com.google.android.gms.maps.Projection projection = map.getProjection();
        Point pixel = projection.toScreenLocation(anchors[index]);
        pixel.y -= Math.round((float) Math.sin(Math.PI * ms / duration) * height * density);
        markers.get(index).setPosition(projection.fromScreenLocation(pixel));
    }

    private List<LatLng> slice(float from, float to) {
        List<LatLng> result = new ArrayList<>();
        int start = segment(from), end = segment(to);
        result.add(interpolate(start, from));
        for (int i = start + 1; i <= end; i++) result.add(points.get(i));
        result.add(interpolate(end, to));
        return result;
    }

    private int segment(float meters) {
        int low = 0, high = distance.length - 1;
        while (high - low > 1) {
            int mid = (low + high) / 2;
            if (distance[mid] <= meters) low = mid; else high = mid;
        }
        return low;
    }

    private LatLng interpolate(int index, float meters) {
        LatLng a = points.get(index), b = points.get(index + 1);
        float length = distance[index + 1] - distance[index];
        if (length <= 0) return b;
        float f = (meters - distance[index]) / length;
        double longitude = (b.longitude - a.longitude + 540) % 360 - 180;
        return new LatLng(a.latitude + (b.latitude - a.latitude) * f, a.longitude + longitude * f);
    }

    private void orbit(CameraPosition base, int stage) {
        if (stage == 2) {
            active = false;
            return;
        }
        CameraPosition position = new CameraPosition.Builder(base)
                .zoom(Math.max(map.getMinZoomLevel(), base.zoom - .9f))
                .tilt(stage == 0 ? 23f : 16f)
                .bearing(base.bearing + (stage == 0 ? -12f : 17f))
                .build();
        map.animateCamera(CameraUpdateFactory.newCameraPosition(position), stage == 0 ? 650 : 950,
                new GoogleMap.CancelableCallback() {
                    @Override public void onFinish() { if (active) orbit(base, stage + 1); }
                    @Override public void onCancel() { cancel(); }
                });
    }

    boolean isActive() { return active; }

    void cancel() {
        if (!active) return;
        active = false;
        if (timeline != null) timeline.cancel();
        route.setPoints(points);
        clearBeam();
        for (int i = 0; i < anchors.length; i++) markers.get(i).setPosition(anchors[i]);
    }

    private void clearBeam() {
        if (glow == null) return;
        glow.remove();
        beam.remove();
        glow = null;
        beam = null;
    }
}
