package com.transiva.app.customer.presentation;

import com.transiva.app.customer.domain.DashboardState;

public interface CustomerDashboardContract {
    interface View {
        void showLoading(boolean visible);
        void showDashboard(DashboardState state);
        void showError(String message);
    }
}
