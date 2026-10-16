package com.transiva.app;

import org.json.JSONObject;

import java.util.Locale;

public final class CustomerChatPreviewFormatter {

    private static final String IMAGE_PREFIX =
            "[[IMAGE]]";

    private static final String IMAGE_V2_PREFIX =
            "[[IMAGE2]]";

    private static final String VOICE_PREFIX = "[[VOICE]]";

    private CustomerChatPreviewFormatter() {
    }

    public static boolean isImageMessage(
            String message
    ) {
        String value =
                message == null
                        ? ""
                        : message.trim();

        return value.startsWith(IMAGE_PREFIX)
                || value.startsWith(IMAGE_V2_PREFIX);
    }

    /** Display-only preview. Chat storage keeps the original media payload for playback. */
    public static String notificationText(String raw) {
        String value=raw==null?"":raw.trim();
        if(value.startsWith(VOICE_PREFIX)) {
            int split=value.lastIndexOf('|');
            if(split>=VOICE_PREFIX.length())try {
                long millis=Long.parseLong(value.substring(split+1).trim());
                if(millis>0 && millis<=600000) {
                    long seconds=millis/1000;
                    return String.format(Locale.US,"🎙 Pesan suara • %d:%02d",seconds/60,seconds%60);
                }
            } catch(NumberFormatException ignored) {}
            return "🎙 Pesan suara";
        }
        if(isImageMessage(value))return "📷 Mengirim sebuah foto";
        return raw==null?"":raw;
    }

    public static boolean isMine(
            JSONObject item
    ) {
        if (item == null) {
            return false;
        }

        if (
                item.optBoolean(
                        "last_message_is_mine",
                        false
                )
                        || item.optBoolean(
                        "is_mine",
                        false
                )
        ) {
            return true;
        }

        String sender = first(
                item.optString(
                        "last_sender_type"
                ),
                item.optString(
                        "sender_type"
                ),
                item.optString(
                        "last_message_sender"
                ),
                item.optString(
                        "sender_role"
                )
        ).toLowerCase(Locale.US);

        return sender.equals("customer")
                || sender.equals("user")
                || sender.equals("pelanggan");
    }

    public static String previewText(
            JSONObject item,
            String participant,
            String fallback
    ) {
        if (item == null) {
            return first(
                    fallback,
                    "Belum ada pesan"
            );
        }

        String message =
                item.optString(
                        "last_message",
                        ""
                ).trim();

        if (message.startsWith(VOICE_PREFIX)) {
            return isMine(item) ? "Anda mengirim voice note" : first(participant, "Mitra") + " mengirim voice note";
        }

        if (!isImageMessage(message)) {
            return first(
                    message,
                    fallback,
                    "Belum ada pesan"
            );
        }

        if (isMine(item)) {
            return "Anda mengirim foto";
        }

        String name = first(
                participant,
                item.optString(
                        "participant_name"
                ),
                item.optString("driver"),
                "Mitra"
        );

        return name + " mengirim foto";
    }

    private static String first(
            String... values
    ) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (
                    value != null
                            && !value.trim().isEmpty()
                            && !"null".equalsIgnoreCase(
                            value.trim()
                    )
                            && !"undefined".equalsIgnoreCase(
                            value.trim()
                    )
            ) {
                return value.trim();
            }
        }

        return "";
    }
}
