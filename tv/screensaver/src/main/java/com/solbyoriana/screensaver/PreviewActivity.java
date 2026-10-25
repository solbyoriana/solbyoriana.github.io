package com.solbyoriana.screensaver;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * The same slideshow as a normal screen, for checking it without waiting 5 minutes:
 *   adb shell am start -n com.solbyoriana.screensaver/.PreviewActivity [--ez refresh true] [--ez halloween true|false]
 * "halloween" forces the Halloween layer on or off; without it, the date decides (as in the screensaver).
 */
public class PreviewActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        PhotoStore store = new PhotoStore(this);
        if (getIntent().getBooleanExtra("refresh", false)) store.forceNextSync();
        store.syncInBackground();
        FrameLayout root = new FrameLayout(this);
        root.addView(new SlideshowView(this, store));
        boolean halloween = getIntent().hasExtra("halloween")
                ? getIntent().getBooleanExtra("halloween", false)
                : HalloweenLayer.inSeason();
        if (halloween) root.addView(new HalloweenLayer(this));
        setContentView(root);
    }
}
