package com.rommayo.app;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.BooleanSupplier;

/** Authenticated downloads never follow redirects or send a cookie to a different origin. */
final class ServerDownload {
    static void fetch(String address, String server, String cookie, String agent, File target, BooleanSupplier cancelled) throws IOException {
        URL url = new URL(address), allowed = new URL(server);
        int port = url.getPort() < 0 ? url.getDefaultPort() : url.getPort();
        int allowedPort = allowed.getPort() < 0 ? allowed.getDefaultPort() : allowed.getPort();
        if (!(url.getProtocol().equals("http") || url.getProtocol().equals("https")) || url.getUserInfo() != null || !url.getProtocol().equals(allowed.getProtocol()) || !url.getHost().equalsIgnoreCase(allowed.getHost()) || port != allowedPort) throw new IOException("The download is outside your RomM server.");
        if (cancelled.getAsBoolean()) throw new IOException("Download cancelled.");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10000); connection.setReadTimeout(20000);
        if (cookie != null) connection.setRequestProperty("Cookie", cookie);
        if (agent != null) connection.setRequestProperty("User-Agent", agent);
        try {
            int code = connection.getResponseCode();
            if (code >= 300 && code < 400) throw new IOException("Redirected downloads are blocked to protect your login. Use your browser for this download.");
            if (code != 200) throw new IOException("The server could not provide this download. Retry or use your browser.");
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(target)) {
                byte[] bytes = new byte[65536]; int count;
                while ((count = input.read(bytes)) != -1) {
                    if (cancelled.getAsBoolean()) throw new IOException("Download cancelled.");
                    if (target.getUsableSpace() < 4L * 1024 * 1024) throw new IOException("Not enough free space for this download.");
                    output.write(bytes, 0, count);
                }
            }
        } finally { connection.disconnect(); }
    }
}
