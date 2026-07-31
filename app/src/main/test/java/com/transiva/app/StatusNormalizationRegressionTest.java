package com.transiva.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StatusNormalizationRegressionTest {
    @Test public void serverFormattingVariants_normalizeConsistently() {
        assertEquals("driver_accepted", CustomerMessageStatus.normalize(" Driver-Accepted "));
        assertEquals("driver_accepted", CustomerMessageStatus.normalize("driver accepted"));
        assertEquals("arrived_pickup", CustomerMessageStatus.normalize("ARRIVED_PICKUP"));
    }

    @Test public void nullStatus_isSafe() {
        assertEquals("", CustomerMessageStatus.normalize(null));
    }
}
