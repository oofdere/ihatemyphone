package com.oofdere.ihatemyphone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.Locale;

/**
 * Vertical volume pill. Dragging is relative: put your finger anywhere (on
 * the pill or, via {@link #handleTouch}, anywhere on the screen) and slide;
 * the level moves from where it already is (no jumping).
 */
final class SliderView extends View {
    interface Listener {
        void onTouchStart();

        /** @return the level that was actually applied */
        float onDrag(float level);

        /**
         * @param moved      the finger actually dragged
         * @param background the touch started outside the pill (anywhere on screen)
         */
        void onRelease(boolean moved, boolean background);
    }

    /** Light haptic "texture" ticks across the full range. */
    private static final int DETENTS = 40;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Path icon = new Path();
    private final RectF rect = new RectF();
    private final float density;
    private final int touchSlop;

    private Listener listener;
    private float min = 0f, max = 15f, level = 0f;
    private boolean fine;

    private float downX, downY, startLevel;
    private boolean moved;
    private boolean background;
    private int lastDetent = -1;

    SliderView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        trackPaint.setColor(0xFF3C4043);
        fillPaint.setColor(0xFFE8EAED);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_SP, 13, context.getResources().getDisplayMetrics()));
        textPaint.setFakeBoldText(true);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        setHapticFeedbackEnabled(true);
        setContentDescription("Volume");
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setRange(int min, int max, boolean fine) {
        this.min = min;
        this.max = Math.max(max, 1);
        this.fine = fine;
        invalidate();
    }

    void setLevel(float level) {
        this.level = level;
        invalidate();
    }

    private float fraction() {
        float f = level / max;
        return f < 0f ? 0f : (f > 1f ? 1f : f);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return handleTouch(e, false);
    }

    /**
     * Drives the slider from any view's touch stream. Only deltas are used, so
     * the coordinate space of {@code e} doesn't matter.
     */
    boolean handleTouch(MotionEvent e, boolean fromBackground) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                downX = e.getX();
                downY = e.getY();
                startLevel = level;
                moved = false;
                background = fromBackground;
                lastDetent = (int) (fraction() * DETENTS);
                if (listener != null) listener.onTouchStart();
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX;
                float dy = downY - e.getY();
                if (!moved) {
                    if (Math.hypot(dx, dy) < touchSlop) return true;
                    moved = true;
                    // Start the volume drag from here so the slop doesn't cause a jump.
                    downY = e.getY();
                    dy = 0f;
                }
                float target = startLevel + dy / getHeight() * max;
                // Re-anchor at the ends so reversing direction responds instantly.
                if (target < min) {
                    target = min;
                    startLevel = min;
                    downY = e.getY();
                } else if (target > max) {
                    target = max;
                    startLevel = max;
                    downY = e.getY();
                }
                level = listener != null ? listener.onDrag(target) : target;
                tick();
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (listener != null) listener.onRelease(moved, background);
                return true;
        }
        return true;
    }

    private void tick() {
        float f = fraction();
        int detent = (int) (f * DETENTS);
        if (detent == lastDetent) return;
        lastDetent = detent;
        boolean edge = f <= 0.0001f || f >= 0.9999f;
        performHapticFeedback(edge ? HapticFeedbackConstants.CLOCK_TICK
                : HapticFeedbackConstants.TEXT_HANDLE_MOVE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float r = w / 2f;
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, r, r, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clip);
        canvas.drawRect(rect, trackPaint);
        float f = fraction();
        float fillTop = h - f * h;
        canvas.drawRect(0, fillTop, w, h, fillPaint);
        canvas.restore();

        // Percentage at the top.
        float pct = f * 100f;
        String text = fine ? String.format(Locale.US, "%.1f", pct) : String.valueOf(Math.round(pct));
        float textY = 14 * density + textPaint.getTextSize();
        textPaint.setColor(fillTop < textY - textPaint.getTextSize() / 2f ? 0xFF202124 : 0xFFE8EAED);
        canvas.drawText(text, w / 2f, textY, textPaint);

        // Speaker icon at the bottom.
        float size = 24 * density;
        float left = (w - size) / 2f;
        float top = h - size - 14 * density;
        boolean covered = fillTop < top + size / 2f;
        iconPaint.setColor(covered ? 0xFF202124 : 0xFFE8EAED);
        drawSpeaker(canvas, left, top, size / 24f, f);
    }

    private void drawSpeaker(Canvas c, float x, float y, float s, float f) {
        icon.reset();
        icon.moveTo(x + 3 * s, y + 9 * s);
        icon.lineTo(x + 7 * s, y + 9 * s);
        icon.lineTo(x + 12 * s, y + 4.5f * s);
        icon.lineTo(x + 12 * s, y + 19.5f * s);
        icon.lineTo(x + 7 * s, y + 15 * s);
        icon.lineTo(x + 3 * s, y + 15 * s);
        icon.close();
        iconPaint.setStyle(Paint.Style.FILL);
        c.drawPath(icon, iconPaint);

        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(2 * s);
        if (f <= 0.0001f) {
            c.drawLine(x + 15 * s, y + 9 * s, x + 21 * s, y + 15 * s, iconPaint);
            c.drawLine(x + 21 * s, y + 9 * s, x + 15 * s, y + 15 * s, iconPaint);
            return;
        }
        rect.set(x + 9 * s, y + 8 * s, x + 17 * s, y + 16 * s);
        c.drawArc(rect, -50, 100, false, iconPaint);
        if (f > 0.5f) {
            rect.set(x + 6 * s, y + 4 * s, x + 22 * s, y + 20 * s);
            c.drawArc(rect, -50, 100, false, iconPaint);
        }
    }
}
