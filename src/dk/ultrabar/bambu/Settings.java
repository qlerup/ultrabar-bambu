package dk.ultrabar.bambu;

import android.content.Context;
import org.json.*;

public final class Settings {
    public static JSONObject read(Context context) throws Exception {
        JSONObject saved = new JSONObject(context.getSharedPreferences("panel", 0).getString("settings", "{}"));
        JSONObject values = new JSONObject();
        values.put("printerName", saved.optString("printerName", "Bambu Lab"));
        values.put("allowControls", saved.optBoolean("allowControls", false));
        values.put("showTemperatures", saved.optBoolean("showTemperatures", true));
        values.put("keepScreenOn", saved.optBoolean("keepScreenOn", false));
        return values;
    }
    public static boolean controls(Context context) {
        try { return read(context).getBoolean("allowControls"); } catch (Exception e) { return false; }
    }
    public static void save(Context context, JSONObject values) throws Exception {
        if (!context.getSharedPreferences("panel", 0).edit().putString("settings", values.toString()).commit()) throw new Exception("Could not save settings");
    }
}
