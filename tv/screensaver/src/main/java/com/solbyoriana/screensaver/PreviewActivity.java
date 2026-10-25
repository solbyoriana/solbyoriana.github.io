package com.solbyoriana.screensaver;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * The same slideshow as a normal screen, for checking it without waiting 5 minutes:
 *   adb shell am start -n com.solbyoriana.screensaver/.PreviewActivity [--ez refresh true] [--ez halloween true|false] [--ei batRound 3]
 * "halloween" forces the Halloween layer on or off; without it, the date decides (as in the screensaver).
 * "batRound" starts the first bat round at once with that many bats (0-3).
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
        if (halloween) {
            HalloweenLayer layer = new HalloweenLayer(this);
            if (getIntent().hasExtra("batRound")) layer.startWithRound(getIntent().getIntExtra("batRound", 3));
            root.addView(layer);
        }
        setContentView(root);
    }
}
