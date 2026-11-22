package dk.ultrabar.bambu;

import org.json.*;
import java.util.Iterator;

/** Merge partial reports, but age printer state separately from command replies. */
public final class PrinterState {
    private JSONObject data = new JSONObject();
    private long stateAt, reportAt;
    public static final long FRESH_MS = 30000;
    public synchronized void clear() { data = new JSONObject(); stateAt = reportAt = 0; }
    public synchronized void accept(JSONObject root, long now) throws JSONException {
        JSONObject print = root.optJSONObject("print");
        if (print == null || !"push_status".equals(print.optString("command"))) return;
        merge(data, print); normalizeTemperatures(print); reportAt = now;
        if (print.has("gcode_state")) stateAt = now;
    }
    private static void merge(JSONObject target, JSONObject patch) throws JSONException {
        Iterator<String> keys = patch.keys();
        while (keys.hasNext()) {
            String key = keys.next(); Object value = patch.get(key);
            if (value instanceof JSONObject && target.opt(key) instanceof JSONObject)
                merge(target.getJSONObject(key), (JSONObject)value);
            else target.put(key, value);
        }
    }
    public synchronized JSONObject snapshot() {
        try { return new JSONObject(data.toString()); } catch (JSONException e) { return new JSONObject(); }
    }
    public synchronized boolean fresh(long now) { return reportAt > 0 && now >= reportAt && now - reportAt < FRESH_MS; }
    public synchronized boolean allows(String action, long now) {
        if (!fresh(now)) return false;
        if (requiresSignedCommands()) return false;
        if ("light_on".equals(action) || "light_off".equals(action)) return hasLight();
        if (stateAt == 0 || now - stateAt >= FRESH_MS) return false;
        String state = data.optString("gcode_state");
        if (action.equals("pause")) return state.equals("RUNNING");
        if (action.equals("resume")) return state.equals("PAUSE");
        if (action.equals("stop")) return state.equals("RUNNING") || state.equals("PAUSE") || state.equals("PREPARE");
        return false;
    }
    public synchronized boolean requiresSignedCommands() {
        String fun = data.optString("fun", "");
        try { return (Long.parseUnsignedLong(fun, 16) & 0x20000000L) != 0; }
        catch (NumberFormatException ignored) { return false; }
    }
    private void normalizeTemperatures(JSONObject patch) throws JSONException {
        JSONObject device = patch.optJSONObject("device"); if (device == null) return;
        for (String[] pair : new String[][]{{"bed", "bed"}, {"ctc", "chamber"}}) {
            JSONObject part = device.optJSONObject(pair[0]); JSONObject info = part == null ? null : part.optJSONObject("info");
            Object raw = info == null ? null : info.opt("temp");
            if (raw == null && pair[0].equals("bed")) raw = device.opt("bed_temp");
            if (raw instanceof Number) {
                long packed = ((Number)raw).longValue();
                data.put(pair[1] + "_temper", packed & 65535L).put(pair[1] + "_target_temper", (packed >>> 16) & 65535L);
            }
        }
        updatePrintTools(device.optJSONObject("nozzle"));
        JSONObject extruder = device.optJSONObject("extruder"); if (extruder == null) return;
        if (extruder.has("state")) data.put("panel_active_nozzle", (extruder.optInt("state") >>> 4) & 15);
        JSONArray info = extruder.optJSONArray("info");
        JSONObject nozzles = data.optJSONObject("panel_nozzles"); if (nozzles == null) nozzles = new JSONObject();
        for (int i = 0; info != null && i < info.length() && i < 16; i++) {
            JSONObject item = info.optJSONObject(i); if (item == null || !item.has("id") || !(item.opt("temp") instanceof Number)) continue;
            int id = item.optInt("id", -1); if (id < 0 || id > 15) continue;
            long packed = item.getLong("temp");
            nozzles.put(Integer.toString(id), new JSONObject().put("actual", packed & 65535L).put("target", (packed >>> 16) & 65535L));
        }
        data.put("panel_nozzles", nozzles);
        JSONObject active = nozzles.optJSONObject(Integer.toString(data.optInt("panel_active_nozzle",0)));
        if(active!=null)data.put("nozzle_temper",active.get("actual")).put("nozzle_target_temper",active.get("target"));
    }
    private void updatePrintTools(JSONObject nozzle) throws JSONException {
        JSONArray info = nozzle == null ? null : nozzle.optJSONArray("info");
        if (info == null) return;
        JSONObject tools = data.optJSONObject("panel_printtools");
        if (tools == null) tools = new JSONObject();
        for (int i = 0; i < Math.min(info.length(), 16); i++) {
            JSONObject item = info.optJSONObject(i);
            if (item == null) continue;
            Object address = item.opt("addr_id"), liveId = item.opt("id"), raw = item.opt("temp");
            if (!(address instanceof Integer) || !(liveId instanceof Integer) ||
                    !(raw instanceof Integer || raw instanceof Long)) continue;
            int slot = (Integer) address, id = (Integer) liveId;
            long packed = ((Number) raw).longValue();
            // SwapTek uses the same rack address when a PrintTool mounts as live ID 0.
            if (slot >= 0 && slot < 9 && (id == 0 || id == 16 + slot) && packed >= 0 && packed <= 0xffffffffL) {
                tools.put(Integer.toString(slot), new JSONObject()
                    .put("actual", packed & 65535L).put("target", packed >>> 16));
            }
        }
        if (tools.length() > 0) data.put("panel_printtools", tools);
    }
    public static JSONArray nozzleReadings(JSONObject snapshot) throws JSONException {
        JSONObject tools = snapshot.optJSONObject("panel_printtools");
        if (tools == null) tools = snapshot.optJSONObject("panel_nozzles");
        JSONArray readings = new JSONArray();
        if (tools == null) return readings;
        for (int slot = 0; slot < 16; slot++) {
            JSONObject tool = tools.optJSONObject(Integer.toString(slot));
            if (tool != null) readings.put(new JSONObject().put("tool", slot)
                .put("temperature", tool.getInt("actual")).put("target", tool.getInt("target")));
        }
        return readings;
    }
    public static String nozzleText(JSONObject snapshot) {
        JSONObject nozzles = snapshot.optJSONObject("panel_nozzles");
        if (nozzles == null || nozzles.length() == 0) return null;
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            JSONObject nozzle = nozzles.optJSONObject(Integer.toString(i)); if (nozzle == null) continue;
            if (nozzles.length() > 2 && i != snapshot.optInt("panel_active_nozzle", 0)) continue;
            if (summary.length() > 0) summary.append("  ·  ");
            summary.append("T").append(i).append(" ").append(nozzle.optInt("actual")).append(" / ").append(nozzle.optInt("target")).append("°");
        }
        return summary.length() == 0 ? "No active tool temperature" : summary.toString();
    }
    public synchronized boolean hasLight() { return lightMode() != null; }
    public synchronized String lightMode() {
        JSONArray lights = data.optJSONArray("lights_report");
        if (lights != null) for (int i = 0; i < lights.length(); i++) {
            JSONObject light = lights.optJSONObject(i);
            if (light != null && "chamber_light".equals(light.optString("node"))) {
                String mode = light.optString("mode");
                if (mode.equals("on") || mode.equals("off")) return mode;
            }
        }
        return null;
    }
    public synchronized boolean confirmed(String action) {
        String s = data.optString("gcode_state");
        if (action.equals("pause")) return s.equals("PAUSE");
        if (action.equals("resume")) return s.equals("RUNNING");
        if (action.equals("stop")) return s.equals("IDLE") || s.equals("FINISH") || s.equals("FAILED");
        if (action.equals("light_on")) return "on".equals(lightMode());
        if (action.equals("light_off")) return "off".equals(lightMode());
        return false;
    }
    public static JSONObject command(String action, String sequence) throws JSONException {
        JSONObject message = new JSONObject().put("sequence_id", sequence);
        if (action.equals("pushall")) return new JSONObject().put("pushing", message.put("command", "pushall"));
        if (action.equals("light_on") || action.equals("light_off")) {
            message.put("command", "ledctrl").put("led_node", "chamber_light")
                .put("led_mode", action.equals("light_on") ? "on" : "off")
                .put("led_on_time", 500).put("led_off_time", 500).put("loop_times", 0).put("interval_time", 0);
            return new JSONObject().put("system", message);
        }
        if (!action.equals("pause") && !action.equals("resume") && !action.equals("stop")) throw new IllegalArgumentException("Unsupported command");
        return new JSONObject().put("print", message.put("command", action).put("param", ""));
    }
    public static boolean privateIpv4(String host) {
        if (!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return false;
        String[] parts = host.split("\\."); int[] n = new int[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].length() > 1 && parts[i].startsWith("0")) return false;
            n[i] = Integer.parseInt(parts[i]); if (n[i] > 255) return false;
        }
        return n[3] != 0 && n[3] != 255 && (n[0] == 10 || (n[0] == 192 && n[1] == 168) || (n[0] == 172 && n[1] >= 16 && n[1] <= 31));
    }
}
