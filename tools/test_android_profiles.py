"""Build and run a same-signer Android instrumentation test, with no printer I/O.

Requires build.py output, its signing environment and explicit ADB target.
Refuses existing printer profiles. Installs then removes only its test APK.
"""
import argparse
import os
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--adb", required=True)
    p.add_argument("--serial", required=True)
    args = p.parse_args()
    tools = Path(os.environ["ANDROID_BUILD_TOOLS"])
    platform = Path(os.environ["ANDROID_PLATFORM_JAR"])
    build = ROOT / "build/profile-tests"
    classes = build / "classes"
    classes.mkdir(parents=True, exist_ok=True)
    manifest = build / "AndroidManifest.xml"
    manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dk.ultrabar.bambu.tests" android:versionCode="1" android:versionName="1.0">
 <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="29"/>
 <instrumentation android:name="dk.ultrabar.bambu.tests.ProfileProbe" android:targetPackage="dk.ultrabar.bambu"/>
 <application android:label="Bambu profile tests" android:debuggable="false"/>
</manifest>''')

    def run(*parts):
        subprocess.run(list(map(str, parts)), check=True)

    cp = os.pathsep.join(map(str, (platform, ROOT / "build/classes")))
    run("javac", "-encoding", "UTF-8", "-source", "8", "-target", "8", "-cp", cp, "-d", classes, ROOT / "tests/android/ProfileProbe.java")
    run("java", "-cp", tools / "lib/d8.jar", "com.android.tools.r8.D8", "--lib", platform, "--min-api", "26", "--output", build, *classes.glob("**/*.class"))
    suffix = ".exe" if os.name == "nt" else ""
    apk = build / "unsigned.apk"
    run(tools / ("aapt" + suffix), "package", "-f", "-M", manifest, "-I", platform, "-F", apk)
    with zipfile.ZipFile(apk, "a") as z:
        z.write(build / "classes.dex", "classes.dex")
    run(tools / ("zipalign" + suffix), "-f", "4", apk, build / "aligned.apk")
    run("java", "-jar", tools / "lib/apksigner.jar", "sign", "--ks", os.environ["ANDROID_KEYSTORE"], "--ks-pass", "env:ANDROID_KEY_PASSWORD", "--out", build / "tests.apk", build / "aligned.apk")
    adb = [args.adb, "-s", args.serial]
    run(*adb, "install", "--no-incremental", "-r", build / "tests.apk")
    try:
        result = subprocess.check_output(adb + ["shell", "am", "instrument", "-r", "-w", "dk.ultrabar.bambu.tests/dk.ultrabar.bambu.tests.ProfileProbe"], timeout=30).decode()
        print(result)
        if "PASS:" not in result or "INSTRUMENTATION_CODE: -1" not in result:
            raise SystemExit("Profile instrumentation failed")
    finally:
        run(*adb, "uninstall", "dk.ultrabar.bambu.tests")
        run(*adb, "shell", "am", "force-stop", "dk.ultrabar.bambu")


if __name__ == "__main__":
    main()
