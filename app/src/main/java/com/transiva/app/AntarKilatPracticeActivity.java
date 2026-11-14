package com.transiva.app;

import android.os.Bundle;

public final class AntarKilatPracticeActivity extends CustomerAdaptiveActivity {
    private AntarKilatGameView game;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        game = new AntarKilatGameView(this, null, this::finish);
        setContentView(game);
    }

    @Override protected void onPause() {
        game.pauseRound();
        super.onPause();
    }
}
