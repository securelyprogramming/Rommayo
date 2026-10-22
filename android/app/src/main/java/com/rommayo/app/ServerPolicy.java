package com.rommayo.app;

import java.net.URI;

public final class ServerPolicy {
    private ServerPolicy() {}
    public static String normalize(String input) throws Exception {
        if (input == null || input.length() > 2048) throw new Exception("Enter a valid RomM server address.");
        String value = input.trim();
        URI uri = new URI(value.contains("://") ? value : "https://" + value).normalize();
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || uri.getPort() > 65535) {
            throw new Exception("Use an HTTP or HTTPS address without credentials, query parameters or fragments.");
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        int end = path.length();
        while (end > 0 && path.charAt(end - 1) == '/') end--;
        path = path.substring(0, end);
        return new URI(uri.getScheme().toLowerCase(), null, uri.getHost().toLowerCase(), uri.getPort(), path + "/", null, null).toASCIIString();
    }
    public static String origin(String url) {
        try {
            URI uri = new URI(url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) return "";
            int port = uri.getPort();
            if (port == ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80)) port = -1;
            return new URI(uri.getScheme().toLowerCase(), null, uri.getHost().toLowerCase(), port, null, null, null).toString();
        } catch (Exception error) { return ""; }
    }
}
