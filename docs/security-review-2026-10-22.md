# Security review · October 22, 2026

Scope: desktop Electron IPC and navigation, session persistence/logout, web proxy request handling, Android WebView/file access and download bridges, local asset resolution, and the Steam installer/artwork helper. This is a focused source review with regression tests, not a guarantee of absence of vulnerabilities.

## Fixed findings

- **Web companion denial of service:** a malformed Origin on the logout route could throw outside request error handling. Invalid request URLs also lacked a guard. Both now return 400/403, and a regression sends malformed values then verifies the server remains responsive.
- **Android authenticated download redirect:** a Cookie header supplied to DownloadManager could be reused across redirects. The app now streams direct downloads with redirects disabled and full origin validation, then opens Android's save picker. A loopback test verifies the intended server receives the dummy cookie and that a redirect target/different origin is never contacted. See [AOSP DownloadThread request headers and redirect loop](https://android.googlesource.com/platform/packages/providers/DownloadProvider/+/master/src/com/android/providers/downloads/DownloadThread.java).
- The web edition now serves the shared control script added by the desktop update, avoiding a missing-script error.

## Boundaries checked

Desktop remote pages remain sandboxed with Node integration disabled; only the local app shell may invoke Rommayo IPC. Player actions use a fixed allowlist and existing upstream handlers, with replacement operations confirmed. Remembered configuration contains only server/theme settings; cookies remain in the isolated Chromium session and no password is saved. Logout preserves browser game saves.

Android backup and release WebView debugging remain disabled. File/content WebView access stays disabled, including the transient popup routing view; the blob-save bridge is restricted to the selected origin and main frame, with a one-use transaction token and bounded messages. HTTP LAN connections remain an explicit user choice; HTTPS is needed for transport confidentiality.

Steam artwork edits require Steam to be closed. Existing shortcut IDs/settings and other apps are preserved, changed files get backups, and app/artwork downloads are checksum verified before replacing the app. Tests use temporary profiles, never the real Steam account.

## Automated scans

The requested public fork is [securelyprogramming/Rommayo](https://github.com/securelyprogramming/Rommayo). Its centralized baseline runs Gitleaks, Trivy and zizmor, plus CodeQL for JavaScript/TypeScript, Java/Kotlin and Actions. Scan results must be assessed on the exact tested revision; scan success does not certify every game, server or upstream emulator core.

## Detailed CodeQL alert triage

The security workflow can pass while CodeQL still reports alerts; inspect the Security tab as well as job results. The follow-up review replaced the trailing-slash regular expression with a linear scan (the URL was already bounded to 2,048 characters) and explicitly disabled JavaScript/file/content access on transient popup views.

Two flagged design choices remain: JavaScript is necessary in the trusted-server player WebView for RomM and emulation, and arbitrary user-selected HTTPS servers cannot share a fixed certificate pin. Standard platform certificate verification stays enabled; certificate failures are never bypassed. Users explicitly trust the server and separately opt in to plain HTTP. These controls reduce exposure but do not make compromised server content safe. Those alerts are documented design risks, not silently suppressed findings.

The October 22 npm audit reports eight moderate advisories in development-only Electron packaging dependencies and zero high/critical advisories. The production-only npm audit reports zero advisories; Electron/Chromium and upstream cores still require separate maintenance. npm’s suggested fix downgrades electron-builder and is marked as a breaking change, so this patch does not blindly force that change. The build-tool advisories remain tracked maintenance risk.
