package com.transiva.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class CustomerOrderLifecycleRegressionTest {
    @Test public void radarKeepsSearchingOnlyBeforeDriverAccepted() {
        assertTrue(CustomerOrderState.isSearching("pending"));
        assertTrue(CustomerOrderState.isSearching("merchant_accepted"));
        assertFalse(CustomerOrderState.isSearching("driver_accepted"));
        assertTrue(CustomerOrderState.hasDriver("driver_accepted"));
    }

    @Test public void stalePollingResponseCannotMoveTripBackwards() {
        assertEquals("on_delivery", CustomerOrderState.laterOf("on_delivery", "arrived_pickup"));
        assertEquals("arrived_delivery", CustomerOrderState.laterOf("arrived_delivery", "driver_accepted"));
    }

    @Test public void terminalStateWinsAndCannotBeReopenedByLateResponse() {
        assertEquals("canceled", CustomerOrderState.laterOf("canceled", "driver_accepted"));
        assertEquals("finished", CustomerOrderState.laterOf("finished", "on_delivery"));
        assertTrue(CustomerOrderState.isEnded("cancelled"));
    }

    @Test public void activeOrderRecoveryRoutingContractIsStable() {
        assertTrue(CustomerOrderState.isSearching("pending"));
        assertTrue(CustomerOrderState.isTrip("accepted"));
        assertTrue(CustomerOrderState.isTrip("arrived_pickup"));
        assertFalse(CustomerOrderState.isTrip("finished"));
    }
}
