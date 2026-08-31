package com.transiva.app;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Privacy-safe order lifecycle breadcrumbs for Crashlytics. */
public final class CustomerOrderBreadcrumbs {
    private static final Map<String, String> LAST = new ConcurrentHashMap<>();
    private CustomerOrderBreadcrumbs() { }

    public static void state(String orderId, String status, String source) {
        String id = safe(orderId); String s = safe(status).toLowerCase();
        if (s.isEmpty()) return;
        String key = Integer.toHexString(id.hashCode());
        String before = LAST.put(key, s);
        if (s.equals(before)) return;
        TransivaCrashReporter.orderState(key, s, safe(source));
        if (LAST.size() > 64) LAST.clear();
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
