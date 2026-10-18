# Virtual runtime and camera API integration status

## Current status

This repository does not currently contain an embedded Android application runtime. The previous `VirtualSpaceManager.cloneAppToVirtualSpace` implementation only created a private directory and returned success; it did not install, load, or run the selected APK. That false-success behavior has been removed and the UI now disables cloning until a real runtime backend exists.

The existing Xposed entry point is diagnostic only. Registering `Camera.addCallbackBuffer` is not frame injection, and it does not cover Camera2.

## Runtime work required

A real embedded runtime must provide, at minimum:

1. APK installation/parsing inside the host without installing the package for the Android user.
2. Per-virtual-app data, package identity, and process isolation.
3. Activity/service/receiver/provider routing and package-manager virtualization.
4. Runtime compatibility with Android 16 hidden-API restrictions, storage rules, and process lifecycle.
5. A maintained upstream runtime/framework integration and license review.

A private directory, work profile provisioning, or a launcher shortcut is not a substitute for these pieces. The legacy VirtualXposed project targets older Android releases; it must not be assumed compatible with Android 16 without porting and device tests.

## Camera API integration required

### Camera1
- Hook callback registration and delivery, not just `addCallbackBuffer`.
- Obtain the selected media stream through a permission-safe cross-process source.
- Decode frames and convert to the exact preview dimensions/format (typically NV21), respecting callback buffer size and camera lifecycle.
- Preserve the original callback and fall back safely when the virtual source is unavailable.

### Camera2
- Camera2 applications commonly receive frames through `CameraCaptureSession` output `Surface` objects.
- Correct injection needs API-specific session/output handling and a producer surface driven by a decoded media stream. Camera1 callback hooks cannot cover this path.
- Handle session recreation, target surface changes, rotation, timestamps, and app/device variations.

### Validation gate
Do not advertise the feature as active until automated build checks pass and device tests confirm Camera1 and Camera2 clients receive replacement frames. Target-app tests must be performed on a test device with the required Xposed/LSPosed setup. CI compilation alone cannot validate camera injection.
