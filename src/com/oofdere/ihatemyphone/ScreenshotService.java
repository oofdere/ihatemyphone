package com.oofdere.ihatemyphone;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * Optional. Subscribes to no events and can't read window content; it only
 * exists so the panel can trigger the system's own screenshot.
 */
public class ScreenshotService extends AccessibilityService {
    static volatile ScreenshotService instance;

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        if (instance == this) instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    static boolean takeScreenshot() {
        ScreenshotService s = instance;
        return s != null && s.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
