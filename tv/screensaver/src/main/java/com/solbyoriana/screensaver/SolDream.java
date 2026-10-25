package com.solbyoriana.screensaver;

import android.service.dreams.DreamService;
import android.widget.FrameLayout;

/** The TV screensaver. Android starts it after 5 idle minutes (system screen_off_timeout). */
public class SolDream extends DreamService {
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        PhotoStore store = new PhotoStore(this);
        store.syncInBackground();
        FrameLayout root = new FrameLayout(this);
        root.addView(new SlideshowView(this, store));
        if (HalloweenLayer.inSeason()) root.addView(new HalloweenLayer(this));
        setContentView(root);
    }
}
