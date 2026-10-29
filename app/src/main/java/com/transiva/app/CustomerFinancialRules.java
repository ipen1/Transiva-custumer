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

    /** Mirrors server coin_quote_redeem, including integer Coin units and admin caps. */
    public static double foodCoinDiscount(int balance,int value,double total,int floor,int maxPercent,int minimumCoins) {
        if(balance<=0||value<=0||total<=0)return 0d;
        double maxValue=Math.min(Math.max(0,total-Math.max(0,floor)),Math.floor(total*Math.max(1,Math.min(100,maxPercent))/100d));
        int coins=(int)Math.floor(Math.min(maxValue,(double)balance*value)/value);
        return coins<Math.max(1,minimumCoins)?0d:(double)coins*value;
    }

    public static int remainingQuota(int limit, int used) {
        return Math.max(0, Math.max(0, limit) - Math.max(0, used));
    }

    /** A successful zero-pay quote is valid only against a real, positive gross fare. */
    public static boolean validRideQuote(int original, int payable) { return original > 0 && payable >= 0 && payable <= original; }

    public static int safeCoinReward(int reward) { return Math.max(0, reward); }
}
