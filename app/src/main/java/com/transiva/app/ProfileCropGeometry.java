package com.transiva.app;

/** Square crop transform, independent of Android for deterministic boundary tests. */
final class ProfileCropGeometry {
    float side, width, height, scale, minimum, x, y;
    void fit(float side, float width, float height) {
        if (side <= 0 || width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid crop dimensions");
        this.side = side; this.width = width; this.height = height;
        minimum = Math.max(side / width, side / height); scale = minimum;
        x = (side - width * scale) / 2f; y = (side - height * scale) / 2f;
        clamp();
    }
    void drag(float dx, float dy) { x += dx; y += dy; clamp(); }
    void zoom(float factor, float focusX, float focusY) {
        if (Float.isNaN(factor) || Float.isInfinite(factor) || factor <= 0) return;
        float next = Math.max(minimum, Math.min(minimum * 5f, scale * factor));
        float ratio = next / scale;
        x = focusX - (focusX - x) * ratio; y = focusY - (focusY - y) * ratio;
        scale = next; clamp();
    }
    private void clamp() {
        x = Math.min(0, Math.max(side - width * scale, x));
        y = Math.min(0, Math.max(side - height * scale, y));
    }
}
