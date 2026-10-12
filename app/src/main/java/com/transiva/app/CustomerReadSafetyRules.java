package com.transiva.app;
/** Pure guards for privacy and late results, shared by cache/request regression tests. */
public final class CustomerReadSafetyRules {
    private CustomerReadSafetyRules() { }
    public static boolean sameSession(String expectedId, String expectedToken, String currentId, String currentToken, boolean loggedIn) {
        return loggedIn && expectedId != null && !expectedId.isEmpty() && expectedId.equals(currentId)
                && expectedToken != null && expectedToken.equals(currentToken);
    }
    public static boolean fresh(String expectedOwner, String storedOwner, long savedAt, long now, long maxAge) {
        return expectedOwner != null && !expectedOwner.isEmpty() && expectedOwner.equals(storedOwner)
                && savedAt > 0 && now >= savedAt && now - savedAt <= maxAge;
    }
}
