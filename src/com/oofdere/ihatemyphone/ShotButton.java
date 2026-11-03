package com.oofdere.ihatemyphone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Round button with a "screenshot" viewfinder glyph. */
final class ShotButton extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    ShotButton(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        paint.setColor(0xFFE8EAED);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setClickable(true);
        setFocusable(true);
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = density;
        float size = 24 * s;
        float x = (getWidth() - size) / 2f, y = (getHeight() - size) / 2f;
        float a = 4 * s, b = 20 * s, l = 5 * s;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * s);
        // Four corner brackets.
        c.drawLine(x + a, y + a, x + a + l, y + a, paint);
        c.drawLine(x + a, y + a, x + a, y + a + l, paint);
        c.drawLine(x + b, y + a, x + b - l, y + a, paint);
        c.drawLine(x + b, y + a, x + b, y + a + l, paint);
        c.drawLine(x + a, y + b, x + a + l, y + b, paint);
        c.drawLine(x + a, y + b, x + a, y + b - l, paint);
        c.drawLine(x + b, y + b, x + b - l, y + b, paint);
        c.drawLine(x + b, y + b, x + b, y + b - l, paint);

        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(x + 12 * s, y + 12 * s, 3 * s, paint);
    }
}
