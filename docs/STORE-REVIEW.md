# Release 1.0.1 review evidence

Historical Android-app review. Release 2.0.1 is a data-only Studio widget requiring
Studio 0.48.1+. It keeps the original `studio.bambu` Store registration and PC-owned
connections; placed widgets are preserved. Core credentials remain untouched. See the root README
for current setup and the remaining physical acceptance checks.

## Completed

- Independent public source repository, MIT license and original 512px PNG icon.
- CoreBAR app format: prebuilt signed release APK, complete pinned root archive,
  stable Android package ID, versionCode 101, versionName 1.0.1, minimum SDK 26.
- Pure Java/Dex, ARM32-compatible, enabled/exported MAIN/LAUNCHER activity.
- Exact permissions: INTERNET and FOREGROUND_SERVICE. No debugger, cleartext
  networking, boot receiver, wake lock or Android backup.
- Settings API 1, protected fixed receiver and identical embedded descriptor.
- Host tests cover MQTT framing/size bounds, malformed packets, QoS1, telemetry
  patches, command gates, disconnects, signed-command enforcement and legacy/new
  packed multi-extruder temperature reports.
- Local TLS fake-printer session covers connect/subscribe, pause/resume/stop/light,
  subsequent-state confirmation and disconnect cleanup. First probe transmits no
  credentials; changed certificate is rejected before MQTT authentication.
- Physical CB01, Android 10, LineOS 1.0.9: APK install and launch; ultrawide empty,
  preview and settings layouts visually inspected at 1424×280. No app crash seen.
- Physical settings receiver: current-value read, all four fields, durable apply,
  readback, process restart and four invalid-value/unknown-key rejection paths.
  Original settings restored after testing. No printer credentials supplied.
- Actual Android Keystore instrumentation: encrypted save/read, two profiles,
  selection, update-by-serial, isolated removal and final key deletion. Only
  synthetic profiles used; all test profiles and the test APK removed afterwards.
- Studio: 158 regression/browser tests covering independent
  per-card readings, sizes, controls/motion, Save, reload and Undo.
- Native geometry: spring endpoints/overshoot, 2–5 widths and edge anchors.
- Actual LineOS host probe: trusted launcher IPC accepted, temporary 2/3/4/5-slot
  expansion at 542/823/1104/1385px, 60px native rounded corners, neighbor movement,
  both edges, outside tap, Back, rapid taps, reduced motion and exact restoration
  of neighbor positions and source opacity passed. No saved layouts or profiles
  changed. Separate renderer preview APK uses synthetic data; no real printer.

## Required manual acceptance before Store approval

- Connect representative real printers, starting with the owner's H2D and H3C;
  verify live state, remaining time, layers, each nozzle and chamber/bed readings.
- Verify certificate approval, encrypted profile persistence, switching profiles,
  forgetting one profile without losing others, and credential preservation during
  same-signer upgrade. No production printer credentials have been tested yet.
- Test pause/resume/light and deliberate stop on a disposable print, plus blocked
  Developer Mode, stale/offline reports and command confirmation timeout.
- Test Store install → drag → Deploy with Studio 0.46+, read/apply settings through
  the actual Studio UI, update without data loss, library removal, APK uninstall
  and reinstall. Direct ADB receiver tests do not establish the entire Studio flow.
- Test earlier LineOS 1.0.8 before adding it to the compatibility declaration.

Source/binary equivalence is not established by a signature or static checks.
Store acceptance remains manual. The submission PR should remain draft until
the outstanding hardware tests have been performed and recorded honestly.

## Publication

One Store package PR changes only `submissions/bambu-panel/`. Its APK embeds
assets/ultrabar-widgets.json, identical to the root widget.json descriptor.
Studio stages app and widget in one install, adds one Background apps entry when
placing a widget, and installs the matching APK at Deploy. The package depends
on the Studio 0.46 runtime; Store validation support is included in the same Bambu
Store PR. It pins the source repository,
full 40-character commit and stable release; includes exact README, LICENSE, icon,
settings descriptor, APK and source.zip. Official immutable artifact/icon mirrors
and their SHA-256 metadata are required for the published catalog. Do not modify
catalog.json or replace release assets. New packages require maintainer approval;
passing checks does not establish compatibility with every printer.

## Deployment and rollback

Verify trusted APK signer and local package checks before deployment. Install or
update keeps controls disabled until explicitly enabled in app settings. Never
infer command success from MQTT delivery alone. Inspect the printer after a
timeout. No job start, file upload, heating or tool-changing commands are exposed.

Close consuming views and allow the service lease to expire. Disable controls or forget the selected
printer to stop further use. Remove the placed app and Deploy to remove its Core
launcher; uninstall `dk.ultrabar.bambu` to remove private data. Do not downgrade
with a different signing key. Android normally rejects lower versionCode; use a
forward versioned fix to preserve encrypted settings, or explicitly uninstall and
reconfigure if data removal is intended.
