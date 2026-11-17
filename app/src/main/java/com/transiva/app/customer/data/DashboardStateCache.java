package com.transiva.app.customer.data;

import android.content.Context;
import com.transiva.app.SessionManager;
import com.transiva.app.NetworkSafetyRules;
import android.content.SharedPreferences;
import com.transiva.app.customer.domain.DashboardState;
import com.transiva.app.customer.domain.DashboardState.Section;
import org.json.JSONArray;
import org.json.JSONObject;

/** Small render cache for dashboard reads only. Never stores or replays mutations. */
public final class DashboardStateCache {
    private static final String PREF = "transiva_dashboard_render_poin_v2";
    private static final String KEY = "state";
    private static final long MAX_AGE_MS = 5L * 60L * 1000L;
    private static String owner(Context context) {
        SessionManager session = new SessionManager(context);
        if (!session.isLoggedIn()) return "";
        String id = session.getId();
        if (id == null || id.trim().isEmpty()) id = session.getUserId();
        return id == null ? "" : id.trim();
    }
    private DashboardStateCache() {}

    private static String orderId(JSONObject order) {
        if (order == null) return "";
        String id = order.optString("order_id", "").trim();
        if (id.isEmpty()) id = order.optString("id_order", "").trim();
        return id.isEmpty() ? order.optString("id", "").trim() : id;
    }

    /** Keep confirmed cancellations out of renders from earlier reads or a restored cache. */
    public static synchronized void rememberCancellation(Context context, String account, JSONObject order) {
        String id = orderId(order);
        if (id.isEmpty() || account.isEmpty() || !account.equals(owner(context))) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String key = "canceled_" + account;
        JSONArray previous = canceledOrders(prefs, key);
        JSONArray recent = new JSONArray();
        for (int i = Math.max(0, previous.length() - 19); i < previous.length(); i++) {
            if (!id.equals(previous.optString(i))) recent.put(previous.optString(i));
        }
        recent.put(id);
        prefs.edit().putString(key, recent.toString()).apply();
    }

    public static boolean wasCanceled(Context context, String account, JSONObject order) {
        String id = orderId(order);
        if (id.isEmpty() || account.isEmpty()) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        JSONArray recent = canceledOrders(prefs, "canceled_" + account);
        for (int i = 0; i < recent.length(); i++) {
            if (id.equals(recent.optString(i))) return true;
        }
        return false;
    }

    private static JSONArray canceledOrders(SharedPreferences prefs, String key) {
        try { return new JSONArray(prefs.getString(key, "[]")); }
        catch (org.json.JSONException invalid) { return new JSONArray(); }
    }

    public static void put(Context context, Section section, DashboardState state, String account) {
        if (!stores(section) || account.isEmpty() || !account.equals(owner(context))) return;
        try {
            JSONObject entry = new JSONObject();
            entry.put("owner", account);
            entry.put("saved_at", System.currentTimeMillis());
            switch (section) {
                case BALANCE: entry.put("balance", state.balance); break;
                case ORDER:
                    entry.put("activeOrderText", state.activeOrderText);
                    entry.put("activeOrder", state.activeOrder);
                    break;
                case LOYALTY: entry.put("loyalty", state.loyalty); break;
                case REFERRAL: entry.put("referral", state.referral); break;
                case BEST_OFFER: entry.put("bestOffer", state.bestOffer); break;
                default: return;
            }
            context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit().putString(sectionKey(section), entry.toString()).apply();
        } catch (org.json.JSONException ignored) { }
    }

    public static DashboardState get(Context context, Section section) {
        if (!stores(section)) return null;
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String raw = prefs.getString(sectionKey(section), null);
        // Existing installs can still render their most recent pre-upgrade snapshot.
        if (raw == null) raw = prefs.getString(KEY, null);
        if (raw == null) return null;
        try {
            JSONObject entry = new JSONObject(raw);
            if (!NetworkSafetyRules.mayUseAccountCache(owner(context), entry.optString("owner"),
                    entry.optLong("saved_at"), System.currentTimeMillis(), MAX_AGE_MS)) return null;
            if (section == Section.BALANCE && !entry.has("balance")) return null;
            if (section == Section.LOYALTY && !entry.has("loyalty")) return null;
            if (section == Section.REFERRAL && !entry.has("referral")) return null;
            if (section == Section.BEST_OFFER && !entry.has("bestOffer")) return null;
            return new DashboardState(entry.optDouble("balance", 0), entry.optString("activeOrderText"),
                    entry.optJSONObject("activeOrder"), entry.optJSONObject("loyalty"),
                    entry.optJSONObject("referral"), entry.optJSONObject("bestOffer"), null);
        } catch (org.json.JSONException invalid) {
            return null;
        }
    }

    private static boolean stores(Section section) {
        return section != Section.PROMOS && section != Section.ORDER_DETAILS;
    }

    private static String sectionKey(Section section) {
        return KEY + "_" + section.name();
    }
}
