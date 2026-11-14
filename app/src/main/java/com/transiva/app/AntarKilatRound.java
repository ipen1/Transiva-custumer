package com.transiva.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class AntarKilatRound {
    static final float DURATION = 30f;
    static final float TRAVEL_TIME = 3.65f;
    private static final float TURN_TIME = .26f;

    enum Kind { PARCEL, POTHOLE, PUDDLE }

    static final class ObjectOnRoad {
        final int lane;
        final Kind kind;
        float depth;

        ObjectOnRoad(int lane, Kind kind) {
            this.lane = lane;
            this.kind = kind;
        }
    }

    final List<ObjectOnRoad> objects = new ArrayList<>();
    private final Random random;
    float elapsed;
    float riderLane = 1;
    float impact;
    float pickup;
    float lean;
    int pickupCount;
    int hitCount;
    int penalty;
    private float turnStartLane = 1;
    private float turnElapsed = TURN_TIME;
    int targetLane = 1;
    int score;
    private float nextRow = .18f;

    AntarKilatRound(Random random) {
        this.random = random;
    }

    void steer(int direction) {
        int nextLane = Math.max(0, Math.min(2, targetLane + direction));
        if (nextLane == targetLane) return;
        turnStartLane = riderLane;
        targetLane = nextLane;
        turnElapsed = 0;
    }

    void advance(float seconds) {
        float delta = Math.min(seconds, DURATION - elapsed);
        float end = elapsed + delta;
        while (elapsed < end) {
            float stepEnd = Math.min(end, elapsed + 1f / 120);
            update(stepEnd - elapsed, stepEnd);
        }
    }

    private void update(float delta, float stepEnd) {
        elapsed = stepEnd;
        turnElapsed = Math.min(TURN_TIME, turnElapsed + delta);
        float turn = turnElapsed / TURN_TIME;
        float distance = targetLane - turnStartLane;
        riderLane = turnStartLane + distance * turn * turn * (3 - 2 * turn);
        float desiredLean = Math.max(-1, Math.min(1, distance * 4 * turn * (1 - turn)));
        lean += (desiredLean - lean) * Math.min(1, delta * 20);
        impact = Math.max(0, impact - delta);
        pickup = Math.max(0, pickup - delta);
        for (int i = 0; i < objects.size();) {
            ObjectOnRoad object = objects.get(i);
            object.depth += delta / TRAVEL_TIME;
            if (object.depth < 1) {
                i++;
                continue;
            }
            if (Math.abs(object.lane - riderLane) < .48f) {
                if (object.kind == Kind.PARCEL) {
                    score += 10;
                    pickup = .55f;
                    pickupCount++;
                } else {
                    penalty = Math.min(score, 5);
                    score -= penalty;
                    impact = .32f;
                    hitCount++;
                }
            }
            objects.remove(i);
        }
        if (elapsed >= nextRow && nextRow < DURATION - TRAVEL_TIME) {
            int parcelLane = random.nextInt(3);
            objects.add(new ObjectOnRoad(parcelLane, Kind.PARCEL));
            if (nextRow > 1) {
                int hazardLane = (parcelLane + 1 + random.nextInt(2)) % 3;
                Kind kind = random.nextBoolean() ? Kind.POTHOLE : Kind.PUDDLE;
                objects.add(new ObjectOnRoad(hazardLane, kind));
            }
            nextRow += 1.14f;
        }
    }
}
