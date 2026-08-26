package com.transiva.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class CustomerFinancialRulesTest {
    @Test public void voucherDiscountNeverReducesProtectedDriverFare() {
        assertEquals(10000L, CustomerFinancialRules.protectedDriverFare(10000, 2000));
        assertEquals(10000L, CustomerFinancialRules.protectedDriverFare(10000, 10000));
    }

    @Test public void foodCoinDiscountCannotPushTotalBelowAdminMinimum() {
        assertEquals(9000d, CustomerFinancialRules.foodCoinDiscount(20000, 1, 10000d, 1000), 0.001d);
        assertEquals(500d, CustomerFinancialRules.foodCoinDiscount(500, 1, 10000d, 1000), 0.001d);
    }

    @Test public void hematQuotaNeverBecomesNegative() {
        assertEquals(5, CustomerFinancialRules.remainingQuota(10, 5));
        assertEquals(0, CustomerFinancialRules.remainingQuota(10, 12));
    }

    @Test public void coinRewardNeverDisplaysNegativeValue() {
        assertEquals(0, CustomerFinancialRules.safeCoinReward(-100));
        assertEquals(125, CustomerFinancialRules.safeCoinReward(125));
    }
}
