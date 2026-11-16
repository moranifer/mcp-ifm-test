package com.mcpifm.test;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Keeps running in the foreground and listens for media apps announcing their audio session
 * (Spotify does this when "Device Broadcast Status" is on). Each announced session gets the effect.
 */
public class EqService extends Service {

    static final String EXTRA_GLOBAL = "global";
    private static final String CHANNEL = "mcp_ifm_eq";
    private boolean registered = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audio;

    /** Every 4 s: if the global effect lost control or vanished, put it back. */
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (Engine.globalWanted && !Engine.globalHealthy()) {
                Engine.reattachGlobal("watchdog found it inactive");
            }
            handler.postDelayed(this, 4000);
        }
    };

    private final Runnable reattachAfterRouteChange = () -> {
        if (Engine.globalWanted) {
            Engine.reattachGlobal("audio output changed");
        }
    };

    /** Android Auto connecting or disconnecting changes the audio output. Re-attach after it settles. */
    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            for (AudioDeviceInfo d : added) {
                if (d.isSink()) {
                    Engine.log("Output added: " + describe(d));
                }
            }
            handler.removeCallbacks(reattachAfterRouteChange);
            handler.postDelayed(reattachAfterRouteChange, 1500);
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            for (AudioDeviceInfo d : removed) {
                if (d.isSink()) {
                    Engine.log("Output removed: " + describe(d));
                }
            }
            handler.removeCallbacks(reattachAfterRouteChange);
            handler.postDelayed(reattachAfterRouteChange, 1500);
        }
    };

    static String describe(AudioDeviceInfo d) {
        String kind;
        switch (d.getType()) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: kind = "phone speaker"; break;
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE: kind = "earpiece"; break;
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: kind = "Bluetooth audio"; break;
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: kind = "Bluetooth call"; break;
            case AudioDeviceInfo.TYPE_USB_ACCESSORY: kind = "USB accessory"; break;
            case AudioDeviceInfo.TYPE_USB_DEVICE: kind = "USB device"; break;
            case AudioDeviceInfo.TYPE_REMOTE_SUBMIX: kind = "remote submix"; break;
            case AudioDeviceInfo.TYPE_BUS: kind = "bus"; break;
            default: kind = "type " + d.getType();
        }
        return kind + " (" + d.getProductName() + ")";
    }

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
        audio = getSystemService(AudioManager.class);
        for (AudioDeviceInfo d : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            Engine.log("Output present: " + describe(d));
        }
        audio.registerAudioDeviceCallback(deviceCallback, handler);
        handler.postDelayed(watchdog, 4000);
        Engine.eqServiceRunning = true;
        Engine.log("System EQ service started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra(EXTRA_GLOBAL, false)) {
            Engine.log("Trying the global output (session 0)");
            Engine.globalWanted = true;
            Engine.attach(0, "GLOBAL OUTPUT");
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(watchdog);
        handler.removeCallbacks(reattachAfterRouteChange);
        if (audio != null) {
            audio.unregisterAudioDeviceCallback(deviceCallback);
        }
        Engine.globalWanted = false;
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
