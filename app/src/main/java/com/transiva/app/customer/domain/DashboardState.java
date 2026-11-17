package com.transiva.app.customer.domain;

import org.json.JSONObject;
import java.util.Collections;
import java.util.List;

public final class DashboardState {
    public enum Section { BALANCE, ORDER, PROMOS, LOYALTY, BEST_OFFER, REFERRAL, ORDER_DETAILS }

    public final JSONObject recoveryOrder;
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
        this(balance, activeOrderText, activeOrder, loyalty, referral, bestOffer, promos, null);
    }

    public DashboardState(double balance, String activeOrderText, JSONObject activeOrder,
                          JSONObject loyalty, JSONObject referral, JSONObject bestOffer,
                          List<Promo> promos, JSONObject recoveryOrder) {
        this.recoveryOrder = recoveryOrder;
        this.balance = balance;
        this.activeOrderText = activeOrderText == null ? "Belum ada pesanan aktif" : activeOrderText;
        this.activeOrder = activeOrder;
        this.loyalty = loyalty;
        this.referral = referral;
        this.bestOffer = bestOffer;
        this.promos = promos == null ? Collections.emptyList() : promos;
    }
}
