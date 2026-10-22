Security update: malformed logout Origin headers and request targets return errors instead of crashing the web companion. Android authenticated downloads reject redirects and use the system save picker so the session cookie never follows a redirect to another host. Keep the app open until the picker appears.

Steam Deck update: original Steam library art and app icons, a visible Quit app control, save-before-exit guidance, and clickable EmulatorJS toolbar controls during Deck gameplay. A labeled action menu supports touch, F10 and a standard controller (L3 + R3, D-pad, A/B). The artwork helper preserves existing shortcut IDs and controls and requires Steam to be closed.

Rommayo automatically reopens the last remembered session using cookies, preserving the theme choice. No password is stored. Expired sessions ask you to sign in again.

Install Rommayo for Android, macOS Apple Silicon or Intel, Windows, and Linux/Steam Deck. Packaged apps do not need Node.js, npm or a development server. Downloads: https://rommayo.quentinmayo.com/#downloads.

This release handles RomM’s CSRF-protected logout and adds **Remember login** and **Log out**. Android and desktop retain a checked session between app restarts; an unchecked session is cleared before the next app launch. Android: Settings → Log out. Desktop: Log out in the toolbar or application menu. Logout contacts RomM, clears local authentication even if the server is unavailable, and preserves browser-local game saves and exported files. Passwords are never stored by Rommayo. The self-hosted web edition uses browser-session cookies when unchecked; browser session-restoration settings may retain them. Its Log out control ends the current session explicitly.

Android uses native, Steam-inspired library, search, recents, details and controller settings. WebViews handle gameplay and Full RomM. Desktop uses Electron and the full RomM interface. No games, BIOS files or keys are included. Physical Steam Deck and Xbox/retro-controller gameplay testing is pending.

Mac apps are ad-hoc signed, not Apple-notarized. Windows installers are unsigned. Linux AppImages require executable permission; Steam Deck users add the AppImage as a non-Steam app with --deck. Check SHA256SUMS.txt to verify downloaded assets.

Use only content you are authorized to access. Downloads and use are at your own risk. Independent of RomM and Valve.

Website: https://rommayo.quentinmayo.com · Support: https://buymeacoffee.com/quentinmayo
