# Bambu widget source

For release 2.x this is the reference renderer for Studio-owned printer
connections. Core receives sanitized readings through Studio's existing metrics
channel. The separate Store widget requires Studio 0.48.0+ and no companion APK.
The description below documents the historical 1.x app bundle only.

This is the Bambu-specific native widget source used by the trusted Studio host.
The same repository therefore contains both the Android app (`src/`) and widget
descriptor/renderer (`widget.json`, `studio-support/native-widgets/`).

They are one Bambu Store product and one signed APK installation. The APK carries
`assets/ultrabar-widgets.json`; Studio detects the descriptor from the verified
APK, exposes one library widget and automatically stages the app in Background
apps when that widget is placed. The app is not a second library product.

Studio's generic support for app/widget bundles is a platform update. It keeps
contributor bytecode out of the privileged launcher: the renderer is compiled
into the trusted Studio native runtime, while the APK owns printer credentials,
discovery and network control. The files here record the Bambu implementation
alongside its app instead of requiring a separate Bambu-widget repository.
