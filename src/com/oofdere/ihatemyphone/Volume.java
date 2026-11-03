package com.oofdere.ihatemyphone;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.DynamicsProcessing;

/**
 * Volume control with (way) more steps than Android gives you.
 *
 * Android only exposes integer volume indexes per stream (usually 15-25 for
 * media). For media we treat the volume as a continuous "level" between 0 and
 * max. The system index is set to ceil(level), and the leftover fraction of a
 * step is made up by attenuating the global output mix with a
 * DynamicsProcessing effect on audio session 0. The size of each step in dB is
 * read from the device's real volume curve, so the in-between positions are
 * smooth instead of guessed.
 *
 * If the device refuses the global effect, everything still works, it just
 * falls back to the normal integer steps.
 */
final class Volume {
    static final int[] STREAMS = {
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_VOICE_CALL,
    };

    /** How far below step 1 the bottom of the slider goes, in dB, when the curve is unknown. */
    private static final float BOTTOM_SPAN_DB = 30f;
    /** Guess for a step's size, in dB, when the device won't tell us. */
    private static final float DEFAULT_STEP_DB = 2.5f;
    private static final float MAX_TRIM_DB = -45f;

    private static Volume instance;

    private final AudioManager am;
    private final SharedPreferences prefs;

    private DynamicsProcessing trimEffect;
    private boolean trimBroken;
    private float currentTrimDb;

    /** Fine media level we last set, and the system index it maps to. */
    private float fineLevel = -1f;
    private int fineIndex = -1;

    static synchronized Volume get(Context context) {
        if (instance == null) instance = new Volume(context.getApplicationContext());
        return instance;
    }

    private Volume(Context context) {
        am = context.getSystemService(AudioManager.class);
        prefs = context.getSharedPreferences("volume", Context.MODE_PRIVATE);
        fineLevel = prefs.getFloat("fine_level", -1f);
        fineIndex = prefs.getInt("fine_index", -1);
    }

    static String label(int stream) {
        switch (stream) {
            case AudioManager.STREAM_RING: return "RING";
            case AudioManager.STREAM_ALARM: return "ALARM";
            case AudioManager.STREAM_VOICE_CALL: return "CALL";
            default: return "MEDIA";
        }
    }

