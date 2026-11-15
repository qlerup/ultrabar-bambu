package dk.ultrabar.bambu.preview;

import android.app.Activity;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import dk.ultrabarx.widgets.BambuTile;

/** Test APK only: exact Studio renderer with prominently labelled synthetic data. */
public final class LiquidPreviewActivity extends Activity {
    private final Handler handler = new Handler();
    private JSONObject spec;
    private View tile;
    public void onCreate(Bundle saved) {
        super.onCreate(saved);getWindow().setFlags(1024,1024);getWindow().getDecorView().setSystemUiVisibility(5894);
        try { getContentResolver().call(android.net.Uri.parse("content://dk.ultrabar.bambu.status"),"snapshot",null,null);android.util.Log.e("BambuPreview","FAIL: untrusted preview UID reached provider"); }
        catch(SecurityException expected){android.util.Log.i("BambuPreview","PASS: ordinary app UID rejected by Bambu provider");}
        catch(Exception absent){android.util.Log.i("BambuPreview","Provider unavailable; authorization test not run");}
        try { spec=new JSONObject("{\"kind\":\"bambu\",\"expandedSlots\":3,\"primary\":\"progress\",\"secondary\":\"remaining\",\"background\":\"#14202b\",\"accent\":\"#86e4c3\"}"); } catch(Exception e){throw new RuntimeException(e);}
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xff080e16);root.setPadding(0,0,0,20);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark=text("BAMBU PANEL  /  LIQUID WIDGET",10,0xffeaf4fa);mark.setPadding(20,0,0,0);header.addView(mark,new LinearLayout.LayoutParams(0,20,1));
        header.addView(text("SAMPLE DATA · NO PRINTER CONNECTION   ",10,0xffc3ad80));
        for(int slots=2;slots<=5;slots++){final int n=slots;Button b=new Button(this);b.setText(slots+" slots");b.setTextSize(9);b.setAllCaps(false);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(6,0,6,0);b.setTextColor(0xff86e4c3);b.setBackgroundColor(0xff16212b);b.setOnClickListener(v->{try{spec.put("expandedSlots",n);}catch(Exception ignored){}tile.performClick();});header.addView(b,new LinearLayout.LayoutParams(72,20));}
        root.addView(header,new LinearLayout.LayoutParams(-1,20));LinearLayout row=new LinearLayout(this);
        for(int i=0;i<5;i++){
            final int index=i;
            View v=new View(this){
                Paint p=new Paint(3);
                protected void onDraw(Canvas canvas){float scale=getHeight()/240f;canvas.save();canvas.scale(scale,scale);float width=getWidth()/scale;
                    if(index==1){BambuTile.draw(this,canvas,width,spec,"Bambu Lab");}
                    else{p.setColor(0xff101923);canvas.drawRoundRect(0,0,width,240,60,60,p);p.setColor(0xff718390);p.setTextSize(12);canvas.drawText(new String[]{"CORE CLOCK","","SYSTEM","WEATHER","NOW PLAYING"}[index],22,32,p);p.setColor(0xffe5edf4);p.setTextSize(36);canvas.drawText(new String[]{"10:42","","28%","14°","Focus"}[index],22,115,p);p.setTextSize(13);p.setColor(0xff718390);canvas.drawText(new String[]{"Sunday, 15 November","","CPU · connected","Partly cloudy","Desk playlist"}[index],22,148,p);}
                    canvas.restore();}
                public boolean performClick(){super.performClick();if(index==1)BambuTile.expand(this,spec);return true;}
                public boolean onTouchEvent(MotionEvent e){if(index==1){if(e.getAction()==1)performClick();return true;}return false;}
            };
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(20,0,i==4?19:0,0);row.addView(v,lp);if(i==1)tile=v;
        }
        root.addView(row,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        handler.post(new Runnable(){public void run(){try{
            JSONObject sample=new JSONObject("{\"name\":\"Workshop printer\",\"fresh\":true,\"gcode_state\":\"RUNNING\",\"mc_percent\":64,\"mc_remaining_time\":47,\"subtask_name\":\"Desk organiser\",\"layer_num\":128,\"total_layer_num\":200,\"nozzle_temper\":220,\"bed_temper\":55,\"chamber_temper\":38,\"controls\":false}");
            java.lang.reflect.Field data=BambuTile.class.getDeclaredField("data");data.setAccessible(true);data.set(null,sample);
            java.lang.reflect.Field received=BambuTile.class.getDeclaredField("received");received.setAccessible(true);received.setLong(null,SystemClock.elapsedRealtime());tile.invalidate();
            java.lang.reflect.Field panel=BambuTile.class.getDeclaredField("expanded");panel.setAccessible(true);Object expanded=panel.get(null);if(expanded!=null){java.lang.reflect.Method refresh=expanded.getClass().getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(expanded);}
        }catch(Exception e){throw new RuntimeException(e);}handler.postDelayed(this,100);}});
    }
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);t.setTextColor(color);return t;}
    protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
