package dk.ultrabar.bambu;
import android.content.Context;
import org.json.JSONObject;
import studio.settings.StudioSettingsBridge;

public final class StudioSettingsReceiver extends StudioSettingsBridge {
    protected JSONObject readValues(Context context, JSONObject schema) throws Exception { return Settings.read(context); }
    protected void applyValues(Context context, JSONObject values) throws Exception { Settings.save(context, values); }
}
