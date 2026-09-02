package com.transiva.app;

import android.content.Context;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Central realtime budget. Keeps critical order/trip work responsive while relaxing
 * non-critical polling when maps/chat are foreground or the device is constrained.
 */
public final class CustomerRealtimeCoordinator {
    public enum Role { IDLE, DASHBOARD, HISTORY, SHOP, FOOD, CHAT, SEARCH, TRIP }
    public enum Priority { CRITICAL, ACTIVE, BACKGROUND }

    private static final AtomicReference<Role> FOREGROUND = new AtomicReference<>(Role.IDLE);
    private CustomerRealtimeCoordinator() {}

    public static void enter(Role role) { if (role != null) FOREGROUND.set(role); }
    public static void leave(Role role) { FOREGROUND.compareAndSet(role, Role.IDLE); }
    public static Role foregroundRole() { return FOREGROUND.get(); }

    public static long interval(Context c, Priority priority, long normalMs) {
        long base = CustomerPerformanceManager.pollingBase(c, normalMs);
        Role role = foregroundRole();
        boolean realtimeHeavy = role == Role.TRIP || role == Role.SEARCH || role == Role.CHAT;
        if (priority == Priority.CRITICAL) return Math.max(900L, base);
        if (priority == Priority.ACTIVE) {
            if (realtimeHeavy) return Math.max(base, Math.round(base * 1.20));
            return base;
        }
        // Background work yields aggressively while a realtime screen is active.
        if (realtimeHeavy) return Math.max(base + 4000L, Math.round(base * 2.20));
        if (CustomerPerformanceManager.isConstrained(c)) return Math.max(base + 2500L, Math.round(base * 1.70));
        return Math.max(base, Math.round(base * 1.25));
    }
}
