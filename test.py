"""Host-side protocol/telemetry tests. The JSON jar is test-only, checksum pinned.

Use --jar PATH for an already downloaded copy (offline). No test dependency is
included in the APK. TLS integration tests require Python cryptography.
"""
import argparse
import hashlib
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
JSON_SHA = "3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed"


def main():
    subprocess.run([sys.executable, str(ROOT / 'tests/presets_test.py')], check=True)
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path)
    args = parser.parse_args()
    jar = args.jar or ROOT / "build/test-deps/json.jar"
    if not jar.exists():
        jar.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen("https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar", timeout=30) as response:
            jar.write_bytes(response.read(1024 * 1024))
    if hashlib.sha256(jar.read_bytes()).hexdigest() != JSON_SHA:
        raise SystemExit("Test dependency checksum mismatch")
    classes = ROOT / "build/tests"
    classes.mkdir(parents=True, exist_ok=True)
    sources = [ROOT / "src/dk/ultrabar/bambu" / (name + ".java") for name in ("MqttWire", "PrinterState", "PrinterTls", "PrinterLink", "Discovery")]
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", str(classes), *map(str, sources), *map(str, ROOT.glob("tests/*.java"))], check=True)
    import os
    cp = os.pathsep.join((str(classes), str(jar)))
    subprocess.run(["java", "-cp", cp, "dk.ultrabar.bambu.PanelTest"], check=True)
    subprocess.run([sys.executable, str(ROOT / "tests/tls_integration.py"), cp], check=True)


if __name__ == "__main__":
    main()
