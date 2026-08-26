package com.transiva.app;

/** Pure customer-side financial invariants used by checkout and regression tests. */
public final class CustomerFinancialRules {
    private CustomerFinancialRules() {}

    /** Customer voucher never reduces the driver's protected gross fare. */
    public static long protectedDriverFare(long originalDriverFare, long voucherDiscount) {
        return Math.max(0L, originalDriverFare);
    }

    /** Exact TransFood coin rule: keep the admin-defined minimum payable amount after coin. */
    public static double foodCoinDiscount(int coinBalance, int coinValueRupiah, double grossTotal, int minOrderAfterDiscount) {
        if (coinBalance <= 0 || coinValueRupiah <= 0 || grossTotal <= 0) return 0d;
        double spendable = Math.max(0d, grossTotal - Math.max(0, minOrderAfterDiscount));
        return Math.min((double) coinBalance * coinValueRupiah, spendable);
    }

    public static int remainingQuota(int limit, int used) {
        return Math.max(0, Math.max(0, limit) - Math.max(0, used));
    }

    public static int safeCoinReward(int reward) { return Math.max(0, reward); }
}
