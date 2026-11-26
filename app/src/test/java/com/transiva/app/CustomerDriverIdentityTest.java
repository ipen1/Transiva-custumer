package com.transiva.app;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CustomerDriverIdentityTest {
    @Test public void relativePhotoUsesDriverProfileDirectory() throws Exception {
        JSONObject driver = new JSONObject().put("driver_photo", "uploads/drivers/portrait.jpg")
                .put("profile_photo", "/server/uploads/drivers/account.jpg");
        assertEquals("https://transiva.my.id/server/uploads/drivers/portrait.jpg",
                CustomerDriverIdentity.portrait(driver, new JSONObject(), new JSONObject()));
    }

    @Test public void vehiclePhotoDoesNotReplacePortrait() throws Exception {
        JSONObject driver = new JSONObject().put("driver_photo", "uploads/drivers/vehicle.jpg")
                .put("vehicle_photo", "/server/uploads/drivers/vehicle.jpg")
                .put("profile_photo", "https://transiva.my.id/server/uploads/drivers/account.jpg");
        assertEquals("https://transiva.my.id/server/uploads/drivers/account.jpg",
                CustomerDriverIdentity.portrait(driver, new JSONObject(), new JSONObject()));
    }

    @Test public void placeholderAndMissingPortraitLeaveAvatarEmpty() throws Exception {
        JSONObject driver = new JSONObject().put("driver_photo", "assets/default-driver.png")
                .put("profile_photo", JSONObject.NULL);
        assertEquals("", CustomerDriverIdentity.portrait(driver, new JSONObject(), new JSONObject()));
    }

    @Test public void modelAndVehicleCategoryHaveSeparateFallbacks() throws Exception {
        JSONObject driver = new JSONObject().put("vehicle_model", "Honda Vario 150");
        JSONObject empty = new JSONObject();
        assertEquals("Honda Vario 150", CustomerDriverIdentity.vehicleLabel(driver, empty, empty, false));
        assertEquals("Mobil", CustomerDriverIdentity.vehicleLabel(empty, empty, empty, true));
    }
}
