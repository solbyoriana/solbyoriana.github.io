package com.solbyoriana.screensaver;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Halloween decorations drawn over the slideshow, only in the blur strips beside the photo:
 * pumpkin garlands in both top corners, a "Happy Halloween" sign planted in grass bottom-left,
 * and bats that come in rounds. Port of tv/bat-preview.html; the rules are the same:
 * a round every 10-20 min picks 0-3 bats, never 3 flying or 3 resting at once, one bat per perch,
 * and a round's set of bats never repeats either of the two sets before it.
 */
final class HalloweenLayer extends View {

    /** October, and November 1st. */
    static boolean inSeason() {
        Calendar c = Calendar.getInstance();
        int m = c.get(Calendar.MONTH);
        return m == Calendar.OCTOBER || (m == Calendar.NOVEMBER && c.get(Calendar.DAY_OF_MONTH) == 1);
    }

    // Everything is laid out in the TV's 1280x720 design space and scaled to the real screen.
    private static final float DH = 720f, S = 2f, BAND = 280f;
    private static final int MAX_BATS = 3, MAX_FLYING = 2, MAX_RESTING = 2, NO_REPEAT = 2;
    private static final float ROUND_MIN = 10 * 60, ROUND_MAX = 20 * 60, LIFE_MIN = 4 * 60, LIFE_MAX = 9 * 60;
    private static final int FLY = 0, LEAVE = 1, AWAY = 2, TO_PERCH = 3, HANG = 4, SLEEP = 5, EXIT = 6, GONE = 7;
    private static final int CLASSIC = 0, CUTE = 1, GOTHIC = 2, EYES = 3, IMAGE = 4;
    private static final int INK = Color.rgb(13, 11, 10), ORANGE = Color.rgb(255, 154, 31);

    // Kept for the whole process, so the no-repeat rule holds across screensaver sessions.
    private static final List<String> recentSets = new ArrayList<>();

    private static final class Type {
        final String name; final int kind; final Bitmap img; final float width, flapHz, speed;
        final boolean perches, glide, tilted;
        Type(String name, int kind, Bitmap img, float width, float flapHz, float speed, boolean perches, boolean glide, boolean tilted) {
            this.name = name; this.kind = kind; this.img = img; this.width = width; this.flapHz = flapHz; this.speed = speed;
            this.perches = perches; this.glide = glide; this.tilted = tilted;
        }
    }

    private static final class Perch {
        final float x, y; Bat owner;
        Perch(float x, float y) { this.x = x; this.y = y; }
    }

    private static final class Bat {
        final Type v; int state = AWAY;
        float x = -100, y = 200, vx, vy, tx, ty, t, timer, life;
        Perch perch;
        Bat(Type v) { this.v = v; }
    }

    private final Random rnd = new Random();
    private final Paint art = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint feet = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final List<Type> pool = new ArrayList<>();
    private final List<Bat> bats = new ArrayList<>();
    private final Perch[] perches;
    private Bitmap garland, sign, grass;
    private float k = 1f, dw = 1280f, clock, nextRound;
    private long lastFrame;
    private boolean running;

    HalloweenLayer(Context context) {
        super(context);
        Resources res = getResources();
        garland = load(res, R.drawable.halloween_garland);
        sign = load(res, R.drawable.halloween_sign);
        grass = load(res, R.drawable.halloween_grass);
        pool.add(new Type("A classic", CLASSIC, null, 0, 7, 300, true, false, false));
        pool.add(new Type("B cute", CUTE, null, 0, 4.5f, 230, true, false, false));
        pool.add(new Type("C gothic", GOTHIC, null, 0, 5, 340, true, true, false));
        pool.add(new Type("D glowing eyes", EYES, null, 0, 7, 300, true, false, false));
        pool.add(new Type("E silhouette", IMAGE, load(res, R.drawable.bat_e), 110, 6, 300, false, false, false));
        pool.add(new Type("F swooper", IMAGE, load(res, R.drawable.bat_f), 110, 5, 320, false, false, true));
        pool.add(new Type("G little one", IMAGE, load(res, R.drawable.bat_g), 80, 8, 280, false, false, false));
        pool.add(new Type("H wide wings", IMAGE, load(res, R.drawable.bat_h), 120, 5, 300, false, false, false));
        pool.add(new Type("I glossy", IMAGE, load(res, R.drawable.bat_i), 105, 6, 300, false, false, true));
        perches = new Perch[]{new Perch(160, 120), new Perch(90, 116), new Perch(0, 120), new Perch(0, 116)};
        ink.setColor(INK);
        feet.setColor(INK);
        feet.setStyle(Paint.Style.STROKE);
        feet.setStrokeWidth(1.4f);
        zPaint.setColor(Color.rgb(239, 232, 220));
        zPaint.setFakeBoldText(true);
        nextRound = 5 + rnd.nextFloat() * 55;      // first round soon after the screensaver starts
    }

