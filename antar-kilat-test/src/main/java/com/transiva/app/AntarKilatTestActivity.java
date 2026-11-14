package com.transiva.app;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.widget.FrameLayout;

public final class AntarKilatTestActivity extends Activity {
    private AntarKilatGameView game;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(0xff081833);
        if (Build.VERSION.SDK_INT >= 35) {
            page.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return WindowInsets.CONSUMED;
            });
        } else {
            getWindow().setStatusBarColor(0xff092659);
            getWindow().setNavigationBarColor(0xff081833);
        }
        game = new AntarKilatGameView(this, null, this::finish, true);
        page.addView(game, new FrameLayout.LayoutParams(-1, -1));
        setContentView(page);
    }

    @Override protected void onPause() {
        game.pauseRound();
        super.onPause();
    }
}
