package com.oofdere.ihatemyphone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.voice.VoiceInteractionSession;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The thing that pops up when you invoke the assistant: a slim volume pill on
 * the right edge plus a screenshot button, over a fully transparent window.
 * Swiping up/down anywhere on the screen changes the volume; letting go (or
 * tapping) makes it vanish so the phone is back to normal.
 */
public class PanelSession extends VoiceInteractionSession {
    private static final long IDLE_HIDE_MS = 4000;
    private static final long SCREENSHOT_DELAY_MS = 500;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Runnable idleHide = this::hide;

    private Volume volume;
    private int stream;
    private Bitmap assistShot;

    private LinearLayout panel;
    private TextView streamButton;
    private SliderView slider;

    PanelSession(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        volume = Volume.get(getContext());
        Window w = getWindow().getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    @Override
    public View onCreateContentView() {
        Context c = getContext();
        FrameLayout root = new FrameLayout(c);

        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(8);
        panel.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0202124);
        bg.setCornerRadius(dp(36));
        panel.setBackground(bg);
        panel.setElevation(dp(8));

        streamButton = new TextView(c);
        streamButton.setTextColor(0xFFE8EAED);
        streamButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        streamButton.setLetterSpacing(0.08f);
        streamButton.setGravity(Gravity.CENTER);
        streamButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        streamButton.setBackground(ripple(dp(20)));
        streamButton.setOnClickListener(v -> cycleStream());
        panel.addView(streamButton, new LinearLayout.LayoutParams(dp(56), dp(40)));

        int screenH = c.getResources().getDisplayMetrics().heightPixels;
        int sliderH = Math.min(dp(380), (int) (screenH * 0.45f));
        slider = new SliderView(c);
        slider.setListener(new SliderView.Listener() {
            @Override
            public void onTouchStart() {
                MAIN.removeCallbacks(idleHide);
            }

            @Override
            public float onDrag(float level) {
                return volume.setLevel(stream, level);
            }

            @Override
            public void onRelease(boolean moved, boolean background) {
                volume.save();
                if (moved || background) {
                    hide();
                } else {
                    scheduleIdleHide();
                }
            }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(56), sliderH);
        sp.topMargin = dp(4);
        sp.bottomMargin = dp(8);
        panel.addView(slider, sp);

        ShotButton shot = new ShotButton(c);
        shot.setBackground(ripple(dp(28)));
        shot.setContentDescription("Take screenshot");
        shot.setOnClickListener(v -> takeScreenshot());
        panel.addView(shot, new LinearLayout.LayoutParams(dp(56), dp(56)));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        lp.setMarginEnd(dp(12));
        root.addView(panel, lp);

        // The whole (transparent) screen is a swipe surface for the slider.
        // Touches the buttons don't take land here.
        root.setOnTouchListener((v, e) -> slider.handleTouch(e, true));
        return root;
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        stream = volume.defaultStream();
        refresh();
        panel.setAlpha(0f);
        panel.setTranslationX(dp(24));
        panel.animate().alpha(1f).translationX(0f).setDuration(140).start();
        scheduleIdleHide();
    }

    @Override
    public void onHide() {
        MAIN.removeCallbacks(idleHide);
        if (volume != null) volume.save();
        assistShot = null;
        super.onHide();
    }

    @Override
    public void onDestroy() {
        MAIN.removeCallbacks(idleHide);
        assistShot = null;
        super.onDestroy();
    }

    /** Android passes the assistant a screenshot of whatever was on screen when it was invoked. */
    @Override
    public void onHandleScreenshot(Bitmap screenshot) {
        assistShot = screenshot;
    }

    /**
     * The whole screen catches touches while the panel is up (so you can swipe
     * anywhere), but content insets cover everything so the app below is never
     * resized or pushed around.
     */
    @Override
    public void onComputeInsets(Insets outInsets) {
        View decor = getWindow().getWindow().getDecorView();
        outInsets.contentInsets.set(0, decor.getHeight(), 0, 0);
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_FRAME;
        outInsets.touchableRegion.setEmpty();
    }

    private void refresh() {
        streamButton.setText(Volume.label(stream));
        slider.setRange(volume.min(stream), volume.max(stream), volume.isFine(stream));
        slider.setLevel(volume.level(stream));
    }

    private void cycleStream() {
        int next = 0;
        for (int i = 0; i < Volume.STREAMS.length; i++) {
            if (Volume.STREAMS[i] == stream) next = (i + 1) % Volume.STREAMS.length;
        }
        stream = Volume.STREAMS[next];
        refresh();
        scheduleIdleHide();
    }

    private void scheduleIdleHide() {
        MAIN.removeCallbacks(idleHide);
        MAIN.postDelayed(idleHide, IDLE_HIDE_MS);
    }

    private void takeScreenshot() {
        final Bitmap shot = assistShot;
        final Context app = getContext().getApplicationContext();
        hide();
        if (ScreenshotService.instance != null) {
            // Wait for the panel to leave the screen so it isn't in the picture.
            MAIN.postDelayed(() -> {
                if (!ScreenshotService.takeScreenshot()) fallbackScreenshot(app, shot);
            }, SCREENSHOT_DELAY_MS);
        } else {
            fallbackScreenshot(app, shot);
        }
    }

    private static void fallbackScreenshot(Context app, Bitmap shot) {
        if (shot != null) {
            Screenshots.save(app, shot);
        } else {
            Toast.makeText(app, "Screenshots aren't set up yet. Open I Hate My Phone for the 30-second fix.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private RippleDrawable ripple(int radius) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radius);
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, mask);
    }

    private int dp(float v) {
        return Math.round(v * getContext().getResources().getDisplayMetrics().density);
    }
}
