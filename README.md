<p align="center"><img src="assets/banner.svg" alt="Rommayo — Your whole RomM. Wherever you press play." width="100%"></p>

<p align="center">
  <a href="https://github.com/quentinmayo/Rommayo/releases"><img alt="Downloads" src="https://img.shields.io/badge/Download-Public%20Alpha-66c0f4?style=for-the-badge&labelColor=171d25"></a>
  <a href="LICENSE"><img alt="MIT license" src="https://img.shields.io/badge/License-MIT-66c0f4?style=for-the-badge&labelColor=171d25"></a>
  <a href="https://github.com/rommapp/romm"><img alt="Made for RomM" src="https://img.shields.io/badge/Made%20for-RomM-66c0f4?style=for-the-badge&labelColor=171d25"></a>
</p>

**Your server. Your collection. Your next save point.**

[Explore the Rommayo website](https://rommayo.quentinmayo.com) · [Emulatormayo](https://emulatormayo.quentinmayo.com)

Rommayo brings your self-hosted [RomM](https://romm.app/) library to Android phones and tablets, desktop computers, Steam Deck, and a self-hosted web app. Android now has a **native, Steam-inspired library**: cover art, search, platform filters, recents and game details, with touch and controller navigation. RomM's web players handle emulation; **Full RomM** provides collections, saves, metadata and administration. Desktop uses Electron with the actual RomM website.

Enter your **server IP or URL, username, and password**. Rommayo signs into RomM and keeps the session cookie on your device. **Remember login** keeps the session between app restarts; uncheck it for a temporary session. Use **Settings → Log out** on Android or the desktop toolbar’s **Log out** button to end this device’s session without deleting local game saves. The wrapper does not save your password, and there is no Rommayo account or central service.

> **Public alpha:** The full RomM interface is available, but that does not mean every emulator and web API has been certified on every device. See the [compatibility and test matrix](docs/compatibility.md).

<p align="center"><img src="docs/screenshots/desktop-connect.png" alt="Rommayo's blue desktop connection screen" width="900"></p>

## Download & install

**No Node.js, npm or development server required.** Download the installer for your device below, then connect to your existing RomM server. These are the v0.2.8 public alpha packages.

## Pick your screen

| Device | Edition | Start here |
| --- | --- | --- |
| Android phone / tablet | Signed APK · Android 10+ | [Download APK](https://github.com/quentinmayo/Rommayo/releases/download/v0.2.8/Rommayo-v0.2.8-android.apk) |
| Steam Deck / Linux x64 | AppImage · fullscreen mode | [Download AppImage](https://github.com/quentinmayo/Rommayo/releases/download/v0.2.8/Rommayo-0.2.8-linux-x86_64.AppImage) · [Deck guide](docs/steam-deck.md) |
| Windows x64 | Desktop installer | [Download Windows installer](https://github.com/quentinmayo/Rommayo/releases/download/v0.2.8/Rommayo-0.2.8-win-x64.exe) |
| macOS Apple Silicon | Desktop disk image | [Download Apple Silicon DMG](https://github.com/quentinmayo/Rommayo/releases/download/v0.2.8/Rommayo-0.2.8-mac-arm64.dmg) |
| macOS Intel | Desktop disk image | [Download Intel DMG](https://github.com/quentinmayo/Rommayo/releases/download/v0.2.8/Rommayo-0.2.8-mac-x64.dmg) |
| Browser | Self-hosted web companion | [Web / Docker guide](docs/web.md) |

Verify downloads with [SHA256SUMS.txt](https://github.com/quentinmayo/Rommayo/releases/download/v0.2.8/SHA256SUMS.txt). The website is [rommayo.quentinmayo.com](https://rommayo.quentinmayo.com); the commands in “Build it yourself” are only for contributors.

Steam Deck support means a **non-Steam application** you add to your library. Rommayo is not listed on the Steam store, Steam Verified, or affiliated with Valve.

## The whole library comes along

- **Native Android library.** Android Views render sign-in, paged cover grids, search, platform filters, recents, details and settings directly from the RomM API.
- **Controller-friendly menus.** D-pad/stick navigation, A/B actions, shoulder navigation, dead-zone and A/B swap settings, compact cards, landscape mode and a controller check screen. See the [Android handheld guide](docs/android-handhelds.md).
- **Full RomM interface.** Use the same pages and permissions as your website, including features added by your server version.
- **Your existing login.** Native username/password sign-in, or RomM's own sign-in page for SSO.
- **Sessions that stick.** Cookies stay in the OS web engine's app storage. Switch servers without giving a central service your credentials.
- **Blue library theme.** Deep navy surfaces, cyan accents and compact cards. Desktop can disable the web theme on the connection screen. Android has a native blue library plus themed web players.
- **Native files.** Uploads use your file picker. Desktop supports normal and blob downloads; Android uses Downloads or a save dialog for generated files.
- **Room to play.** Fullscreen browser players, touch input and the web engine's gamepad support. No separate emulator binaries are bundled.
- **Self-hosted web option.** One configured upstream, independent browser sessions, streaming uploads/downloads and WebSocket forwarding.

## Connect in three steps

1. Run your own [RomM server](https://docs.romm.app/latest/getting-started/quick-start-guide/).
2. Install Rommayo and enter the same URL or IP you use in your browser. Include `http://` explicitly for an unencrypted LAN server; addresses without a scheme use HTTPS.
3. Enter your RomM username and password, acknowledge the server notice, and connect. Later, tap a recent server to resume its saved session. For SSO or an expired session, use RomM's own sign-in.

Your device must be able to reach your server. A private LAN IP needs the same network or a suitable VPN. HTTPS is recommended, especially for gamepad, WebRTC and other browser APIs that require a secure context. Certificate validation stays enabled.

On Android, the native Library/Recent/Settings navigation moves to a side rail in landscape and a bottom bar on phones. The player toolbar returns to the native library after confirming that you have saved your progress. Menu controller mappings never intercept gameplay inputs.

The desktop app's toolbar provides **Back**, **Home**, **Reload**, **Fullscreen**, and **Switch server**. Leaving fullscreen on Android uses the system Back gesture. Desktop shortcuts: `F11` fullscreen, `Esc` exit fullscreen, `Alt+Left` back, and `Ctrl/Cmd+Shift+S` switch server.

## Build it yourself

```sh
git clone https://github.com/quentinmayo/Rommayo.git
cd Rommayo
npm ci
npm start
```

```sh
# Desktop packaging for your current platform
npm run dist

# Android: JDK 21 and Android SDK 36
printf 'sdk.dir=/your/android/sdk\n' > android/local.properties
cd android
./gradlew assembleDebug
```

Release APKs require signing environment variables; see [development and releases](docs/development.md). Debug and release APKs use different signing identities. Uninstall a debug installation before switching to the public release; that clears its local session.

For the web edition:

```sh
ROMMAYO_UPSTREAM=https://romm.example.com npm run start:web
# Open http://127.0.0.1:8787/__rommayo/
```

## No games included

**Rommayo does not provide, host, source, or distribute ROMs, BIOS files, decryption keys, or game content.** We do not support finding games or downloading them from third-party sites. Transfers from your own RomM server are your responsibility. Use only content you are authorized to access; downloads and use are at your own risk.

RomM handles emulation, metadata, server permissions, save behavior and game compatibility. Rommayo is an independent companion, not an official RomM, Steam or emulator project. The blue visual style uses original assets and does not include Valve branding.

## Under the hood

```text
shared/    Original connection UI + optional RomM theme
desktop/   Sandboxed Electron shell; separate cookie session per server origin
android/   Native Android Views + direct RomM API; isolated WebView for players/Full RomM
web/       Self-hosted, fixed-upstream streaming companion proxy
tests/     Address policy, proxy behavior and desktop integration checks
docs/      Setup, compatibility and release instructions
```

See [security](SECURITY.md), [contributing](CONTRIBUTING.md), and the [MIT license](LICENSE). RomM is a separate [AGPL-3.0 project](https://github.com/rommapp/romm); its source and emulator components are not bundled here.

## Support the developers ☕

I need help affording my AI token usage :) If Rommayo makes your setup a little more fun, [buy the developers a coffee](https://buymeacoffee.com/quentinmayo). Always optional. Always appreciated. Rommayo stays free and open source.

## One-line desktop & Steam Deck setup

In Steam Deck **Desktop Mode**, open Konsole and run:

```sh
curl -fsSL https://rommayo.quentinmayo.com/steam-deck.sh | bash
```

For macOS or Linux desktop: `curl -fsSL https://rommayo.quentinmayo.com/install.sh | bash`. [Read the installer](scripts/install.sh) before running it. It downloads the pinned packaged release, verifies its SHA-256 checksum, and installs into your own account without sudo. Game files and app profiles are preserved during updates. Windows users can use the installer in Downloads above.

On Steam Deck, open **Steam → Games → Add a Non-Steam Game**, select **Rommayo**, then return to **Gaming Mode → Library → Non-Steam**. The generated launcher includes `--deck`; leave Proton/Compatibility disabled. This is a native Linux Electron app. Save/export progress before closing a game.

[Follow the illustrated Steam Deck setup guide](docs/steam-deck.md).

![Rommayo running on a real Steam Deck](docs/screenshots/steam-deck-connect.png)

## Tested devices

Physical hardware checks recorded October 22, 2026. These are specific smoke tests, not certification of every game, core or controller. CI builds for Windows and Intel Mac do not count as physical device tests. No device serials or private server details are published.

| Device | Environment | Verified | Still to check |
| --- | --- | --- | --- |
| Samsung Galaxy S24 Ultra | Android 17 | Signed APK; native library; remembered login after force-stop. | Physical external-controller gameplay not verified. |
| Retroid Pocket 5 | Android 13 | Signed APK; landscape native library; remembered login after force-stop; owner confirmed D-pad menu navigation and Recent platform filter opening, D-pad selection, A selection and B cancellation. | Physical left-stick menu and gameplay bindings require further checks. |
| Steam Deck | SteamOS, docked 1920 × 1080 | Linux 0.2.9 candidate: Desktop Mode remembered login, C64 core boot, fullscreen touch toggle, Cancel/Exit returning home, and remembered touch-control preference. Earlier Gaming Mode launch also verified. | Current-update Gaming Mode and physical gameplay controls remain under test. |
| MacBook Pro (Apple M4 Pro) | macOS, Apple Silicon | Electron launch, session and download regression tests. | Other emulator cores and systems are not certified. |

[Setup and current device notes](https://rommayo.quentinmayo.com/getting-started#tested-devices).

Android contributors: [controller menu validation](docs/android-controller-tests.md) covers focus movement and the physical handheld checklist.
