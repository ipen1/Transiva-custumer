package com.transiva.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;

/** Adaptive customer performance profile using memory, battery saver and thermal pressure. */
public final class CustomerPerformanceManager {
    public static final String MODE_AUTO = "auto";
    public static final String MODE_ECO = "eco";
    public static final String MODE_NORMAL = "normal";
    public static final String MODE_HIGH = "high";

    private static final String PREF = "customer_performance";
    private static final String KEY_MODE = "mode";
    private static final String KEY_LAST_AUTO = "last_auto";
    private static volatile DeviceScore cachedScore;
    private static volatile long cachedScoreAt;
    private static final long SCORE_CACHE_MS = 5000L;
    private CustomerPerformanceManager() {}

    private static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE); }
    public static String selectedMode(Context c) { return prefs(c).getString(KEY_MODE, MODE_AUTO); }
    public static void setSelectedMode(Context c, String mode) {
        if (!MODE_AUTO.equals(mode) && !MODE_ECO.equals(mode) && !MODE_NORMAL.equals(mode) && !MODE_HIGH.equals(mode)) mode = MODE_AUTO;
        prefs(c).edit().putString(KEY_MODE, mode).apply();
    }

    public static String effectiveMode(Context c) {
        String selected = selectedMode(c);
        String desired = MODE_AUTO.equals(selected) ? recommendedMode(c) : selected;
        // Android Battery Saver / low-memory / serious thermal pressure always caps HIGH.
        DeviceScore s = score(c);
        if (s.powerSave || s.lowMemory || s.thermalStatus >= thermalSevere()) {
            if (MODE_HIGH.equals(desired)) return MODE_NORMAL;
            if (MODE_AUTO.equals(selected) && s.score < 45) return MODE_ECO;
        }
        return desired;
    }

    public static String recommendedMode(Context c) {
        DeviceScore s = score(c);
        String mode = s.score < 42 ? MODE_ECO : (s.score >= 76 ? MODE_HIGH : MODE_NORMAL);
        if (s.powerSave || s.lowMemory || s.thermalStatus >= thermalSevere()) mode = MODE_ECO;
        prefs(c).edit().putString(KEY_LAST_AUTO, mode).apply();
        return mode;
    }

    public static DeviceScore score(Context c) {
        long now = SystemClock.elapsedRealtime();
        DeviceScore cached = cachedScore;
        if (cached != null && now - cachedScoreAt < SCORE_CACHE_MS) return cached;
        ActivityManager am = (ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        long total = 0, avail = 0; int memoryClass = 128; boolean low = false;
        if (am != null) {
            try { am.getMemoryInfo(mi); total = mi.totalMem/(1024L*1024L); avail = mi.availMem/(1024L*1024L); low = mi.lowMemory; } catch (Throwable ignored) {}
            try { memoryClass = am.getMemoryClass(); } catch (Throwable ignored) {}
        }
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        boolean saver = false; int thermal = 0;
        try {
            PowerManager pm = (PowerManager)c.getSystemService(Context.POWER_SERVICE);
            saver = pm != null && pm.isPowerSaveMode();
            if (pm != null && Build.VERSION.SDK_INT >= 29) thermal = pm.getCurrentThermalStatus();
        } catch (Throwable ignored) {}
        int battery = -1;
        try {
            BatteryManager bm = (BatteryManager)c.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (Throwable ignored) {}

        int score = 0;
        if (total >= 8000) score += 34; else if (total >= 6000) score += 28; else if (total >= 4000) score += 21; else if (total >= 3000) score += 14; else score += 7;
        if (memoryClass >= 384) score += 22; else if (memoryClass >= 256) score += 18; else if (memoryClass >= 192) score += 12; else score += 6;
        if (cores >= 8) score += 20; else if (cores >= 6) score += 16; else if (cores >= 4) score += 11; else score += 5;
        if (Build.VERSION.SDK_INT >= 31) score += 14; else if (Build.VERSION.SDK_INT >= 28) score += 10; else score += 6;
        if (total > 0 && avail * 100 / total >= 35) score += 10; else if (total > 0 && avail * 100 / total >= 20) score += 6;
        if (low) score -= 22;
        if (saver) score -= 18;
        if (battery >= 0 && battery <= 15) score -= 8;
        if (thermal >= thermalSevere()) score -= 22;
        score = Math.max(0, Math.min(100, score));
        DeviceScore result = new DeviceScore(total, avail, memoryClass, cores, Build.VERSION.SDK_INT, low, saver, thermal, battery, score);
        cachedScore = result;
        cachedScoreAt = now;
        return result;
    }

    private static int thermalSevere() { return Build.VERSION.SDK_INT >= 29 ? PowerManager.THERMAL_STATUS_SEVERE : 3; }
    public static boolean isEco(Context c) { return MODE_ECO.equals(effectiveMode(c)); }
    public static boolean isHigh(Context c) { return MODE_HIGH.equals(effectiveMode(c)); }
    public static boolean isConstrained(Context c) { DeviceScore s = score(c); return isEco(c) || s.lowMemory || s.powerSave || s.thermalStatus >= thermalSevere(); }

    static long pollingBase(Context c, long normalMs) {
        if (isEco(c)) return Math.max(normalMs + 1800L, Math.round(normalMs * 1.55));
        if (isHigh(c)) return Math.max(1000L, Math.round(normalMs * 0.88));
        return normalMs;
    }
    public static long polling(Context c, long normalMs) { return CustomerRealtimeCoordinator.interval(c, CustomerRealtimeCoordinator.Priority.ACTIVE, normalMs); }
    public static long pollingCritical(Context c, long normalMs) { return CustomerRealtimeCoordinator.interval(c, CustomerRealtimeCoordinator.Priority.CRITICAL, normalMs); }
    public static long pollingBackground(Context c, long normalMs) { return CustomerRealtimeCoordinator.interval(c, CustomerRealtimeCoordinator.Priority.BACKGROUND, normalMs); }
    public static long animationFrame(Context c) { return isEco(c) ? 66L : (isHigh(c) ? 22L : 33L); }
    public static long promoInterval(Context c, long normalMs) { return isEco(c) ? Math.max(normalMs, 8000L) : normalMs; }

    public static String label(String mode) { if (MODE_ECO.equals(mode)) return "Hemat Daya"; if (MODE_HIGH.equals(mode)) return "Performa Tinggi"; if (MODE_NORMAL.equals(mode)) return "Normal"; return "Otomatis"; }
    public static int rank(String mode) { if (MODE_ECO.equals(mode)) return 0; if (MODE_NORMAL.equals(mode)) return 1; if (MODE_HIGH.equals(mode)) return 2; return 1; }
    public static boolean isAboveRecommendation(Context c, String requested) { return !MODE_AUTO.equals(requested) && rank(requested) > rank(recommendedMode(c)); }
    public static String deviceSummary(Context c) {
        DeviceScore s = score(c);
        String state = s.powerSave ? " • Battery Saver" : (s.thermalStatus >= thermalSevere() ? " • Suhu tinggi" : "");
        return s.ramMb + " MB RAM • " + s.cpuCores + " core • Skor " + s.score + "/100 • Android " + Build.VERSION.RELEASE + state;
    }

    public static final class DeviceScore {
        public final long ramMb, availableRamMb; public final int memoryClassMb, cpuCores, sdk; public final boolean lowMemory, powerSave; public final int thermalStatus, batteryPercent, score;
        DeviceScore(long ramMb,long availableRamMb,int memoryClassMb,int cpuCores,int sdk,boolean lowMemory,boolean powerSave,int thermalStatus,int batteryPercent,int score) {
            this.ramMb=ramMb; this.availableRamMb=availableRamMb; this.memoryClassMb=memoryClassMb; this.cpuCores=cpuCores; this.sdk=sdk; this.lowMemory=lowMemory; this.powerSave=powerSave; this.thermalStatus=thermalStatus; this.batteryPercent=batteryPercent; this.score=score;
        }
    }
}
