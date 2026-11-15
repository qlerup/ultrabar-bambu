package dk.ultrabar.bambu;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;

/** Shared selected-printer connection for the activity and native home widget. */
public final class BambuService extends Service {
    private static volatile BambuService running;
    private static volatile long touched;
    private volatile PrinterLink link;
    private volatile String status = "Starting", detail = "Waiting for printer status";
    private String identity = "";
    private int generation;
    private final Handler handler = new Handler(Looper.getMainLooper());
    public static PrinterLink current() { BambuService s = running; return s == null ? null : s.link; }
    public static String status() { BambuService s = running; return s == null ? "Not connected" : s.status; }
    public static String detail() { BambuService s = running; return s == null ? "Set up your printer in Settings" : s.detail; }
    public static boolean selectedMatches(Context context) {
        BambuService s = running;
        try { JSONObject selected = Credentials.read(context); return s != null && selected != null && selected.toString().equals(s.identity); }
        catch (Exception e) { return false; }
    }
    public static void touch(Context context, boolean reconnect) {
        touched = SystemClock.elapsedRealtime();
        try {
            if (Credentials.read(context) == null) { if (running != null) running.handler.post(() -> { if (running != null) running.stopSelf(); }); return; }
            if (running == null || reconnect) context.startForegroundService(new Intent(context, BambuService.class).putExtra("reconnect", reconnect));
            else running.handler.post(() -> { BambuService s = running; if (s != null) s.ensure(false); });
        } catch (Exception ignored) {}
    }
    public void onCreate() {
        super.onCreate(); running = this;
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel("printer", "Local printer connection", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        startForeground(1, new Notification.Builder(this, "printer").setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Bambu Panel").setContentText("Local printer widget connection").setContentIntent(open).setOngoing(true).build());
        handler.postDelayed(new Runnable() { public void run() {
            if (SystemClock.elapsedRealtime() - touched > 25000) { stopSelf(); return; }
            handler.postDelayed(this, 5000);
        }}, 5000);
    }
    public int onStartCommand(Intent intent, int flags, int id) { ensure(intent != null && intent.getBooleanExtra("reconnect", false)); return START_NOT_STICKY; }
    private void ensure(boolean reconnect) {
        try {
            JSONObject c = Credentials.read(this);
            if (c == null) { stopSelf(); return; }
            String next = c.toString();
            if (!reconnect && next.equals(identity) && link != null) return;
            if (link != null) link.close(); identity = next; final int current = ++generation;
            status = "Connecting"; detail = "Establishing trusted LAN connection";
            PrinterLink created = new PrinterLink(c.getString("host"), c.getString("serial"), c.getString("code"), c.getString("pin"),
                () -> Settings.controls(this) && selectedMatches(this), SystemClock::elapsedRealtime, (state, message) -> { if(generation==current){status = state; detail = message;} });
            link = created; created.start();
        } catch (Exception e) { status = "Setup required"; detail = "Check saved printer connection"; }
    }
    public void onDestroy() { if (link != null) link.close(); if (running == this) running = null; handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    public IBinder onBind(Intent intent) { return null; }
}
