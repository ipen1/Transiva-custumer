package com.transiva.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChatNotificationPreviewTest {
    @Test public void actualVoicePayloadBecomesReadableWithoutLeakingUrl() {
        String raw="[[VOICE]]https://transiva.my.id/server/play_chat_voice.php?file=2026%2F10%2Fdriver_ROOM-test.m4a|2685";
        assertEquals("🎙 Pesan suara • 0:02",CustomerChatPreviewFormatter.notificationText(raw));
        assertFalse(CustomerChatPreviewFormatter.notificationText(raw).contains("https"));
    }
    @Test public void missingAndMalformedDurationStillHideTheMediaPayload() {
        for(String value:new String[]{"[[VOICE]]https://example.test/a.m4a"," [[VOICE]]x|oops ","[[VOICE]]x|-1","[[VOICE]]x|999999999999999999999","[[VOICE]]x|600001"})
            assertEquals("🎙 Pesan suara",CustomerChatPreviewFormatter.notificationText(value));
        assertEquals("🎙 Pesan suara • 1:05",CustomerChatPreviewFormatter.notificationText("[[VOICE]]x|65000"));
    }
    @Test public void photosAndOrdinaryTextKeepTheirCorrectPreview() {
        assertEquals("📷 Mengirim sebuah foto",CustomerChatPreviewFormatter.notificationText("[[IMAGE2]]{\"url\":\"a.webp\"}"));
        assertEquals("📷 Mengirim sebuah foto",CustomerChatPreviewFormatter.notificationText("[[IMAGE]]a.webp"));
        assertEquals("Buka https://transiva.my.id",CustomerChatPreviewFormatter.notificationText("Buka https://transiva.my.id"));
        assertEquals("",CustomerChatPreviewFormatter.notificationText(null));
    }
}
