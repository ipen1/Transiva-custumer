package com.transiva.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Coordinates, cache identity and stable distance order shared by transport search. */
final class TransportSearchRanking {
    static final class Result {
        final String id, name, address;
        final double latitude, longitude;
        final Integer meters;
        Result(String id, String name, String address, double latitude, double longitude, Integer meters) {
            this.id = id; this.name = name == null ? "" : name;
            this.address = address == null ? "" : address;
            this.latitude = latitude; this.longitude = longitude; this.meters = meters;
        }
        boolean hasCoordinates() { return valid(latitude, longitude); }
    }

    private TransportSearchRanking() { }

    static boolean valid(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng)
                && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    static int distance(double lat1, double lng1, double lat2, double lng2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dlat = p2 - p1, dlng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dlat / 2) * Math.sin(dlat / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dlng / 2) * Math.sin(dlng / 2);
        return (int)Math.round(6371000d * 2 * Math.atan2(Math.sqrt(Math.max(0, a)), Math.sqrt(Math.max(0, 1 - a))));
    }

    static String cacheKey(String query, double lat, double lng) {
        return String.format(Locale.US, "%s|%.4f|%.4f",
                query == null ? "" : query.trim().toLowerCase(Locale.ROOT), lat, lng);
    }

    static List<Result> merge(List<Result> predictions, List<Result> places, int maxMeters) {
        LinkedHashMap<String, Result> byId = new LinkedHashMap<>();
        for (List<Result> source : java.util.Arrays.asList(predictions, places)) {
            for (Result row : source) {
                if (row == null || row.id == null || row.id.isEmpty() || row.name.trim().isEmpty()) continue;
                Result old = byId.get(row.id);
                if (old == null || row.hasCoordinates() || (!old.hasCoordinates() && row.meters != null)) byId.put(row.id, row);
            }
        }
        List<Result> result = new ArrayList<>();
        for (Result row : byId.values()) {
            if (maxMeters > 0 && row.meters != null && row.meters > maxMeters) continue;
            result.add(row);
        }
        Collections.sort(result, (a, b) -> {
            int da = a.meters == null ? Integer.MAX_VALUE : a.meters;
            int db = b.meters == null ? Integer.MAX_VALUE : b.meters;
            if (da != db) return Integer.compare(da, db);
            int names = a.name.compareToIgnoreCase(b.name);
            return names != 0 ? names : a.id.compareTo(b.id);
        });
        return result;
    }

    static String distanceLabel(Integer meters) {
        if (meters == null || meters < 0) return "—";
        if (meters < 1000) return meters + " m";
        return String.format(new Locale("id", "ID"), "%.1f km", meters / 1000d);
    }
}
