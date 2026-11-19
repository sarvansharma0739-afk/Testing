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
            String pathPart = reqLine.split(" ")[1].split("\\?")[0];
            OutputStream out = client.getOutputStream();

            // Route 1: HLS Offline Vault (/hls/{classId}/index.m3u8 or /hls/{classId}/seg_X.rwts)
            if (pathPart.startsWith("/hls/")) {
                String rel = pathPart.substring(5); // {classId}/filename
                File target = new File(new File(getFilesDir(), "rw_vault"), rel);
                if (!target.exists() || !target.isFile()) {
                    out.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                    client.close();
                    return;
                }
                boolean isPlaylist = rel.endsWith(".m3u8");
                String contentType = isPlaylist ? "application/vnd.apple.mpegurl" : "video/mp2t";
                long fileLen = target.length();
                String headers = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: " + contentType + "\r\n"
                    + "Access-Control-Allow-Origin: *\r\n"
                    + "Content-Length: " + fileLen + "\r\n"
                    + "Connection: close\r\n\r\n";
                out.write(headers.getBytes(StandardCharsets.UTF_8));

                FileInputStream fis = new FileInputStream(target);
                byte[] buf = new byte[16384];
                int read;
                while ((read = fis.read(buf)) != -1) {
                    if (!isPlaylist) {
                        for (int i = 0; i < read; i++) buf[i] = (byte) (buf[i] ^ XOR_KEY);
                    }
                    out.write(buf, 0, read);
                }
                out.flush();
                fis.close();
                client.close();
                return;
            }

            // Route 2: Direct MP4 Vault (/{classId}.mp4)
            String classId = pathPart.replace("/", "").replace(".mp4", "");
            File vidFile = new File(new File(getFilesDir(), "rw_vault"), classId + ".rwvid");
            if (!vidFile.exists()) {
                out.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.UTF_8));
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
            String headers = (isPartial ? "HTTP/1.1 206 Partial Content\r\n" : "HTTP/1.1 200 OK\r\n")
                + "Content-Type: video/mp4\r\n"
                + "Accept-Ranges: bytes\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
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

        // Verify Device-Locked, One-Time Burn, 15-Minute Expiry Key:
        // Format: RW-<TS_HEX_8>-<NONCE_4>-<SIG_8>
        // Signature = SHA256(SECRET_SALT + ":" + DEVICE_ID + ":" + TS_HEX_8 + ":" + NONCE_4)[0..8]
        @JavascriptInterface
        public boolean verifyAccessKey(String inputKey) {
            if (inputKey == null) return false;
            String clean = inputKey.trim().toUpperCase(Locale.ROOT);
            String[] parts = clean.split("-");
            if (parts.length != 4 || !"RW".equals(parts[0])) return false;

            String tsHex = parts[1];
            String nonce = parts[2];
            String sig = parts[3];

            if (tsHex.length() != 8 || nonce.length() != 4 || sig.length() != 8) return false;

            try {
                // 1. Check if key was already used (One-Time Burn)
                android.content.SharedPreferences prefs = getSharedPreferences("rw_burned_keys", Context.MODE_PRIVATE);
                if (prefs.getBoolean(clean, false)) {
                    return false;
                }

                // 2. Check 15-minute expiry window (900 seconds)
                long keyEpochSec = Long.parseLong(tsHex, 16);
                long nowSec = System.currentTimeMillis() / 1000L;
                if (nowSec - keyEpochSec > 900L || keyEpochSec - nowSec > 120L) {
                    return false;
                }

                // 3. Verify cryptographic signature bound to THIS exact phone's Device ID
                String devId = getDeviceId().toUpperCase(Locale.ROOT);
                String expectedSig = computeKeySignature(devId, tsHex, nonce);
                if (expectedSig.equals(sig)) {
                    // Burn key immediately so it can NEVER be reused!
                    prefs.edit().putBoolean(clean, true).apply();
                    return true;
                }
            } catch (Exception ignored) {}
            return false;
        }

        private String computeKeySignature(String devId, String tsHex, String nonce) throws Exception {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String payload = SECRET_SALT + ":" + devId + ":" + tsHex + ":" + nonce;
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                hex.append(String.format("%02X", hash[i]));
            }
            return hex.toString();
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

        private static final String BUNNY_CDN_HOST = "vz-0d9ed9ee-956.b-cdn.net";

        @JavascriptInterface
        public void saveOfflineVideo(String classId, String title, String videoUrl) {
            saveOfflineVideoWithQuality(classId, title, videoUrl, "480p");
        }

        // Universal Bunny.net HLS (.m3u8) + Direct MP4 Encrypted Offline Downloader (Works on ALL old & new videos without MP4 fallback!)
        @JavascriptInterface
        public void saveOfflineVideoWithQuality(String classId, String title, String videoUrl, String quality) {
            if (videoUrl == null || videoUrl.trim().isEmpty()) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "⚠️ Video link not available for this lecture.", Toast.LENGTH_SHORT).show());
                return;
            }
            final String qLabel = ("720p".equals(quality)) ? "720p" : "480p";
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "📥 Downloading (" + qLabel + ") to Encrypted Vault...", Toast.LENGTH_SHORT).show());
            new Thread(() -> {
                try {
                    String raw = videoUrl.trim();
                    File vaultRoot = new File(getFilesDir(), "rw_vault");
                    if (!vaultRoot.exists()) vaultRoot.mkdirs();

                    // Case A: Direct MP4 link
                    if (raw.endsWith(".mp4") || raw.contains(".mp4?")) {
                        downloadEncryptedMp4(vaultRoot, classId, title, qLabel, raw);
                        return;
                    }

                    // Case B: Bunny.net Stream URL -> Download HLS (.m3u8 + .ts segments) directly!
                    String videoUuid = extractUuid(raw);
                    if (videoUuid == null) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "⚠️ Invalid Bunny video link.", Toast.LENGTH_LONG).show());
                        return;
                    }

                    // Try requested quality HLS playlist, fallback to 480p or 360p if needed
                    String[] tryQualities = "720p".equals(qLabel) ? new String[]{"720p", "480p", "360p"} : new String[]{"480p", "360p", "720p"};
                    String activeQuality = null;
                    String playlistText = null;
                    String baseSegmentUrl = null;

                    for (String q : tryQualities) {
                        String m3u8Url = "https://" + BUNNY_CDN_HOST + "/" + videoUuid + "/" + q + "/video.m3u8";
                        String fetched = fetchTextUrl(m3u8Url);
                        if (fetched != null && fetched.contains("#EXTM3U")) {
                            activeQuality = q;
                            playlistText = fetched;
                            baseSegmentUrl = "https://" + BUNNY_CDN_HOST + "/" + videoUuid + "/" + q + "/";
                            break;
                        }
                    }

                    if (playlistText == null) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "❌ Could not load video stream. Check internet connection.", Toast.LENGTH_LONG).show());
                        return;
                    }

                    File hlsDir = new File(vaultRoot, classId);
                    if (!hlsDir.exists()) hlsDir.mkdirs();

                    String[] lines = playlistText.split("\r?\n");
                    StringBuilder localM3u8 = new StringBuilder();
                    int segIndex = 0;
                    long totalBytes = 0;

                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty()) continue;
                        if (trimmed.startsWith("#")) {
                            localM3u8.append(trimmed).append("\n");
                        } else {
                            String segUrl = trimmed.startsWith("http") ? trimmed : (baseSegmentUrl + trimmed);
                            String localSegName = "seg_" + segIndex + ".rwts";
                            File segFile = new File(hlsDir, localSegName);
                            long bytes = downloadAndEncryptFile(segUrl, segFile);
                            if (bytes <= 0) {
                                throw new Exception("Failed on segment " + segIndex);
                            }
                            totalBytes += bytes;
                            localM3u8.append(localSegName).append("\n");
                            segIndex++;
                        }
                    }

                    if (segIndex == 0) {
                        throw new Exception("No video segments found in playlist.");
                    }

                    File localPlaylistFile = new File(hlsDir, "index.m3u8");
                    FileOutputStream pFos = new FileOutputStream(localPlaylistFile);
                    pFos.write(localM3u8.toString().getBytes(StandardCharsets.UTF_8));
                    pFos.close();

                    File metaFile = new File(vaultRoot, classId + ".json");
                    JSONObject meta = new JSONObject();
                    meta.put("id", classId);
                    meta.put("type", "hls");
                    meta.put("title", title + " (" + activeQuality + ")");
                    meta.put("sizeMb", String.format(Locale.US, "%.1f MB", totalBytes / (1024.0 * 1024.0)));
                    FileOutputStream mFos = new FileOutputStream(metaFile);
                    mFos.write(meta.toString().getBytes(StandardCharsets.UTF_8));
                    mFos.close();

                    final String doneQ = activeQuality;
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "✅ Saved (" + doneQ + ") to Offline Vault!", Toast.LENGTH_LONG).show());
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "❌ Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        }

        private void downloadEncryptedMp4(File vaultRoot, String classId, String title, String qLabel, String mp4Url) throws Exception {
            File outFile = new File(vaultRoot, classId + ".rwvid");
            long bytes = downloadAndEncryptFile(mp4Url, outFile);
            if (bytes <= 0) throw new Exception("Empty MP4 file");
            File metaFile = new File(vaultRoot, classId + ".json");
            JSONObject meta = new JSONObject();
            meta.put("id", classId);
            meta.put("type", "mp4");
            meta.put("title", title + " (" + qLabel + ")");
            meta.put("sizeMb", String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0)));
            FileOutputStream mFos = new FileOutputStream(metaFile);
            mFos.write(meta.toString().getBytes(StandardCharsets.UTF_8));
            mFos.close();
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "✅ Saved (" + qLabel + ") to Offline Vault!", Toast.LENGTH_LONG).show());
        }

        private String extractUuid(String rawUrl) {
            Pattern uuidPattern = Pattern.compile("([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");
            Matcher m = uuidPattern.matcher(rawUrl);
            return m.find() ? m.group(1) : null;
        }

        private String fetchTextUrl(String urlStr) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestProperty("Referer", "https://iframe.mediadelivery.net/");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                if (conn.getResponseCode() >= 400) return null;
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                br.close();
                return sb.toString();
            } catch (Exception e) {
                return null;
            }
        }

        private long downloadAndEncryptFile(String urlStr, File outFile) throws Exception {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestProperty("Referer", "https://iframe.mediadelivery.net/");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            if (conn.getResponseCode() >= 400) return -1;
            InputStream is = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(outFile);
            byte[] buffer = new byte[16384];
            int len;
            long total = 0;
            while ((len = is.read(buffer)) != -1) {
                for (int i = 0; i < len; i++) buffer[i] = (byte) (buffer[i] ^ XOR_KEY);
                fos.write(buffer, 0, len);
                total += len;
            }
            fos.close();
            is.close();
            return total;
        }

        @JavascriptInterface
        public String getLocalStreamUrl(String classId) {
            File hlsPlaylist = new File(new File(new File(getFilesDir(), "rw_vault"), classId), "index.m3u8");
            if (hlsPlaylist.exists()) {
                return "http://127.0.0.1:" + localStreamPort + "/hls/" + classId + "/index.m3u8";
            }
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
            File hlsDir = new File(privateDir, classId);
            if (hlsDir.exists() && hlsDir.isDirectory()) {
                File[] children = hlsDir.listFiles();
                if (children != null) {
                    for (File c : children) c.delete();
                }
                hlsDir.delete();
            }
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "🗑️ Deleted from Offline Vault", Toast.LENGTH_SHORT).show());
        }
    }
}
