package com.transiva.app.customer.domain;

import java.util.Collections;
import java.util.List;

public final class DashboardState {
    public final double balance;
    public final String activeOrderText;
    public final List<Promo> promos;

    public DashboardState(double balance, String activeOrderText, List<Promo> promos) {
        this.balance = balance;
        this.activeOrderText = activeOrderText == null ? "Belum ada pesanan aktif" : activeOrderText;
        this.promos = promos == null ? Collections.emptyList() : promos;
    }
}
