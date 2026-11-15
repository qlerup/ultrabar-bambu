package dk.ultrabar.bambu;

import javax.net.ssl.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;

public final class PrinterTls {
    public static String fingerprint(X509Certificate cert) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
        StringBuilder s = new StringBuilder();
        for (byte b : digest) s.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return s.toString();
    }
    private static SSLSocketFactory factory(String pin, boolean probe) throws Exception {
        if (!probe && (pin == null || !pin.matches("[a-f0-9]{64}"))) throw new CertificateException("Missing trusted certificate");
        SSLContext tls = SSLContext.getInstance("TLSv1.2");
        tls.init(null, new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain, String auth) throws CertificateException { throw new CertificateException(); }
            public void checkServerTrusted(X509Certificate[] chain, String auth) throws CertificateException {
                if (chain == null || chain.length == 0) throw new CertificateException("Missing certificate");
                // The initial probe sends no MQTT credentials. Authentication always requires a user-approved pin.
                if (probe) return;
                try { if (!pin.equals(fingerprint(chain[0]))) throw new CertificateException("Printer certificate changed"); }
                catch (CertificateException e) { throw e; } catch (Exception e) { throw new CertificateException(e); }
            }
        }}, new SecureRandom());
        return tls.getSocketFactory();
    }
    public static SSLSocket create(String host, String pin, boolean probe) throws Exception {
        if (!PrinterState.privateIpv4(host)) throw new IllegalArgumentException("Use a private LAN IPv4 address");
        return (SSLSocket)factory(pin, probe).createSocket();
    }
    public static void connect(SSLSocket socket, String host) throws Exception {
        socket.connect(new InetSocketAddress(host, 8883), 6000);
        socket.setSoTimeout(6000); socket.startHandshake();
    }
    public static String probe(String host) throws Exception {
        try (SSLSocket socket = create(host, null, true)) {
            connect(socket, host);
            return fingerprint((X509Certificate)socket.getSession().getPeerCertificates()[0]);
        }
    }
}
