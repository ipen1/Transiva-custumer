package com.transiva.app;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class CallPeerIdentityTest {
    @Test public void emptyAndSerializedNullNeverReplaceIdentity() {
        assertEquals("Citra",CallPeerIdentity.first(null," ","null"," Citra "));
        assertEquals("",CallPeerIdentity.first("NULL",""));
    }
    @Test public void photoUsesActualPeerFieldsAndSupportsLegacyDriverResponse() throws Exception {
        assertEquals("",CallPeerIdentity.photo(null));
        assertEquals("uploads/driver.webp",CallPeerIdentity.photo(new JSONObject()
                .put("profile_photo",JSONObject.NULL).put("driver_photo","uploads/driver.webp")));
        assertEquals("profile.webp",CallPeerIdentity.photo(new JSONObject()
                .put("profile_photo","profile.webp").put("driver_photo","driver.webp")));
    }
    @Test public void driverPhotoUsesTheServerUploadPath() {
        String url = "https://transiva.my.id/server/uploads/drivers/avatar.webp";
        assertEquals(url, CallPeerIdentity.photoUrl("uploads/drivers/avatar.webp"));
        assertEquals(url, CallPeerIdentity.photoUrl("./server/uploads/drivers/avatar.webp"));
        assertEquals(url, CallPeerIdentity.photoUrl("/uploads/drivers/avatar.webp"));
        assertEquals(url, CallPeerIdentity.photoUrl(url));
        assertEquals("", CallPeerIdentity.photoUrl("null"));
    }
    @Test public void initialsSupportWhitespaceSingleNameAndUnicode() {
        assertEquals("CA",CallPeerIdentity.initials("  Citra   Ayu  "));
        assertEquals("A",CallPeerIdentity.initials("angeline"));
        assertEquals("T",CallPeerIdentity.initials(null));
        assertEquals("😀A",CallPeerIdentity.initials("😀 Ayu"));
    }
}
