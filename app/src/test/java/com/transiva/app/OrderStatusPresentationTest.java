package com.transiva.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OrderStatusPresentationTest {
    @Test public void pendingRide_waitsForDriver() {
        assertEquals("Sedang Mencari Driver", OrderStatusPresentation.label("pending", "transride"));
    }

    @Test public void pendingCar_waitsForCarDriver() {
        assertEquals("Sedang Mencari Driver", OrderStatusPresentation.label("pending", "transcar"));
    }

    @Test public void pendingFood_waitsForMerchant() {
        assertEquals("Menunggu Konfirmasi Penjual", OrderStatusPresentation.label("pending", "transfood"));
    }

    @Test public void completedAliases_areRenderedAsFinished() {
        assertEquals("Pesanan Selesai 🎉", OrderStatusPresentation.label("finish", "transride"));
        assertEquals("Pesanan Selesai 🎉", OrderStatusPresentation.label("finished", "transride"));
        assertEquals("Pesanan Selesai 🎉", OrderStatusPresentation.label("completed", "transride"));
    }

    @Test public void cancelledAliases_areRenderedAsCancelled() {
        assertEquals("Pesanan Dibatalkan", OrderStatusPresentation.label("canceled", "transride"));
        assertEquals("Pesanan Dibatalkan", OrderStatusPresentation.label("cancelled", "transride"));
    }
}
