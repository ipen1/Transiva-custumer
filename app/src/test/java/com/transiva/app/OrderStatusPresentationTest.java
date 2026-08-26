package com.transiva.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OrderStatusPresentationTest {
    @Test public void pendingRide_waitsForDriver() {
        assertEquals("Menunggu Driver", OrderStatusPresentation.label("pending", "transride"));
    }

    @Test public void pendingCar_waitsForCarDriver() {
        assertEquals("Menunggu Driver Mobil", OrderStatusPresentation.label("pending", "transcar"));
    }

    @Test public void pendingFood_waitsForMerchant() {
        assertEquals("Menunggu Merchant", OrderStatusPresentation.label("pending", "transfood"));
    }

    @Test public void completedAliases_areRenderedAsFinished() {
        assertEquals("Selesai", OrderStatusPresentation.label("finish", "transride"));
        assertEquals("Selesai", OrderStatusPresentation.label("finished", "transride"));
        assertEquals("Selesai", OrderStatusPresentation.label("completed", "transride"));
    }

    @Test public void cancelledAliases_areRenderedAsCancelled() {
        assertEquals("Dibatalkan", OrderStatusPresentation.label("canceled", "transride"));
        assertEquals("Dibatalkan", OrderStatusPresentation.label("cancelled", "transride"));
    }
}
