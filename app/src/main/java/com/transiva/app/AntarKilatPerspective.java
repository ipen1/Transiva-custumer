package com.transiva.app;

/** Shared projection for the road, roadside scenery and objects that can be collected. */
final class AntarKilatPerspective {
    static final float HORIZON = .265f;
    static final float RIDER_GROUND = .885f;
    // Keep looping geometry below the screen before recycling its segments.
    static final float END_DEPTH = 1.29f;

    private AntarKilatPerspective() {}

    static float scale(float depth) {
        return depth / (1.65f - .65f * depth);
    }

    static float x(float lane, float depth, float width) {
        return width * (.5f + lane * (.009f + .27f * scale(depth)));
    }

    static float y(float depth, float height) {
        return height * (HORIZON + (RIDER_GROUND - HORIZON) * scale(depth));
    }
}
