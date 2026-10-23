# Android controller menu checks

The `ControllerMenuSmoke` instrumentation runner uses Android Views on a running device. It requires no ROM, BIOS or server account. Run it on an emulator or isolated debug install; the signed release apps use their own signing identity.

```sh
cd android
./gradlew assembleDebug assembleDebugAndroidTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.rommayo.app.test/com.rommayo.app.ControllerMenuSmoke
```

Keep the device awake and unlocked. For an isolated debug build configured with `applicationIdSuffix '.diagnostics'`, use `com.rommayo.app.diagnostics.test` as the instrumentation package. The runner must print `PASS`; build success alone does not establish working navigation. CI compiles the runner and runs lint, but does not run this physical-device test.

Checks cover spatial focus between actions, grid columns/rows, restoring a missing selection, actual settings navigation through D-pad keys, joystick and HAT axes, hold/release repeat, A to open Controller Check and B to return. Dialog checks verify that background menu repetition stops and the opening A/B release cannot select or dismiss the popup; a fresh press must work. These are synthesized input events. They do not certify physical controller mappings or gameplay.

For physical testing, open Library and Recent, move between game cards, filters and navigation with both the D-pad and left stick, open a card with A and return with B. On Recent, focus All platforms and press A once: the dialog must stay open after release. Move with the D-pad, select with a fresh A press, then reopen and cancel with B. Check Settings as well. Tap an item using the screen, then return to the controller and verify the visible highlight moves. Repeat with the app restarted. Do not record a pass for a controller merely because Android detects it.

The isolated instrumentation runner also checks the Android player host: the touch toggle leaves the toolbar available, unknown messages are ignored, and an EmulatorJS exit returns to the native library. This regression uses a local fixture and synthesized input; it does not certify physical gameplay mappings.
