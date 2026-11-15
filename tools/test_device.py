"""Explicit physical CoreBAR settings smoke test; never supplies printer credentials.

Requires an installed APK and root-enabled ADB. Restores existing display settings.
No device identifiers or addresses are written to the source tree.
"""
import argparse
import json
import re
import subprocess
import time
import uuid
from pathlib import Path

PACKAGE = "dk.ultrabar.bambu"


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--adb", required=True)
    p.add_argument("--serial", required=True)
    args = p.parse_args()
    adb = [args.adb, "-s", args.serial]

    def call(*parts, data=None):
        return subprocess.check_output(adb + list(parts), input=data, timeout=20)

    assert call("shell", "id", "-u").strip() == b"0"
    dump = call("shell", "dumpsys", "package", PACKAGE).decode()
    uid = re.search(r"^\s*userId=(\d+)\s*$", dump, re.M).group(1)
    assert 10000 <= int(uid) < 20000
    base = "/data/user/0/" + PACKAGE + "/files/ultrabar-studio"

    def exchange(values=None, invalid=False):
        revision = uuid.uuid4().hex
        request = dict(api=1, revision=revision, operation="read" if values is None else "apply")
        if values is not None:
            request["values"] = values
        raw = json.dumps(request, separators=(",", ":")).encode()
        call("shell", f"su {uid} sh -c 'mkdir -p {base}; chmod 700 {base}'")
        call("exec-in", f"su {uid} sh -c 'umask 077; cat > {base}/request.tmp'", data=raw)
        call("shell", f"su {uid} sh -c 'chmod 600 {base}/request.tmp; mv {base}/request.tmp {base}/request.json'")
        assert call("exec-out", "su", uid, "cat", base + "/request.json") == raw
        call("shell", "am", "broadcast", "--include-stopped-packages", "-n", PACKAGE + "/.StudioSettingsReceiver", "-a", PACKAGE + ".STUDIO_SETTINGS")
        for _ in range(20):
            response = json.loads(call("shell", f"su {uid} sh -c 'cat {base}/response.json'").decode())
            if response.get("revision") == revision:
                assert response["status"] == ("error" if invalid else "ok")
                return response.get("values")
            time.sleep(.1)
        raise AssertionError("Missing durable settings confirmation")

    original = exchange()
    try:
        changed = dict(printerName="Core test", allowControls=True, showTemperatures=False, keepScreenOn=True)
        assert exchange(changed) == changed
        assert exchange() == changed
        call("shell", "am", "force-stop", PACKAGE)
        assert exchange() == changed
        invalids = [dict(changed, allowControls="yes"), dict(changed, printerName="x" * 49), dict(changed, unknown=True), dict(changed, keepScreenOn=1)]
        for invalid in invalids:
            exchange(invalid, invalid=True)
            assert exchange() == changed
        print("Physical Core settings passed: read, all fields, durable apply, restart and four rejection paths")
    finally:
        assert exchange(original) == original
        # Leave the app stopped after the test; no printer connection has been added.
        call("shell", "am", "force-stop", PACKAGE)


if __name__ == "__main__":
    main()
