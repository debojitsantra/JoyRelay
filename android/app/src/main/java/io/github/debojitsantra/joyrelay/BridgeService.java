package io.github.debojitsantra.joyrelay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class BridgeService extends Service {
    static final String ACTION_START = "io.github.debojitsantra.joyrelay.START";
    static final String ACTION_STOP = "io.github.debojitsantra.joyrelay.STOP";

    private static final String CHANNEL = "joyrelay";
    private static final int ID = 1;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (!Bridge.running) {
                return;
            }
            notifyNow();
            ui.postDelayed(this, 1500);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_START.equals(action)) {
            createChannel();
            startForeground(
                    ID,
                    build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            );
            Bridge.start(this);
            ui.removeCallbacks(refresh);
            ui.post(refresh);
        } else if (ACTION_STOP.equals(action)) {
            stopBridge();
        } else if (!Bridge.running) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void stopBridge() {
        ui.removeCallbacks(refresh);
        Bridge.stop();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        ui.removeCallbacks(refresh);
        if (Bridge.running) {
            Bridge.stop();
        }
        super.onDestroy();
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL,
                "JoyRelay connection",
                NotificationManager.IMPORTANCE_LOW
        );
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private void notifyNow() {
        getSystemService(NotificationManager.class).notify(ID, build());
    }

    private PendingIntent action(int code, String action) {
        Intent intent = new Intent(this, BridgeService.class).setAction(action);
        return PendingIntent.getService(
                this,
                code,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    private Notification build() {
        Bridge.RttStats stats = Bridge.rttStats();
        String latency = stats == null || stats.stale
                ? "no reply from PC"
                : String.format("%.1f ms", stats.last);

        Intent activityIntent = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(
                this,
                0,
                activityIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        Icon icon = Icon.createWithResource(this, R.drawable.ic_stat_bridge);

        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_bridge)
                .setContentTitle("JoyRelay - Streaming")
                .setContentText(Bridge.host + ":" + Bridge.port + "  |  " + latency)
                .setColor(C.ACCENT)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(
                        icon,
                        "Stop",
                        action(2, ACTION_STOP)
                ).build())
                .build();
    }
}
