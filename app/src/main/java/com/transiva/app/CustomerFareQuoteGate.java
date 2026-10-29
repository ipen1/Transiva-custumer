package com.transiva.app;

/** Monotonic ownership of a fare request, independent of Android views or network libraries. */
final class CustomerFareQuoteGate {
    private long generation;
    private long accepted = -1;
    private String acceptedKey;
    private String pendingKey;

    synchronized long begin() { return begin(null); }
    synchronized long begin(String key) { invalidate(); pendingKey=key; return generation; }
    synchronized void invalidate() { generation++; accepted = -1; acceptedKey = null; pendingKey = null; }
    synchronized boolean isCurrent(long ticket) { return ticket == generation; }
    synchronized boolean accept(long ticket, String key) {
        if (!isCurrent(ticket) || key == null) return false;
        accepted = ticket; acceptedKey = key; pendingKey = null; return true;
    }
    synchronized boolean reject(long ticket) {
        if (!isCurrent(ticket)) return false;
        invalidate(); return true;
    }
    synchronized boolean isPending(String key) { return key!=null && key.equals(pendingKey); }
    synchronized boolean hasQuote(String key) {
        return accepted == generation && key != null && key.equals(acceptedKey);
    }
    static String key(double a, double b, double x, double y, String... extra) {
        StringBuilder out = new StringBuilder();
        for (double v : new double[]{a,b,x,y}) out.append(Double.doubleToLongBits(v)).append(':');
        for (String value : extra) { String s=value==null?"":value; out.append(s.length()).append(':').append(s).append('|'); }
        return out.toString();
    }
}
