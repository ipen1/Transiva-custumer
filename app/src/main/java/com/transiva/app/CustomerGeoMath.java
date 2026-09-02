package com.transiva.app;

/** Shared coordinate validation and lightweight geometry for customer map screens. */
public final class CustomerGeoMath {
    private CustomerGeoMath() {}

    public static boolean valid(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng)
                && lat >= -90d && lat <= 90d && lng >= -180d && lng <= 180d
                && lat != 0d && lng != 0d;
    }

    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double earth = 6371000d;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dp / 2d) * Math.sin(dp / 2d)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2d) * Math.sin(dl / 2d);
        return earth * 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
    }

    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        return distanceMeters(lat1, lng1, lat2, lng2) / 1000d;
    }

    public static float distanceMetersOrFar(double lat1, double lng1, double lat2, double lng2) {
        if (!valid(lat1, lng1) || !valid(lat2, lng2)) return 999999f;
        try {
            float[] out = new float[1];
            android.location.Location.distanceBetween(lat1, lng1, lat2, lng2, out);
            return out[0];
        } catch (Exception ignored) {
            return 999999f;
        }
    }

    public static double bearingOrFallback(double lat1, double lng1, double lat2, double lng2, double fallback) {
        if (!valid(lat1, lng1) || !valid(lat2, lng2)) return fallback;
        return bearing(lat1, lng1, lat2, lng2);
    }

    public static double bearing(double lat1, double lng1, double lat2, double lng2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dl = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return normalizeDegrees(Math.toDegrees(Math.atan2(y, x)));
    }

    public static double normalizeDegrees(double value) {
        value %= 360d;
        if (value < 0d) value += 360d;
        return value;
    }

    public static double smoothBearing(double oldBearing, double newBearing) {
        double delta = ((newBearing - oldBearing + 540d) % 360d) - 180d;
        return normalizeDegrees(oldBearing + delta * 0.35d);
    }
}
