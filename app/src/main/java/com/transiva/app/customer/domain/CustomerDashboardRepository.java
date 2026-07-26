package com.transiva.app.customer.domain;

public interface CustomerDashboardRepository {
    DashboardState load(String username, int userId) throws Exception;
}
