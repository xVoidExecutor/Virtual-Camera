# Android 16 embedded virtual runtime port

## Goal

Replace the Android Work Profile dependency with an in-process/host-managed virtual application runtime, while retaining the existing camera/media assistant features.

## Runtime contract

A clone is only considered runnable after all of these work on a physical Android 16 device:

1. Import an APK and validate its package name, version, ABI/native libraries, and signing metadata.
2. Persist a clone record and APK under the host app's private storage; never claim that copying files installs an Android package.
3. Create isolated per-clone data, files, cache, preferences, databases, and virtual external-storage mappings.
4. Resolve virtual package metadata and route launcher intents into the runtime.
5. Dispatch Activities, Services, BroadcastReceivers, ContentProviders, permissions, and package-manager queries through proxy components.
6. Handle process lifecycle, task/back-stack behavior, notifications, pending intents, and app updates.
7. Add native/ART compatibility only where required and test 32-bit and 64-bit APKs.
8. Keep camera frame injection as a separate capability and verify it against Camera1 and Camera2 clients.

## Android 16 compatibility work

Android 16 is API 36. A virtualization engine cannot be made API-36-compatible by changing `compileSdk` or `targetSdk` alone. The port must audit and test:

- Hidden framework API and reflection assumptions in package parsing and Activity launch.
- ActivityThread / ClientTransaction / instrumentation lifecycle integration.
- PackageManager and permission behavior changes.
- ART/JNI/native inline-hook assumptions, ABI support, linker namespaces, and SELinux boundaries.
- Background execution limits, foreground-service requirements, notifications, and pending intents.
- Scoped storage and per-app data redirection.

## Current implementation status

The app currently has a real Android-managed Work Profile flow and an Xposed camera hook, but it does **not** yet contain the embedded virtual runtime described above. `VirtualSpaceManager.cloneAppToVirtualSpace()` deliberately returns false instead of treating a directory as a working clone.

The Android 14-based VirtualApp fork is a candidate source engine, not a verified Android 16 dependency. Its virtualization core (Java and native code) must be vendored or maintained as a dedicated module, then ported and tested. Merely adding a Gradle dependency or renaming the target API is not sufficient.

## Acceptance gates

- Build succeeds from a clean checkout using the checked-in Gradle wrapper.
- Install and launch on Android 16 (API 36) without Work Profile provisioning.
- Clone at least two instances of a test app and verify separate preferences, databases, login state, and files.
- Launch/stop/relaunch cloned Activities and Services without host-app crashes.
- Verify camera/media functionality independently; do not report Camera2 support until tested.
- Fail safely for incompatible APKs and show actionable UI errors.

Until these gates pass, the UI must not label an APK as a successfully cloned/runnable app.
