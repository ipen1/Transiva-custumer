package com.transiva.app;

import android.app.Activity;

final class CustomerAntarKilatView extends AntarKilatGameView implements CustomerAppSettings.ThemeAware {
    private final Activity host;

    CustomerAntarKilatView(Activity host, String orderStatus, Runnable close) {
        super(host, orderStatus, close, CustomerAppSettings.isDarkMode(host));
        this.host = host;
        CustomerAppSettings.preserveArtwork(this);
        onCustomerThemeChanged(CustomerAppSettings.isDarkMode(host));
    }

    @Override public void onCustomerThemeChanged(boolean dark) {
        applyPalette(dark);
        CustomerWindowPolicy.setBarColorsIfChanged(host.getWindow(), 0xff092659, 0xff081833);
    }
}
