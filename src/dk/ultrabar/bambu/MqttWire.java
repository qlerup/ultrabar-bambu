package dk.ultrabar.bambu;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Bounded MQTT 3.1.1 framing. Clean sessions; QoS 1 writes are never replayed. */
public final class MqttWire {
    public static final int MAX_PACKET = 262144;
    public static final class Packet {
        public final int header;
        public final byte[] body;
        Packet(int header, byte[] body) { this.header = header; this.body = body; }
        public int type() { return header >>> 4; }
    }
    public static Packet read(int first, InputStream in) throws IOException {
        if (first < 0) throw new EOFException();
        int size = 0, multiplier = 1;
        for (int i = 0; i < 4; i++) {
            int b = in.read();
            if (b < 0) throw new EOFException();
            size += (b & 127) * multiplier;
            if (size > MAX_PACKET) throw new IOException("MQTT packet too large");
            if ((b & 128) == 0) {
                byte[] data = new byte[size];
                new DataInputStream(in).readFully(data);
                return new Packet(first, data);
            }
            multiplier *= 128;
        }
        throw new IOException("Invalid MQTT length");
    }
    public static synchronized void write(OutputStream out, int header, byte[] data) throws IOException {
        if (data.length > MAX_PACKET) throw new IOException("MQTT packet too large");
        out.write(header);
        int n = data.length;
        do { int digit = n % 128; n /= 128; out.write(digit | (n > 0 ? 128 : 0)); } while (n > 0);
        out.write(data); out.flush();
    }
    public static void utf(DataOutputStream out, String text) throws IOException {
        byte[] raw = text.getBytes(StandardCharsets.UTF_8);
        if (raw.length > 65535 || text.indexOf('\0') >= 0) throw new IOException("Invalid MQTT string");
        out.writeShort(raw.length); out.write(raw);
    }
    public static byte[] connect(String id, String password) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        utf(out, "MQTT"); out.writeByte(4); out.writeByte(0xC2); out.writeShort(30);
        utf(out, id); utf(out, "bblp"); utf(out, password);
        return bytes.toByteArray();
    }
    public static byte[] subscribe(int id, String topic) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(id); utf(out, topic); out.writeByte(1);
        return bytes.toByteArray();
    }
    public static byte[] publish(int id, String topic, byte[] payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        utf(out, topic); out.writeShort(id); out.write(payload);
        return bytes.toByteArray();
    }
    public static final class Publication {
        public final String topic;
        public final byte[] payload;
        public final int packetId;
        public Publication(Packet p) throws IOException {
            int qos = (p.header >>> 1) & 3;
            if (p.type() != 3 || qos > 1 || (p.header & 1) != 0) throw new IOException("Unsupported/retained MQTT report");
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(p.body));
            int len = in.readUnsignedShort();
            if (len == 0 || len > in.available()) throw new IOException("Invalid MQTT topic");
            byte[] topicBytes = new byte[len]; in.readFully(topicBytes);
            topic = new String(topicBytes, StandardCharsets.UTF_8);
            packetId = qos == 1 ? in.readUnsignedShort() : 0;
            if (qos == 1 && packetId == 0) throw new IOException("Invalid MQTT packet id");
            payload = new byte[in.available()]; in.readFully(payload);
        }
    }
}
