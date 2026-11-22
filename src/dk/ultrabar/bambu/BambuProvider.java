package dk.ultrabar.bambu;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import org.json.*;

/** Fixed IPC surface. Only this app, root/ADB and the verified Core system UID. */
public final class BambuProvider extends ContentProvider {
    public boolean onCreate() { return true; }
    private void authorize() {
        int uid = Binder.getCallingUid();
        if (uid == android.os.Process.myUid() || uid == 0 || uid == 2000) return;
        try {
            ApplicationInfo launcher = getContext().getPackageManager().getApplicationInfo("com.ultrabar.launch", 0);
            if (uid == 1000 && launcher.uid == 1000 && (launcher.flags & ApplicationInfo.FLAG_SYSTEM) != 0) return;
        } catch (Exception ignored) {}
        throw new SecurityException("Only the trusted Core launcher may use the printer widget");
    }
    public Bundle call(String method, String argument, Bundle extras) {
        authorize(); Context context = getContext(); Bundle result = new Bundle();
        long token = Binder.clearCallingIdentity();
        try {
            if (method.equals("snapshot")) {
                BambuService.touch(context, false);
                PrinterLink link = BambuService.selectedMatches(context) ? BambuService.current() : null; JSONObject data = new JSONObject();
                JSONObject c = Credentials.read(context);
                data.put("name", c == null ? "Set up printer" : c.optString("name", "Bambu Lab"));
                data.put("connection", BambuService.status()).put("detail", BambuService.detail());
                boolean fresh = link != null && link.connected() && link.state.fresh(SystemClock.elapsedRealtime());
                data.put("fresh", fresh).put("controls", Settings.controls(context)).put("busy", link != null && link.busy());
                JSONObject source = link == null ? new JSONObject() : link.state.snapshot();
                // IPC never exports credentials, addresses, serials, certificate pins or raw reports.
                for (String key : new String[]{"gcode_state", "subtask_name", "mc_percent", "mc_remaining_time", "layer_num", "total_layer_num", "nozzle_temper", "nozzle_target_temper", "bed_temper", "bed_target_temper", "chamber_temper", "chamber_target_temper"})
                    if (source.has(key)) data.put(key, source.get(key));
                data.put("nozzles", PrinterState.nozzleText(source));
                data.put("nozzle_readings", fresh ? PrinterState.nozzleReadings(source) : new JSONArray());
                data.put("light", link == null ? JSONObject.NULL : link.state.lightMode());
                for (String action : new String[]{"pause", "resume", "stop", "light_on", "light_off"})
                    data.put("can_" + action, link != null && Settings.controls(context) && !link.busy() && link.state.allows(action, SystemClock.elapsedRealtime()));
                result.putString("json", data.toString());
            } else if (method.equals("reconnect")) { BambuService.touch(context, true); result.putBoolean("accepted", true); }
            else if (method.equals("pause") || method.equals("resume") || method.equals("stop") || method.equals("light_on") || method.equals("light_off")) {
                PrinterLink link = BambuService.current();
                // Verify the selected connection still exists before allowing a command.
                result.putBoolean("accepted", BambuService.selectedMatches(context) && link != null && link.request(method));
            } else throw new IllegalArgumentException("Unsupported printer operation");
        } catch (SecurityException | IllegalArgumentException e) { throw e; }
        catch (Exception e) { result.putString("error", "Printer setup unavailable"); }
        finally { Binder.restoreCallingIdentity(token); }
        return result;
    }
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { authorize(); throw new UnsupportedOperationException(); }
    public String getType(Uri uri) { authorize(); return "application/vnd.ultrabar.bambu"; }
    public Uri insert(Uri uri, ContentValues values) { authorize(); throw new UnsupportedOperationException(); }
    public int update(Uri uri, ContentValues values, String selection, String[] args) { authorize(); throw new UnsupportedOperationException(); }
    public int delete(Uri uri, String selection, String[] args) { authorize(); throw new UnsupportedOperationException(); }
}
