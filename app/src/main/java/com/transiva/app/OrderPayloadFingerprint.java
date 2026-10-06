package com.transiva.app;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
/** Stable structural serialization; object key insertion order does not define an order attempt. */
public final class OrderPayloadFingerprint {
    private OrderPayloadFingerprint() {}
    public static String canonical(Object value) throws Exception {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            ArrayList<String> keys = new ArrayList<>();
            Iterator<String> iterator = object.keys();
            while (iterator.hasNext()) keys.add(iterator.next());
            Collections.sort(keys);
            StringBuilder result = new StringBuilder("{");
            for (String key : keys) {
                if (result.length() > 1) result.append(',');
                result.append(JSONObject.quote(key)).append(':').append(canonical(object.get(key)));
            }
            return result.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder result = new StringBuilder("[");
            for (int i = 0; i < array.length(); i++) {
                if (i > 0) result.append(',');
                result.append(canonical(array.get(i)));
            }
            return result.append(']').toString();
        }
        if (value == null || value == JSONObject.NULL) return "null";
        if (value instanceof String) return JSONObject.quote((String)value);
        if (value instanceof Number) return JSONObject.numberToString((Number)value);
        if (value instanceof Boolean) return value.toString();
        throw new IllegalArgumentException("Unsupported JSON value");
    }
}
