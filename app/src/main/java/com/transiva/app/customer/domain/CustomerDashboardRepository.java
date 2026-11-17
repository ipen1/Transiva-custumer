package com.transiva.app.customer.domain;

import org.json.JSONObject;

public interface CustomerDashboardRepository {
    DashboardState load(DashboardState.Section section, int userId,
                        JSONObject order, int budgetMs) throws Exception;
}
