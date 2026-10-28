package com.transiva.app;

/** Only display catalogs qualify. Balances, quotes, orders and mutations stay live. */
final class CustomerReadCachePolicy {
    static long ttl(String url) {
        String path=url.split("\\?",2)[0];
        if(path.endsWith("/get_food_restaurants.php"))return 15000L;
        if(path.endsWith("/search_food_menus.php"))return 20000L;
        if(path.endsWith("/get_food_menus.php"))return 10000L;
        return 0L;
    }
    private CustomerReadCachePolicy(){}
}
