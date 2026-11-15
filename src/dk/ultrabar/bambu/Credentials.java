package dk.ultrabar.bambu;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Access code, address, serial and certificate pin stay in Keystore-encrypted app-private storage. */
public final class Credentials {
    private static final String ALIAS = "bambu-panel-connection-v1";
    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey)store.getKey(ALIAS, null);
    }
    private static JSONObject root(Context context) throws Exception {
        String stored = context.getSharedPreferences("private-connection", 0).getString("encrypted", "");
        if (stored.isEmpty()) return new JSONObject().put("selected", 0).put("profiles", new org.json.JSONArray());
        String[] parts = stored.split(":"); if (parts.length != 2) throw new Exception("Invalid connection storage");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new JSONObject(new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8));
    }
    private static void store(Context context, JSONObject value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        String stored = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        if (!context.getSharedPreferences("private-connection", 0).edit().putString("encrypted", stored).commit()) throw new Exception("Could not save connection");
    }
    public static synchronized JSONObject read(Context context) throws Exception {
        JSONObject all = root(context); org.json.JSONArray profiles = all.getJSONArray("profiles");
        return profiles.length() == 0 ? null : profiles.getJSONObject(all.getInt("selected"));
    }
    public static synchronized String[] names(Context context) throws Exception {
        org.json.JSONArray profiles = root(context).getJSONArray("profiles"); String[] names = new String[profiles.length()];
        for (int i = 0; i < names.length; i++) names[i] = profiles.getJSONObject(i).optString("name", "Printer " + (i + 1));
        return names;
    }
    public static synchronized void select(Context context, int index) throws Exception {
        JSONObject all = root(context);
        if (index < 0 || index >= all.getJSONArray("profiles").length()) throw new Exception("Unknown printer");
        store(context, all.put("selected", index));
    }
    public static synchronized void save(Context context, JSONObject value) throws Exception {
        JSONObject all = root(context); org.json.JSONArray profiles = all.getJSONArray("profiles"); int index = profiles.length();
        for (int i = 0; i < profiles.length(); i++) if (value.getString("serial").equals(profiles.getJSONObject(i).getString("serial"))) { index = i; break; }
        if (index >= 16) throw new Exception("Maximum 16 saved printers");
        profiles.put(index, value); store(context, all.put("selected", index));
    }
    public static synchronized void clear(Context context) throws Exception {
        JSONObject all = root(context); org.json.JSONArray profiles = all.getJSONArray("profiles");
        if (profiles.length() > 0) profiles.remove(all.getInt("selected"));
        if (profiles.length() > 0) { store(context, all.put("selected", 0)); return; }
        if (!context.getSharedPreferences("private-connection", 0).edit().clear().commit()) throw new Exception("Could not remove connection");
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null); store.deleteEntry(ALIAS);
    }
}
