package com.transiva.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkSafetyRulesTest {
    @Test public void credentialsOnlyGoToExactHttpsOrigin() {
        assertTrue(NetworkSafetyRules.maySendCredentials("https://transiva.my.id/server/getBalance.php"));
        assertTrue(NetworkSafetyRules.maySendCredentials("https://TRANSIVA.MY.ID:443/images/a.png"));
        for (String url : new String[]{"http://transiva.my.id/a", "https://transiva.my.id.evil.test/a",
                "https://evil.test/transiva.my.id/", "https://transiva.my.id@evil.test/a",
                "https://evil@transiva.my.id/a", "https://transiva.my.id:8443/a", "file:///a", "bad url"}) {
            assertFalse(url, NetworkSafetyRules.maySendCredentials(url));
        }
    }
    @Test public void authenticationAndRateLimitFailuresNeverAutoRetry() {
        for (int status : new int[]{301,400,401,403,404,409,422,429,501,505})
            assertFalse(Integer.toString(status), NetworkSafetyRules.retryableStatus(status));
        for (int status : new int[]{408,500,502,503,504}) assertTrue(NetworkSafetyRules.retryableStatus(status));
    }
    @Test public void giantAndPanoramicImagesFitBothEdgeAndPixelBudgets() {
        for (int[] size : new int[][]{{12000,9000},{100000,80},{80,100000},{2147483647,2147483647},{1024,1024}}) {
            int sample = NetworkSafetyRules.bitmapSample(size[0],size[1],1024,1048576L);
            long width=((long)size[0]+sample-1)/sample, height=((long)size[1]+sample-1)/sample;
            assertTrue(width<=1024);assertTrue(height<=1024);assertTrue(width*height<=1048576L);
            assertEquals(0, sample & (sample-1));
        }
    }
    @Test public void tinyImagesDoNotLoseResolution() {
        assertEquals(1,NetworkSafetyRules.bitmapSample(128,96,512,262144L));
    }    @Test public void dashboardCacheRejectsOtherAccountsAndExpiredSnapshots() {
        assertTrue(NetworkSafetyRules.mayUseAccountCache("7","7",1000,1050,100));
        assertFalse(NetworkSafetyRules.mayUseAccountCache("8","7",1000,1050,100));
        assertFalse(NetworkSafetyRules.mayUseAccountCache("","",1000,1050,100));
        assertFalse(NetworkSafetyRules.mayUseAccountCache("7","7",1000,999,100));
        assertFalse(NetworkSafetyRules.mayUseAccountCache("7","7",1000,1101,100));
        assertFalse(NetworkSafetyRules.mayUseAccountCache("7","7",0,50,100));
    }

}
