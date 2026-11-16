package io.github.debojitsantra.joyrelay;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;

final class UdpSender extends Thread {
    interface RttCallback {
        void onRtt(float ms);
    }

    private static final class State {
        final int buttons;
        final int lx;
        final int ly;
        final int rx;
        final int ry;
        final int lt;
        final int rt;

        State(int buttons, int lx, int ly, int rx, int ry, int lt, int rt) {
            this.buttons = buttons;
            this.lx = lx;
            this.ly = ly;
            this.rx = rx;
            this.ry = ry;
            this.lt = lt;
            this.rt = rt;
        }
    }

    final String host;
    final int port;

    private final RttCallback onRtt;
    private final Object lock = new Object();
    private final ArrayDeque<State> pending = new ArrayDeque<>();
    private final long[] pingSent = new long[64];

    private volatile boolean running = true;
    private volatile int lastRttTenthsMs = 0xffff;
    volatile String error;

    private State latest = new State(0, 0, 0, 0, 0, 0, 0);
    private int seq;
    private int sentCount;
    private int pingId;
    private long lastRateTime = System.nanoTime();
    private long lastPingNs;

    UdpSender(String host, int port, RttCallback callback) {
        super("joyrelay-sender");
        this.host = host;
        this.port = port;
        onRtt = callback;
    }

    void update(int buttons, int lx, int ly, int rx, int ry, int lt, int rt) {
        synchronized (lock) {
            latest = new State(buttons, lx, ly, rx, ry, lt, rt);
            pending.addLast(latest);
            lock.notifyAll();
        }
    }

    void shutdown() {
        synchronized (lock) {
            running = false;
            lock.notifyAll();
        }
    }

    synchronized int takeRate() {
        long now = System.nanoTime();
        double seconds = (now - lastRateTime) / 1e9;
        int rate = seconds > 0 ? (int) (sentCount / seconds) : 0;
        sentCount = 0;
        lastRateTime = now;
        return rate;
    }

    @Override
    public void run() {
        final DatagramSocket socket;
        final InetAddress address;
        try {
            address = InetAddress.getByName(host);
            socket = new DatagramSocket();
        } catch (Exception exception) {
            error = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            return;
        }

        Thread receiver = new Thread(() -> {
            byte[] receiveBuffer = new byte[32];
            ByteBuffer buffer = ByteBuffer.wrap(receiveBuffer).order(ByteOrder.LITTLE_ENDIAN);
            try {
                while (running) {
                    DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                    socket.receive(packet);
                    if (packet.getLength() >= 6
                            && receiveBuffer[0] == 'P'
                            && receiveBuffer[1] == 'G') {
                        int receivedPingId = buffer.getInt(2);
                        long sentAt = pingSent[receivedPingId & 63];
                        if (sentAt != 0) {
                            float ms = (System.nanoTime() - sentAt) / 1_000_000f;
                            lastRttTenthsMs = Math.max(
                                    0,
                                    Math.min(0xfffe, (int) (ms * 10))
                            );
                            onRtt.onRtt(ms);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }, "joyrelay-receiver");
        receiver.setDaemon(true);
        receiver.start();

        ByteBuffer buffer = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        try {
            while (true) {
                boolean withPing;
                synchronized (lock) {
                    if (!running) {
                        return;
                    }
                    if (pending.isEmpty()) {
                        lock.wait(100);
                    }
                    if (!running) {
                        return;
                    }

                    State state = pending.isEmpty() ? latest : pending.removeFirst();
                    long now = System.nanoTime();
                    withPing = now - lastPingNs > 250_000_000L;
                    if (withPing) {
                        lastPingNs = now;
                    }

                    buffer.clear();
                    buffer.put((byte) 'P')
                            .put((byte) 'R')
                            .put((byte) 1)
                            .putShort((short) (seq & 0xffff))
                            .putShort((short) state.buttons)
                            .putShort((short) state.lx)
                            .putShort((short) state.ly)
                            .putShort((short) state.rx)
                            .putShort((short) state.ry)
                            .put((byte) state.lt)
                            .put((byte) state.rt)
                            .put((byte) (withPing ? 2 : 0));

                    if (withPing) {
                        pingId++;
                        pingSent[pingId & 63] = System.nanoTime();
                        buffer.putInt(pingId).putShort((short) lastRttTenthsMs);
                    }
                }

                seq = (seq + 1) & 0xffff;
                socket.send(new DatagramPacket(
                        buffer.array(),
                        withPing ? 24 : 18,
                        address,
                        port
                ));
                synchronized (this) {
                    sentCount++;
                }
            }
        } catch (Exception exception) {
            if (running) {
                error = exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();
            }
        } finally {
            socket.close();
        }
    }
}
