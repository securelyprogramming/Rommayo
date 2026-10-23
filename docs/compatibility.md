# Interface coverage and validation

Android implements the main library in native Views using RomM's API. Full RomM and desktop expose the complete upstream website. Features still depend on RomM permissions, server version, browser capabilities, BIOS/core setup and device.

Initial live validation used RomM 5.4.0, a Galaxy S24 Ultra and macOS Apple Silicon. No ROMs, BIOS files, server addresses or credentials from that environment are included in the release.

| Area | Implementation | Validation |
| --- | --- | --- |
| Username/password login | RomM Basic login → native/browser session cookies | Live Android, desktop and web login checks |
| Session restoration | Native cookie store; password never persisted by wrapper | Automated desktop restart; Android force-stop check |
| Android library, search, platforms, recents, details | Native Android Views and paged RomM API | Galaxy S24 Ultra native hierarchy and live library checks |
| Collections and advanced tools | Full RomM web fallback | Interface inherited; operations not exhaustively tested |
| Favorites, notes, saves, metadata and administration | Actual RomM pages and server permissions | Interface inherited; each operation not exhaustively tested |
| HTTP and generated blob downloads | Desktop save dialog; Android same-origin streaming / document picker | Automated desktop HTTP/blob checks; Java HTTP harness rejects redirects and foreign origins; Android save-picker device coverage remains limited |
| Uploads | Browser/Android document picker | Automated desktop file selection and proxy streaming checks |
| Browser emulation | RomM's own players, engines and configured cores | Player smoke check; no all-platform game certification |
| SSO | RomM sign-in with explicit external-origin navigation | Implemented; provider-specific flows not certified |
| Android menu controllers | Android key/axis input, focus, A/B swap, dead zone, controller check | Synthesized key/axis checks; owner confirmed Retroid Pocket 5 D-pad navigation and the Recent filter A/open, D-pad/select and B/cancel flow |
| Steam Deck installation / launch | Checksum-verified AppImage and --deck launcher | Physical Deck install, Desktop Mode login/library and C64 homebrew core boot; Gaming Mode app launch after excluding overlay injection, 2026-10-22 |
| Gameplay controllers / fullscreen | Browser player mapping and native web engine | Real Deck Desktop Mode fullscreen and touch toggle pass; physical Deck/gamepad mappings remain under test |
| Web streaming | HTTP streaming, ranges, WebSocket upgrades | Automated forwarding checks; remote emulator streaming not certified |

HTTPS is needed for some browser APIs. An HTTP LAN server may browse correctly while gamepads, microphone access, WebRTC or threaded emulator features are unavailable. Rommayo never bypasses certificate errors or Chromium's same-origin checks.

Android uses the installed Android System WebView and requires Android 10+. Keep WebView updated. Sign-in and library screens have no WebView. The isolated RomM/player view has a token-gated download helper and an origin-restricted player-exit message channel; neither provides general native scripting. File, camera, microphone, notifications, WebAuthn and unusual popup flows can differ from a full browser. Device permissions beyond fullscreen/pointer lock are not automatically granted on desktop. If a server feature needs a web API the host does not support, use a full browser and report the gap.

The theme supports RomM's Vuetify pages and 5.4's component tokens. Disable it on the connection screen if a newer server theme changes unexpectedly. Public alpha means **full interface access with explicit platform caveats**, not a claim of universal emulator compatibility.

## Tested devices

Physical hardware checks recorded October 22, 2026. These are specific smoke tests, not certification of every game, core or controller. CI builds for Windows and Intel Mac do not count as physical device tests. No device serials or private server details are published.

| Device | Environment | Verified | Still to check |
| --- | --- | --- | --- |
| Samsung Galaxy S24 Ultra | Android 17 | Signed APK; native library; remembered login after force-stop. | Physical external-controller gameplay not verified. |
| Retroid Pocket 5 | Android 13 | Signed APK; landscape native library; remembered login after force-stop; built-in controller detected; owner confirmed physical D-pad navigation and the Recent filter A/open, D-pad/select and B/cancel flow. | Gameplay bindings still require per-core checks. |
| Steam Deck | SteamOS, docked 1920 × 1080 | Linux 0.2.9 candidate: Desktop Mode remembered login, C64 core boot, fullscreen touch toggle, Cancel/Exit returning home, and remembered touch-control preference. Earlier Gaming Mode launch also verified. | Current-update Gaming Mode and physical gameplay controls remain under test. |
| MacBook Pro (Apple M4 Pro) | macOS, Apple Silicon | Electron launch, session and download regression tests. | Other emulator cores and systems are not certified. |

[Setup and current device notes](https://rommayo.quentinmayo.com/getting-started#tested-devices).
