package com.solbyoriana.screensaver;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

/**
 * The same slideshow as a normal screen, for checking it without waiting 5 minutes:
 *   adb shell am start -n com.solbyoriana.screensaver/.PreviewActivity [--ez refresh true]
 */
public class PreviewActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        PhotoStore store = new PhotoStore(this);
        if (getIntent().getBooleanExtra("refresh", false)) store.forceNextSync();
        store.syncInBackground();
        setContentView(new SlideshowView(this, store));
    }
}
