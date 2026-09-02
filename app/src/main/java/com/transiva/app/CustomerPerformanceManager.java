package com.transiva.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/** Lightweight customer performance profile with automatic hardware recommendation. */
public final class CustomerPerformanceManager {
    public static final String MODE_AUTO = "auto";
    public static final String MODE_ECO = "eco";
    public static final String MODE_NORMAL = "normal";
    public static final String MODE_HIGH = "high";

    private static final String PREF = "customer_performance";
    private static final String KEY_MODE = "mode";
    private static final String KEY_LAST_AUTO = "last_auto";

    private CustomerPerformanceManager() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String selectedMode(Context c) {
        return prefs(c).getString(KEY_MODE, MODE_AUTO);
    }

    public static void setSelectedMode(Context c, String mode) {
        if (!MODE_AUTO.equals(mode) && !MODE_ECO.equals(mode) && !MODE_NORMAL.equals(mode) && !MODE_HIGH.equals(mode)) mode = MODE_AUTO;
        prefs(c).edit().putString(KEY_MODE, mode).apply();
    }

    public static String effectiveMode(Context c) {
        String selected = selectedMode(c);
        return MODE_AUTO.equals(selected) ? recommendedMode(c) : selected;
    }

    public static String recommendedMode(Context c) {
        DeviceScore s = score(c);
        String mode;
        if (s.memoryClassMb <= 192 || s.ramMb < 3000 || s.cpuCores <= 4) mode = MODE_ECO;
        else if (s.ramMb >= 7000 && s.cpuCores >= 8 && Build.VERSION.SDK_INT >= 30) mode = MODE_HIGH;
        else mode = MODE_NORMAL;
        prefs(c).edit().putString(KEY_LAST_AUTO, mode).apply();
        return mode;
    }

    public static DeviceScore score(Context c) {
        ActivityManager am = (ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        long total = 0;
        int memoryClass = 128;
        if (am != null) {
            try { am.getMemoryInfo(mi); total = mi.totalMem / (1024L * 1024L); } catch (Throwable ignored) {}
            try { memoryClass = am.getMemoryClass(); } catch (Throwable ignored) {}
        }
        return new DeviceScore(total, memoryClass, Runtime.getRuntime().availableProcessors(), Build.VERSION.SDK_INT);
    }

    public static boolean isEco(Context c) { return MODE_ECO.equals(effectiveMode(c)); }
    public static boolean isHigh(Context c) { return MODE_HIGH.equals(effectiveMode(c)); }

    /** Network refreshes remain responsive but are relaxed in battery-saving mode. */
    public static long polling(Context c, long normalMs) {
        if (isEco(c)) return Math.max(normalMs + 1500L, Math.round(normalMs * 1.45));
        if (isHigh(c)) return Math.max(1000L, Math.round(normalMs * 0.85));
        return normalMs;
    }

    public static long animationFrame(Context c) {
        return isEco(c) ? 66L : (isHigh(c) ? 24L : 33L);
    }

    public static long promoInterval(Context c, long normalMs) {
        return isEco(c) ? Math.max(normalMs, 7000L) : normalMs;
    }

    public static String label(String mode) {
        if (MODE_ECO.equals(mode)) return "Hemat Daya";
        if (MODE_HIGH.equals(mode)) return "Performa Tinggi";
        if (MODE_NORMAL.equals(mode)) return "Normal";
        return "Otomatis";
    }

    public static int rank(String mode) {
        if (MODE_ECO.equals(mode)) return 0;
        if (MODE_NORMAL.equals(mode)) return 1;
        if (MODE_HIGH.equals(mode)) return 2;
        return rank(MODE_NORMAL);
    }

    public static boolean isAboveRecommendation(Context c, String requested) {
        return !MODE_AUTO.equals(requested) && rank(requested) > rank(recommendedMode(c));
    }

    public static String deviceSummary(Context c) {
        DeviceScore s = score(c);
        return s.ramMb + " MB RAM • " + s.cpuCores + " core • Android " + Build.VERSION.RELEASE;
    }

    public static final class DeviceScore {
        public final long ramMb;
        public final int memoryClassMb;
        public final int cpuCores;
        public final int sdk;
        DeviceScore(long ramMb, int memoryClassMb, int cpuCores, int sdk) {
            this.ramMb = ramMb; this.memoryClassMb = memoryClassMb; this.cpuCores = cpuCores; this.sdk = sdk;
        }
    }
}
