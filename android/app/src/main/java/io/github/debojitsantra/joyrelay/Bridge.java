package io.github.debojitsantra.joyrelay;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.view.InputDevice;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class Bridge {
    static final class RttStats {
        final float last;
        final float avg;
        final float min;
        final float max;
        final float jitter;
        final boolean stale;
        final List<Float> series;

        RttStats(float last, float avg, float min, float max, float jitter,
                 boolean stale, List<Float> series) {
            this.last = last;
            this.avg = avg;
            this.min = min;
            this.max = max;
            this.jitter = jitter;
            this.stale = stale;
            this.series = series;
        }
    }

    static volatile String host = "";
    static volatile String status = "Stopped";
    static volatile String error;
    static volatile int port = 47500;
    static volatile int inputHz;
    static volatile int sentPerSec;
    static volatile int rBtn;
    static volatile int rLx;
    static volatile int rLy;
    static volatile int rRx;
    static volatile int rRy;
    static volatile int rLt;
    static volatile int rRt;
    static volatile boolean running;

    private static UdpSender sender;
    private static WifiManager.WifiLock wifiLock;
    private static final AtomicInteger submits = new AtomicInteger();
    private static final ArrayDeque<Float> rtts = new ArrayDeque<>();
    private static long rateTime = System.nanoTime();
    private static long lastRttAt;

    private Bridge() {}

    static synchronized void start(Context context) {
        if (running) {
            return;
        }

        Context app = context.getApplicationContext();
        error = null;
        synchronized (rtts) {
            rtts.clear();
        }

        WifiManager wifiManager = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
        wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                "joyrelay"
        );
        wifiLock.setReferenceCounted(false);
        wifiLock.acquire();

        sender = new UdpSender(host, port, Bridge::addRtt);
        sender.start();
        status = "Streaming to " + host + ":" + port;
        running = true;
        push();
    }

    static synchronized void stop() {
        if (sender != null) {
            sender.shutdown();
            sender = null;
        }
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        wifiLock = null;

        running = false;
        status = "Stopped";
        rBtn = 0;
        rLx = 0;
        rLy = 0;
        rRx = 0;
        rRy = 0;
        rLt = 0;
        rRt = 0;
    }

    static void submit(int buttons, int lx, int ly, int rx, int ry, int lt, int rt) {
        rBtn = buttons;
        rLx = lx;
        rLy = ly;
        rRx = rx;
        rRy = ry;
        rLt = lt;
        rRt = rt;
        submits.incrementAndGet();
        push();
    }

    private static void push() {
        UdpSender currentSender = sender;
        if (currentSender != null) {
            currentSender.update(rBtn, rLx, rLy, rRx, rRy, rLt, rRt);
        }
    }

    static void refreshRates() {
        long now = System.nanoTime();
        double seconds = (now - rateTime) / 1e9;
        if (seconds > 0) {
            inputHz = (int) (submits.getAndSet(0) / seconds);
        }
        rateTime = now;

        UdpSender currentSender = sender;
        sentPerSec = currentSender == null ? 0 : currentSender.takeRate();
        error = currentSender == null ? null : currentSender.error;
    }

    private static void addRtt(float ms) {
        lastRttAt = System.currentTimeMillis();
        synchronized (rtts) {
            rtts.addLast(ms);
            while (rtts.size() > LatencyGraph.MAX_POINTS) {
                rtts.removeFirst();
            }
        }
    }

    static RttStats rttStats() {
        List<Float> values;
        synchronized (rtts) {
            values = new ArrayList<>(rtts);
        }
        if (values.isEmpty()) {
            return null;
        }

        float jitter = 0;
        for (int i = 1; i < values.size(); i++) {
            jitter += Math.abs(values.get(i) - values.get(i - 1));
        }
        if (values.size() > 1) {
            jitter /= values.size() - 1;
        }

        int from = Math.max(0, values.size() - 40);
        float sum = 0;
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        for (float value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        for (int i = from; i < values.size(); i++) {
            sum += values.get(i);
        }

        return new RttStats(
                values.get(values.size() - 1),
                sum / (values.size() - from),
                min,
                max,
                jitter,
                System.currentTimeMillis() - lastRttAt > 2500,
                values
        );
    }

    static boolean isGamepad(int source) {
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }
}
