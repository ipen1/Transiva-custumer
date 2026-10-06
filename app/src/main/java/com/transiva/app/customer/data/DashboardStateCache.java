package com.transiva.app.customer.data;

import android.content.Context;
import com.transiva.app.SessionManager;
import com.transiva.app.NetworkSafetyRules;
import android.content.SharedPreferences;
import com.transiva.app.customer.domain.DashboardState;
import com.transiva.app.customer.domain.Promo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Small render cache for dashboard reads only. Never stores or replays mutations. */
public final class DashboardStateCache {
    private static final String PREF = "transiva_dashboard_render_v1";
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

    public static void put(Context context, DashboardState s) {
        if (context != null) put(context, s, owner(context));
    }

    public static void put(Context context, DashboardState s, String account) {
        if (context == null || s == null) return;
        try {
            if (account == null || account.isEmpty() || !account.equals(owner(context))) return;
            JSONObject o = new JSONObject();
            o.put("owner", account);
            o.put("saved_at", System.currentTimeMillis());
            o.put("balance", s.balance);
            o.put("activeOrderText", s.activeOrderText);
            if (s.activeOrder != null) o.put("activeOrder", s.activeOrder);
            if (s.loyalty != null) o.put("loyalty", s.loyalty);
            if (s.referral != null) o.put("referral", s.referral);
            if (s.bestOffer != null) o.put("bestOffer", s.bestOffer);
            JSONArray a = new JSONArray();
            for (Promo p : s.promos) {
                JSONObject x = new JSONObject();
                x.put("id", p.id); x.put("title", p.title); x.put("description", p.description);
                x.put("code", p.code); x.put("imageUrl", p.imageUrl);
                x.put("themeStart", p.themeStart); x.put("themeEnd", p.themeEnd); a.put(x);
            }
            o.put("promos", a);
            context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit().putString(KEY, o.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static DashboardState get(Context context) {
        if (context == null) return null;
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String raw = p.getString(KEY, null); if (raw == null || raw.trim().isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(raw);
            String account = owner(context);
            if (!NetworkSafetyRules.mayUseAccountCache(account, o.optString("owner"),
                    o.optLong("saved_at", 0L), System.currentTimeMillis(), MAX_AGE_MS)) return null;
            List<Promo> promos = new ArrayList<>();
            JSONArray a = o.optJSONArray("promos");
            if (a != null) for (int i=0;i<a.length();i++) { JSONObject x=a.optJSONObject(i); if(x!=null) promos.add(new Promo(
                    x.optInt("id"), x.optString("title"), x.optString("description"), x.optString("code"),
                    x.optString("imageUrl"), x.optString("themeStart", "#0759E8"), x.optString("themeEnd", "#18B5FF")));
            }
            return new DashboardState(o.optDouble("balance",0d), o.optString("activeOrderText","Belum ada pesanan aktif"),
                    o.optJSONObject("activeOrder"), o.optJSONObject("loyalty"), o.optJSONObject("referral"),
                    o.optJSONObject("bestOffer"), promos);
        } catch (Exception ignored) { return null; }
    }
}
