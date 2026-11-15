package dk.ultrabar.bambu;

import java.util.concurrent.atomic.AtomicBoolean;

public final class LinkHarness {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
        long end = System.currentTimeMillis() + 10000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < end) Thread.sleep(20);
        check(condition.getAsBoolean(), "Timed out waiting for test condition");
    }
    public static void main(String[] args) throws Exception {
        String host = args[0], pin = args[1], mode = args[2];
        if (mode.equals("probe")) { check(PrinterTls.probe(host).equals(pin), "Probe fingerprint"); return; }
        if (mode.equals("wrongpin")) {
            try (javax.net.ssl.SSLSocket socket = PrinterTls.create(host, "0".repeat(64), false)) {
                PrinterTls.connect(socket, host); throw new AssertionError("Changed certificate accepted");
            } catch (javax.net.ssl.SSLException expected) { return; }
        }
        AtomicBoolean controls = new AtomicBoolean(false);
        AtomicBoolean disconnected = new AtomicBoolean(false);
        AtomicBoolean sent = new AtomicBoolean(false), timedOut = new AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());
        boolean timeoutTest = mode.equals("timeout");
        PrinterLink link = new PrinterLink(host, "TESTSERIAL", "TESTCODE", pin, controls::get,
            () -> timeoutTest ? clock.get() : System.currentTimeMillis(),
            (connection, detail) -> { if (connection.equals("Disconnected")) disconnected.set(true);
                if (detail.startsWith("Command sent")) sent.set(true);
                if (detail.startsWith("No confirmation")) timedOut.set(true);
            });
        try {
            link.start(); waitFor(() -> link.state.allows("pause", System.currentTimeMillis()));
            check(!link.request("pause"), "Disabled controls must refuse commands");
            controls.set(true); check(link.request("pause"), "Pause accepted");
            check(!link.request("pause"), "Duplicate/inflight command refused");
            if (timeoutTest) {
                waitFor(sent::get); check(link.busy(), "PUBACK must not complete command");
                clock.addAndGet(13000); waitFor(timedOut::get);
                check(!link.busy() && !link.state.confirmed("pause"), "Unconfirmed command times out without success");
                return;
            }
            waitFor(() -> link.state.confirmed("pause") && !link.busy());
            check(link.state.snapshot().getInt("mc_percent") == 42, "Partial report retained progress");
            check(link.request("resume"), "Resume accepted");
            waitFor(() -> link.state.confirmed("resume") && !link.busy());
            check(link.request("light_on"), "Light accepted");
            waitFor(() -> link.state.confirmed("light_on") && !link.busy());
            check(link.request("stop"), "Stop accepted");
            waitFor(() -> link.state.confirmed("stop") && !link.busy());
            waitFor(disconnected::get);
            check(!link.request("resume") && !link.connected(), "No offline commands");
            check(link.state.snapshot().length() == 0, "Disconnected state must clear");
        } finally { link.close(); }
        System.out.println("TLS MQTT integration passed: login, QoS1, partial reports, controls, confirmation and disconnect");
    }
}
