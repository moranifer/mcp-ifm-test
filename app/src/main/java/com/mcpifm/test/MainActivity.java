package com.mcpifm.test;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_PERMS = 7;
    private static final int REQ_CAPTURE = 42;
    private static final int BG = Color.parseColor("#0B0B0C");
    private static final int CARD = Color.parseColor("#17171A");
    private static final int TEXT = Color.parseColor("#F2F2F3");
    private static final int MUTED = Color.parseColor("#A6A6AD");
    private static final int ACCENT = Color.parseColor("#FF2B2B");

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView eqStatus;
    private TextView sessions;
    private TextView captureStatus;
    private TextView captureLevel;
    private TextView logView;
    private final List<Button> modeButtons = new ArrayList<>();

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            updateViews();
            ui.postDelayed(this, 300);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        setContentView(buildUi());
        askPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(refresh);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(refresh);
        super.onPause();
    }

    // ---------------------------------------------------------------- UI

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        root.setPadding(p, p, p, p * 2);
        scroll.addView(root);

        TextView brand = label("MCP-ifm", 26, TEXT, true);
        brand.setLetterSpacing(0.12f);
        root.addView(brand);
        root.addView(label("Feasibility test · does processing reach your car?", 14, MUTED, false));

        // ---- Test A
        LinearLayout a = card(root);
        a.addView(label("Test A · System EQ on other apps", 18, TEXT, true));
        a.addView(label("Uses Android's built-in effect on Spotify, YouTube Music and others. "
                + "Start it, play music, connect Android Auto, then switch the modes below.", 14, MUTED, false));
        LinearLayout rowA = row(a);
        rowA.addView(button("Start", v -> startEq(false)));
        rowA.addView(button("Start + global", v -> startEq(true)));
        rowA.addView(button("Stop", v -> stopService(new Intent(this, EqService.class))));
        a.addView(label("Mode", 13, MUTED, true));
        LinearLayout rowM = row(a);
        modeButtons.add(modeButton(rowM, "OFF", Engine.MODE_OFF));
        modeButtons.add(modeButton(rowM, "BASS TEST", Engine.MODE_BASS));
        modeButtons.add(modeButton(rowM, "LOUD +6", Engine.MODE_LOUD));
        LinearLayout rowX = row(a);
        rowX.addView(button("Re-attach now", v -> Engine.reattachGlobal("button")));
        rowX.addView(button("Battery: no limits", v -> askBatteryExemption()));
        eqStatus = label("", 14, TEXT, false);
        a.addView(eqStatus);
        sessions = label("", 14, TEXT, false);
        a.addView(sessions);

        // ---- Test B
        LinearLayout b = card(root);
        b.addView(label("Test B · Capture other apps' audio", 18, TEXT, true));
        b.addView(label("Checks whether each music app lets us take its audio for our own full DSP. "
                + "Android will ask to record the screen; this only measures sound and records nothing.", 14, MUTED, false));
        LinearLayout rowB = row(b);
        rowB.addView(button("Start capture test", v -> startCapture()));
        rowB.addView(button("Stop", v -> stopService(new Intent(this, CaptureService.class))));
        captureLevel = label("—", 34, TEXT, true);
        captureLevel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.addView(captureLevel);
        captureStatus = label("", 14, MUTED, false);
        b.addView(captureStatus);

        // ---- Report
        LinearLayout r = card(root);
        r.addView(label("Report", 18, TEXT, true));
        r.addView(label(deviceLine(), 14, MUTED, false));
        LinearLayout rowR = row(r);
        rowR.addView(button("Copy report", v -> copyReport()));
        rowR.addView(button("Clear effects", v -> Engine.releaseAll()));
        logView = label("", 12, MUTED, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        r.addView(logView);

        return scroll;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        c.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.parseColor("#2A2A2E"));
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16);
        parent.addView(c, lp);
        return c;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        parent.addView(r, lp);
        return r;
    }

    private TextView label(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        t.setPadding(0, dp(6), 0, 0);
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setOnClickListener(l);
        b.setMinHeight(dp(48));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.rightMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private Button modeButton(LinearLayout parent, String text, int mode) {
        Button b = button(text, v -> Engine.setMode(mode));
        b.setTag(mode);
        parent.addView(b);
        return b;
    }

    private void updateViews() {
        PowerManager pm = getSystemService(PowerManager.class);
        boolean free = pm.isIgnoringBatteryOptimizations(getPackageName());
        eqStatus.setText("Service: " + (Engine.eqServiceRunning ? "running" : "stopped")
                + "\nBattery limits: " + (free ? "off (good)" : "ON, press Battery: no limits")
                + "\nGlobal output: " + Engine.globalStatus
                + (Engine.globalWanted ? (Engine.globalHealthy() ? " · active" : " · INACTIVE") : "")
                + "\nAutomatic re-attaches: " + Engine.reattachCount
                + "\nMode: " + Engine.modeName(Engine.mode));
        sessions.setText("Sessions with the effect:\n" + Engine.sessionsText());
        for (Button b : modeButtons) {
            boolean on = ((Integer) b.getTag()) == Engine.mode;
            b.setTextColor(on ? ACCENT : TEXT);
        }
        if (Engine.captureRunning) {
            double db = Engine.captureDb;
            captureLevel.setText(db <= -90 ? "silence" : Math.round(db) + " dBFS");
            captureLevel.setTextColor(db > -60 ? Color.parseColor("#34C759") : TEXT);
        } else {
            captureLevel.setText("—");
            captureLevel.setTextColor(TEXT);
        }
        captureStatus.setText(Engine.captureStatus
                + (Engine.captureMaxDb > -120 ? "\nLoudest so far: " + Math.round(Engine.captureMaxDb) + " dBFS" : ""));
        logView.setText(Engine.logText());
    }

    // ---------------------------------------------------------------- actions

    private void startEq(boolean global) {
        Intent i = new Intent(this, EqService.class);
        i.putExtra(EqService.EXTRA_GLOBAL, global);
        startForegroundService(i);
    }

    private void askBatteryExemption() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "Open Settings › Apps › MCP-ifm Test › Battery › Unrestricted", Toast.LENGTH_LONG).show();
        }
    }

    private void startCapture() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            askPermissions();
            Toast.makeText(this, "Allow audio recording first, then press again", Toast.LENGTH_LONG).show();
            return;
        }
        MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE) {
            return;
        }
        if (resultCode != RESULT_OK || data == null) {
            Engine.captureStatus = "You declined the capture permission";
            Engine.log("Capture permission declined");
            return;
        }
        Intent i = new Intent(this, CaptureService.class);
        i.putExtra(CaptureService.EXTRA_CODE, resultCode);
        i.putExtra(CaptureService.EXTRA_DATA, data);
        startForegroundService(i);
    }

    private void askPermissions() {
        List<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), REQ_PERMS);
        }
    }

    private String deviceLine() {
        return "v0.2 · " + Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE
                + " (SDK " + Build.VERSION.SDK_INT + ")";
    }

    private void copyReport() {
        String report = "MCP-ifm feasibility report\n" + deviceLine()
                + "\n\n" + eqStatus.getText()
                + "\n\n" + sessions.getText()
                + "\n\nCapture: " + captureStatus.getText()
                + "\n\nLog:\n" + Engine.logText();
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newPlainText("MCP-ifm report", report));
        Toast.makeText(this, "Report copied. Paste it in the chat", Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
