package com.oofdere.ihatemyphone;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Launcher screen: setup checklist + a test button. */
public class SetupActivity extends Activity {
    private TextView assistantStatus;
    private TextView screenshotStatus;
    private TextView fineStatus;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        col.setPadding(pad, pad, pad, pad);
        scroll.addView(col);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            // Deprecated getters on purpose: they also work on Android 10 (minSdk 29).
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);

        title(col, "I Hate My Phone");
        body(col, "Volume + screenshot panel for phones with dead buttons. Invoke your assistant, "
                + "drag the pill, let go, and it disappears.");

        // 1. Assistant
        heading(col, "1. Make this your digital assistant");
        assistantStatus = body(col, "");
        button(col, "Open assistant settings", v -> openAssistantSettings());
        body(col, "Then open it by: swiping diagonally up from a bottom corner (gesture navigation), "
                + "long-pressing Home (3-button navigation), or setting "
                + "\"Press and hold power button → Digital assistant\" (usually under Settings → System → Gestures). "
                + "These work from the lock screen too.");

        // 2. Screenshots
        heading(col, "2. Screenshots");
        screenshotStatus = body(col, "");
        body(col, "Best: turn on the \"" + getString(R.string.a11y_label) + "\" accessibility service. "
                + "It listens to nothing and can't read your screen; it only presses the system's own "
                + "screenshot action. On Android 13+, if the switch is greyed out, open App info → ⋮ → "
                + "Allow restricted settings first.");
        button(col, "Open accessibility settings", v -> open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button(col, "Open app info", v -> open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        body(col, "No accessibility? It falls back to the screenshot Android gives assistants. For that, in "
                + "the assistant settings from step 1 turn on \"Use text from screen\" and \"Use screenshot\". "
                + "Those get saved to Pictures/Screenshots.");

        // 3. Fine volume
        heading(col, "3. Extra-fine media volume");
        Switch fine = new Switch(this);
        fine.setText("Sub-step media volume");
        fine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        fine.setChecked(Volume.get(this).fineEnabled());
        fine.setOnCheckedChangeListener((b, on) -> {
            Volume.get(this).setFineEnabled(on);
            refresh();
        });
        col.addView(fine, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        fineStatus = body(col, "");
        body(col, "Android only has ~15-25 media steps. With this on, the space between steps is filled "
                + "in with a global audio trim, so the slider is basically continuous. The trim can also make "
                + "notification sounds slightly quieter (by up to one step) while it's active, and some phones "
                + "refuse global audio effects, in which case it quietly falls back to normal steps.");

        // 4. Try
        heading(col, "4. Try it");
        button(col, "Show the panel", v -> {
            if (!AssistService.showPanel()) {
                Toast.makeText(this, "Set this app as the digital assistant first (step 1).",
                        Toast.LENGTH_LONG).show();
            }
        });
        body(col, "Tap MEDIA at the top of the panel to switch to ring, alarm or call volume. "
                + "During a call it starts on call volume.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        RoleManager rm = getSystemService(RoleManager.class);
        boolean assistant = rm != null && rm.isRoleHeld(RoleManager.ROLE_ASSISTANT);
        assistantStatus.setText(assistant ? "✅ This app is your assistant." : "❌ Not set as assistant yet.");

        boolean a11y = accessibilityEnabled();
        screenshotStatus.setText(a11y ? "✅ System screenshots enabled."
                : "⚠️ Accessibility service off (using fallback).");

        Volume vol = Volume.get(this);
        Boolean works = vol.trimWorks();
        String s;
        if (!vol.fineEnabled()) s = "Off: normal steps.";
        else if (works == null) s = "On. (Not used yet: drag the media slider once to test it.)";
        else if (works) s = "✅ On and working.";
        else s = "❌ This phone refused the global audio trim, using normal steps.";
        fineStatus.setText(s);
    }

    private boolean accessibilityEnabled() {
        if (ScreenshotService.instance != null) return true;
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName me = new ComponentName(this, ScreenshotService.class);
        for (String part : enabled.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(part);
            if (me.equals(cn)) return true;
        }
        return false;
    }

    private void openAssistantSettings() {
        if (open(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))) return;
        if (open(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) return;
        open(new Intent(Settings.ACTION_SETTINGS));
    }

    private boolean open(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    private void title(LinearLayout col, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        col.addView(t);
    }

    private void heading(LinearLayout col, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(24), 0, dp(4));
        col.addView(t);
    }

    private TextView body(LinearLayout col, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setPadding(0, dp(4), 0, dp(4));
        col.addView(t);
        return t;
    }

    private void button(LinearLayout col, String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        col.addView(b, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
