package com.mcpifm.test;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Test B: can this phone hand other apps' audio to us (needed for our own full DSP)?
 * It only measures the level of what it captures. It does not play anything back.
 */
public class CaptureService extends Service {

    static final String EXTRA_CODE = "code";
    static final String EXTRA_DATA = "data";
    private static final String CHANNEL = "mcp_ifm_capture";
    private static final int RATE = 48000;

    private MediaProjection projection;
    private AudioRecord record;
    private Thread reader;
    private volatile boolean running = false;

    @Override
    @SuppressWarnings("deprecation")
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Capture test", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("MCP-ifm Test")
                .setContentText("Capture test is measuring other apps' audio")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
        startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);

        if (intent == null) {
            fail("No permission data received");
            return START_NOT_STICKY;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("Microphone permission is off. Allow it and try again");
            return START_NOT_STICKY;
        }
        try {
            int code = intent.getIntExtra(EXTRA_CODE, 0);
            Intent data = intent.getParcelableExtra(EXTRA_DATA);
            MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
            projection = mpm.getMediaProjection(code, data);
            if (projection == null) {
                fail("Screen-capture permission was not granted");
                return START_NOT_STICKY;
            }
            projection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    Engine.log("Capture permission ended by the system");
                    stopSelf();
                }
            }, new Handler(Looper.getMainLooper()));

            AudioPlaybackCaptureConfiguration cfg = new AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build();
            AudioFormat fmt = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build();
            int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            record = new AudioRecord.Builder()
                    .setAudioFormat(fmt)
                    .setBufferSizeInBytes(Math.max(min * 2, 19200))
                    .setAudioPlaybackCaptureConfig(cfg)
                    .build();
            record.startRecording();
        } catch (Throwable t) {
            fail("Could not start capture: " + t.getClass().getSimpleName() + " " + t.getMessage());
            return START_NOT_STICKY;
        }

        running = true;
        Engine.captureRunning = true;
        Engine.captureMaxDb = -120;
        Engine.captureStatus = "Running. Play music in another app now";
        Engine.log("Capture test started");
        reader = new Thread(this::readLoop, "capture-meter");
        reader.start();
        return START_NOT_STICKY;
    }

    private void readLoop() {
        short[] buf = new short[4800];
        double smooth = -120;
        while (running) {
            int n = record.read(buf, 0, buf.length);
            if (n <= 0) {
                continue;
            }
            double sum = 0;
            for (int i = 0; i < n; i++) {
                double s = buf[i] / 32768.0;
                sum += s * s;
            }
            double rms = Math.sqrt(sum / n);
            double db = rms > 1e-6 ? 20 * Math.log10(rms) : -120;
            smooth = smooth + (db - smooth) * 0.3;
            Engine.captureDb = smooth;
            if (smooth > Engine.captureMaxDb) {
                Engine.captureMaxDb = smooth;
            }
        }
    }

    private void fail(String why) {
        Engine.captureStatus = "Failed: " + why;
        Engine.captureRunning = false;
        Engine.log("Capture test failed: " + why);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        running = false;
        if (reader != null) {
            try {
                reader.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        if (record != null) {
            try {
                record.stop();
            } catch (Throwable ignored) {
                // not recording
            }
            record.release();
            record = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
        }
        if (Engine.captureRunning) {
            Engine.captureStatus = "Stopped. Loudest level seen: " + Math.round(Engine.captureMaxDb) + " dBFS";
            Engine.log("Capture test stopped");
        }
        Engine.captureRunning = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
