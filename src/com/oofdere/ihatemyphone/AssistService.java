package com.oofdere.ihatemyphone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.service.voice.VoiceInteractionService;

/**
 * The always-running half of the assistant. Android keeps this bound while the
 * app is the default digital assistant, which also keeps the fine-volume
 * effect alive between invocations.
 */
public class AssistService extends VoiceInteractionService {
    private static final String VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    private static final String EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE";
    private static final String EXTRA_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE";

    /** Ask for the screenshot too, same as a normal assistant gesture. */
    private static final int SHOW_FLAGS = android.service.voice.VoiceInteractionSession.SHOW_WITH_ASSIST
            | android.service.voice.VoiceInteractionSession.SHOW_WITH_SCREENSHOT;

    static volatile AssistService instance;

    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getIntExtra(EXTRA_STREAM, -1) != AudioManager.STREAM_MUSIC) return;
            int value = intent.getIntExtra(EXTRA_VALUE, -1);
            if (value >= 0) Volume.get(context).onExternalMediaChange(value);
        }
    };
    private boolean receiverRegistered;

    @Override
    public void onReady() {
        super.onReady();
        instance = this;
        Volume.get(this).restore();
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(VOLUME_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                // Sent by the system, which can still reach non-exported receivers.
                registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(volumeReceiver, filter);
            }
            receiverRegistered = true;
        }
    }

    @Override
    public void onShutdown() {
        if (receiverRegistered) {
            unregisterReceiver(volumeReceiver);
            receiverRegistered = false;
        }
        if (instance == this) instance = null;
        super.onShutdown();
    }

    /** The lock screen's "voice assist" shortcut. */
    @Override
    public void onLaunchVoiceAssistFromKeyguard() {
        showSession(new Bundle(), SHOW_FLAGS);
    }

    /** Used by the setup screen's "Try it" button. */
    static boolean showPanel() {
        AssistService s = instance;
        if (s == null) return false;
        s.showSession(new Bundle(), SHOW_FLAGS);
        return true;
    }
}
