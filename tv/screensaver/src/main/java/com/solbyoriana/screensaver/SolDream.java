package com.solbyoriana.screensaver;

import android.service.dreams.DreamService;

/** The TV screensaver. Android starts it after 5 idle minutes (system screen_off_timeout). */
public class SolDream extends DreamService {
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        PhotoStore store = new PhotoStore(this);
        store.syncInBackground();
        setContentView(new SlideshowView(this, store));
    }
}
