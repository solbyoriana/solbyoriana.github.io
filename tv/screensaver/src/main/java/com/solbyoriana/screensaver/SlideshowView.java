package com.solbyoriana.screensaver;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
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
 * random order, a slow fade every 10 seconds, and the white SOL logo in the bottom-right blur strip.
 * Built for a 1 GB TV: photos are decoded at screen size and only two are held in memory.
 */
final class SlideshowView extends View {
    private static final long SHOW_MS = 10_000;
    private static final long FADE_MS = 1_500;
    private static final long EMPTY_RETRY_MS = 30_000;
    private static final int DIM_ALPHA = 90;        // ~35% black over the blur
    private static final int LOGO_ALPHA = 217;      // 85%

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
        logo = whiteLogo(getResources(), Math.round(band(w, h) * 0.75f));
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
                s = decode(f, w, h);
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
        paint.setAlpha(LOGO_ALPHA);
        if (current == null) {
            // Nothing saved yet (first run, still downloading): just the logo, centred.
            canvas.drawBitmap(logo, (w - logo.getWidth()) / 2f, (h - logo.getHeight()) / 2f, paint);
        } else {
            int band = band(w, h);
            float x = w - band / 2f - logo.getWidth() / 2f;
            float y = h - h * 0.07f - logo.getHeight();
            canvas.drawBitmap(logo, x, y, paint);
        }
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
        // Photo: whole, fitted inside the centred h×h square.
        Bitmap p = s.photo;
        float fit = Math.min((float) Math.min(w, h) / p.getWidth(), (float) h / p.getHeight());
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

        // Cheap blur: shrink to a few dozen pixels, then grow back with smoothing.
        int tw = 24, th = Math.max(1, Math.round(24f * photo.getHeight() / photo.getWidth()));
        Bitmap tiny = Bitmap.createScaledBitmap(photo, tw, th, true);
        Bitmap blur = Bitmap.createScaledBitmap(tiny, tw * 4, th * 4, true);
        if (tiny != blur) tiny.recycle();
        if (!blur.isMutable()) {
            Bitmap m = blur.copy(Bitmap.Config.ARGB_8888, true);
            blur.recycle();
            blur = m;
        }
        new Canvas(blur).drawColor(Color.argb(DIM_ALPHA, 0, 0, 0));
        return new Slide(photo, blur);
    }

    /** The website logo turned white: dark ink becomes white, anything light or transparent becomes see-through. */
    private static Bitmap whiteLogo(android.content.res.Resources res, int targetWidth) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        Bitmap src = BitmapFactory.decodeResource(res, R.drawable.sol_logo, o);
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        src.recycle();
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int a = c >>> 24;
            int lum = (299 * ((c >> 16) & 0xff) + 587 * ((c >> 8) & 0xff) + 114 * (c & 0xff)) / 1000;
            int ink = Math.max(0, Math.min(255, (200 - lum) * 255 / 140));
            px[i] = (Math.min(a, ink) << 24) | 0xffffff;
        }
        Bitmap white = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        int tw = Math.max(1, targetWidth > 0 ? targetWidth : w / 4);
        Bitmap scaled = Bitmap.createScaledBitmap(white, tw, Math.max(1, Math.round((float) tw * h / w)), true);
        if (scaled != white) white.recycle();
        return scaled;
    }
}