    /** The stream that the hardware buttons would most likely control right now. */
    int defaultStream() {
        int mode = am.getMode();
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
            return AudioManager.STREAM_VOICE_CALL;
        }
        return AudioManager.STREAM_MUSIC;
    }

    boolean fineEnabled() {
        return prefs.getBoolean("fine_enabled", true);
    }

    void setFineEnabled(boolean enabled) {
        prefs.edit().putBoolean("fine_enabled", enabled).apply();
        if (!enabled) {
            applyTrim(0f);
            forgetFine();
        }
    }

    /** True if sub-step volume is actually in effect for this stream. */
    boolean isFine(int stream) {
        return stream == AudioManager.STREAM_MUSIC && fineEnabled() && !trimBroken;
    }

    /** null = not tried yet, true = works, false = device refused it. */
    Boolean trimWorks() {
        if (trimBroken) return false;
        return trimEffect != null ? Boolean.TRUE : null;
    }

    int min(int stream) {
        return am.getStreamMinVolume(stream);
    }

    int max(int stream) {
        return am.getStreamMaxVolume(stream);
    }

    /** Current level as a float in [min, max]. */
    float level(int stream) {
        int index = am.getStreamVolume(stream);
        if (isFine(stream) && fineIndex == index && fineLevel >= 0f
                && stepFor(fineLevel) == index) {
            return fineLevel;
        }
        return index;
    }

    /** Sets a continuous level in [min, max]. Returns the level actually applied. */
    float setLevel(int stream, float level) {
        float lo = min(stream), hi = max(stream);
        if (level < lo) level = lo;
        if (level > hi) level = hi;
        int index = stepFor(level);
        if (index < lo) index = (int) lo;

        boolean fine = isFine(stream);
        if (fine) {
            // Record before touching the system volume so our own
            // VOLUME_CHANGED broadcast isn't mistaken for an outside change.
            fineLevel = level;
            fineIndex = index;
        }

        if (am.getStreamVolume(stream) != index) {
            try {
                am.setStreamVolume(stream, index, 0);
            } catch (SecurityException e) {
                // Do Not Disturb blocks changing ring volume to/from 0.
                return am.getStreamVolume(stream);
            }
        }

        if (fine) {
            applyTrim(trimFor(index, level));
            if (trimBroken) return index; // fell back during applyTrim
            return level;
        }
        if (stream == AudioManager.STREAM_MUSIC) applyTrim(0f);
        return index;
    }

    /** Persist the fine level so it survives the panel closing / process dying. */
    void save() {
        prefs.edit().putFloat("fine_level", fineLevel).putInt("fine_index", fineIndex).apply();
    }

    /** Called at startup: re-apply the saved fine trim if the system volume still matches. */
    void restore() {
        if (!fineEnabled()) return;
        int index = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (fineIndex == index && fineLevel >= 0f && stepFor(fineLevel) == index) {
            applyTrim(trimFor(index, fineLevel));
        }
    }

    /** Media volume was changed by something other than us. Drop the trim. */
    void onExternalMediaChange(int newIndex) {
        if (newIndex == fineIndex) return;
        forgetFine();
        applyTrim(0f);
    }

    private void forgetFine() {
        fineLevel = -1f;
        fineIndex = -1;
        save();
    }

    private static int stepFor(float level) {
        return (int) Math.ceil(level - 1e-4f);
    }

    /** dB to subtract from step {@code index} so it sounds like {@code level}. */
    private float trimFor(int index, float level) {
        if (index <= 0) return 0f;
        float frac = level - (index - 1); // (0, 1]
        if (frac >= 0.9999f) return 0f;

        int device = outputDeviceType();
        float hi = db(index, device);
        float span;
        if (index >= 2) {
            span = hi - db(index - 1, device);
            if (!(span > 0.05f && span < 20f)) span = DEFAULT_STEP_DB;
        } else {
            span = BOTTOM_SPAN_DB;
        }
        float trim = -(1f - frac) * span;
        return Math.max(trim, MAX_TRIM_DB);
    }

    private float db(int index, int device) {
        try {
            return am.getStreamVolumeDb(AudioManager.STREAM_MUSIC, index, device);
        } catch (RuntimeException e) {
            return Float.NaN;
        }
    }

    private int outputDeviceType() {
        int best = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
        int bestRank = 0;
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int rank;
            switch (d.getType()) {
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                case AudioDeviceInfo.TYPE_BLE_HEADSET:
                case AudioDeviceInfo.TYPE_BLE_SPEAKER:
                    rank = 3; break;
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                    rank = 2; break;
                default:
                    rank = 0;
            }
            if (rank > bestRank) {
                bestRank = rank;
                best = d.getType();
            }
        }
        return best;
    }

    private void applyTrim(float db) {
        if (trimBroken) return;
        if (trimEffect == null) {
            if (Math.abs(db) < 0.01f) return;
            try {
                DynamicsProcessing.Config config = new DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        2,
                        false, 1,   // pre-EQ off
                        false, 1,   // multiband compressor off
                        false, 1,   // post-EQ off
                        false)      // limiter off
                        .build();
                // Session 0 = the global output mix.
                trimEffect = new DynamicsProcessing(Integer.MAX_VALUE, 0, config);
            } catch (RuntimeException e) {
                trimEffect = null;
                trimBroken = true;
                return;
            }
        }
        try {
            if (Math.abs(db - currentTrimDb) < 0.005f && trimEffect.getEnabled() == (db < -0.01f)) {
                return;
            }
            trimEffect.setInputGainAllChannelsTo(db);
            trimEffect.setEnabled(db < -0.01f);
            currentTrimDb = db;
        } catch (RuntimeException e) {
            try { trimEffect.release(); } catch (RuntimeException ignored) { }
            trimEffect = null;
            trimBroken = true;
        }
    }
}
