"""Build a local-only preview APK with the exact trusted Studio native renderer.

Pass --studio PATH. No actual printer calls are exposed by the preview. This is
not the production APK, a Store package, or evidence of deployed home integration.
"""
import argparse
import os
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--studio", required=True, type=Path)
    args = p.parse_args()
    tools = Path(os.environ["ANDROID_BUILD_TOOLS"])
    platform = Path(os.environ["ANDROID_PLATFORM_JAR"])
    build = ROOT / "build/liquid-preview"
    classes = build / "classes"
    classes.mkdir(parents=True, exist_ok=True)
    manifest = build / "AndroidManifest.xml"
    manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dk.ultrabar.bambu.preview" android:versionCode="1" android:versionName="1.0">
 <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="29"/>
 <application android:label="Bambu liquid design preview" android:theme="@android:style/Theme.Material.NoActionBar" android:allowBackup="false" android:debuggable="false">
 <activity android:name=".LiquidPreviewActivity" android:exported="true" android:screenOrientation="landscape"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity>
 </application></manifest>''')

    def run(*parts):
        subprocess.run(list(map(str, parts)), check=True)

    native = args.studio / "native-widgets/src/dk/ultrabarx/widgets"
    run("javac", "-encoding", "UTF-8", "-source", "8", "-target", "8", "-cp", platform, "-d", classes,
        native / "BambuTile.java", native / "LiquidGeometry.java", ROOT / "tests/android/LiquidPreviewActivity.java")
    run("java", "-cp", tools / "lib/d8.jar", "com.android.tools.r8.D8", "--lib", platform, "--min-api", "26", "--output", build, *classes.glob("**/*.class"))
    suffix = ".exe" if os.name == "nt" else ""
    apk = build / "unsigned.apk"
    run(tools / ("aapt" + suffix), "package", "-f", "-M", manifest, "-I", platform, "-F", apk)
    with zipfile.ZipFile(apk, "a") as z:
        z.write(build / "classes.dex", "classes.dex")
    run(tools / ("zipalign" + suffix), "-f", "4", apk, build / "aligned.apk")
    run("java", "-jar", tools / "lib/apksigner.jar", "sign", "--ks", os.environ["ANDROID_KEYSTORE"], "--ks-pass", "env:ANDROID_KEY_PASSWORD", "--out", build / "preview.apk", build / "aligned.apk")
    print("Test-only preview: build/liquid-preview/preview.apk")


if __name__ == "__main__":
    main()
