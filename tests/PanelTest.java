package dk.ultrabar.bambu;

import java.io.*;
import java.util.Arrays;
import org.json.*;

public final class PanelTest {
    private static int assertions;
    static void check(boolean b) { assertions++; if (!b) throw new AssertionError("Assertion " + assertions); }
    static JSONObject report(String body) throws Exception { return new JSONObject("{\"print\":{\"command\":\"push_status\"," + body + "}}"); }
    public static void main(String[] args) throws Exception {
        for (int size : new int[]{0, 1, 127, 128, 16383, 16384, MqttWire.MAX_PACKET}) {
            byte[] bytes = new byte[size]; Arrays.fill(bytes, (byte)73);
            ByteArrayOutputStream out = new ByteArrayOutputStream(); MqttWire.write(out, 0x30, bytes);
            ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
            check(Arrays.equals(bytes, MqttWire.read(in.read(), in).body));
        }
        for (byte[] malformed : new byte[][]{{(byte)128,(byte)128,(byte)128,(byte)128}, {(byte)255,(byte)255,127}, {4,1}, {}}) {
            boolean rejected = false;
            try { MqttWire.read(0x30, new ByteArrayInputStream(malformed)); } catch (IOException e) { rejected = true; }
            check(rejected);
        }
        byte[] publication = MqttWire.publish(42, "device/TESTSERIAL/report", "{}".getBytes("UTF-8"));
        MqttWire.Publication parsed = new MqttWire.Publication(new MqttWire.Packet(0x32, publication));
        check(parsed.packetId == 42 && parsed.topic.equals("device/TESTSERIAL/report") && new String(parsed.payload, "UTF-8").equals("{}"));
        for (int header : new int[]{0x33, 0x34, 0x36}) {
            boolean rejected = false; try { new MqttWire.Publication(new MqttWire.Packet(header, publication)); } catch(IOException e) { rejected = true; } check(rejected);
        }
        check(MqttWire.connect("test-client", "TESTCODE")[7] == (byte)0xC2);
        for (String ip : new String[]{"192.168.1.12", "10.0.0.1", "172.16.1.5", "172.31.2.4"}) check(PrinterState.privateIpv4(ip));
        for (String ip : new String[]{"127.0.0.1", "8.8.8.8", "172.32.1.1", "192.168.1.999", "192.168.1.255", "192.168.001.2", "printer.local", "http://192.168.1.5", "192.168.1.0", "::1"}) check(!PrinterState.privateIpv4(ip));
        PrinterState state = new PrinterState(); check(!state.allows("pause", 1000));
        state.accept(report("\"gcode_state\":\"RUNNING\",\"mc_percent\":42,\"ams\":{\"tray_now\":\"0\",\"ams_status\":1}"), 1000);
        check(state.allows("pause", 1001)); check(state.allows("stop", 1001)); check(!state.allows("resume", 1001));
        state.accept(report("\"nozzle_temper\":215,\"ams\":{\"tray_now\":\"1\"}"), 2000);
        check(state.snapshot().getInt("mc_percent") == 42); check(state.snapshot().getJSONObject("ams").getInt("ams_status") == 1);
        check(state.snapshot().getJSONObject("ams").getString("tray_now").equals("1"));
        state.accept(new JSONObject("{\"print\":{\"command\":\"pause\",\"result\":\"SUCCESS\",\"gcode_state\":\"PAUSE\"}}"), 29000);
        check(state.snapshot().getString("gcode_state").equals("RUNNING"));
        check(!state.allows("pause", 32000)); // partial reports do not extend stale state authority
        state.accept(report("\"nozzle_temper\":200"), 32000); check(!state.allows("pause", 32001));
        check(!state.allows("light_on", 32001));
        state.accept(report("\"gcode_state\":\"PAUSE\",\"lights_report\":[{\"node\":\"chamber_light\",\"mode\":\"on\"}]"), 33000);
        check(state.allows("resume", 33001)); check(!state.allows("pause", 33001)); check(state.confirmed("pause"));
        check(state.allows("light_off", 33001)); check(state.confirmed("light_on"));
        JSONObject copy = state.snapshot(); copy.put("gcode_state", "RUNNING"); check(state.confirmed("pause"));
        check(!state.allows("resume", 63000)); check(!state.fresh(100));
        state.accept(report("\"device\":{\"bed\":{\"info\":{\"temp\":" + ((60L << 16) | 58) + "}},\"ctc\":{\"info\":{\"temp\":" + ((40L << 16) | 38) + "}},\"extruder\":{\"state\":18,\"info\":[{\"id\":0,\"temp\":" + ((220L << 16) | 218) + "},{\"id\":1,\"temp\":" + ((210L << 16) | 208) + "}]}}"), 64000);
        check(state.snapshot().getInt("bed_temper") == 58); check(state.snapshot().getInt("bed_target_temper") == 60);
        check(state.snapshot().getInt("chamber_temper") == 38); check(state.snapshot().getInt("chamber_target_temper") == 40);
        check(state.snapshot().getInt("nozzle_temper") == 208);
        check(PrinterState.nozzleText(state.snapshot()).equals("T0 218 / 220°  ·  T1 208 / 210°"));
        state.accept(report("\"device\":{\"extruder\":{\"info\":[{\"id\":1,\"temp\":" + ((210L << 16) | 209) + "}]}}"), 65000);
        check(PrinterState.nozzleText(state.snapshot()).contains("T0 218 / 220°"));
        check(PrinterState.nozzleText(state.snapshot()).contains("T1 209 / 210°"));
        check(state.snapshot().getInt("nozzle_temper") == 209);
        state.accept(report("\"device\":{\"extruder\":{\"state\":2}}"),65500);
        check(state.snapshot().getInt("nozzle_temper") == 218);
        state.accept(report("\"gcode_state\":\"RUNNING\",\"fun\":\"20000000\""), 66000);
        check(state.requiresSignedCommands()); check(!state.allows("pause", 66001)); check(!state.allows("light_on", 66001));
        state.accept(report("\"fun\":\"0\""), 67000); check(!state.requiresSignedCommands()); check(state.allows("pause", 67001));
        check(PrinterState.command("pause", "7").getJSONObject("print").getString("param").equals(""));
        check(PrinterState.command("pushall", "8").getJSONObject("pushing").getString("command").equals("pushall"));
        check(PrinterState.command("light_off", "9").getJSONObject("system").getString("led_mode").equals("off"));
        boolean refused = false; try { PrinterState.command("gcode_line", "10"); } catch (IllegalArgumentException e) { refused = true; } check(refused);
        state.clear(); check(state.snapshot().length() == 0); check(!state.fresh(33001));
        System.out.println(assertions + " assertions passed: MQTT framing, partial telemetry, freshness, command gates and LAN validation");
    }
}
