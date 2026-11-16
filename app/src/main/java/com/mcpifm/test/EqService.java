package com.mcpifm.test;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.os.IBinder;

/**
 * Keeps running in the foreground and listens for media apps announcing their audio session
 * (Spotify does this when "Device Broadcast Status" is on). Each announced session gets the effect.
 */
public class EqService extends Service {

    static final String EXTRA_GLOBAL = "global";
    private static final String CHANNEL = "mcp_ifm_eq";
    private boolean registered = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE);
            String pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
            if (session == AudioEffect.ERROR_BAD_VALUE) {
                Engine.log("Broadcast without a session id from " + pkg);
                return;
            }
            String action = intent.getAction();
            if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
                Engine.log("App announced session " + session + ": " + pkg);
                Engine.attach(session, pkg);
            } else if (AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
                Engine.detach(session);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "System EQ test", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("MCP-ifm Test")
                .setContentText("System EQ test is listening for music apps")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
        startForeground(1, n);

        IntentFilter f = new IntentFilter();
        f.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        f.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(receiver, f);
        }
        registered = true;
        Engine.eqServiceRunning = true;
        Engine.log("System EQ service started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra(EXTRA_GLOBAL, false)) {
            Engine.log("Trying the global output (session 0)");
            Engine.attach(0, "GLOBAL OUTPUT");
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (registered) {
            unregisterReceiver(receiver);
            registered = false;
        }
        Engine.releaseAll();
        Engine.eqServiceRunning = false;
        Engine.log("System EQ service stopped");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
