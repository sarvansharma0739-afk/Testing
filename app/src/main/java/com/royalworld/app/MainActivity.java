package com.royalworld.app;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private static final byte XOR_KEY = (byte) 0x5A; // Vault encryption key
    private static final String SECRET_SALT = "RW_KING_2026_SECURE_KEY_9981";
    private int localStreamPort = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. ANTI-PIRACY: Block Screenshots & Screen Recording
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Color.parseColor("#080b12"));
        getWindow().setNavigationBarColor(Color.parseColor("#080b12"));

        // 2. ENVIRONMENT SECURITY CHECKS (Root, Emulator, Proxy Sniffer)
        String securityIssue = checkSecurityEnvironment();
        if (securityIssue != null) {
            new AlertDialog.Builder(this)
                .setTitle("🛡️ Security Alert")
                .setMessage(securityIssue)
                .setCancelable(false)
                .setPositiveButton("Exit", (d, w) -> finishAffinity())
                .show();
            return;
        }

        startLocalVaultStreamServer();

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#080b12"));
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new RoyalBridge(), "AndroidBridge");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            private boolean handleUrl(String url) {
                if (url.startsWith("royalworld://")) {
                    handleDeepLinkUrl(url);
                    return true;
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    if (url.contains("127.0.0.1") || url.contains("localhost") || url.contains("mediadelivery.net") || url.contains("drive.google.com")) {
                        return false;
                    }
                    // Open all external shortlinks / Telegram / WhatsApp in real Chrome browser so multi-tab ads NEVER crash!
                    openInChromeBrowser(url);
                    return true;
                }
                return false;
            }
        });

        handleIntent(getIntent());
    }

    private String checkSecurityEnvironment() {
        // Root / Magisk Check
        String[] rootPaths = {
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su", "/sbin/magisk"
        };
        for (String path : rootPaths) {
            if (new File(path).exists()) {
                return "Rooted device detected! RoyalWorld cannot run on rooted devices to protect course content.";
            }
        }
        // Emulator Check
        if ((Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
            || Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.HARDWARE.contains("goldfish")
            || Build.HARDWARE.contains("ranchu")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")) {
            return "Emulator detected! Please install RoyalWorld on a real Android smartphone.";
        }
        // Packet Sniffer / HTTP Proxy Check (HTTP Canary / Charles Proxy)
        String proxyHost = System.getProperty("http.proxyHost");
        String proxyPort = System.getProperty("http.proxyPort");
        if (proxyHost != null && !proxyHost.isEmpty() && proxyPort != null && !proxyPort.isEmpty()) {
            return "Network Proxy / Packet Sniffer detected! Please disable VPN/Proxy settings and restart the app.";
        }
        return null;
    }

    private void openInChromeBrowser(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open browser", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleDeepLinkUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            String key = uri.getQueryParameter("key");
            String cid = uri.getQueryParameter("b");
            if (cid == null) cid = uri.getQueryParameter("unlock");
            final String fKey = key != null ? key : "";
            final String fCid = cid != null ? cid : "";
            runOnUiThread(() -> {
                webView.evaluateJavascript("if(window.handleIncomingKeyFromDeepLink){ window.handleIncomingKeyFromDeepLink('" + fKey + "', '" + fCid + "'); }", null);
            });
        } catch (Exception ignored) {}
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getData() != null && "royalworld".equals(intent.getData().getScheme())) {
            handleDeepLinkUrl(intent.getData().toString());
            if (webView.getUrl() != null) return;
        }
        webView.loadUrl("file:///android_asset/index.html");
    }

    // Local lightweight HTTP streaming server that decrypts .rwvid on-the-fly for native video playback
    private void startLocalVaultStreamServer() {
        new Thread(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket(0);
                localStreamPort = serverSocket.getLocalPort();
                while (!serverSocket.isClosed()) {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleStreamClient(client)).start();
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void handleStreamClient(Socket client) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            String reqLine = in.readLine();
            if (reqLine == null || !reqLine.startsWith("GET ")) {
                client.close();
                return;
            }
            String pathPart = reqLine.split(" ")[1];
            String classId = pathPart.replace("/", "").replace(".mp4", "");
            File vidFile = new File(new File(getFilesDir(), "rw_vault"), classId + ".rwvid");
            if (!vidFile.exists()) {
                client.close();
                return;
            }
            long fileLen = vidFile.length();
            long start = 0;
            long end = fileLen - 1;
            String line;
            boolean isPartial = false;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("range: bytes=")) {
                    isPartial = true;
                    String range = line.substring(13);
                    String[] parts = range.split("-");
                    start = Long.parseLong(parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        end = Long.parseLong(parts[1]);
                    }
                }
            }
            long contentLen = end - start + 1;
            OutputStream out = client.getOutputStream();
            String headers = (isPartial ? "HTTP/1.1 206 Partial Content\r\n" : "HTTP/1.1 200 OK\r\n")
                + "Content-Type: video/mp4\r\n"
                + "Accept-Ranges: bytes\r\n"
                + "Content-Length: " + contentLen + "\r\n"
                + (isPartial ? "Content-Range: bytes " + start + "-" + end + "/" + fileLen + "\r\n" : "")
                + "Connection: close\r\n\r\n";
            out.write(headers.getBytes(StandardCharsets.UTF_8));

            FileInputStream fis = new FileInputStream(vidFile);
            fis.skip(start);
            byte[] buf = new byte[16384];
            long remaining = contentLen;
            int read;
            while (remaining > 0 && (read = fis.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
                for (int i = 0; i < read; i++) {
                    buf[i] = (byte) (buf[i] ^ XOR_KEY);
                }
                out.write(buf, 0, read);
                remaining -= read;
            }
            out.flush();
            fis.close();
            client.close();
        } catch (Exception ignored) {
            try { client.close(); } catch (Exception ignored2) {}
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    public class RoyalBridge {
        @JavascriptInterface
        public void openExternal(String url) {
            openInChromeBrowser(url);
        }

        @JavascriptInterface
        public String getDeviceId() {
            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            return androidId != null ? androidId : "RW_DEVICE";
        }

        // Verify Key generated by RoyalWorld Key Generator Page (Valid for current UTC/IST date + next day window)
        @JavascriptInterface
        public boolean verifyAccessKey(String inputKey) {
            if (inputKey == null) return false;
            String clean = inputKey.trim().toUpperCase(Locale.ROOT);
            if (!clean.startsWith("RW-")) return false;
            try {
                // Check today and yesterday (in case generated around midnight)
                for (int offsetDays = 0; offsetDays <= 1; offsetDays++) {
                    long ts = System.currentTimeMillis() - (offsetDays * 86400000L);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
                    String dateStr = sdf.format(new Date(ts));
                    String expected = generateDailyKeyForDate(dateStr);
                    if (expected.equals(clean)) {
                        return true;
                    }
                }
            } catch (Exception ignored) {}
            return false;
        }

        private String generateDailyKeyForDate(String dateStr) throws Exception {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((SECRET_SALT + ":" + dateStr).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02X", hash[i]));
            }
            String raw = hex.toString();
            return "RW-" + raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8, 12);
        }

        @JavascriptInterface
        public void downloadNoteToDevice(String url, String fileName) {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setTitle(fileName);
                request.setDescription("Downloading RoyalWorld PDF Notes...");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName + ".pdf");
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                dm.enqueue(request);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "📥 Downloading PDF to Phone Downloads...", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                openExternal(url);
            }
        }

        // Resolves direct MP4 OR Bunny MediaDelivery Embed/Play URLs automatically & saves encrypted .rwvid!
        @JavascriptInterface
        public void saveOfflineVideo(String classId, String title, String videoUrl) {
            if (videoUrl == null || videoUrl.trim().isEmpty()) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "⚠️ Video link not available for this lecture.", Toast.LENGTH_SHORT).show());
                return;
            }
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "📥 Preparing Offline Download...", Toast.LENGTH_SHORT).show());
            new Thread(() -> {
                try {
                    String resolvedUrl = resolvePlayableMp4Url(videoUrl.trim());
                    if (resolvedUrl == null || resolvedUrl.isEmpty()) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "⚠️ Could not extract MP4 stream. Please add MP4 link in Admin or try another lecture.", Toast.LENGTH_LONG).show());
                        return;
                    }

                    File privateDir = new File(getFilesDir(), "rw_vault");
                    if (!privateDir.exists()) privateDir.mkdirs();
                    File outFile = new File(privateDir, classId + ".rwvid");

                    URL url = new URL(resolvedUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("Referer", "https://iframe.mediadelivery.net/");
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36");
                    conn.connect();

                    int code = conn.getResponseCode();
                    if (code >= 400) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "❌ Server denied download (HTTP " + code + ")", Toast.LENGTH_LONG).show());
                        return;
                    }

                    InputStream is = conn.getInputStream();
                    FileOutputStream fos = new FileOutputStream(outFile);
                    byte[] buffer = new byte[16384];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        for (int i = 0; i < len; i++) {
                            buffer[i] = (byte) (buffer[i] ^ XOR_KEY);
                        }
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    is.close();

                    File metaFile = new File(privateDir, classId + ".json");
                    JSONObject meta = new JSONObject();
                    meta.put("id", classId);
                    meta.put("title", title);
                    meta.put("sizeMb", String.format(Locale.US, "%.1f MB", outFile.length() / (1024.0 * 1024.0)));
                    FileOutputStream mFos = new FileOutputStream(metaFile);
                    mFos.write(meta.toString().getBytes(StandardCharsets.UTF_8));
                    mFos.close();

                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "✅ Saved to Encrypted Offline Vault!", Toast.LENGTH_LONG).show());
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "❌ Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        }

        private String resolvePlayableMp4Url(String rawUrl) {
            try {
                if (rawUrl.endsWith(".mp4") || rawUrl.contains(".mp4?")) {
                    return rawUrl;
                }
                // Handle Bunny Stream player.mediadelivery.net/play/{libId}/{videoId} or /embed/{libId}/{videoId}
                if (rawUrl.contains("mediadelivery.net/")) {
                    String embedUrl = rawUrl.replace("/play/", "/embed/");
                    URL u = new URL(embedUrl);
                    HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                    conn.setRequestProperty("Referer", "https://royalworldofficial.vercel.app/");
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36");
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder html = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        html.append(line).append("\n");
                    }
                    br.close();
                    String page = html.toString();

                    // Look for direct MP4 fallback URLs inside Bunny embed HTML (e.g., https://vz-xxxx.b-cdn.net/{uuid}/play_480p.mp4 or 720p/360p)
                    String[] preferredQualities = {"play_480p.mp4", "play_720p.mp4", "play_360p.mp4", ".mp4"};
                    for (String q : preferredQualities) {
                        Pattern p = Pattern.compile("https://[^\"'\\s<>]+?" + Pattern.quote(q) + "[^\"'\\s<>]*");
                        Matcher m = p.matcher(page);
                        if (m.find()) {
                            return m.group(0).replace("&amp;", "&");
                        }
                    }
                }
            } catch (Exception ignored) {}
            return null;
        }

        @JavascriptInterface
        public String getLocalStreamUrl(String classId) {
            return "http://127.0.0.1:" + localStreamPort + "/" + classId + ".mp4";
        }

        @JavascriptInterface
        public String getOfflineVideos() {
            JSONArray arr = new JSONArray();
            try {
                File privateDir = new File(getFilesDir(), "rw_vault");
                if (privateDir.exists()) {
                    File[] files = privateDir.listFiles((dir, name) -> name.endsWith(".json"));
                    if (files != null) {
                        for (File f : files) {
                            java.util.Scanner s = new java.util.Scanner(f).useDelimiter("\\A");
                            String content = s.hasNext() ? s.next() : "";
                            s.close();
                            arr.put(new JSONObject(content));
                        }
                    }
                }
            } catch (Exception ignored) {}
            return arr.toString();
        }

        @JavascriptInterface
        public void deleteOfflineVideo(String classId) {
            File privateDir = new File(getFilesDir(), "rw_vault");
            new File(privateDir, classId + ".rwvid").delete();
            new File(privateDir, classId + ".json").delete();
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "🗑️ Deleted from Offline Vault", Toast.LENGTH_SHORT).show());
        }
    }
}
