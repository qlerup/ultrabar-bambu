package dk.ultrabar.bambu;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit, bounded Bambu SSDP discovery. Never logs devices or reads access codes. */
public final class Discovery {
    public static final String SEARCH="M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1990\r\nMAN: \"ssdp:discover\"\r\nMX: 3\r\nST: urn:bambulab-com:device:3dprinter:1\r\n\r\n";
    public static final class Found {
        public final String host,serial,name,model;
        Found(String host,String serial,String name,String model){this.host=host;this.serial=serial;this.name=name;this.model=model;}
    }
    public static Found parse(String packet,String sender){
        if(packet.length()>8192||!PrinterState.privateIpv4(sender)||(!packet.startsWith("NOTIFY * HTTP/1.1")&&!packet.startsWith("HTTP/1.1 200")))return null;
        String[] lines=packet.split("\r?\n");if(lines.length>64)return null;
        Map<String,String> headers=new HashMap<>();
        for(int i=1;i<lines.length;i++){int colon=lines[i].indexOf(':');if(colon<1)continue;String key=lines[i].substring(0,colon).trim().toLowerCase(Locale.ROOT),value=lines[i].substring(colon+1).trim();if(headers.containsKey(key)&&!headers.get(key).equals(value))return null;headers.put(key,value);}
        String type="urn:bambulab-com:device:3dprinter:1";
        if(!type.equals(headers.get("nt"))&&!type.equals(headers.get("st")))return null;
        String serial=headers.get("usn");if(serial==null||!serial.matches("[A-Za-z0-9]{6,32}"))return null;
        String name=clean(headers.get("devname.bambu.com"),"Bambu Lab"),model=clean(headers.get("devmodel.bambu.com"),"");
        // The UDP peer is the address. Never follow an advertised URL or spoofable LOCATION redirect.
        return new Found(sender,serial.toUpperCase(Locale.ROOT),name,model);
    }
    private static String clean(String value,String fallback){if(value==null||value.isEmpty()||value.length()>64||value.matches("(?s).*[\\p{Cntrl}].*"))return fallback;return value;}
    public static List<Found> search(AtomicBoolean canceled) throws Exception {
        ArrayList<MulticastSocket> sockets=new ArrayList<>();Map<String,Found> found=new LinkedHashMap<>();
        try{
            InetAddress group=InetAddress.getByName("239.255.255.250");
            ArrayList<NetworkInterface> interfaces=new ArrayList<>();Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();
            while(all!=null&&all.hasMoreElements()){NetworkInterface n=all.nextElement();if(n.isUp()&&!n.isLoopback())for(InetAddress address:Collections.list(n.getInetAddresses()))if(address instanceof Inet4Address&&PrinterState.privateIpv4(address.getHostAddress())){interfaces.add(n);break;}}
            for(int port:new int[]{2021,1990}){MulticastSocket s=null;try{s=new MulticastSocket(null);s.setReuseAddress(true);s.bind(new InetSocketAddress(port));s.setSoTimeout(150);s.setTimeToLive(1);s.setBroadcast(true);sockets.add(s);for(NetworkInterface n:interfaces)try{s.joinGroup(new InetSocketAddress(group,port),n);}catch(Exception ignored){}}catch(Exception ignored){if(s!=null&&!sockets.contains(s))s.close();}}
            if(sockets.isEmpty()){MulticastSocket s=new MulticastSocket();s.setSoTimeout(150);s.setTimeToLive(1);s.setBroadcast(true);sockets.add(s);}
            byte[] query=SEARCH.getBytes(StandardCharsets.US_ASCII);MulticastSocket sender=sockets.get(0);
            for(NetworkInterface n:interfaces){sender.setNetworkInterface(n);for(int port:new int[]{2021,1990})try{sender.send(new DatagramPacket(query,query.length,group,port));}catch(Exception ignored){}try{sender.send(new DatagramPacket(query,query.length,InetAddress.getByName("255.255.255.255"),2021));}catch(Exception ignored){}}
            long end=System.nanoTime()+8000000000L;byte[] buffer=new byte[8193];
            while(!canceled.get()&&System.nanoTime()<end&&found.size()<32){for(MulticastSocket socket:sockets){if(canceled.get())break;DatagramPacket packet=new DatagramPacket(buffer,buffer.length);try{socket.receive(packet);if(packet.getLength()>8192)continue;Found printer=parse(new String(packet.getData(),0,packet.getLength(),StandardCharsets.UTF_8),packet.getAddress().getHostAddress());if(printer!=null)found.put(printer.serial,printer);}catch(SocketTimeoutException expected){}}}
            return new ArrayList<>(found.values());
        }finally{for(MulticastSocket socket:sockets)socket.close();}
    }
}
