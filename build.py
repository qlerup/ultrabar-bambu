"""Build a signed release APK using Android SDK build tools and JDK 17/21.

No Gradle, Maven, runtime downloads or native libraries are required.
Signing material must be supplied outside the repository.
"""
import os
import shutil
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def main():
    tools = Path(os.environ["ANDROID_BUILD_TOOLS"])
    platform = Path(os.environ["ANDROID_PLATFORM_JAR"])
    keystore = Path(os.environ["ANDROID_KEYSTORE"])
    if not os.environ.get("ANDROID_KEY_PASSWORD") or not keystore.is_file():
        raise SystemExit("Supply a private ANDROID_KEYSTORE and ANDROID_KEY_PASSWORD.")
    build = ROOT / "build"
    if build.exists():
        if build.resolve().parent != ROOT.resolve():
            raise SystemExit("Unexpected build directory")
        shutil.rmtree(build)
    classes = build / "classes"
    classes.mkdir(parents=True)
    (ROOT / "dist").mkdir(exist_ok=True)

    def run(*args):
        subprocess.run([str(x) for x in args], cwd=ROOT, check=True)

    run("javac", "-encoding", "UTF-8", "-source", "8", "-target", "8", "-classpath", platform,
        "-d", classes, *sorted(ROOT.glob("src/**/*.java")))
    run("java", "-cp", tools / "lib/d8.jar", "com.android.tools.r8.D8", "--lib", platform,
        "--min-api", "26", "--output", build, *sorted(classes.glob("**/*.class")))
    suffix = ".exe" if os.name == "nt" else ""
    unsigned = build / "unsigned.apk"
    run(tools / ("aapt" + suffix), "package", "-f", "-M", ROOT / "AndroidManifest.xml",
        "-I", platform, "-S", ROOT / "res", "-F", unsigned)
    with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(build / "classes.dex", "classes.dex")
        archive.write(ROOT / "studio-settings.json", "assets/studio-settings.json")
        archive.write(ROOT / "widget.json", "assets/ultrabar-widgets.json")
    run(tools / ("zipalign" + suffix), "-f", "4", unsigned, build / "aligned.apk")
    run("java", "-jar", tools / "lib/apksigner.jar", "sign", "--ks", keystore,
        "--ks-pass", "env:ANDROID_KEY_PASSWORD", "--v1-signing-enabled", "true",
        "--v2-signing-enabled", "true", "--out", ROOT / "dist/app.apk", build / "aligned.apk")
    run("java", "-jar", tools / "lib/apksigner.jar", "verify", "--verbose", "--print-certs", ROOT / "dist/app.apk")
    print("Release APK: dist/app.apk")


if __name__ == "__main__":
    main()
