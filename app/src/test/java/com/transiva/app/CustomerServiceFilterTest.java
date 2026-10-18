package com.transiva.app;

import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class CustomerServiceFilterTest {
    private JSONObject order(String type, String status) throws Exception {
        return new JSONObject().put("order_type", type).put("status", status);
    }
    @Test public void serverAndUiAliasesShareOneTab() throws Exception {
        String[][] aliases = {{"Transbike","ride"},{"TransRide","ride"},{"bike","ride"},
                {"motor","ride"},{"Transcar","car"},{"mobil","car"},{"food","food"},
                {"TransFood","food"},{"pickup","pickup"},{"TransPickup","pickup"},
                {"TransSend","pickup"},{"TransShop","shop"},{"TransMart","shop"},
                {"TransTour","tour"},{"wisata","tour"},{"laundry","laundry"}};
        for (String[] entry : aliases)
            assertEquals(entry[0], entry[1], CustomerHistoryPolicy.serviceKey(order(entry[0], "pending")));
    }
    @Test public void businessNoteWinsOverTransportMode() throws Exception {
        JSONObject shop = order("Transbike", "pending").put("note", "{\"type\":\"shop\"}");
        JSONObject food = order("Transbike", "pending").put("note", new JSONObject().put("type", "food"));
        assertEquals("shop", CustomerHistoryPolicy.serviceKey(shop));
        assertEquals("food", CustomerHistoryPolicy.serviceKey(food));
        assertEquals("TransShop", CustomerHistoryPolicy.serviceName(shop));
    }
    @Test public void pickupSourceAndInvalidNotesAreSafe() throws Exception {
        JSONObject send = new JSONObject().put("source", "pickup_orders").put("note", "catatan bebas");
        assertEquals("pickup", CustomerHistoryPolicy.serviceKey(send));
        assertEquals("ride", CustomerHistoryPolicy.serviceKey(order("Transbike", "pending").put("note", "invalid")));
        assertEquals("other", CustomerHistoryPolicy.serviceKey(order("layanan_baru", "pending")));
    }
    @Test public void populatedTabsMoveToFrontWithoutLosingOtherServices() throws Exception {
        List<JSONObject> orders = Arrays.asList(order("TransShop", "pending"), order("TransPickup", "on_delivery"), order("Transbike", "finished"));
        List<String> active = CustomerHistoryPolicy.orderedServiceKeys(orders, true);
        assertEquals(Arrays.asList("pickup", "shop"), active.subList(0, 2));
        assertEquals(8, active.size());
        assertEquals("ride", CustomerHistoryPolicy.orderedServiceKeys(orders, false).get(0));
    }
    @Test public void countsAndFiltersPartitionEveryOrderExactlyOnce() throws Exception {
        List<JSONObject> orders = Arrays.asList(order("Transbike", "pending"), order("TransRide", "driver_accepted"),
                order("TransCar", "finished"), order("TransSend", "canceled"), order("TransShop", "on_delivery"), order("TransFood", "customer_cancelled"));
        assertEquals(3, CustomerHistoryPolicy.serviceCount(orders, "all", true));
        assertEquals(3, CustomerHistoryPolicy.serviceCount(orders, "all", false));
        assertEquals(2, CustomerHistoryPolicy.serviceCount(orders, "ride", true));
        int total = 0;
        for (String key : CustomerHistoryPolicy.orderedServiceKeys(orders, true))
            total += CustomerHistoryPolicy.serviceCount(orders, key, true) + CustomerHistoryPolicy.serviceCount(orders, key, false);
        assertEquals(orders.size(), total);
    }
    @Test public void selectingAnotherTabDoesNotMutateOrderPayload() throws Exception {
        JSONObject item = order("Transbike", "driver_accepted").put("id", 9);
        String before = item.toString();
        CustomerHistoryPolicy.serviceKey(item);
        CustomerHistoryPolicy.orderedServiceKeys(Arrays.asList(item), true);
        assertEquals(before, item.toString());
    }
}
