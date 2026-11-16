package io.github.debojitsantra.joyrelay;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final Map<Integer, Integer> KEY_MAP = createKeyMap();

    private final Handler ui = new Handler(Looper.getMainLooper());

    private TextView pill;
    private TextView latBig;
    private TextView latSub;
    private TextView tileInput;
    private TextView tileSent;
    private TextView errView;
    private TextView controllerHint;
    private LatencyGraph graph;
    private ControllerSimulationView simulationView;
    private EditText ipField;
    private EditText portField;
    private Button startBtn;

    private int keyMask;
    private int hatMask;
    private int slow;
    private boolean ltKey;
    private boolean rtKey;
    private boolean ltAxisSupported;
    private boolean rtAxisSupported;
    private float lx;
    private float ly;
    private float rx;
    private float ry;
    private float ltAxis;
    private float rtAxis;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(C.BG);
        getWindow().setNavigationBarColor(C.BG);
        buildUi();
    }

    private LinearLayout.LayoutParams lp(int width, int height, int top) {
        return Ui.lp(this, width, height, top);
    }

    private void buildUi() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(
                Ui.dp(this, 16),
                Ui.dp(this, 10),
                Ui.dp(this, 16),
                Ui.dp(this, 28)
        );

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);

        LinearLayout iconRow = new LinearLayout(this);
        iconRow.setGravity(Gravity.CENTER);
        iconRow.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38)));
        column.addView(iconRow, lp(-1, -2, 0));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER);
        pill = Ui.text(this, "IDLE", 11, C.MUTED, true);
        pill.setLetterSpacing(.12f);
        pill.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
        statusRow.addView(pill);
        column.addView(statusRow, lp(-1, -2, 5));

        LinearLayout latency = Ui.card(this);
        latency.addView(Ui.caption(this, "Round-trip latency"));

        LinearLayout latencyRow = new LinearLayout(this);
        latencyRow.setGravity(Gravity.BOTTOM);
        latBig = Ui.text(this, "--", 46, C.TEXT, true);
        latencyRow.addView(latBig);

        TextView ms = Ui.text(this, " ms", 16, C.MUTED, false);
        ms.setPadding(0, 0, 0, Ui.dp(this, 8));
        latencyRow.addView(ms);
        latency.addView(latencyRow, lp(-1, -2, 4));

        latSub = Ui.text(this, "waiting for the PC receiver...", 12, C.MUTED, false);
        latency.addView(latSub);

        graph = new LatencyGraph(this);
        latency.addView(graph, lp(-1, Ui.dp(this, 92), 10));
        column.addView(latency, lp(-1, -2, 14));

        LinearLayout controller = Ui.card(this);
        controller.addView(Ui.caption(this, "Controller"));
        simulationView = new ControllerSimulationView(this);
        controller.addView(simulationView, lp(-1, Ui.dp(this, 300), 8));
        column.addView(controller, lp(-1, -2, 12));

        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout inputTile = tile("Input samples/s", true);
        LinearLayout sentTile = tile("Packets/s", false);
        tiles.addView(inputTile, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout.LayoutParams sentTileParams = new LinearLayout.LayoutParams(0, -2, 1);
        sentTileParams.leftMargin = Ui.dp(this, 10);
        tiles.addView(sentTile, sentTileParams);
        column.addView(tiles, lp(-1, -2, 12));

        LinearLayout connection = Ui.card(this);
        connection.addView(Ui.caption(this, "Connection"));

        ipField = field("PC IP address (e.g. 192.168.1.20)");
        ipField.setText(getSharedPreferences("joyrelay", Context.MODE_PRIVATE)
                .getString("ip", ""));
        connection.addView(ipField, lp(-1, -2, 10));

        portField = field("Port");
        portField.setText(getSharedPreferences("joyrelay", Context.MODE_PRIVATE)
                .getString("port", "47500"));
        portField.setInputType(InputType.TYPE_CLASS_NUMBER);
        connection.addView(portField, lp(-1, -2, 8));

        TextView help = Ui.text(
                this,
                "Keep JoyRelay open while streaming so Android can deliver controller input. "
                        + "The screen stays on while this app is open.",
                11,
                C.MUTED,
                false
        );
        connection.addView(help, lp(-1, -2, 14));

        controllerHint = Ui.text(this, "Connect a controller to start", 12, C.MUTED, false);
        connection.addView(controllerHint, lp(-1, -2, 14));

        startBtn = Ui.button(this, "Start", true);
        startBtn.setEnabled(false);
        startBtn.setOnClickListener(view -> onStartStop());
        connection.addView(startBtn, lp(-1, Ui.dp(this, 52), 8));

        errView = Ui.text(this, "", 12, C.BAD, false);
        connection.addView(errView, lp(-1, -2, 12));
        column.addView(connection, lp(-1, -2, 12));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(C.BG);
        scroll.setFillViewport(true);
        scroll.addView(column, new ViewGroup.LayoutParams(-1, -2));
        setContentView(scroll);
    }

    private LinearLayout tile(String label, boolean first) {
        LinearLayout tile = Ui.card(this);
        tile.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        tile.addView(Ui.caption(this, label));

        TextView value = Ui.text(this, "--", 20, C.TEXT, true);
        tile.addView(value, lp(-1, -2, 4));
        if (first) {
            tileInput = value;
        } else {
            tileSent = value;
        }
        return tile;
    }

    private EditText field(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setHintTextColor(C.MUTED);
        field.setTextColor(C.TEXT);
        field.setSingleLine();
        field.setBackground(Ui.squareBg(C.CARD_HI, C.BORDER));
        field.setPadding(
                Ui.dp(this, 14),
                Ui.dp(this, 12),
                Ui.dp(this, 14),
                Ui.dp(this, 12)
        );
        return field;
    }

    private void onStartStop() {
        if (Bridge.running) {
            startService(new Intent(this, BridgeService.class).setAction(BridgeService.ACTION_STOP));
            return;
        }
        if (!isControllerConnected()) {
            Toast.makeText(
                    this,
                    "Connect a controller before starting the server",
                    Toast.LENGTH_SHORT
            ).show();
            refresh();
            return;
        }

        String ip = ipField.getText().toString().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().toString().trim());
        } catch (Exception exception) {
            port = 47500;
        }
        if (ip.isEmpty()) {
            Toast.makeText(this, "Enter the PC's IP address first", Toast.LENGTH_SHORT).show();
            return;
        }

        getSharedPreferences("joyrelay", 0).edit()
                .putString("ip", ip)
                .putString("port", String.valueOf(port))
                .apply();
        Bridge.host = ip;
        Bridge.port = port;

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        startForegroundService(
                new Intent(this, BridgeService.class).setAction(BridgeService.ACTION_START)
        );
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            ui.postDelayed(this, 100);
        }
    };

    private int latColor(float ms) {
        return ms < 10 ? C.GOOD : ms < 25 ? C.WARN : C.BAD;
    }

    private void setPill(String label, int foreground) {
        pill.setText("●  " + label);
        pill.setTextColor(foreground);
        pill.setBackground(Ui.squareBg(colorAlpha(foreground), foreground));
    }

    private int colorAlpha(int color) {
        return android.graphics.Color.argb(
                32,
                android.graphics.Color.red(color),
                android.graphics.Color.green(color),
                android.graphics.Color.blue(color)
        );
    }

    private void refresh() {
        if (slow++ % 10 == 0) {
            Bridge.refreshRates();
        }

        boolean running = Bridge.running;
        setPill(running ? "STREAMING" : "IDLE", running ? C.GOOD : C.MUTED);
        Bridge.RttStats stats = Bridge.rttStats();

        if (!running || stats == null || stats.stale) {
            latBig.setText("--");
            latBig.setTextColor(C.MUTED);
            latSub.setText(running
                    ? "no reply from the PC - is receiver running?"
                    : "start the bridge to measure");
        } else {
            latBig.setText(String.format("%.1f", stats.last));
            latBig.setTextColor(latColor(stats.last));
            latSub.setText(String.format(
                    "avg %.1f  |  min %.1f  |  max %.1f  |  jitter %.1f ms",
                    stats.avg,
                    stats.min,
                    stats.max,
                    stats.jitter
            ));
        }

        graph.setData(running && stats != null
                ? stats.series
                : java.util.Collections.emptyList());
        simulationView.setState(
                Bridge.rBtn,
                Bridge.rLx / 32767f,
                Bridge.rLy / 32767f,
                Bridge.rRx / 32767f,
                Bridge.rRy / 32767f,
                Bridge.rLt,
                Bridge.rRt
        );
        tileInput.setText(Bridge.inputHz + " Hz");
        tileSent.setText(Bridge.sentPerSec + "/s");

        boolean controllerConnected = isControllerConnected();
        controllerHint.setText(controllerConnected
                ? "Controller connected"
                : "Connect a controller to start");
        controllerHint.setTextColor(controllerConnected ? C.TEXT : C.MUTED);
        startBtn.setText(running ? "Stop" : "Start");
        startBtn.setEnabled(running || controllerConnected);
        Ui.style(startBtn, !running, running);

        String error = Bridge.error;
        errView.setText(error != null ? "Error: " + error : running ? Bridge.status : "");
        errView.setTextColor(error != null ? C.BAD : C.MUTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(tick);
        keyMask = 0;
        hatMask = 0;
        ltKey = false;
        rtKey = false;
        lx = 0;
        ly = 0;
        rx = 0;
        ry = 0;
        ltAxis = 0;
        rtAxis = 0;
        if (Bridge.running) {
            push();
        }
        super.onPause();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!Bridge.isGamepad(event.getSource())) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getRepeatCount() > 0) {
            return true;
        }

        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BUTTON_L2) {
            ltAxisSupported = hasAnalogTrigger(
                    event.getDevice(),
                    MotionEvent.AXIS_LTRIGGER,
                    MotionEvent.AXIS_BRAKE
            );
            ltKey = down && !ltAxisSupported;
        } else if (code == KeyEvent.KEYCODE_BUTTON_R2) {
            rtAxisSupported = hasAnalogTrigger(
                    event.getDevice(),
                    MotionEvent.AXIS_RTRIGGER,
                    MotionEvent.AXIS_GAS
            );
            rtKey = down && !rtAxisSupported;
        } else {
            Integer bit = KEY_MAP.get(code);
            if (bit == null) {
                return super.dispatchKeyEvent(event);
            }
            keyMask = down ? keyMask | bit : keyMask & ~bit;
        }

        push();
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (!Bridge.isGamepad(event.getSource()) || event.getAction() != MotionEvent.ACTION_MOVE) {
            return super.dispatchGenericMotionEvent(event);
        }
        for (int i = 0; i < event.getHistorySize(); i++) {
            consumeMotion(event, i);
        }
        consumeMotion(event, -1);
        return true;
    }

    private float axis(MotionEvent event, int axis, int history) {
        return history < 0
                ? event.getAxisValue(axis)
                : event.getHistoricalAxisValue(axis, history);
    }

    private void consumeMotion(MotionEvent event, int history) {
        lx = axis(event, MotionEvent.AXIS_X, history);
        ly = -axis(event, MotionEvent.AXIS_Y, history);
        ltAxisSupported = hasAnalogTrigger(
                event.getDevice(),
                MotionEvent.AXIS_LTRIGGER,
                MotionEvent.AXIS_BRAKE
        );
        rtAxisSupported = hasAnalogTrigger(
                event.getDevice(),
                MotionEvent.AXIS_RTRIGGER,
                MotionEvent.AXIS_GAS
        );

        float z = axis(event, MotionEvent.AXIS_Z, history);
        float rz = axis(event, MotionEvent.AXIS_RZ, history);
        if (z == 0 && rz == 0) {
            z = axis(event, MotionEvent.AXIS_RX, history);
            rz = axis(event, MotionEvent.AXIS_RY, history);
        }
        rx = z;
        ry = -rz;

        ltAxis = Math.max(
                axis(event, MotionEvent.AXIS_LTRIGGER, history),
                axis(event, MotionEvent.AXIS_BRAKE, history)
        );
        rtAxis = Math.max(
                axis(event, MotionEvent.AXIS_RTRIGGER, history),
                axis(event, MotionEvent.AXIS_GAS, history)
        );

        float hatX = axis(event, MotionEvent.AXIS_HAT_X, history);
        float hatY = axis(event, MotionEvent.AXIS_HAT_Y, history);
        hatMask = (hatY < -.5 ? 1 : 0)
                | (hatY > .5 ? 2 : 0)
                | (hatX < -.5 ? 4 : 0)
                | (hatX > .5 ? 8 : 0);
        push();
    }

    private int stick(float value) {
        return Math.abs(value) < .08
                ? 0
                : (int) (Math.max(-1, Math.min(1, value)) * 32767);
    }

    private int trigger(float axis, boolean key, boolean analog) {
        float value = analog ? axis : key ? 1 : 0;
        return (int) (Math.max(0, Math.min(1, value)) * 255);
    }

    private void push() {
        Bridge.submit(
                keyMask | hatMask,
                stick(lx),
                stick(ly),
                stick(rx),
                stick(ry),
                trigger(ltAxis, ltKey, ltAxisSupported),
                trigger(rtAxis, rtKey, rtAxisSupported)
        );
    }

    private boolean isControllerConnected() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device != null && Bridge.isGamepad(device.getSources())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnalogTrigger(InputDevice device, int primary, int alternate) {
        return device != null
                && (device.getMotionRange(primary) != null
                || device.getMotionRange(alternate) != null);
    }

    private static Map<Integer, Integer> createKeyMap() {
        Map<Integer, Integer> keyMap = new HashMap<>();
        keyMap.put(KeyEvent.KEYCODE_BUTTON_A, 0x1000);
        keyMap.put(KeyEvent.KEYCODE_BUTTON_B, 0x2000);
        keyMap.put(KeyEvent.KEYCODE_BUTTON_X, 0x4000);
        keyMap.put(KeyEvent.KEYCODE_BUTTON_Y, 0x8000);
        keyMap.put(KeyEvent.KEYCODE_BUTTON_L1, 0x0100);
        keyMap.put(KeyEvent.KEYCODE_BUTTON_R1, 0x0200);
        keyMap.put(KeyEvent.KEYCODE_BUTTON_THUMBL, 0x0040);
        keyMap.put(KeyEvent.KEYCODE_BUTTON_THUMBR, 0x0080);
        keyMap.put(KeyEvent.KEYCODE_BUTTON_START, 0x0010);
        keyMap.put(KeyEvent.KEYCODE_BUTTON_9, 0x0010);
        keyMap.put(KeyEvent.KEYCODE_BUTTON_SELECT, 0x0020);
        keyMap.put(KeyEvent.KEYCODE_BUTTON_8, 0x0020);
        keyMap.put(KeyEvent.KEYCODE_BUTTON_MODE, 0x0400);
        keyMap.put(KeyEvent.KEYCODE_DPAD_UP, 1);
        keyMap.put(KeyEvent.KEYCODE_DPAD_DOWN, 2);
        keyMap.put(KeyEvent.KEYCODE_DPAD_LEFT, 4);
        keyMap.put(KeyEvent.KEYCODE_DPAD_RIGHT, 8);
        return keyMap;
    }
}
