package com.transiva.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONObject;

final class TransSendContactClient {
    static final String EXTRA_CONTACT = "transsend_contact_id";
    private TransSendContactClient() {}

    static JSONObject contact(Context context, JSONObject body) throws Exception {
        return TransivaHttpRepository.postJson(context, ApiConfig.server("transsend_contact.php"), body, 6000);
    }

    static JSONObject action(String action, String id) throws org.json.JSONException {
        return new JSONObject().put("action", action).put("contact_id", id);
    }

    static void openChat(Activity activity, JSONObject contact) {
        activity.startActivity(new Intent(activity, CustomerChatRoomActivity.class)
                .putExtra(EXTRA_CONTACT, contact.optString("id"))
                .putExtra("room_id", contact.optString("room_id"))
                .putExtra("order_id", contact.optString("order_id"))
                .putExtra("order_source", "pickup_orders")
                .putExtra("order_type", "trans_simpel".equals(contact.optString("service")) ? "Trans Simpel" : "TransSend")
                .putExtra("order_status", "contact_accepted")
                .putExtra("participant_name", contact.optString("peer_name"))
                .putExtra("participant_photo", contact.optString("peer_photo")));
    }

    static JSONObject quote(Context context, Intent pickup, Intent delivery) throws Exception {
        Uri.Builder url = Uri.parse(ApiConfig.server("calculatePickupOngkir.php")).buildUpon()
                .appendQueryParameter("package_size", "small");
        for (String axis : new String[]{"lat", "lng"}) {
            url.appendQueryParameter("pickup_" + axis, Double.toString(pickup.getDoubleExtra(axis, 0)));
            url.appendQueryParameter("delivery_" + axis, Double.toString(delivery.getDoubleExtra(axis, 0)));
        }
        return TransivaHttpRepository.getJsonWithin(context, url.toString(), 12000);
    }
}
