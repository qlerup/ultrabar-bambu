package dk.ultrabar.bambu;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.Locale;

/** Native ultrawide foreground dashboard; independent of a running PC after launch. */
public final class MainActivity extends Activity {
    private static final int BG = 0xff0b1112, CARD = 0xff152022, TEXT = 0xffedf5f1, MUTED = 0xff9bafaa, GREEN = 0xff9ee6b3;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PrinterLink link;
    private boolean demo, resumed;
    private int generation;
    private String connection = "Not connected", detail = "Set up your printer to begin";
    private TextView title, badge, stateText, job, percent, remaining, layers, nozzle, bed, chamber, note;
    private ProgressBar progress;
    private Button pause, stop, light, reconnect, preview, printTools;
    private LinearLayout temperatures;
    private JSONObject values = new JSONObject();
    private final Runnable tick = new Runnable() {
        public void run() { if (!resumed) return; render(); handler.postDelayed(this, 1000); }
    };
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        build();
    }
    protected void onResume() { super.onResume(); resumed = true; connect(); handler.post(tick); }
    protected void onPause() { resumed = false; handler.removeCallbacks(tick); disconnect(); super.onPause(); }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setFontFeatureSettings("tnum"); return view;
    }
    private Button button(String label, int color, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(13); b.setTextColor(color);
        b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(dp(12), dp(5), dp(12), dp(5)); b.setBackground(shape(0xff243335, 9));
        b.setOnClickListener(v -> action.run()); return b;
    }
    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout card(float weight, LinearLayout row) {
        LinearLayout card = vertical(); card.setPadding(dp(16), dp(10), dp(16), dp(10)); card.setBackground(shape(CARD, 14));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, weight); p.setMargins(dp(4), 0, dp(4), 0); row.addView(card, p); return card;
    }
    private void build() {
        LinearLayout root = vertical(); root.setBackgroundColor(BG); root.setPadding(dp(12), dp(6), dp(12), dp(8));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        title = text("Bambu", 17, TEXT); title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        badge = text("LOCAL CONNECTION", 11, GREEN); badge.setPadding(dp(10), 0, dp(14), 0); header.addView(badge);
        preview = button("Preview", MUTED, () -> { demo = !demo; disconnect(); if (!demo) connect(); render(); });
        header.addView(button("Printers", GREEN, this::printers), new LinearLayout.LayoutParams(dp(86), dp(34)));
        preview.setTextSize(11);
        header.addView(preview, new LinearLayout.LayoutParams(dp(105), dp(34)));
        reconnect = button("Reconnect", TEXT, this::connect); header.addView(reconnect, new LinearLayout.LayoutParams(dp(108), dp(34)));
        header.addView(button("Settings", TEXT, () -> settings(false)), new LinearLayout.LayoutParams(dp(96), dp(34)));
        header.addView(button("Close", MUTED, this::finish), new LinearLayout.LayoutParams(dp(75), dp(34)));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(40)));
        LinearLayout row = new LinearLayout(this);
        LinearLayout overview = card(44, row);
        stateText = text("Not connected", 24, GREEN); stateText.setTypeface(null, Typeface.BOLD); overview.addView(stateText);
        job = text("Your printer, at a glance", 14, TEXT); job.setSingleLine(true); job.setEllipsize(android.text.TextUtils.TruncateAt.END); overview.addView(job);
        LinearLayout numbers = new LinearLayout(this); numbers.setGravity(Gravity.CENTER_VERTICAL);
        percent = text("—", 30, TEXT); percent.setTypeface(null, Typeface.BOLD); numbers.addView(percent, new LinearLayout.LayoutParams(0, -2, 1));
        remaining = text("No live print", 15, MUTED); numbers.addView(remaining); overview.addView(numbers);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN)); overview.addView(progress, new LinearLayout.LayoutParams(-1, dp(6)));
        layers = text("Awaiting printer status", 12, MUTED); layers.setPadding(0, dp(5), 0, 0); overview.addView(layers);
        temperatures = card(24, row); temperatures.addView(text("TEMPERATURES", 11, MUTED));
        nozzle = text("Nozzle   —", 17, TEXT); nozzle.setPadding(0, dp(10), 0, 0); nozzle.setMaxLines(2); temperatures.addView(nozzle);
        bed = text("Bed        —", 21, TEXT); bed.setPadding(0, dp(10), 0, 0); temperatures.addView(bed);
        temperatures.addView(text("Actual / target · °C", 11, MUTED));
        chamber = text("", 13, MUTED); chamber.setPadding(0, dp(6), 0, 0); temperatures.addView(chamber);
        printTools = button("PrintTools", TEXT, this::showPrintTools);
        printTools.setVisibility(View.GONE);
        temperatures.addView(printTools, new LinearLayout.LayoutParams(-1, dp(34)));
        LinearLayout actions = card(32, row); actions.addView(text("PRINTER CONTROLS", 11, MUTED));
        LinearLayout controls = new LinearLayout(this);
        pause = button("Pause", GREEN, () -> command("PAUSE".equals(snapshot().optString("gcode_state")) ? "resume" : "pause"));
        stop = button("Stop print", 0xffffac9e, () -> new AlertDialog.Builder(this).setTitle("Stop this print?")
            .setMessage("This ends the current print. It cannot be resumed. Check your printer before continuing.")
            .setNegativeButton("Keep printing", null).setPositiveButton("Stop print", (d, w) -> command("stop")).show());
        LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(0, dp(38), 1); c.setMargins(0, dp(7), dp(4), 0);
        controls.addView(pause, c); controls.addView(stop, c); actions.addView(controls);
        light = button("Chamber light", TEXT, () -> command(link != null && "on".equals(link.state.lightMode()) ? "light_off" : "light_on"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(34)); lp.setMargins(0, dp(5), 0, 0); actions.addView(light, lp);
        note = text("Controls are off. Enable them in Settings.", 11, MUTED); note.setPadding(0, dp(5), 0, 0); note.setMaxLines(3); actions.addView(note);
        root.addView(row, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
    }
    private JSONObject snapshot() {
        if (demo) try { return new JSONObject("{\"gcode_state\":\"RUNNING\",\"subtask_name\":\"Desk organiser · sample data\",\"mc_percent\":64,\"mc_remaining_time\":47,\"layer_num\":128,\"total_layer_num\":200,\"nozzle_temper\":220,\"nozzle_target_temper\":220,\"bed_temper\":55,\"bed_target_temper\":55}"); } catch (Exception ignored) {}
        return link == null ? new JSONObject() : link.state.snapshot();
    }
    private void render() {
        if (!demo && resumed) { BambuService.touch(this, false); link = BambuService.selectedMatches(this)?BambuService.current():null; connection = BambuService.status(); detail = BambuService.detail(); }
        try { values = Settings.read(this); } catch (Exception ignored) {}
        String selectedName = "Bambu";
        try { JSONObject selected = Credentials.read(this); if (selected != null) selectedName = selected.optString("name", selectedName); } catch (Exception ignored) {}
        title.setText(values.optString("printerName", "Bambu Lab") + "  /  " + selectedName); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (values.optBoolean("keepScreenOn")) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        temperatures.setVisibility(values.optBoolean("showTemperatures", true) ? View.VISIBLE : View.GONE);
        JSONObject s = snapshot(); long now = SystemClock.elapsedRealtime();
        boolean fresh = demo || (link != null && link.connected() && link.state.fresh(now));
        String raw = s.optString("gcode_state", "");
        badge.setText(demo ? "PREVIEW · SAMPLE DATA" : fresh ? "LAN · LIVE" : connection.toUpperCase(Locale.ROOT));
        badge.setTextColor(demo ? 0xffffd38b : fresh ? GREEN : MUTED);
        stateText.setText(demo || fresh ? label(raw) : link != null && link.connected() ? "Status unavailable" : connection);
        job.setText(s.optString("subtask_name", "Set up your printer in Settings"));
        percent.setText(fresh && s.has("mc_percent") ? Math.max(0, Math.min(100, s.optInt("mc_percent"))) + "%" : "—");
        int mins = s.optInt("mc_remaining_time", -1);
        remaining.setText(fresh && mins >= 0 ? mins / 60 + "h " + mins % 60 + "m left" : "No live estimate");
        progress.setProgress(fresh ? Math.max(0, Math.min(100, s.optInt("mc_percent"))) : 0);
        layers.setText(fresh && s.has("layer_num") ? "Layer " + s.optInt("layer_num") + " / " + s.optInt("total_layer_num") : "Waiting for a fresh printer report");
        String nozzleSummary = fresh ? PrinterState.nozzleText(s) : null;
        nozzle.setText(nozzleSummary == null ? "Nozzle  " + temperature(s, "nozzle", fresh) : nozzleSummary); bed.setText("Bed       " + temperature(s, "bed", fresh));
        chamber.setText(fresh && s.has("chamber_temper") ? "Chamber  " + temperature(s, "chamber", true) : "");
        JSONObject rack = s.optJSONObject("panel_printtools");
        printTools.setVisibility(fresh && rack != null ? View.VISIBLE : View.GONE);
        if (rack != null) printTools.setText("PrintTools (" + rack.length() + ")");
        boolean enabled = !demo && link != null && link.connected() && !link.busy() && values.optBoolean("allowControls");
        String pauseAction = raw.equals("PAUSE") ? "resume" : "pause"; pause.setText(raw.equals("PAUSE") ? "Resume" : "Pause");
        pause.setEnabled(enabled && link.state.allows(pauseAction, now)); stop.setEnabled(enabled && link.state.allows("stop", now));
        light.setEnabled(enabled && link.state.allows("light_on", now));
        light.setText(link != null && link.state.hasLight() ? "Chamber light · " + link.state.lightMode() : "Chamber light · unavailable");
        for (Button b : new Button[]{pause, stop, light}) b.setAlpha(b.isEnabled() ? 1f : .4f);
        note.setText(demo ? "Preview only. No printer connection or commands." : !values.optBoolean("allowControls") ? "Controls are off. Enable them in Settings." : link != null && link.state.requiresSignedCommands() ? "Firmware requires signed commands. Enable LAN Developer Mode for controls." : detail);
        preview.setText(demo ? "Exit preview" : "Preview"); reconnect.setEnabled(!demo);
    }
    private String temperature(JSONObject s, String key, boolean fresh) {
        if (!fresh || !s.has(key + "_temper")) return "—";
        return s.optInt(key + "_temper") + " / " + (s.has(key + "_target_temper") ? s.optInt(key + "_target_temper") : "—") + "°";
    }
    private void showPrintTools() {
        if (link == null || !link.connected() || !link.state.fresh(SystemClock.elapsedRealtime())) return;
        try {
            JSONArray readings = PrinterState.nozzleReadings(link.state.snapshot());
            String[] rows = new String[readings.length()];
            for (int i = 0; i < readings.length(); i++) {
                JSONObject reading = readings.getJSONObject(i);
                rows[i] = "PrintTool " + (reading.getInt("tool") + 1) + "   " + reading.getInt("temperature") + " °C";
            }
            new AlertDialog.Builder(this).setTitle("PrintTool temperatures")
                .setItems(rows, null).setPositiveButton("Close", null).show();
        } catch (JSONException e) {
            Toast.makeText(this, "PrintTool temperatures unavailable", Toast.LENGTH_SHORT).show();
        }
    }
    private String label(String state) {
        switch (state) { case "RUNNING": return "Printing"; case "PAUSE": return "Paused"; case "PREPARE": return "Preparing";
            case "FINISH": return "Print complete"; case "FAILED": return "Print stopped / failed"; case "IDLE": return "Ready";
            default: return state.isEmpty() ? "Awaiting status" : state; }
    }
    private void disconnect() { generation++; link = null; }
    private void connect() {
        disconnect(); if (demo || !resumed) return;
        final int current = generation;
        try {
            JSONObject c = Credentials.read(this);
            if (c == null) { connection = "Not connected"; detail = "Set up your printer in Settings"; render(); return; }
            connection = "Connecting"; detail = "Establishing trusted LAN connection";
            BambuService.touch(this, true); link = BambuService.current();
        } catch (Exception failure) { connection = "Setup required"; detail = "Saved connection unavailable. Re-enter it in Settings."; }
        render();
    }
    private void command(String action) {
        if (demo || link == null || !link.request(action)) Toast.makeText(this, "Command unavailable. Check fresh status and control settings.", Toast.LENGTH_LONG).show();
        render();
    }
    private EditText field(LinearLayout form, String label, String value, boolean secret) {
        form.addView(text(label, 13, MUTED)); EditText input = new EditText(this); input.setText(value); input.setSingleLine(true);
        input.setTextSize(15); input.setInputType(InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        input.setSaveEnabled(false); input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        form.addView(input, new LinearLayout.LayoutParams(-1, dp(46))); return input;
    }
    private Switch toggle(LinearLayout form, String label, boolean value) {
        Switch s = new Switch(this); s.setText(label); s.setChecked(value); s.setPadding(0, dp(8), 0, dp(8)); form.addView(s); return s;
    }
    private void printers() {
        try {
            String[] saved = Credentials.names(this); String[] choices = java.util.Arrays.copyOf(saved, saved.length + 1); choices[saved.length] = "+ Add printer";
            new AlertDialog.Builder(this).setTitle("Choose printer").setItems(choices, (d,w) -> {
                if (w == saved.length) { settings(true); return; }
                try { disconnect(); Credentials.select(this, w); demo = false; connect(); } catch (Exception e) { error("Could not select printer."); }
            }).setNegativeButton("Cancel", null).show();
        } catch (Exception e) { error("Saved printers unavailable. Use Settings to remove and re-enter a connection."); }
    }
    private void settings(boolean add) {
        LinearLayout form = vertical(); form.setPadding(dp(20), dp(8), dp(20), dp(8));
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        JSONObject saved = null; if (!add) try { saved = Credentials.read(this); } catch (Exception ignored) {}
        JSONObject prefs; try { prefs = Settings.read(this); } catch (Exception e) { prefs = new JSONObject(); }
        EditText name = field(form, "Panel title", prefs.optString("printerName", "Bambu Lab"), false);
        EditText profileName = field(form, "Connection label (for example H2D or workshop printer)", saved == null ? "" : saved.optString("name"), false);
        Switch control = toggle(form, "Enable pause / resume / stop / light", prefs.optBoolean("allowControls"));
        Switch temps = toggle(form, "Show temperatures", prefs.optBoolean("showTemperatures", true));
        Switch awake = toggle(form, "Keep screen awake", prefs.optBoolean("keepScreenOn"));
        form.addView(text("LOCAL CONNECTION · credentials stay on this CoreBAR", 12, GREEN));
        EditText host = field(form, "Private IPv4 address", saved == null ? "" : saved.optString("host"), false);
        EditText serial = field(form, "Printer serial number", saved == null ? "" : saved.optString("serial"), false);
        EditText code = field(form, "8-character LAN access code (blank keeps saved code)", "", true);
        form.addView(button("Search local network", GREEN, () -> discover(host,serial,profileName)));
        form.addView(text("Enable LAN mode and, where required, Developer Mode on the printer. CoreBAR must be on the same network. Cloud login is not used.", 12, MUTED));
        if (!add) form.addView(button("Forget connection", 0xffffac9e, () -> new AlertDialog.Builder(this).setTitle("Forget printer connection?")
            .setMessage("Removes the saved address, serial, access code and trusted certificate from this app.")
            .setNegativeButton("Cancel", null).setPositiveButton("Forget", (d,w) -> {
                try { disconnect(); Credentials.clear(this); connection = "Not connected"; host.setText(""); serial.setText(""); code.setText(""); render(); }
                catch (Exception e) { error("Could not remove the saved connection."); }
            }).show()));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Bambu settings").setView(scroll)
            .setNegativeButton("Cancel", null).setNeutralButton("Save display", null).setPositiveButton("Save & connect", null).create();
        dialog.setOnShowListener(shown -> {
            Runnable saveDisplay = () -> {
                try {
                    String printerName = name.getText().toString().trim();
                    if (printerName.codePointCount(0, printerName.length()) > 48 || printerName.matches("(?s).*[\\p{Cntrl}].*")) throw new Exception();
                    Settings.save(this, new JSONObject().put("printerName", printerName).put("allowControls", control.isChecked())
                        .put("showTemperatures", temps.isChecked()).put("keepScreenOn", awake.isChecked())); render();
                } catch (Exception e) { throw new IllegalArgumentException("Use a name up to 48 characters and try again."); }
            };
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                try { saveDisplay.run(); dialog.dismiss(); } catch (Exception e) { error(e.getMessage()); }
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    String h = host.getText().toString().trim(), sn = serial.getText().toString().trim(), access = code.getText().toString();
                    JSONObject old = add ? null : Credentials.read(this);
                    if (access.isEmpty() && old != null && h.equals(old.optString("host")) && sn.equals(old.optString("serial"))) access = old.getString("code");
                    if (!PrinterState.privateIpv4(h) || !sn.matches("[A-Za-z0-9]{6,32}") || !access.matches("[A-Za-z0-9]{8}")) throw new Exception("Enter a private IPv4 address, serial number and 8-character LAN code.");
                    saveDisplay.run();
                    String label = profileName.getText().toString().trim();
                    if (label.length() > 48 || label.matches("(?s).*[\\p{Cntrl}].*")) throw new Exception("Use a connection label up to 48 characters.");
                    JSONObject candidate = new JSONObject().put("host", h).put("serial", sn).put("code", access).put("name", label.isEmpty() ? "Bambu Lab" : label);
                    boolean existing = old != null && h.equals(old.optString("host")) && sn.equals(old.optString("serial"));
                    dialog.dismiss(); disconnect(); demo = false;
                    if (existing) { candidate.put("pin", old.getString("pin")); Credentials.save(this, candidate); connect(); }
                    else probe(candidate);
                } catch (Exception e) { error(e.getMessage() == null ? "Could not save connection." : e.getMessage()); }
            });
        });
        dialog.getWindow(); dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private void probe(JSONObject candidate) {
        connection = "Checking certificate"; detail = "No access code is sent until you approve the certificate."; render();
        final int current = generation;
        new Thread(() -> {
            try {
                String pin = PrinterTls.probe(candidate.getString("host"));
                runOnUiThread(() -> {
                    if (!resumed || generation != current) return;
                    new AlertDialog.Builder(this).setTitle("Trust this printer certificate?")
                        .setMessage("First connection to " + candidate.optString("host") + ". Compare this SHA-256 fingerprint with a trusted connection to your printer. First-use trust cannot establish identity on a hostile network.\n\n" + pin + "\n\nA changed certificate will be blocked. Credentials are only sent after approval.")
                        .setNegativeButton("Cancel", (d,w) -> { connection = "Not connected"; render(); })
                        .setPositiveButton("Trust & connect", (d,w) -> {
                            if (!resumed || generation != current) return;
                            try { candidate.put("pin", pin); Credentials.save(this, candidate); connect(); }
                            catch (Exception e) { error("Could not save trusted connection."); }
                        }).show();
                });
            } catch (Exception e) { runOnUiThread(() -> { if (resumed && generation == current) { connection = "Not connected"; render(); error("Could not read printer certificate. Check address, network and LAN mode."); } }); }
        }, "bambu-certificate").start();
    }
    private void error(String message) { new AlertDialog.Builder(this).setTitle("Bambu").setMessage(message).setPositiveButton("OK", null).show(); }
    private void discover(EditText host,EditText serial,EditText label){
        java.util.concurrent.atomic.AtomicBoolean canceled=new java.util.concurrent.atomic.AtomicBoolean();
        AlertDialog searching=new AlertDialog.Builder(this).setTitle("Searching for Bambu printers").setMessage("Listening on the local network for up to 8 seconds. No access codes are sent.").setNegativeButton("Cancel",(d,w)->canceled.set(true)).create();
        searching.setOnCancelListener(d->canceled.set(true));searching.show();
        new Thread(()->{
            java.util.List<Discovery.Found> devices;
            try{devices=Discovery.search(canceled);}catch(Exception e){devices=new java.util.ArrayList<>();}
            final java.util.List<Discovery.Found> result=devices;
            runOnUiThread(()->{searching.dismiss();if(!resumed||canceled.get())return;if(result.isEmpty()){error("No printers found. Check that CoreBAR and printers share a network. Guest networks, VLANs or multicast filtering can block discovery. You can still enter IP and serial manually.");return;}
                String[] choices=new String[result.size()];for(int i=0;i<choices.length;i++){Discovery.Found p=result.get(i);choices[i]=p.name+"  ·  "+p.host+"\n"+p.model+"  ·  "+p.serial;}
                new AlertDialog.Builder(this).setTitle("Choose a printer").setItems(choices,(d,w)->{Discovery.Found p=result.get(w);host.setText(p.host);serial.setText(p.serial);if(label.getText().toString().trim().isEmpty())label.setText(p.name);Toast.makeText(this,"IP and serial filled. Enter the LAN access code, then Save & connect.",Toast.LENGTH_LONG).show();}).setNegativeButton("Cancel",null).show();
            });
        },"bambu-discovery").start();
    }
}
