package com.solbyoriana.screensaver;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen nail-art slideshow: each photo whole and centred on a darkened blur of itself,
 * random order, a slow fade every 10 seconds, and the SOL logo as a round badge in the right-hand blur strip,
 * centred between the photo's right edge and the screen's right edge.
 * Built for a 1 GB TV: photos are decoded at screen size and only two are held in memory.
 */
final class SlideshowView extends View {
    private static final long SHOW_MS = 10_000;
    private static final long FADE_MS = 1_500;
    private static final long EMPTY_RETRY_MS = 30_000;
    private static final int DIM_ALPHA = 90;        // ~35% black over the blur

    private static final class Slide {
        final Bitmap photo, blur;
        Slide(Bitmap photo, Bitmap blur) { this.photo = photo; this.blur = blur; }
        void recycle() { photo.recycle(); blur.recycle(); }
    }

    private final PhotoStore store;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final List<File> queue = new ArrayList<>();

    private Slide current, next;
    private float fade;
    private ValueAnimator fader;
    private Bitmap logo;
    private boolean running;

    SlideshowView(Context context, PhotoStore store) {
        super(context);
        this.store = store;
        setBackgroundColor(Color.BLACK);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w == 0 || h == 0) return;
        if (logo != null) logo.recycle();
        try {
            logo = logoCard(getResources(), Math.round(band(w, h) * 0.75f));
        } catch (Throwable t) {
            logo = null; // photos without the logo beat a crashed screensaver
        }
        if (!running) {
            running = true;
            loadNext();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
        main.removeCallbacksAndMessages(null);
        if (fader != null) fader.cancel();
        loader.shutdownNow();
        if (current != null) current.recycle();
        if (next != null) next.recycle();
        if (logo != null) logo.recycle();
        current = next = null;
        logo = null;
    }

    /** Width of the blur strip on each side of a square photo; every photo sits inside the square, so the logo never covers nails. */
    private static int band(int w, int h) {
        return Math.max(0, (w - h) / 2);
    }

    private void loadNext() {
        final int w = getWidth(), h = getHeight();
        loader.execute(() -> {
            Slide s = null;
            for (int tries = 0; s == null && tries < 5; tries++) {
                File f = nextFile();
                if (f == null) break;
                // If the screensaver ever crashes, Android puts the TV to sleep, so a bad photo (or running
                // out of memory on this 1 GB TV) only skips that photo.
                try {
                    s = decode(f, w, h);
                } catch (Throwable t) {
                    android.util.Log.w("SolSlideshow", "Skipping " + f.getName(), t);
                }
            }
            final Slide loaded = s;
            main.post(() -> {
                if (!running) {
                    if (loaded != null) loaded.recycle();
                    return;
                }
                if (loaded == null) {
                    invalidate();
                    main.postDelayed(this::loadNext, EMPTY_RETRY_MS);
                } else if (current == null) {
                    current = loaded;
                    invalidate();
                    main.postDelayed(this::loadNext, SHOW_MS);
                } else {
                    next = loaded;
                    startFade();
                }
            });
        });
    }

    private File nextFile() {
        if (queue.isEmpty()) {
            queue.addAll(store.photos());
            Collections.shuffle(queue);
        }
        return queue.isEmpty() ? null : queue.remove(queue.size() - 1);
    }

