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
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Halloween decorations drawn over the slideshow, only in the blur strips beside the photo:
 * pumpkin garlands in both top corners, a "Happy Halloween" sign planted in grass bottom-left, and bats.
 *
 * Bat rules (set by the studio owner):
 * - Flying bats are the round white-eyed bat, 3-7 of them. Resting bats (hanging/sleeping under the garlands)
 *   are the classic, gothic and white-eyed classic bats, 2-5 of them, one per perch. At most 8 bats in all.
 * - Every 10-20 minutes a round picks new counts; a round's counts never repeat either of the two rounds before it.
 * - The two sides are never even (2+2, 3+3, 4+4). When they are, a flying bat switches sides Pac-Man style:
 *   out through the outer edge of its side, back in through the outer edge of the other. Flying bats keep to the
 *   side strips and only now and then cross over the nail photo.
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
    private static final int FLY_MIN = 3, FLY_MAX = 7, REST_MIN = 2, REST_MAX = 5, MAX_BATS = 8, NO_REPEAT = 2;
    private static final float ROUND_MIN = 10 * 60, ROUND_MAX = 20 * 60;
    private static final int FLY = 0, WRAP = 1, TO_PERCH = 2, HANG = 3, SLEEP = 4, EXIT = 5, GONE = 6;
    private static final int CLASSIC = 0, CUTE = 1, GOTHIC = 2, EYES = 3;
    private static final int INK = Color.rgb(13, 11, 10);
    private static final int[] RESTING_KINDS = {CLASSIC, GOTHIC, EYES};

    // Kept for the whole process, so the no-repeat rule holds across screensaver sessions.
    private static final List<String> recentRounds = new ArrayList<>();

    private static final class Perch {
        final float x, y; Bat owner;
        Perch(float x, float y) { this.x = x; this.y = y; }
    }

    private static final class Bat {
        final boolean flyer; final int kind; final float flapHz, speed; final boolean glide;
        int state; float x, y, vx, vy, tx, ty, t, timer;
        Perch perch;
        Bat(boolean flyer, int kind) {
            this.flyer = flyer; this.kind = kind;
            flapHz = kind == CUTE ? 4.5f : kind == GOTHIC ? 5 : 7;
            speed = kind == CUTE ? 230 : kind == GOTHIC ? 340 : 300;
            glide = kind == GOTHIC;
        }
    }

    private final Random rnd = new Random();
    private final Paint art = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint feet = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final List<Bat> bats = new ArrayList<>();
    private final Perch[] perches = new Perch[8];
    private Bitmap garland, sign, grass;
    private float k = 1f, dw = 1280f, clock, nextRound, adjustIn;
    private int targetFly, targetRest;
    private long lastFrame;
    private boolean running;

    HalloweenLayer(Context context) {
        super(context);
        Resources res = getResources();
        garland = load(res, R.drawable.halloween_garland);
        sign = load(res, R.drawable.halloween_sign);
        grass = load(res, R.drawable.halloween_grass);
        ink.setColor(INK);
        feet.setColor(INK);
        feet.setStyle(Paint.Style.STROKE);
        feet.setStrokeWidth(1.4f);
        zPaint.setColor(Color.rgb(239, 232, 220));
        zPaint.setFakeBoldText(true);
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
        // four spots under each garland's branch (bat's feet), mirrored on the right
        float[][] left = {{45, 112}, {100, 116}, {160, 120}, {220, 118}};
        for (int i = 0; i < 4; i++) {
            perches[i] = new Perch(left[i][0], left[i][1]);
            perches[i + 4] = new Perch(dw - left[i][0], left[i][1]);
        }
        running = true;
        lastFrame = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
    }

    // ---------- rounds and counts ----------

    private float rnd(float a, float b) { return a + rnd.nextFloat() * (b - a); }
    private int rndInt(int a, int b) { return a + rnd.nextInt(b - a + 1); }

    private void startRound() {
        for (int tries = 0; tries < 100; tries++) {
            int f = rndInt(FLY_MIN, FLY_MAX), rr = rndInt(REST_MIN, REST_MAX);
            if (f + rr > MAX_BATS) continue;
            String key = f + " flying + " + rr + " resting";
            if (recentRounds.contains(key)) continue;
            targetFly = f;
            targetRest = rr;
            recentRounds.add(0, key);
            while (recentRounds.size() > NO_REPEAT) recentRounds.remove(recentRounds.size() - 1);
            break;
        }
        nextRound = clock + rnd(ROUND_MIN, ROUND_MAX);
    }

    private boolean active(Bat b) { return b.state != EXIT && b.state != GONE; }

    private int countRole(boolean flyers) {
        int n = 0;
        for (Bat b : bats) if (b.flyer == flyers && active(b)) n++;
        return n;
    }

    /** Bats on screen per side: [left, right]. */
    private int[] sides() {
        int[] s = new int[2];
        for (Bat b : bats) {
            if (b.state == GONE || b.x < 0 || b.x > dw) continue;
            s[b.x < dw / 2 ? 0 : 1]++;
        }
        return s;
    }

    /** Which side a newcomer should use so the two sides end up uneven; the emptier side when both work. */
    private boolean pickLeft(boolean leftAllowed, boolean rightAllowed) {
        int[] s = sides();
        boolean leftOk = leftAllowed && s[0] + 1 != s[1], rightOk = rightAllowed && s[1] + 1 != s[0];
        if (leftOk && rightOk) return s[0] == s[1] ? rnd.nextBoolean() : s[0] < s[1];
        if (leftOk) return true;
        if (rightOk) return false;
        return leftAllowed;
    }

    /** Would moving this bat to the other side keep the sides uneven and within 3 of each other? */
    private boolean switchKeepsBalance(Bat b) {
        int[] s = sides();
        int from = b.x < dw / 2 ? 0 : 1;
        if (b.x >= 0 && b.x <= dw) s[from]--;
        s[1 - from]++;
        return s[0] != s[1] && Math.abs(s[0] - s[1]) <= 3;
    }

    /** A bat whose leaving keeps the sides uneven, if there is one (random among those). */
    private Bat pickLeaver(boolean flyers, int... states) {
        int[] s = sides();
        List<Bat> good = new ArrayList<>(), any = new ArrayList<>();
        for (Bat b : bats) {
            if (b.flyer != flyers) continue;
            boolean match = false;
            for (int st : states) if (b.state == st) { match = true; break; }
            if (!match) continue;
            any.add(b);
            int side = b.x < dw / 2 ? 0 : 1;
            int l = s[0] - (side == 0 ? 1 : 0), rr = s[1] - (side == 1 ? 1 : 0);
            if (l != rr || l < 2) good.add(b);
        }
        List<Bat> from = good.isEmpty() ? any : good;
        return from.isEmpty() ? null : from.get(rnd.nextInt(from.size()));
    }

    /** One step toward the round's counts every few seconds: leavers first, so the total never passes 8. */
    private void adjust() {
        int flyers = countRole(true), resters = countRole(false), total = 0;
        for (Bat b : bats) if (b.state != GONE) total++;      // bats still flying out count too
        if (flyers > targetFly) {
            Bat b = pickLeaver(true, FLY);
            if (b != null) exit(b);
        } else if (resters > targetRest) {
            Bat b = pickLeaver(false, SLEEP, HANG);
            if (b != null) exit(b);
        } else if (flyers < targetFly && total < MAX_BATS) {
            Bat b = new Bat(true, CUTE);
            boolean left = pickLeft(true, true);
            enter(b, left);
            b.state = FLY;
            pickTarget(b, left);
            bats.add(b);
        } else if (resters < targetRest && total < MAX_BATS) {
            boolean leftFree = freePerch(true) != null, rightFree = freePerch(false) != null;
            if (!leftFree && !rightFree) return;
            boolean left = pickLeft(leftFree, rightFree);
            Bat b = new Bat(false, RESTING_KINDS[rnd.nextInt(RESTING_KINDS.length)]);
            enter(b, left);
            claim(b, freePerch(left));
            bats.add(b);
        }
    }

    /** Starts just past the outer edge of a side. */
    private void enter(Bat b, boolean left) {
        b.x = left ? -60 : dw + 60;
        b.y = rnd(60, 380);
        b.t = rnd(0, 10);
        b.timer = rnd(15, 30);
    }

    /** Leaves through the outer edge of its own side (dropping off its perch first if it was resting). */
    private void exit(Bat b) {
        boolean perched = b.state == HANG || b.state == SLEEP;
        release(b);
        b.state = EXIT;
        b.tx = b.x < dw / 2 ? -120 : dw + 120;
        b.ty = rnd(60, 300);
        if (perched) b.vy = 140;
    }

    private Perch freePerch(boolean left) {
        List<Perch> open = new ArrayList<>();
        for (Perch p : perches) if (p != null && p.owner == null && (p.x < dw / 2) == left) open.add(p);
        return open.isEmpty() ? null : open.get(rnd.nextInt(open.size()));
    }

    private void claim(Bat b, Perch p) {
        b.perch = p;
        p.owner = b;
        b.tx = p.x;
        b.ty = p.y;
        b.state = TO_PERCH;
    }

    private static void release(Bat b) {
        if (b.perch != null) { b.perch.owner = null; b.perch = null; }
    }

    /** If the sides are even, one flying bat switches sides Pac-Man style (one at a time). */
    private void keepSidesUneven() {
        for (Bat b : bats) if (b.state == WRAP) return;
        int[] s = sides();
        if (s[0] != s[1] || s[0] < 2) return;
        Bat b = null;                                   // the flying bat nearest its outer edge fixes it fastest
        float best = Float.MAX_VALUE;
        for (Bat c : bats) {
            if (!c.flyer || c.state != FLY || c.x < 0 || c.x > dw) continue;
            float edge = Math.min(c.x, dw - c.x);
            if (edge < best) { best = edge; b = c; }
        }
        if (b == null) return;
        b.state = WRAP;
        b.tx = b.x < dw / 2 ? -80 : dw + 80;   // out through the outer edge of its side
        b.ty = b.y + rnd(-60, 60);
    }

    // ---------- movement ----------

    // Flying targets live in the side strips; now and then a bat crosses over the photo instead.
    private void pickTarget(Bat b, boolean left) {
        b.tx = left ? rnd(30, BAND - 40) : rnd(dw - BAND + 40, dw - 30);
        b.ty = rnd(40, 470);                          // stays above the sign and the logo
    }

    private float steer(Bat b, float dt, boolean arrive) {
        float dx = b.tx - b.x, dy = b.ty - b.y, d = (float) Math.hypot(dx, dy);
        if (d < 0.001f) d = 0.001f;
        float sp = b.speed;
        if (b.x > BAND && b.x < dw - BAND) sp *= 1.5f;  // over the nail photo: hurry across
        if (b.state == WRAP) sp *= 1.6f;                // switching sides: be quick about it
        if (arrive && d < 140) sp *= Math.max(0.15f, d / 140);
        float kk = Math.min(1, dt * 2.2f);
        b.vx += (dx / d * sp - b.vx) * kk;
        b.vy += (dy / d * sp - b.vy) * kk;
        b.x += b.vx * dt;
        b.y += b.vy * dt;
        return d;
    }

    private void update(Bat b, float dt) {
        b.t += dt;
        b.timer -= dt;
        switch (b.state) {
            case FLY:
                if (steer(b, dt, false) < 40) {
                    boolean onLeft = b.x < dw / 2;
                    float r = rnd.nextFloat();
                    boolean canSwitch = switchKeepsBalance(b);
                    if (r < 0.012f && canSwitch) pickTarget(b, !onLeft);          // rare crossover over the photo
                    else if (r < 0.03f && canSwitch) {                            // side switch the Pac-Man way
                        b.state = WRAP;
                        b.tx = onLeft ? -80 : dw + 80;
                        b.ty = b.y + rnd(-60, 60);
                    } else pickTarget(b, onLeft);
                }
                break;
            case WRAP:
                if (b.x < -60 || b.x > dw + 60) {                     // off one edge: come back from the other
                    boolean nowLeft = b.x > dw;
                    b.x = nowLeft ? -60 : dw + 60;
                    b.state = FLY;
                    pickTarget(b, nowLeft);
                } else steer(b, dt, false);
                break;
            case TO_PERCH:
                if (steer(b, dt, true) < 4) { b.x = b.perch.x; b.y = b.perch.y; b.vx = b.vy = 0; b.state = HANG; b.timer = rnd(6, 14); }
                break;
            case HANG:
                if (b.timer <= 0) { b.state = SLEEP; b.timer = rnd(40, 120); }
                break;
            case SLEEP:
                if (b.timer <= 0) {
                    Perch other = freePerch(b.x < dw / 2);
                    if (other != null && rnd.nextBoolean()) {         // wake up and move to another spot on the same side
                        release(b);
                        b.vy = 100;
                        claim(b, other);
                    } else { b.state = HANG; b.timer = rnd(6, 14); }
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
        adjustIn -= dt;
        if (adjustIn <= 0) { adjust(); adjustIn = rnd(1.5f, 3); }
        for (Iterator<Bat> it = bats.iterator(); it.hasNext(); ) {
            Bat b = it.next();
            update(b, dt);
            if (b.state == GONE) it.remove();
        }
        keepSidesUneven();

        canvas.save();
        canvas.scale(k, k);
        drawDecorations(canvas);
        for (Bat b : bats) {
            if (b.state == HANG || b.state == SLEEP) {
                drawHanging(canvas, b, b.state == SLEEP);
                if (b.state == SLEEP) drawZzz(canvas, b);
            } else {
                drawFlying(canvas, b);
            }
        }
        canvas.restore();
        postInvalidateDelayed(33);                     // ~30 fps; there are always bats now
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
        if (b.glide && b.t % 2.6f > 1.0f) return 0.15f + (float) Math.sin(b.t * 2) * 0.05f;
        return (float) Math.sin(b.t * Math.PI * 2 * b.flapHz);
    }

    private void drawFlying(Canvas c, Bat b) {
        float f = flapOf(b);
        c.save();
        c.translate(b.x, b.y + (float) Math.sin(b.t * 3) * 5);
        c.scale(S, S);
        c.rotate((float) Math.toDegrees(Math.max(-0.35f, Math.min(0.35f, b.vx / 1200))));
        switch (b.kind) {
            case CLASSIC: classic(c, f); break;
            case EYES: classic(c, f); eyes(c, -8.5f, 1.2f, Color.WHITE, 1.7f); break;
            case CUTE: cute(c, f); break;
            case GOTHIC: gothic(c, f); break;
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

    private void eyes(Canvas c, float y, float radius, int color, float spread) {
        int old = ink.getColor();
        ink.setColor(color);
        c.drawCircle(-spread, y, radius, ink);
        c.drawCircle(spread, y, radius, ink);
        ink.setColor(old);
    }

    /** Hanging upside down, wings wrapped. The only motion is a slow sway on one sine wave, so the loop is seamless. */
    private void drawHanging(Canvas c, Bat b, boolean asleep) {
        c.save();
        c.translate(b.x, b.y);
        c.scale(S, S);
        c.rotate((float) Math.toDegrees(Math.sin(b.t * 1.3) * 0.06));
        c.drawLine(-2, 0, -2.5f, 5, feet);
        c.drawLine(2, 0, 2.5f, 5, feet);
        c.drawOval(-7, 3, 7, 27, ink);
        float hy = 28;
        c.drawCircle(0, hy, 4.2f, ink);
        path.reset();
        path.moveTo(-3.5f, hy + 1); path.lineTo(-3.5f, hy + 8); path.lineTo(-0.8f, hy + 3); path.close();
        path.moveTo(3.5f, hy + 1); path.lineTo(3.5f, hy + 8); path.lineTo(0.8f, hy + 3); path.close();
        c.drawPath(path, ink);
        if (!asleep && b.kind == EYES) eyes(c, hy + 0.5f, 1.1f, Color.WHITE, 1.7f);
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
