package com.transiva.app.customer.domain;

import org.json.JSONObject;
import java.util.Collections;
import java.util.List;

public final class DashboardState {
    public final double balance;
    public final String activeOrderText;
    public final JSONObject activeOrder;
    public final JSONObject loyalty;
    public final JSONObject referral;
    public final JSONObject bestOffer;
    public final List<Promo> promos;

    public DashboardState(double balance, String activeOrderText, JSONObject activeOrder,
                          JSONObject loyalty, JSONObject referral, JSONObject bestOffer,
                          List<Promo> promos) {
        this.balance = balance;
        this.activeOrderText = activeOrderText == null ? "Belum ada pesanan aktif" : activeOrderText;
        this.activeOrder = activeOrder;
        this.loyalty = loyalty;
        this.referral = referral;
        this.bestOffer = bestOffer;
        this.promos = promos == null ? Collections.emptyList() : promos;
    }
}
