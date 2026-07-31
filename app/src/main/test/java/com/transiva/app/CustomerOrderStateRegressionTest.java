package com.transiva.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CustomerOrderStateRegressionTest {
    @Test public void pendingOrder_mustNotOpenChatPrematurely() {
        assertFalse(CustomerMessageStatus.canSend("pending"));
    }

    @Test public void driverAcceptedOrder_opensChat() {
        assertTrue(CustomerMessageStatus.canSend("driver_accepted"));
    }

    @Test public void activeTripStatuses_keepChatAvailable() {
        assertTrue(CustomerMessageStatus.canSend("taken"));
        assertTrue(CustomerMessageStatus.canSend("arrived_pickup"));
        assertTrue(CustomerMessageStatus.canSend("on_trip"));
        assertTrue(CustomerMessageStatus.canSend("on_delivery"));
        assertTrue(CustomerMessageStatus.canSend("arrived_delivery"));
    }

    @Test public void terminalStatuses_areAlwaysReadOnly() {
        String[] statuses = {"finished", "finish", "completed", "canceled", "cancelled", "merchant_rejected"};
        for (String status : statuses) {
            assertTrue("Expected ended status: " + status, CustomerMessageStatus.isEnded(status));
            assertFalse("Expected chat disabled: " + status, CustomerMessageStatus.canSend(status));
        }
    }
}
