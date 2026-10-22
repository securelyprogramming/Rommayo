package com.rommayo.app;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.util.Base64;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends ComponentActivity {
    private static final int PICK_FILES = 41, SAVE_BLOB = 42;
    private WebView remote;
    private NativeLibrary nativeLibrary;
    private LinearLayout webToolbar;
    private LinearLayout root;
    private FrameLayout remoteContainer;
    private SharedPreferences preferences;
    private String server = "";
    private JSONArray servers = new JSONArray();
    private final Set<String> allowedOrigins = new HashSet<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ValueCallback<Uri[]> fileCallback;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private boolean fullscreen = false, steamTheme = true;
    private String theme = "";
    private File blobFile;
    private OutputStream blobStream;
    private String blobToken = "", blobName = "download", blobMime = "application/octet-stream";

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleBack(); }
        });
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        preferences = getSharedPreferences("rommayo", MODE_PRIVATE);
        try { servers = new JSONArray(preferences.getString("servers", "[]")); } catch (Exception ignored) {}
        try (InputStream input = getAssets().open("rommayo-theme.css")) { java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream(); copy(input, buffer); theme = buffer.toString("UTF-8"); } catch (Exception ignored) {}
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(16, 24, 34));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets;
        });
        setContentView(root);
        remoteContainer = new FrameLayout(this);
        CookieManager.getInstance().setAcceptCookie(true);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        nativeLibrary = new NativeLibrary(this, root, preferences);
        // A process can be killed without onDestroy. Clear non-remembered authentication
        // before any API request or WebView is allowed to restore a session.
        java.util.List<String> temporary = new java.util.ArrayList<>(preferences.getStringSet("temporarySessions", java.util.Collections.emptySet()));
        clearTemporarySessions(temporary, 0, () -> {
            if (servers.length() > 0) {
                try { server = ServerPolicy.normalize(servers.optString(0)); nativeLibrary.restore(server); }
                catch (Exception issue) { nativeLibrary.login(null); }
            } else nativeLibrary.login(null);
        });
    }

    private void configure(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportMultipleWindows(true); settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setUseWideViewPort(true); settings.setLoadWithOverviewMode(true);
        view.setFocusable(true); view.setFocusableInTouchMode(true);
    }
    String currentServer() { return server; }
    private void clearTemporarySessions(java.util.List<String> addresses, int index, Runnable done) {
        if (index == addresses.size()) { preferences.edit().remove("temporarySessions").commit(); done.run(); return; }
        clearServerCookies(addresses.get(index), () -> clearTemporarySessions(addresses, index + 1, done));
    }
    void beginSession(String address, boolean remember) {
        server = address;
        JSONArray recent = new JSONArray();
        if (remember) recent.put(address);
        for (int i = 0; i < servers.length() && recent.length() < 8; i++) if (!address.equals(servers.optString(i))) recent.put(servers.optString(i));
        servers = recent;
        Set<String> temporary = new HashSet<>(preferences.getStringSet("temporarySessions", java.util.Collections.emptySet()));
        if (remember) temporary.remove(address); else temporary.add(address);
        preferences.edit().putString("servers", servers.toString()).putStringSet("temporarySessions", temporary)
            .putBoolean("rememberLogin", remember).putString("lastServer", address).commit();
    }
    private void removeSession(String address) {
        JSONArray recent = new JSONArray(); for (int i = 0; i < servers.length(); i++) if (!address.equals(servers.optString(i))) recent.put(servers.optString(i));
        servers = recent; Set<String> temporary = new HashSet<>(preferences.getStringSet("temporarySessions", java.util.Collections.emptySet())); temporary.remove(address);
        preferences.edit().putString("servers", recent.toString()).putStringSet("temporarySessions", temporary).commit();
    }
    private void clearServerCookies(String address, Runnable done) {
        CookieManager cookies = CookieManager.getInstance();
        Uri uri = Uri.parse(address); Set<String> names = new HashSet<>();
        for (String location : new String[]{address, address + "api/", ServerPolicy.origin(address) + "/"}) {
            String values = cookies.getCookie(location);
            if (values != null) for (String value : values.split(";")) { int equals = value.indexOf('='); if (equals > 0) names.add(value.substring(0, equals).trim()); }
        }
        java.util.List<String> expired = new java.util.ArrayList<>();
        for (String name : names) for (String path : new String[]{"/", uri.getPath(), uri.getPath() + "api"}) {
            String cookie = name + "=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=" + path + (address.startsWith("https:") ? "; Secure" : "");
            expired.add(cookie); expired.add(cookie + "; Domain=" + uri.getHost());
        }
        if (expired.isEmpty()) { done.run(); return; }
        java.util.concurrent.atomic.AtomicInteger left = new java.util.concurrent.atomic.AtomicInteger(expired.size());
        for (String value : expired) cookies.setCookie(address, value, success -> { if (left.decrementAndGet() == 0) { cookies.flush(); done.run(); } });
    }
    void logOut() {
        String address = server; closeWeb(); nativeLibrary.endSession();
        RommApi logout = new RommApi(address);
        logout.logout((body, error) -> {
            logout.close();
            clearServerCookies(address, () -> { removeSession(address); server = ""; nativeLibrary.login(error == null ? "Logged out. Your local game saves are preserved." : "Logged out on this device. The server could not be reached to end its session."); });
        });
    }
    void forgetSessions() {
        closeWeb(); nativeLibrary.endSession();
        CookieManager.getInstance().removeAllCookies(done -> {
            CookieManager.getInstance().flush();
            servers = new JSONArray(); server = "";
            preferences.edit().remove("servers").remove("temporarySessions").apply(); nativeLibrary.login(null);
        });
        // Keep WebStorage / IndexedDB: authentication cleanup must preserve game saves.
    }
    void openWeb(String path) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) { toast("Update Android System WebView or Chrome to use the player."); return; }
        closeWeb(); nativeLibrary.suspend(); root.removeAllViews();
        webToolbar = new LinearLayout(this);
        android.widget.Button library = new android.widget.Button(this); library.setText("‹ Library"); library.setAllCaps(false); library.setOnClickListener(v -> confirmLeaveWeb());
        android.widget.Button reload = new android.widget.Button(this); reload.setText("Reload"); reload.setAllCaps(false); reload.setOnClickListener(v -> remote.reload());
        android.widget.Button full = new android.widget.Button(this); full.setText("Fullscreen"); full.setAllCaps(false); full.setOnClickListener(v -> setFullscreen(true));
        webToolbar.addView(library, new LinearLayout.LayoutParams(0, dp(48), 1)); webToolbar.addView(reload); webToolbar.addView(full); root.addView(webToolbar);
        String address = server;
        allowedOrigins.clear(); allowedOrigins.add(ServerPolicy.origin(address));
        remote = new WebView(this); configure(remote); remote.setBackgroundColor(Color.rgb(16, 24, 34));
        // This WebView has no general purpose native bridge. The download bridge is origin restricted and token gated.
        WebViewCompat.addWebMessageListener(remote, "rommayoDownload", java.util.Collections.singleton(ServerPolicy.origin(address)), (view, message, origin, mainFrame, reply) -> {
            if (mainFrame && ServerPolicy.origin(address).equals(origin.toString())) receiveBlob(message.getData(), reply);
        });
        remote.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                String url = request.getUrl().toString(), origin = ServerPolicy.origin(url);
                if (!origin.isEmpty() && allowedOrigins.contains(origin)) return false;
                external(url); return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                if (steamTheme && ServerPolicy.origin(server).equals(ServerPolicy.origin(url))) {
                    view.evaluateJavascript("(()=>{let s=document.getElementById('rommayo-theme');if(!s){s=document.createElement('style');s.id='rommayo-theme';document.head.append(s)}s.textContent=" + JSONObject.quote(theme) + ";})()", null);
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!request.isForMainFrame()) return;
                new AlertDialog.Builder(MainActivity.this).setTitle("Server unavailable").setMessage("Check your network and RomM server. You can retry or switch servers.")
                    .setPositiveButton("Retry", (d, w) -> view.reload()).setNegativeButton("Switch server", (d, w) -> returnToLibrary()).show();
            }
        });
        remote.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                String[] types = params.getAcceptTypes();
                // HTML accept values may contain extensions; Android's picker expects MIME types.
                java.util.ArrayList<String> mime = new java.util.ArrayList<>();
                for (String type : types) if (type.contains("/")) mime.add(type);
                if (!mime.isEmpty()) intent.putExtra(Intent.EXTRA_MIME_TYPES, mime.toArray(new String[0]));
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try { startActivityForResult(intent, PICK_FILES); } catch (Exception error) { fileCallback = null; callback.onReceiveValue(null); toast("No file picker is available on this device."); }
                return true;
            }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view; customCallback = callback; root.setVisibility(View.GONE);
                ((FrameLayout) getWindow().getDecorView()).addView(view, new FrameLayout.LayoutParams(-1, -1)); setFullscreen(true);
            }
            @Override public void onHideCustomView() { hideCustomView(); }
            @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean gesture, android.os.Message message) {
                if (!gesture) return false;
                WebView popup = new WebView(MainActivity.this);
                // This transient view only routes the initial user-requested URL.
                popup.getSettings().setJavaScriptEnabled(false);
                popup.getSettings().setAllowFileAccess(false);
                popup.getSettings().setAllowContentAccess(false);
                popup.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
                popup.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView ignored, WebResourceRequest request) {
                        String url = request.getUrl().toString();
                        if (allowedOrigins.contains(ServerPolicy.origin(url)) && remote != null) remote.loadUrl(url); else external(url);
                        popup.destroy(); return true;
                    }
                });
                ((WebView.WebViewTransport) message.obj).setWebView(popup); message.sendToTarget(); return true;
            }
        });
        remote.setDownloadListener((url, agent, disposition, mime, length) -> download(url, agent, disposition, mime));
        remote.setKeepScreenOn(true);
        root.addView(remoteContainer, new LinearLayout.LayoutParams(-1, 0, 1));
        remoteContainer.addView(remote, new FrameLayout.LayoutParams(-1, -1));
        remote.loadUrl(address + path); remote.requestFocus();
    }
    private void external(String url) {
        String origin = ServerPolicy.origin(url); if (origin.isEmpty()) { toast("This link type is not supported. Open it in a browser."); return; }
        new AlertDialog.Builder(this).setTitle("Leave your RomM server?").setMessage("Open " + origin + "? For single sign-on, open here. Only continue to a site you trust.")
            .setNegativeButton("Cancel", null).setNeutralButton("Browser", (d, w) -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception error) { toast("No browser is available."); }
            }).setPositiveButton("Open here", (d, w) -> { allowedOrigins.add(origin); if (remote != null) remote.loadUrl(url); }).show();
    }
    private void download(String url, String agent, String disposition, String mime) {
        if (url.startsWith("blob:")) { startBlob(url, disposition, mime); return; }
        if (ServerPolicy.origin(url).isEmpty()) { toast("This download type is not supported by Android WebView."); return; }
        // Never send RomM cookies to another origin. External download hosts are opened in the browser.
        if (!ServerPolicy.origin(server).equals(ServerPolicy.origin(url))) { external(url); return; }
        String name = URLUtil.guessFileName(url, disposition, mime);
        new AlertDialog.Builder(this).setTitle("Save from your server?").setMessage(name + "\n\nOnly download content you are authorized to use. Downloads are at your own risk.")
            .setNegativeButton("Cancel", null).setPositiveButton("Download", (d, w) -> {
                startServerDownload(url, agent, name, mime);

            }).show();
    }
    private void startServerDownload(String url, String agent, String name, String mime) {
        if (blobFile != null) { toast("Finish or cancel the current save first."); return; }
        try {
            File target = File.createTempFile("rommayo-download-", ".tmp", getCacheDir());
            blobFile = target; blobName = safeFilename(name); blobMime = mime == null ? "application/octet-stream" : mime;
            String cookie = CookieManager.getInstance().getCookie(url), address = server;
            toast("Downloading. Keep Rommayo open until the save picker appears.");
            worker.execute(() -> {
                try {
                    ServerDownload.fetch(url, address, cookie, agent, target, () -> !target.exists() || Thread.currentThread().isInterrupted());
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || blobFile != target) { target.delete(); return; }
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(blobMime).putExtra(Intent.EXTRA_TITLE, blobName);
                        startActivityForResult(intent, SAVE_BLOB);
                    });
                } catch (Exception issue) {
                    target.delete(); runOnUiThread(() -> { if (blobFile == target) { clearBlob(); toast(issue instanceof java.io.IOException ? issue.getMessage() : "Download failed. Check your network and retry."); } });
                }
            });
        } catch (Exception issue) { clearBlob(); toast("Could not prepare this download."); }
    }
    private String safeFilename(String input) { String name = input.replaceAll("[\\\\/\\p{Cntrl}]", "_"); return name.trim().isEmpty() || name.equals(".") || name.equals("..") ? "download" : name.substring(0, Math.min(200, name.length())); }
    private void startBlob(String url, String disposition, String mime) {
        if (!ServerPolicy.origin(server).equals(ServerPolicy.origin(remote.getUrl()))) { toast("Return to your RomM server to save this file."); return; }
        if (blobFile != null) { toast("Finish or cancel the current save first."); return; }
        try {
            blobFile = File.createTempFile("rommayo-", ".download", getCacheDir()); blobStream = new FileOutputStream(blobFile);
            blobToken = UUID.randomUUID().toString(); blobName = safeFilename(URLUtil.guessFileName(url, disposition, mime)); blobMime = mime == null ? "application/octet-stream" : mime;
            String token = JSONObject.quote(blobToken);
            remote.evaluateJavascript("(async()=>{const bridge=window.rommayoDownload;const token=" + token + ";let ack;bridge.onmessage=()=>{if(ack){const a=ack;ack=null;a()}};const send=(data)=>new Promise(resolve=>{ack=resolve;bridge.postMessage(JSON.stringify({...data,token}))});try{const r=await fetch(" + JSONObject.quote(url) + ");if(!r.ok)throw Error();const reader=r.body.getReader();while(true){const {done,value}=await reader.read();if(done)break;for(let i=0;i<value.length;i+=131072){const part=value.subarray(i,i+131072);let s='';for(let j=0;j<part.length;j++)s+=String.fromCharCode(part[j]);await send({type:'chunk',data:btoa(s)})}}await send({type:'done'})}catch(e){bridge.postMessage(JSON.stringify({type:'error',token}))}})()", null);
        } catch (Exception error) { clearBlob(); toast("Could not prepare the save."); }
    }
    private void receiveBlob(String data, JavaScriptReplyProxy reply) {
        try {
            if (data == null || data.length() > 200000) throw new Exception();
            JSONObject message = new JSONObject(data);
            if (blobStream == null || !blobToken.equals(message.optString("token"))) return;
            switch (message.optString("type")) {
                case "chunk":
                    if (blobFile.getUsableSpace() < 4 * 1024 * 1024) throw new Exception();
                    blobStream.write(Base64.decode(message.getString("data"), Base64.DEFAULT)); reply.postMessage("ok"); break;
                case "done":
                    blobStream.close(); blobStream = null; reply.postMessage("ok");
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(blobMime).putExtra(Intent.EXTRA_TITLE, blobName);
                    startActivityForResult(intent, SAVE_BLOB); break;
                default: throw new Exception();
            }
        } catch (Exception error) { clearBlob(); reply.postMessage("error"); toast("Could not save this file. Check storage and retry."); }
    }
    private void clearBlob() {
        try { if (blobStream != null) blobStream.close(); } catch (Exception ignored) {}
        if (blobFile != null) blobFile.delete(); blobFile = null; blobStream = null; blobToken = "";
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_FILES && fileCallback != null) {
            Uri[] uris = null;
            if (result == RESULT_OK && data != null) {
                if (data.getClipData() != null) { uris = new Uri[data.getClipData().getItemCount()]; for (int i = 0; i < uris.length; i++) uris[i] = data.getClipData().getItemAt(i).getUri(); }
                else if (data.getData() != null) uris = new Uri[]{data.getData()};
            }
            fileCallback.onReceiveValue(uris); fileCallback = null;
        }
        if (request == SAVE_BLOB) {
            if (result != RESULT_OK || data == null || data.getData() == null || blobFile == null) { clearBlob(); return; }
            Uri destination = data.getData(); File source = blobFile; blobFile = null; blobToken = "";
            worker.execute(() -> {
                try (InputStream input = new java.io.FileInputStream(source); OutputStream output = getContentResolver().openOutputStream(destination)) {
                    if (output == null) throw new Exception(); copy(input, output); runOnUiThread(() -> toast("File saved."));
                } catch (Exception error) { runOnUiThread(() -> toast("Could not save this file. Check storage and retry.")); }
                finally { source.delete(); }
            });
        }
    }
    private void hideCustomView() {
        if (customView == null) return;
        ((FrameLayout) getWindow().getDecorView()).removeView(customView); customView = null; root.setVisibility(View.VISIBLE);
        if (customCallback != null) customCallback.onCustomViewHidden(); customCallback = null; setFullscreen(false);
    }
    private void setFullscreen(boolean value) {
        fullscreen = value;
        getWindow().getDecorView().setSystemUiVisibility(value ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY : View.SYSTEM_UI_FLAG_VISIBLE);
        if (webToolbar != null) webToolbar.setVisibility(value ? View.GONE : View.VISIBLE);
    }
    private void closeWeb() {
        hideCustomView(); setFullscreen(false); clearBlob();
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        if (remote != null) { CookieManager.getInstance().flush(); remoteContainer.removeAllViews(); remote.destroy(); remote = null; }
        root.removeView(remoteContainer); if (webToolbar != null) root.removeView(webToolbar); webToolbar = null;
    }
    private void returnToLibrary() { closeWeb(); nativeLibrary.fromWeb(); }
    private void confirmLeaveWeb() {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Return to your library?").setMessage("Save your game in the player before leaving. Unsaved progress may be lost.").setNegativeButton("Stay", null).setPositiveButton("Library", (d, w) -> returnToLibrary()).create(); nativeLibrary.controllerDialog(dialog); dialog.show();
    }
    void confirmExit() { AlertDialog dialog = new AlertDialog.Builder(this).setMessage("Close Rommayo?").setNegativeButton("Stay", null).setPositiveButton("Close", (d, w) -> finish()).create(); nativeLibrary.controllerDialog(dialog); dialog.show(); }
    private void handleBack() {
        if (customView != null) hideCustomView();
        else if (fullscreen) setFullscreen(false);
        else if (remote != null) confirmLeaveWeb();
        else nativeLibrary.back();
    }
    // Activity's public input entry point must run before focused native widgets consume A/B.
    // AndroidX core marks its implementation restricted even though this overrides Activity.
    @android.annotation.SuppressLint("RestrictedApi")
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (remote == null && nativeLibrary != null && nativeLibrary.key(event)) return true;
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean dispatchGenericMotionEvent(android.view.MotionEvent event) {
        if (remote == null && nativeLibrary != null && nativeLibrary.motion(event)) return true;
        return super.dispatchGenericMotionEvent(event);
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        if (remote == null && nativeLibrary != null) nativeLibrary.configurationChanged();
    }
    @Override protected void onPause() { super.onPause(); if (nativeLibrary != null) nativeLibrary.stopInput(); CookieManager.getInstance().flush(); if (remote != null) remote.onPause(); }
    @Override protected void onResume() { super.onResume(); if (remote != null) remote.onResume(); }
    @Override protected void onDestroy() { if (remote != null) remote.destroy(); if (nativeLibrary != null) nativeLibrary.close(); clearBlob(); worker.shutdown(); super.onDestroy(); }
    private static void copy(InputStream input, OutputStream output) throws java.io.IOException { byte[] buffer = new byte[65536]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