    private static Bitmap load(Resources res, int id) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            return BitmapFactory.decodeResource(res, id, o);
        } catch (Throwable t) {
            return null; // a missing decoration beats a crashed screensaver (which would put the TV to sleep)
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w == 0 || h == 0) return;
        k = h / DH;
        dw = w / k;
        // right-hand perches mirror the left ones
        perches[2] = new Perch(dw - 160, 120);
        perches[3] = new Perch(dw - 90, 116);
        running = true;
        lastFrame = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
    }

    // ---------- rounds and rules ----------

    private int count(int... states) {
        int n = 0;
        for (Bat b : bats) for (int s : states) if (b.state == s) { n++; break; }
        return n;
    }

    // A bat heading to a perch is still in the air, and its perch is already taken, so it counts in both.
    private boolean canFly() { return count(FLY, LEAVE, TO_PERCH, EXIT) < MAX_FLYING; }
    private boolean canRest() { return count(TO_PERCH, HANG, SLEEP) < MAX_RESTING; }

    private float rnd(float a, float b) { return a + rnd.nextFloat() * (b - a); }

    private static String setKey(List<Type> types) {
        List<String> names = new ArrayList<>();
        for (Type t : types) names.add(t.name);
        Collections.sort(names);
        return names.isEmpty() ? "no bats" : String.join(" + ", names);
    }

    private void startRound() {
        int free = MAX_BATS - bats.size();
        List<Type> available = new ArrayList<>();
        for (Type t : pool) {
            boolean onScreen = false;
            for (Bat b : bats) if (b.v == t) { onScreen = true; break; }
            if (!onScreen) available.add(t);
        }
        List<Type> choices = null;
        for (int tries = 0; tries < 60 && choices == null; tries++) {
            int n = Math.min(Math.min(free, available.size()), rnd.nextInt(MAX_BATS + 1));  // 0, 1, 2 or 3
            Collections.shuffle(available, rnd);
            List<Type> pick = new ArrayList<>(available.subList(0, n));
            if (!recentSets.contains(setKey(pick))) choices = pick;
        }
        if (choices == null) { nextRound = clock + 60; return; }  // last round's bats still around: try again in a minute
        for (Type t : choices) {
            Bat b = new Bat(t);
            b.t = rnd(0, 10);
            b.timer = rnd(0, 4);      // waits just off screen, flies in when the flying limit allows
            b.life = rnd(LIFE_MIN, LIFE_MAX);
            bats.add(b);
        }
        recentSets.add(0, setKey(choices));
        while (recentSets.size() > NO_REPEAT) recentSets.remove(recentSets.size() - 1);
        nextRound = clock + rnd(ROUND_MIN, ROUND_MAX);
    }

    // Flying targets live in the side strips; now and then a bat crosses over the photo to the other side.
    private void pickTarget(Bat b, boolean otherSide) {
        boolean onLeft = b.x < dw / 2;
        boolean goLeft = otherSide ? !onLeft : (b.x < 0 || (b.x <= dw && onLeft));
        b.tx = goLeft ? rnd(30, BAND - 40) : rnd(dw - BAND + 40, dw - 30);
        b.ty = rnd(40, 470);                          // stays above the sign and the logo
    }

    private float steer(Bat b, float dt, boolean arrive) {
        float dx = b.tx - b.x, dy = b.ty - b.y, d = (float) Math.hypot(dx, dy);
        if (d < 0.001f) d = 0.001f;
        float sp = b.v.speed;
        if (b.x > BAND && b.x < dw - BAND) sp *= 1.5f;  // over the nail photo: hurry across
        if (arrive && d < 140) sp *= Math.max(0.15f, d / 140);
        float kk = Math.min(1, dt * 2.2f);
        b.vx += (dx / d * sp - b.vx) * kk;
        b.vy += (dy / d * sp - b.vy) * kk;
        b.x += b.vx * dt;
        b.y += b.vy * dt;
        return d;
    }

    private Perch freePerch() {
        List<Perch> open = new ArrayList<>();
        for (Perch p : perches) if (p.owner == null) open.add(p);
        return open.isEmpty() ? null : open.get(rnd.nextInt(open.size()));
    }

    private static void release(Bat b) {
        if (b.perch != null) { b.perch.owner = null; b.perch = null; }
    }

    private void update(Bat b, float dt) {
        b.t += dt; b.timer -= dt; b.life -= dt;
        if (b.life <= 0 && b.state != EXIT && b.state != GONE) {
            if (b.state == AWAY) { b.state = GONE; return; }
            boolean perched = b.state == HANG || b.state == SLEEP;
            if (!perched || canFly()) {                // a perched bat waits for room in the air before it leaves
                release(b);
                b.state = EXIT;
                b.tx = b.x < dw / 2 ? -120 : dw + 120;
                b.ty = rnd(60, 300);
                if (perched) b.vy = 140;               // drop off the perch, then fly away
            }
        }
        switch (b.state) {
            case FLY:
                if (steer(b, dt, false) < 40) pickTarget(b, rnd.nextFloat() < 0.025f);
                if (b.timer <= 0) {
                    float r = rnd.nextFloat();
                    Perch spot = b.v.perches && canRest() ? freePerch() : null;
                    if (spot != null && r < 0.45f) { b.perch = spot; spot.owner = b; b.tx = spot.x; b.ty = spot.y; b.state = TO_PERCH; }
                    else if (r < 0.75f) { b.tx = b.x < dw / 2 ? -120 : dw + 120; b.ty = rnd(60, 400); b.state = LEAVE; }
                    else b.timer = rnd(10, 20);
                }
                break;
            case LEAVE:
                if (steer(b, dt, false) < 30) { b.state = AWAY; b.timer = rnd(8, 25); }
                break;
            case AWAY:
                if (b.timer <= 0) {
                    if (!canFly()) { b.timer = rnd(3, 8); break; }  // two already flying: try again shortly
                    b.x = rnd.nextBoolean() ? -100 : dw + 100;
                    b.y = rnd(60, 380);
                    b.vx = b.vy = 0;
                    pickTarget(b, false);
                    b.state = FLY;
                    b.timer = rnd(15, 30);
                }
                break;
            case TO_PERCH:
                if (steer(b, dt, true) < 4) { b.x = b.perch.x; b.y = b.perch.y; b.vx = b.vy = 0; b.state = HANG; b.timer = rnd(6, 14); }
                break;
            case HANG:
                if (b.timer <= 0) { b.state = SLEEP; b.timer = rnd(25, 70); }
                break;
            case SLEEP:
                if (b.timer <= 0 && !canFly()) b.timer = rnd(5, 12);  // two already flying: sleep a little longer
                else if (b.timer <= 0) {
                    release(b);
                    b.state = FLY;
                    b.vy = 140;
                    b.vx = b.x < dw / 2 ? 80 : -80;
                    pickTarget(b, rnd.nextFloat() < 0.15f);
                    b.timer = rnd(15, 30);
                }
                break;
            case EXIT:
                if (steer(b, dt, false) < 40) b.state = GONE;
                break;
        }
    }

    // ---------- drawing ----------

    @Override
    protected void onDraw(Canvas canvas) {
        if (!running) return;
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        clock += dt;
        if (clock >= nextRound) startRound();
        for (Iterator<Bat> it = bats.iterator(); it.hasNext(); ) {
            Bat b = it.next();
            update(b, dt);
            if (b.state == GONE) it.remove();
        }

        canvas.save();
        canvas.scale(k, k);
        drawDecorations(canvas);
        boolean visible = false;
        for (Bat b : bats) {
            if (b.state == AWAY) continue;
            visible = true;
            if (b.state == HANG || b.state == SLEEP) {
                drawHanging(canvas, b, b.state == SLEEP);
                if (b.state == SLEEP) drawZzz(canvas, b);
            } else {
                drawFlying(canvas, b);
            }
        }
        canvas.restore();
        // ~30 fps while a bat is on screen; otherwise just keep the clock ticking
        postInvalidateDelayed(visible ? 33 : 500);
    }

    private void drawDecorations(Canvas c) {
        if (garland != null) {
            r.set(-6, -8, BAND, BAND - 2);
            c.drawBitmap(garland, null, r, art);
            c.save();
            c.scale(-1, 1, dw / 2, 0);                  // mirrored copy in the top-right corner
            c.drawBitmap(garland, null, r, art);
            c.restore();
        }
        // grass runs 50 px off the bottom edge; the sign's board sits just above it, post planted in the grass
        float gs = 220, grassY = DH + 50 - gs * 574 / 640, grassTop = grassY + gs * 32 / 640;
        float sw = 236, sy = grassTop - 6 - sw * 0.70f;
        if (sign != null) {
            r.set((BAND - sw) / 2, sy, (BAND + sw) / 2, sy + sw);
            c.drawBitmap(sign, null, r, art);
        }
        if (grass != null) {
            r.set((BAND - gs) / 2, grassY, (BAND + gs) / 2, grassY + gs);
            c.drawBitmap(grass, null, r, art);
        }
    }

    private float flapOf(Bat b) {
        if (b.v.glide && b.t % 2.6f > 1.0f) return 0.15f + (float) Math.sin(b.t * 2) * 0.05f;
        return (float) Math.sin(b.t * Math.PI * 2 * b.v.flapHz);
    }

    private void drawFlying(Canvas c, Bat b) {
        float f = flapOf(b);
        c.save();
        c.translate(b.x, b.y + (float) Math.sin(b.t * 3) * 5);
        c.scale(S, S);
        c.rotate((float) Math.toDegrees(Math.max(-0.35f, Math.min(0.35f, b.vx / 1200))));
        switch (b.v.kind) {
            case CLASSIC: classic(c, f); break;
            case EYES: classic(c, f); eyes(c, -8.5f, 1.2f, ORANGE); break;
            case CUTE: cute(c, f); break;
            case GOTHIC: gothic(c, f); break;
            case IMAGE: image(c, f, b); break;
        }
        c.restore();
    }

    /** One wing; points are {x, y} for a line or {cx, cy, x, y} for a curve, mirrored by side. */
    private void wing(Canvas c, int side, float[][] pts) {
        path.reset();
        path.moveTo(side * pts[0][0], pts[0][1]);
        for (int i = 1; i < pts.length; i++) {
            float[] p = pts[i];
            if (p.length == 4) path.quadTo(side * p[0], p[1], side * p[2], p[3]);
            else path.lineTo(side * p[0], p[1]);
        }
        path.close();
        c.drawPath(path, ink);
    }

    /** Two triangular ears: from (±baseX, baseY) up to (±tipX, tipY) and back in to (±inX, inY). */
    private void ears(Canvas c, float baseX, float baseY, float tipX, float tipY, float inX, float inY) {
        path.reset();
        path.moveTo(-baseX, baseY); path.lineTo(-tipX, tipY); path.lineTo(-inX, inY); path.close();
        path.moveTo(baseX, baseY); path.lineTo(tipX, tipY); path.lineTo(inX, inY); path.close();
        c.drawPath(path, ink);
    }

    private void classic(Canvas c, float f) {
        float up = -f * 11;
        for (int s = -1; s <= 1; s += 2) {
            wing(c, s, new float[][]{{3, -4}, {15, -12 + up * 0.8f}, {30, -7 + up}, {24, 1 + up * 0.5f, 21, 3 + up * 0.35f},
                    {17, 1 + up * 0.2f, 13, 5}, {8, 3, 3, 5}});
        }
        c.drawOval(-4.5f, -6.5f, 4.5f, 8.5f, ink);
        c.drawCircle(0, -8, 4.2f, ink);
        ears(c, 4, -9, 3.5f, -16, 0.8f, -11);
    }

    private void gothic(Canvas c, float f) {
        float up = -f * 14;
        for (int s = -1; s <= 1; s += 2) {
            wing(c, s, new float[][]{{3, -3}, {18, -14 + up * 0.7f}, {44, -10 + up}, {34, -2 + up * 0.5f, 31, 2 + up * 0.4f},
                    {27, -1 + up * 0.25f, 22, 3 + up * 0.2f}, {17, 1, 13, 4}, {8, 2, 3, 5}});
        }
        c.drawOval(-3.5f, -7, 3.5f, 9, ink);
        c.drawCircle(0, -8, 3.5f, ink);
        ears(c, 3, -9, 3.5f, -17, 0.5f, -11);
    }

    private void cute(Canvas c, float f) {
        for (int s = -1; s <= 1; s += 2) {
            c.save();
            c.translate(s * 7, -1);
            c.rotate((float) Math.toDegrees(s * (-0.2f - f * 0.55f)));
            c.drawOval(Math.min(s * 10 - 12, s * 10 + 12), -7, Math.max(s * 10 - 12, s * 10 + 12), 7, ink);
            c.restore();
        }
        c.drawCircle(0, 0, 9, ink);
        ears(c, 8, -4, 7, -15, 2, -8);
        eyes(c, -1.5f, 1.8f, Color.WHITE, 3);
    }

    private void eyes(Canvas c, float y, float radius, int color) { eyes(c, y, radius, color, 1.7f); }

    private void eyes(Canvas c, float y, float radius, int color, float spread) {
        int old = ink.getColor();
        ink.setColor(color);
        c.drawCircle(-spread, y, radius, ink);
        c.drawCircle(spread, y, radius, ink);
        ink.setColor(old);
    }

    /** Wingbeats for a single-pose image: squash it flat and stretch it back. Tilted ones face their direction of flight. */
    private void image(Canvas c, float f, Bat b) {
        Bitmap img = b.v.img;
        if (img == null) return;
        float w = b.v.width / S, h = w * img.getHeight() / img.getWidth(), sy = 0.4f + 0.6f * (f + 1) / 2;
        c.save();
        if (b.v.tilted && b.vx > 0) c.scale(-1, 1);
        r.set(-w / 2, -h * sy / 2, w / 2, h * sy / 2);
        c.drawBitmap(img, null, r, art);
        c.restore();
    }

    /** Hanging upside down, wings wrapped. The only motion is a slow sway on one sine wave, so the loop is seamless. */
    private void drawHanging(Canvas c, Bat b, boolean asleep) {
        boolean cute = b.v.kind == CUTE;
        c.save();
        c.translate(b.x, b.y);
        c.scale(S, S);
        c.rotate((float) Math.toDegrees(Math.sin(b.t * 1.3) * 0.06));
        c.drawLine(-2, 0, -2.5f, 5, feet);
        c.drawLine(2, 0, 2.5f, 5, feet);
        if (cute) c.drawOval(-9, 4, 9, 26, ink); else c.drawOval(-7, 3, 7, 27, ink);
        float hy = cute ? 26 : 28;
        c.drawCircle(0, hy, cute ? 6 : 4.2f, ink);
        path.reset();
        path.moveTo(-3.5f, hy + 1); path.lineTo(-3.5f, hy + 8); path.lineTo(-0.8f, hy + 3); path.close();
        path.moveTo(3.5f, hy + 1); path.lineTo(3.5f, hy + 8); path.lineTo(0.8f, hy + 3); path.close();
        c.drawPath(path, ink);
        if (!asleep) {
            if (b.v.kind == EYES) eyes(c, hy + 0.5f, 1.1f, ORANGE);
            else if (cute) eyes(c, hy + 0.5f, 1.1f, Color.WHITE);
        }
        c.restore();
    }

    private void drawZzz(Canvas c, Bat b) {
        for (int i = 0; i < 3; i++) {
            float p = ((b.t * 0.45f) + i / 3f) % 1f;
            zPaint.setAlpha((int) (Math.sin(p * Math.PI) * 0.9 * 255));
            zPaint.setTextSize((9 + p * 9) * S);
            c.drawText("z", b.x + (10 + p * 18) * S, b.y + (30 - p * 34) * S, zPaint);
        }
    }
}
