"""Ephemeral local TLS MQTT printer fixture. Never connects to a real printer.

The production LAN address rule is preserved: the fixture binds this machine's
own private IPv4, port 8883. Override automatic selection with BAMBU_TEST_ADDRESS.
"""
import datetime
import hashlib
import json
import os
import socket
import ssl
import struct
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID


def exact(stream, count):
    data = b""
    while len(data) < count:
        chunk = stream.recv(count - len(data))
        if not chunk:
            raise EOFError()
        data += chunk
    return data


def read_packet(stream):
    header = exact(stream, 1)[0]
    length, multiplier = 0, 1
    for _ in range(4):
        digit = exact(stream, 1)[0]
        length += (digit & 127) * multiplier
        if not digit & 128:
            return header, exact(stream, length)
        multiplier *= 128
    raise AssertionError("Malformed test packet")


def packet(header, body):
    length = len(body)
    prefix = bytearray([header])
    while True:
        digit, length = length % 128, length // 128
        prefix.append(digit | (128 if length else 0))
        if not length:
            return bytes(prefix) + body


def report(stream, **values):
    topic = b"device/TESTSERIAL/report"
    body = struct.pack(">H", len(topic)) + topic + struct.pack(">H", 300)
    body += json.dumps({"print": {"command": "push_status", **values}}).encode()
    stream.sendall(packet(0x32, body))


def broker(stream, mode):
    header, body = read_packet(stream)
    assert header == 0x10 and body[:10] == b"\x00\x04MQTT\x04\xc2\x00\x1e"
    assert b"bblp" in body and body.endswith(b"\x00\x08TESTCODE")
    stream.sendall(packet(0x20, b"\0\0"))
    header, body = read_packet(stream)
    assert header == 0x82 and b"device/TESTSERIAL/report" in body
    stream.sendall(packet(0x90, body[:2] + b"\x01"))
    seen = []
    while True:
        header, body = read_packet(stream)
        if header == 0x40:
            continue
        if header == 0xC0:
            stream.sendall(packet(0xD0, b""))
            continue
        assert header == 0x32, "Commands must be QoS 1, non-retained"
        topic_len = struct.unpack(">H", body[:2])[0]
        assert body[2:2 + topic_len] == b"device/TESTSERIAL/request"
        msg_id = body[2 + topic_len:4 + topic_len]
        request = json.loads(body[4 + topic_len:])
        stream.sendall(packet(0x40, msg_id))
        command = next(iter(request.values()))["command"]
        if command == "pushall":
            report(stream, gcode_state="RUNNING", mc_percent=42,
                   lights_report=[{"node": "chamber_light", "mode": "off"}])
        elif command == "pause":
            seen.append(command)
            if mode == "timeout":
                # PUBACK without a state transition is deliberately insufficient.
                try:
                    while True:
                        extra_header, _ = read_packet(stream)
                        assert extra_header != 0x32, "Unconfirmed command must not be replayed"
                        if extra_header == 0xC0:
                            stream.sendall(packet(0xD0, b""))
                except EOFError:
                    return
            report(stream, gcode_state="PAUSE")
        elif command == "resume":
            seen.append(command)
            report(stream, gcode_state="RUNNING")
        elif command == "ledctrl":
            seen.append(command)
            assert request["system"]["led_node"] == "chamber_light"
            report(stream, lights_report=[{"node": "chamber_light", "mode": "on"}])
        elif command == "stop":
            seen.append(command)
            report(stream, gcode_state="IDLE")
            # Let the client consume status before deliberately ending the connection.
            assert read_packet(stream) == (0x40, struct.pack(">H", 300))
            assert seen == ["pause", "resume", "ledctrl", "stop"]
            time.sleep(0.25)
            return
        else:
            raise AssertionError("Unexpected command")


def main():
    cp = sys.argv[1]
    host = os.environ.get("BAMBU_TEST_ADDRESS")
    if not host:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as route:
            route.connect(("192.0.2.1", 9))  # Select local interface without sending a datagram.
            host = route.getsockname()[0]
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Bambu Panel test fixture")])
    now = datetime.datetime.now(datetime.timezone.utc)
    cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key())
            .serial_number(x509.random_serial_number()).not_valid_before(now - datetime.timedelta(minutes=1))
            .not_valid_after(now + datetime.timedelta(days=1)).sign(key, hashes.SHA256()))
    pin = hashlib.sha256(cert.public_bytes(serialization.Encoding.DER)).hexdigest()
    with tempfile.TemporaryDirectory(prefix="bambu-tls-tests-") as temporary:
        root = Path(temporary)
        (root / "cert.pem").write_bytes(cert.public_bytes(serialization.Encoding.PEM))
        (root / "key.pem").write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(root / "cert.pem", root / "key.pem")
        for mode in ("probe", "wrongpin", "session", "timeout"):
            errors = []
            with socket.socket() as listener:
                listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                listener.bind((host, 8883))
                listener.listen(1)
                listener.settimeout(15)

                def serve():
                    try:
                        raw, _ = listener.accept()
                        with raw:
                            raw.settimeout(10)
                            try:
                                with context.wrap_socket(raw, server_side=True) as stream:
                                    if mode in ("session", "timeout"):
                                        broker(stream, mode)
                                    else:
                                        assert stream.recv(1) == b"", "Probe must not transmit credentials"
                            except ssl.SSLError:
                                if mode != "wrongpin":
                                    raise
                    except BaseException as exc:
                        errors.append(exc)

                thread = threading.Thread(target=serve, daemon=True)
                thread.start()
                subprocess.run(["java", "-cp", cp, "dk.ultrabar.bambu.LinkHarness", host, pin, mode], check=True, timeout=40)
                thread.join(16)
                if thread.is_alive():
                    raise AssertionError("Test broker did not finish")
                if errors:
                    raise errors[0]
        print("TLS pin and timeout tests passed: credential-free probe, changed-certificate rejection, no PUBACK false success or replay")


if __name__ == "__main__":
    main()
