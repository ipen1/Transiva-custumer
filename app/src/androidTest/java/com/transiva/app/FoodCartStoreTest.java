package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FoodCartStoreTest {
    @Test public void paidLinesAreRemovedFromTheirOwnStoreAndAccount() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        int user = 192734111, otherUser = 192734112;
        int ciResto = 101, kopiKita = 102;
        SharedPreferences prefs = context.getSharedPreferences("transiva_order_attempts", Context.MODE_PRIVATE);
        String attempt = "food_cart_test_attempt";
        FoodCartStore store = new FoodCartStore(context, user);
        FoodCartStore otherStore = new FoodCartStore(context, otherUser);
        try {
            FoodCartStore.Cart first = store.get(ciResto);
            first.name = "Ci Resto";
            FoodCartStore.CartItem original = item(501, 2, "");
            FoodCartStore.CartItem topping = item(501, 1, "Keju");
            first.items.add(original);
            first.items.add(topping);
            assertTrue(store.save(first));
            saveSingle(store, kopiKita, item(601, 1, ""));
            saveSingle(otherStore, ciResto, item(501, 4, ""));

            FoodCartStore reopened = new FoodCartStore(context, user);
            assertEquals(2, reopened.all().size());
            assertEquals("Keju", reopened.get(ciResto).items.get(1).selectedOptions
                    .getJSONObject(0).getString("name"));
            JSONObject payload = new JSONObject().put("restaurant_id", ciResto)
                    .put("items", new JSONArray().put(new JSONObject().put("id", 501)
                            .put("qty", 2).put("_cart_line_id", original.lineId)));
            assertTrue(FoodCartStore.matchesDraft(prefs, String.valueOf(user), payload));
            assertFalse(FoodCartStore.matchesDraft(prefs, String.valueOf(otherUser), payload));

            // Another portion was added while the result of the first attempt was unknown.
            first.items.get(0).qty++;
            assertTrue(store.save(first));
            assertTrue(prefs.edit().putString(attempt, payload.toString()).commit());
            SharedPreferences.Editor acknowledge = prefs.edit().remove(attempt);
            synchronized (FoodCartStore.class) {
                FoodCartStore.consume(prefs, acknowledge, String.valueOf(user), payload);
                assertTrue(acknowledge.commit());
            }
            assertFalse(prefs.contains(attempt));
            assertEquals(2, reopened.get(ciResto).quantity());
            assertEquals(1, reopened.get(ciResto).items.get(0).qty);
            assertEquals(1, reopened.get(kopiKita).quantity());
            assertEquals(4, otherStore.get(ciResto).quantity());
            assertFalse(store.save(first)); // An older screen must not resurrect the paid portions.
            assertFalse(FoodCartStore.matchesDraft(prefs, String.valueOf(user), payload));
        } finally {
            prefs.edit().remove(attempt).remove("food_cart:" + user + ":" + ciResto)
                    .remove("food_cart:" + user + ":" + kopiKita).remove("food_cart:" + otherUser + ":" + ciResto).commit();
        }
    }

    private static void saveSingle(FoodCartStore store, int rid, FoodCartStore.CartItem item) {
        FoodCartStore.Cart cart = store.get(rid);
        cart.items.add(item);
        assertTrue(store.save(cart));
    }

    private static FoodCartStore.CartItem item(int menu, int qty, String topping) throws Exception {
        FoodCartStore.CartItem item = new FoodCartStore.CartItem();
        item.id = menu;
        item.qty = qty;
        item.name = "Menu";
        item.optionText = topping;
        item.selectedOptions = new JSONArray();
        if (!topping.isEmpty()) item.selectedOptions.put(new JSONObject()
                .put("type", "bundle_topping").put("name", topping));
        item.price = 12000;
        return item;
    }
}
