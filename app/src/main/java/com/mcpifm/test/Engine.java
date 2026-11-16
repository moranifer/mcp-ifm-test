package com.mcpifm.test;

import android.media.audiofx.DynamicsProcessing;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared state for the feasibility test.
 * Test A attaches Android's built-in DynamicsProcessing effect to other apps' audio sessions
 * (or to the global output, session 0) and switches between clearly audible test modes.
 */
final class Engine {

    static final int MODE_OFF = 0;
    static final int MODE_BASS = 1;
    static final int MODE_LOUD = 2;

    static volatile int mode = MODE_OFF;
    static volatile boolean eqServiceRunning = false;
    static volatile String globalStatus = "Not tried yet";

    static volatile boolean captureRunning = false;
    static volatile String captureStatus = "Not started";
    static volatile double captureDb = -120;
    static volatile double captureMaxDb = -120;

    private static final Map<Integer, DynamicsProcessing> effects = new LinkedHashMap<>();
    private static final Map<Integer, String> owners = new LinkedHashMap<>();
    private static final List<String> logLines = new ArrayList<>();

    private Engine() { }

    static synchronized void log(String line) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        logLines.add(0, time + "  " + line);
        if (logLines.size() > 80) {
            logLines.remove(logLines.size() - 1);
        }
    }

    static synchronized String logText() {
        StringBuilder sb = new StringBuilder();
        for (String l : logLines) {
            sb.append(l).append('\n');
        }
        return sb.toString();
    }

    static synchronized String sessionsText() {
        if (effects.isEmpty()) {
            return "No sessions attached yet";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, String> e : owners.entrySet()) {
            sb.append("• session ").append(e.getKey()).append("  ")
              .append(e.getValue() == null ? "(unknown app)" : e.getValue()).append('\n');
        }
        return sb.toString().trim();
    }

    static synchronized void attach(int session, String owner) {
        if (effects.containsKey(session)) {
            return;
        }
        String who = owner == null ? "" : " (" + owner + ")";
        try {
            DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,          // channels
                    true, 3,    // pre-EQ, 3 bands
                    false, 0,   // multiband compressor off
                    false, 0,   // post-EQ off
                    true        // limiter
            ).build();
            DynamicsProcessing dp = new DynamicsProcessing(Integer.MAX_VALUE, session, cfg);
            effects.put(session, dp);
            owners.put(session, owner);
            apply(dp);
            log("Attached to session " + session + who);
            if (session == 0) {
                globalStatus = "Attached OK (works only if this phone still honours global effects)";
            }
        } catch (Throwable t) {
            log("FAILED on session " + session + who + ": " + t.getClass().getSimpleName() + " " + t.getMessage());
            if (session == 0) {
                globalStatus = "Failed: " + t.getClass().getSimpleName();
            }
        }
    }

    static synchronized void detach(int session) {
        DynamicsProcessing dp = effects.remove(session);
        String owner = owners.remove(session);
        if (dp != null) {
            try {
                dp.setEnabled(false);
                dp.release();
            } catch (Throwable ignored) {
                // already released by the system
            }
            log("Released session " + session + (owner == null ? "" : " (" + owner + ")"));
        }
    }

    static synchronized void releaseAll() {
        for (Integer s : new ArrayList<>(effects.keySet())) {
            detach(s);
        }
        globalStatus = "Not attached";
    }

    static synchronized void setMode(int m) {
        mode = m;
        for (DynamicsProcessing dp : effects.values()) {
            try {
                apply(dp);
            } catch (Throwable t) {
                log("Could not apply mode: " + t.getMessage());
            }
        }
        log("Mode → " + modeName(m));
    }

    static String modeName(int m) {
        if (m == MODE_BASS) {
            return "BASS TEST (+12 dB below 200 Hz, −12 dB above 4 kHz)";
        }
        if (m == MODE_LOUD) {
            return "LOUD (+6 dB, limiter at −1 dB)";
        }
        return "OFF (flat, bypassed)";
    }

    private static void apply(DynamicsProcessing dp) {
        float low = 0f;
        float high = 0f;
        float inputGain = 0f;
        if (mode == MODE_BASS) {
            low = 12f;
            high = -12f;
            inputGain = -4f;
        } else if (mode == MODE_LOUD) {
            inputGain = 6f;
        }
        dp.setPreEqBandAllChannelsTo(0, new DynamicsProcessing.EqBand(true, 200f, low));
        dp.setPreEqBandAllChannelsTo(1, new DynamicsProcessing.EqBand(true, 4000f, 0f));
        dp.setPreEqBandAllChannelsTo(2, new DynamicsProcessing.EqBand(true, 20000f, high));
        dp.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f));
        dp.setInputGainAllChannelsTo(inputGain);
        dp.setEnabled(mode != MODE_OFF);
    }
}
