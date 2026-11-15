package dk.ultrabar.bambu.tests;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import dk.ultrabar.bambu.Credentials;
import org.json.JSONObject;

/** Exercises real Android Keystore without opening UI or connecting a printer. */
public final class ProfileProbe extends Instrumentation {
    private static void check(boolean condition) { if (!condition) throw new AssertionError("Profile assertion failed"); }
    private JSONObject profile(String serial, String name) throws Exception {
        return new JSONObject().put("serial", serial).put("host", "192.168.255.10").put("code", "TESTCODE")
            .put("name", name).put("pin", new String(new char[64]).replace('\0', 'a'));
    }
    public void onCreate(Bundle args) { super.onCreate(args); start(); }
    public void onStart() {
        Bundle result = new Bundle(); Context context = getTargetContext(); boolean created = false;
        try {
            // Refuse to touch existing user connections.
            check(Credentials.names(context).length == 0); created = true;
            Credentials.save(context, profile("SYNTHETICONE", "Printer one"));
            Credentials.save(context, profile("SYNTHETICTWO", "Printer two"));
            check(Credentials.names(context).length == 2);
            check(Credentials.read(context).getString("serial").equals("SYNTHETICTWO"));
            Credentials.select(context, 0);
            check(Credentials.read(context).getString("serial").equals("SYNTHETICONE"));
            Credentials.save(context, profile("SYNTHETICONE", "Renamed"));
            check(Credentials.names(context).length == 2); check(Credentials.names(context)[0].equals("Renamed"));
            String raw = context.getSharedPreferences("private-connection", 0).getString("encrypted", "");
            check(!raw.isEmpty() && !raw.contains("TESTCODE") && !raw.contains("SYNTHETIC") && !raw.contains("192.168"));
            Credentials.clear(context); check(Credentials.names(context).length == 1);
            check(Credentials.read(context).getString("serial").equals("SYNTHETICTWO"));
            Credentials.clear(context); check(Credentials.read(context) == null);
            java.security.KeyStore store = java.security.KeyStore.getInstance("AndroidKeyStore"); store.load(null);
            check(!store.containsAlias("bambu-panel-connection-v1"));
            result.putString("stream", "PASS: Android Keystore encryption, two profiles, selection, upsert, isolated removal and final key deletion. No network or UI.\n");
            finish(-1, result);
        } catch (Throwable failure) {
            result.putString("stream", "FAIL: " + failure.getClass().getSimpleName() + "\n"); finish(0, result);
        } finally {
            if (created) try { while (Credentials.names(context).length > 0) Credentials.clear(context); } catch (Exception ignored) {}
        }
    }
}