    private void startFade() {
        fader = ValueAnimator.ofFloat(0f, 1f).setDuration(FADE_MS);
        fader.addUpdateListener(a -> {
            fade = (float) a.getAnimatedValue();
            invalidate();
        });
        fader.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                if (!running || next == null) return;
                current.recycle();
                current = next;
                next = null;
                fade = 0f;
                invalidate();
                main.postDelayed(SlideshowView.this::loadNext, SHOW_MS);
            }
        });
        fader.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (current != null) drawSlide(canvas, current, 255, w, h);
        if (next != null && fade > 0f) drawSlide(canvas, next, Math.round(255 * fade), w, h);
        if (logo == null) return;
        paint.setAlpha(255);
        if (current == null) {
            // Nothing saved yet (first run, still downloading): just the logo, centred.
            canvas.drawBitmap(logo, (w - logo.getWidth()) / 2f, (h - logo.getHeight()) / 2f, paint);
        } else {
            // Midway between the photo's right edge and the screen's; glides with the fade when photo widths differ.
            float edge = photoRight(current, w, h);
            if (next != null) edge += (photoRight(next, w, h) - edge) * fade;
            float x = (edge + w) / 2f - logo.getWidth() / 2f;
            float y = h - h * 0.07f - logo.getHeight();
            canvas.drawBitmap(logo, x, y, paint);
        }
    }

    /** Photo scale: whole, fitted inside the centred h×h square. */
    private static float fit(Bitmap p, int w, int h) {
        return Math.min((float) Math.min(w, h) / p.getWidth(), (float) h / p.getHeight());
    }

    private static float photoRight(Slide s, int w, int h) {
        return (w + s.photo.getWidth() * fit(s.photo, w, h)) / 2f;
    }

    private void drawSlide(Canvas canvas, Slide s, int alpha, int w, int h) {
        paint.setAlpha(alpha);
        // Blur: centre-crop to fill the screen.
        Bitmap b = s.blur;
        float scale = Math.max((float) w / b.getWidth(), (float) h / b.getHeight());
        float cw = w / scale, ch = h / scale;
        src.set(Math.round((b.getWidth() - cw) / 2), Math.round((b.getHeight() - ch) / 2),
                Math.round((b.getWidth() + cw) / 2), Math.round((b.getHeight() + ch) / 2));
        dst.set(0, 0, w, h);
        canvas.drawBitmap(b, src, dst, paint);
        Bitmap p = s.photo;
        float fit = fit(p, w, h);
        float pw = p.getWidth() * fit, ph = p.getHeight() * fit;
        dst.set((w - pw) / 2, (h - ph) / 2, (w + pw) / 2, (h + ph) / 2);
        canvas.drawBitmap(p, null, dst, paint);
    }

    /** Runs on the loader thread. Returns null for an unreadable file. */
    private static Slide decode(File f, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int box = Math.min(w, h);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= box && o.outHeight / (sample * 2) >= h) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap photo = BitmapFactory.decodeFile(f.getPath(), o);
        if (photo == null) return null;

        // Blur: shrink to 48 px wide, box-blur three times (close to a gaussian) and darken, then let
        // the GPU stretch it to full screen. Smooth enough that the stretch shows no blocks.
        int tw = 48, th = Math.max(1, Math.round(48f * photo.getHeight() / photo.getWidth()));
        Bitmap tiny = Bitmap.createScaledBitmap(photo, tw, th, true);
        int[] px = new int[tw * th];
        tiny.getPixels(px, 0, tw, 0, 0, tw, th);
        if (tiny != photo) tiny.recycle();
        for (int pass = 0; pass < 3; pass++) boxBlur(px, tw, th, 3);
        int keep = 255 - DIM_ALPHA;
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            px[i] = 0xff000000 | ((((c >> 16) & 0xff) * keep / 255) << 16)
                    | ((((c >> 8) & 0xff) * keep / 255) << 8) | ((c & 0xff) * keep / 255);
        }
        return new Slide(photo, Bitmap.createBitmap(px, tw, th, Bitmap.Config.ARGB_8888));
    }

    private static void boxBlur(int[] px, int w, int h, int r) {
        int[] tmp = new int[px.length];
        for (int pass = 0; pass < 2; pass++) {
            boolean horizontal = pass == 0;
            int[] in = horizontal ? px : tmp, out = horizontal ? tmp : px;
            int lines = horizontal ? h : w, len = horizontal ? w : h;
            for (int line = 0; line < lines; line++) {
                for (int i = 0; i < len; i++) {
                    int rs = 0, gs = 0, bs = 0, n = 0;
                    for (int k = Math.max(0, i - r); k <= Math.min(len - 1, i + r); k++) {
                        int c = in[horizontal ? line * w + k : k * w + line];
                        rs += (c >> 16) & 0xff;
                        gs += (c >> 8) & 0xff;
                        bs += c & 0xff;
                        n++;
                    }
                    out[horizontal ? line * w + i : i * w + line] = 0xff000000 | (rs / n << 16) | (gs / n << 8) | (bs / n);
                }
            }
        }
    }

    /** The square SOL logo (black on its cream background) cut into a circle of the given diameter. */
    private static Bitmap logoCard(android.content.res.Resources res, int size) {
        size = Math.max(1, size > 0 ? size : 200);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        o.inSampleSize = 2; // 1563 px source; half is still sharper than any card size on a TV
        Bitmap src = BitmapFactory.decodeResource(res, R.drawable.sol_logo, o);
        Bitmap scaled = Bitmap.createScaledBitmap(src, size, size, true);
        if (scaled != src) src.recycle();
        Bitmap card = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(new BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        new Canvas(card).drawCircle(size / 2f, size / 2f, size / 2f, p);
        scaled.recycle();
        return card;
    }
}
