package com.transiva.app;

import org.junit.Test;

import static org.junit.Assert.*;

public class CustomerMessageStatusTest {
    @Test public void normalize_acceptsServerVariants() {
        assertEquals("driver_accepted", CustomerMessageStatus.normalize(" Driver-Accepted "));
        assertEquals("arrived_pickup", CustomerMessageStatus.normalize("arrived pickup"));
    }

    @Test public void chat_isNotAvailableWhileOrderIsPending() {
        assertFalse(CustomerMessageStatus.canSend("pending"));
        assertEquals("Chat tersedia setelah order diterima", CustomerMessageStatus.availabilityLabel("pending", false));
    }

    @Test public void chat_becomesAvailableAfterDriverAccepted() {
        assertTrue(CustomerMessageStatus.canSend("driver_accepted"));
        assertEquals("Driver Menerima Pesananmu", CustomerMessageStatus.orderLabel("driver_accepted", "transride"));
    }

    @Test public void completedAndCancelledOrdersAreReadOnly() {
        assertTrue(CustomerMessageStatus.isEnded("completed"));
        assertTrue(CustomerMessageStatus.isEnded("cancelled"));
        assertFalse(CustomerMessageStatus.canSend("completed"));
    }

    @Test public void foodPending_waitsForMerchantNotDriver() {
        assertEquals("Menunggu Konfirmasi Merchant", CustomerMessageStatus.orderLabel("pending", "transfood"));
        assertEquals("Mencari Driver Terdekat", CustomerMessageStatus.orderLabel("pending", "transride"));
    }
}
