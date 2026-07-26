package com.transiva.app.customer.domain;

public final class Promo {
    public final int id;
    public final String title;
    public final String description;
    public final String code;
    public final String imageUrl;
    public final String themeStart;
    public final String themeEnd;

    public Promo(String title, String description, String code) {
        this(0, title, description, code, "", "#0759E8", "#18B5FF");
    }

    public Promo(
            int id,
            String title,
            String description,
            String code,
            String imageUrl,
            String themeStart,
            String themeEnd
    ) {
        this.id = id;
        this.title = title == null ? "" : title;
        this.description = description == null ? "" : description;
        this.code = code == null ? "" : code;
        this.imageUrl = imageUrl == null ? "" : imageUrl;
        this.themeStart = validColor(themeStart, "#0759E8");
        this.themeEnd = validColor(themeEnd, "#18B5FF");
    }

    private static String validColor(String value, String fallback) {
        if (value == null) return fallback;
        String clean = value.trim();
        return clean.matches("#[0-9a-fA-F]{6}") ? clean : fallback;
    }
}
