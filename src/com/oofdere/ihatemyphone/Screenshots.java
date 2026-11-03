package com.oofdere.ihatemyphone;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Saves the screenshot Android hands to the assistant (captured the instant
 * the assistant was invoked, before the panel appeared) into Pictures/Screenshots.
 */
final class Screenshots {
    private Screenshots() {
    }

    /** Remembers whether Android handed us a screenshot, for the setup screen. */
    static void recordAvailable(Context context, boolean available) {
        context.getSharedPreferences("screenshots", Context.MODE_PRIVATE).edit()
                .putInt("last", available ? 1 : 0).apply();
    }

    /** null = never opened yet. */
    static Boolean lastAvailable(Context context) {
        int v = context.getSharedPreferences("screenshots", Context.MODE_PRIVATE).getInt("last", -1);
        return v < 0 ? null : v == 1;
    }

    static void save(Context context, Bitmap shot) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            boolean ok = write(app, shot);
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(app,
                    ok ? "Screenshot saved to Pictures/Screenshots" : "Couldn't save screenshot",
                    Toast.LENGTH_SHORT).show());
        }, "save-screenshot").start();
    }

    private static boolean write(Context context, Bitmap shot) {
        Bitmap bmp = shot;
        if (bmp.getConfig() == Bitmap.Config.HARDWARE) bmp = bmp.copy(Bitmap.Config.ARGB_8888, false);
        if (bmp == null) return false;

        String name = "Screenshot_" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".png";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Screenshots");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        ContentResolver cr = context.getContentResolver();
        Uri uri = null;
        try {
            uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return false;
            try (OutputStream out = cr.openOutputStream(uri)) {
                if (out == null || !bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw new java.io.IOException("compress failed");
                }
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            cr.update(uri, done, null, null);
            return true;
        } catch (Exception e) {
            if (uri != null) {
                try { cr.delete(uri, null, null); } catch (Exception ignored) { }
            }
            return false;
        }
    }
}
