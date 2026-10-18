# Android 16 Virtual Space implementation plan

## Current verified state

- The `fix/build-ready-media-assistant` branch produced a successful GitHub Actions debug build (run #12). This validates compilation and artifact upload only; it does not validate runtime behavior on a phone.
- The current app UI is a local media picker/overlay assistant, not an embedded Android runtime.
- `xVoidExecutor/VirtualXposed` identifies itself as supporting Android 5.0–10.0 and uses an old Android toolchain (compile SDK 28, target SDK 23, Support Library 27). It must not be assumed compatible with Android 16.
- The current `VirtualCameraHook` only has diagnostic hook points. It does not implement real camera-frame or microphone-audio replacement.

## Recommended implementation order

1. Preserve the current buildable media assistant and restore/verify the existing screens before expanding the app.
2. Build an app-catalog/import flow: enumerate launchable installed apps where Android package-visibility policy permits, and accept user-selected APK files using the system document picker. Do not describe this as cloning until the package is installed and launched inside an isolated runtime.
3. Evaluate the VirtualApp/VirtualXposed runtime against Android 16 APIs, process model, package parsing, storage, notifications, foreground services, ABI/native libraries and lifecycle behavior. This is a separate engineering project, not a dependency-only change.
4. Define a virtualization interface so the app catalog can use a tested runtime adapter rather than launching the original host app by mistake.
5. Implement media replacement per camera API and target app, with explicit test coverage for Camera1, Camera2 and CameraX where relevant. Camera preview inside this app is not proof of replacement inside another app.
6. Test on a physical Android 16 device and report per-app compatibility rather than promising universal compatibility.

## Safety and correctness constraints

- Do not spoof Android version/device identity or conceal framework traces as a substitute for compatibility. Those hooks can break apps and produce misleading diagnostics.
- Do not claim camera/audio injection is active until frames/samples are actually delivered in the target process and verified.
- Keep permissions user-granted and transparent. Do not bypass another app's security checks or promise that detection can be defeated.
- App cloning must preserve user data boundaries and clearly explain where imported APKs and virtual app data are stored.

## Acceptance checklist

- [ ] Debug build passes on CI.
- [ ] App catalog lists installed launchable apps without crashing on Android 16.
- [ ] APK import validates package metadata and handles unsupported APKs gracefully.
- [ ] A selected app installs and launches inside the virtual runtime (not the host installation).
- [ ] Runtime storage and permissions are isolated and documented.
- [ ] Camera replacement is demonstrated in a target app on a physical device.
- [ ] Audio replacement is demonstrated independently with correct sample format and lifecycle handling.
