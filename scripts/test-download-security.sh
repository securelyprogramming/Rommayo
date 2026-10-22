#!/usr/bin/env bash
set -euo pipefail
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
javac --add-modules jdk.httpserver -d "$work" android/app/src/main/java/com/rommayo/app/ServerDownload.java android/security-test/ServerDownloadTest.java
java --add-modules jdk.httpserver -cp "$work" com.rommayo.app.ServerDownloadTest
