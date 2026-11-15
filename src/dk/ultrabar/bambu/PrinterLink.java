package dk.ultrabar.bambu;

import java.io.*;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import javax.net.ssl.SSLSocket;
import org.json.*;

/** One foreground-only connection. All I/O is off the UI thread; no offline command queue. */
public final class PrinterLink implements AutoCloseable {
    public interface Listener { void update(String connection, String detail); }
    public final PrinterState state = new PrinterState();
    private final String host, serial, password, pin;
    private final BooleanSupplier enabled;
    private final LongSupplier clock;
    private final Listener listener;
    private final ArrayBlockingQueue<String> commands = new ArrayBlockingQueue<>(1);
    private final AtomicBoolean reserved = new AtomicBoolean();
    private volatile boolean closed, connected;
    private volatile String pending;
    private volatile SSLSocket socket;
    private int packetId = 1;
    private long sequence, deadline;
    private String connection = "Connecting", detail = "Establishing trusted LAN connection";
    public PrinterLink(String host, String serial, String password, String pin, BooleanSupplier enabled, LongSupplier clock, Listener listener) {
        if (!PrinterState.privateIpv4(host) || !serial.matches("[A-Za-z0-9]{6,32}") || !password.matches("[A-Za-z0-9]{8}")) throw new IllegalArgumentException("Invalid printer setup");
        this.host = host; this.serial = serial; this.password = password; this.pin = pin;
        this.enabled = enabled; this.clock = clock; this.listener = listener;
    }
    public void start() { Thread t = new Thread(this::run, "bambu-lan"); t.setDaemon(true); t.start(); }
    public boolean connected() { return connected && !closed; }
    public boolean busy() { return reserved.get(); }
    public synchronized boolean request(String action) {
        if (!connected() || !enabled.getAsBoolean() || !state.allows(action, clock.getAsLong()) || !reserved.compareAndSet(false, true)) return false;
        if (!commands.offer(action)) { reserved.set(false); return false; }
        return true;
    }
    private void notifyState(String status, String message) {
        connection = status; detail = message; if (!closed) listener.update(status, message);
    }
    private int nextId() { packetId = packetId % 65535 + 1; return packetId; }
    private void publish(OutputStream out, String action) throws Exception {
        byte[] payload = PrinterState.command(action, Long.toString(++sequence)).toString().getBytes(StandardCharsets.UTF_8);
        MqttWire.write(out, 0x32, MqttWire.publish(nextId(), "device/" + serial + "/request", payload));
    }
    private MqttWire.Packet read(InputStream in) throws Exception {
        socket.setSoTimeout(1000);
        int first = in.read(); // Only a timeout before a packet is safe to resume.
        socket.setSoTimeout(6000);
        try { return MqttWire.read(first, in); }
        catch (SocketTimeoutException e) { throw new IOException("Incomplete MQTT packet", e); }
    }
    private void run() {
        try {
            SSLSocket current = PrinterTls.create(host, pin, false); socket = current;
            if (closed) { current.close(); return; }
            PrinterTls.connect(current, host);
            InputStream in = current.getInputStream(); OutputStream out = current.getOutputStream();
            MqttWire.write(out, 0x10, MqttWire.connect("ultrabar-" + UUID.randomUUID().toString().substring(0, 12), password));
            current.setSoTimeout(6000);
            MqttWire.Packet ack = MqttWire.read(in.read(), in);
            if (ack.header != 0x20 || ack.body.length != 2 || ack.body[0] != 0 || ack.body[1] != 0) throw new IOException("MQTT login rejected");
            int subscription = nextId();
            MqttWire.write(out, 0x82, MqttWire.subscribe(subscription, "device/" + serial + "/report"));
            ack = MqttWire.read(in.read(), in);
            if (ack.header != 0x90 || ack.body.length != 3 || unsignedShort(ack.body, 0) != subscription || (ack.body[2] != 0 && ack.body[2] != 1)) throw new IOException("MQTT subscription rejected");
            connected = true; notifyState("Connected", "Waiting for printer status");
            publish(out, "pushall");
            long refreshAt = clock.getAsLong(), pingAt = refreshAt, receivedAt = refreshAt;
            while (!closed) {
                long now = clock.getAsLong();
                String action = commands.poll();
                if (action != null && pending == null && enabled.getAsBoolean() && state.allows(action, now)) {
                    pending = action; deadline = now + 12000;
                    publish(out, action);
                    notifyState("Connected", "Command sent; waiting for printer confirmation");
                } else if (action != null) reserved.set(false);
                if (pending != null && now >= deadline) {
                    pending = null; reserved.set(false);
                    notifyState("Connected", "No confirmation. Check printer / Developer Mode before retrying.");
                }
                if (now - receivedAt > 45000) throw new IOException("Printer heartbeat timed out");
                if (now - pingAt >= 10000) { MqttWire.write(out, 0xC0, new byte[0]); pingAt = now; }
                if (now - refreshAt >= 15000) { publish(out, "pushall"); refreshAt = now; }
                MqttWire.Packet packet;
                try { packet = read(in); } catch (SocketTimeoutException timeout) { continue; }
                receivedAt = clock.getAsLong();
                if (packet.type() == 3) {
                    MqttWire.Publication report = new MqttWire.Publication(packet);
                    if (report.packetId != 0) MqttWire.write(out, 0x40, new byte[]{(byte)(report.packetId >> 8), (byte)report.packetId});
                    if (!report.topic.equals("device/" + serial + "/report")) continue;
                    JSONObject root;
                    try { root = new JSONObject(new String(report.payload, StandardCharsets.UTF_8)); }
                    catch (JSONException malformed) { continue; }
                    state.accept(root, clock.getAsLong());
                    if (pending != null && root.optJSONObject("print") != null
                            && "push_status".equals(root.getJSONObject("print").optString("command")) && state.confirmed(pending)) {
                        pending = null; reserved.set(false); detail = "Confirmed by printer";
                    }
                    listener.update(connection, detail);
                } else if (packet.header == 0xD0 && packet.body.length == 0) {
                    // PINGRESP only proves the broker is online, not that telemetry is fresh.
                } else if (packet.header == 0x40 && packet.body.length == 2) {
                    // PUBACK is transport delivery; only fresh printer status confirms an action.
                } else throw new IOException("Unexpected MQTT packet");
            }
        } catch (Exception failure) {
            connected = false; state.clear(); commands.clear(); pending = null; reserved.set(false);
            notifyState("Disconnected", "Connection failed. Check IP, access code, certificate and LAN mode. Tap Reconnect.");
        } finally {
            connected = false;
            SSLSocket current = socket; if (current != null) try { current.close(); } catch (IOException ignored) {}
        }
    }
    private static int unsignedShort(byte[] bytes, int offset) { return ((bytes[offset] & 255) << 8) | (bytes[offset + 1] & 255); }
    public void close() {
        closed = true; connected = false; commands.clear(); pending = null; reserved.set(false); state.clear();
        SSLSocket current = socket; if (current != null) try { current.close(); } catch (IOException ignored) {}
    }
}
