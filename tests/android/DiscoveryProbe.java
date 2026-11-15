package dk.ultrabar.bambu.tests;
import android.app.Instrumentation;
import android.os.Bundle;
import dk.ultrabar.bambu.Discovery;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit LAN discovery test. Output contains only a count, no device identities. */
public final class DiscoveryProbe extends Instrumentation {
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle result=new Bundle();try{
        long start=android.os.SystemClock.elapsedRealtime();int count=Discovery.search(new AtomicBoolean()).size();
        if(android.os.SystemClock.elapsedRealtime()-start>12000)throw new AssertionError("Discovery deadline exceeded");
        result.putString("stream","PASS: bounded local discovery completed; "+count+" candidate(s). No identities, access codes or control commands returned.\n");finish(-1,result);
    }catch(Throwable failure){result.putString("stream","FAIL: discovery "+failure.getClass().getSimpleName()+"\n");finish(0,result);}}
}
