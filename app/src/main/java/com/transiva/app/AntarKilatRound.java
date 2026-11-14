package com.transiva.app;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

final class AntarKilatRound {
    static final float DURATION = 30f;
    static final float TRAVEL_TIME = 2.65f;

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
    int targetLane = 1;
    int score;
    private float nextRow = .15f;

    AntarKilatRound(Random random) {
        this.random = random;
    }

    void steer(int direction) {
        targetLane = Math.max(0, Math.min(2, targetLane + direction));
    }

    void advance(float seconds) {
        float delta = Math.min(seconds, DURATION - elapsed);
        elapsed += delta;
        riderLane += (targetLane - riderLane) * Math.min(1, delta * 16);
        impact = Math.max(0, impact - delta);
        for (Iterator<ObjectOnRoad> it = objects.iterator(); it.hasNext();) {
            ObjectOnRoad object = it.next();
            object.depth += delta / TRAVEL_TIME;
            if (object.depth < 1) continue;
            if (Math.abs(object.lane - riderLane) < .48f) {
                if (object.kind == Kind.PARCEL) {
                    score += 10;
                } else {
                    score = Math.max(0, score - 5);
                    impact = .28f;
                }
            }
            it.remove();
        }
        if (elapsed >= nextRow && nextRow < DURATION - TRAVEL_TIME) {
            int parcelLane = random.nextInt(3);
            objects.add(new ObjectOnRoad(parcelLane, Kind.PARCEL));
            if (nextRow > 1) {
                int hazardLane = (parcelLane + 1 + random.nextInt(2)) % 3;
                Kind kind = random.nextBoolean() ? Kind.POTHOLE : Kind.PUDDLE;
                objects.add(new ObjectOnRoad(hazardLane, kind));
            }
            nextRow += Math.max(.78f, 1.18f - elapsed * .012f);
        }
    }
}
