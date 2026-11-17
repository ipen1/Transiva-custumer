package com.transiva.app.customer.presentation;

import com.transiva.app.customer.domain.DashboardState;
import com.transiva.app.customer.domain.DashboardState.Section;

public interface CustomerDashboardContract {
    interface View {
        void showLoading(boolean visible);
        void showSection(Section section, DashboardState state, boolean live);
        void showError(Section section, String message);
        void finishRefresh(boolean success);
    }
}
