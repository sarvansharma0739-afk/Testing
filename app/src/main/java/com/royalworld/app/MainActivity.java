package com.royalworld.app;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.google.firebase.messaging.FirebaseMessaging;
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
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private static final byte XOR_KEY = (byte) 0x5A;
    private static final String SECRET_SALT = "RW_KING_2026_SECURE_KEY_9981";
    private static final String PUSH_CHANNEL_ID = "royalworld_push";
    private int localStreamPort = 0;

    private final BroadcastReceiver downloadProgressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (VideoDownloadService.ACTION_PROGRESS.equals(intent.getAction()) && webView != null) {
                String cid = intent.getStringExtra(VideoDownloadService.EXTRA_CLASS_ID);
                int pct = intent.getIntExtra(VideoDownloadService.EXTRA_PERCENT, 0);
                String status = intent.getStringExtra(VideoDownloadService.EXTRA_STATUS);
                String state = intent.getStringExtra(VideoDownloadService.EXTRA_STATE);
                String js = "if(window.onDownloadProgress){ window.onDownloadProgress("
                    + JSONObject.quote(cid != null ? cid : "") + ","
                    + pct + ","
                    + JSONObject.quote(status != null ? status : "") + ","
                    + JSONObject.quote(state != null ? state : "") + "); }";
                runOnUiThread(() -> webView.evaluateJavascript(js, null));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. ANTI-PIRACY: Block Screenshots & Screen Recording
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Color.parseColor("#080b12"));
        getWindow().setNavigationBarColor(Color.parseColor("#080b12"));

        // 2. ENVIRONMENT SECURITY CHECKS
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

        // 3. Request Notification Permission on Android 13+ (Required for Download Progress & Push Notifications)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }

        // 4. Safe Firebase Push Topic Subscription (Auto-activates when google-services.json is added)
        try {
            FirebaseMessaging.getInstance().subscribeToTopic("all_students");
        } catch (Throwable ignored) {}

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
public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
    String url = request.getUrl().toString();

    // Sirf Key Generation, Ad-Shortlinks aur Social apps bahar browser mein khulenge
    if (url.contains("key-generator") ||
        url.contains("t.me/") ||
        url.contains("wa.me/") ||
        url.contains("whatsapp.com") ||
        url.contains("instagram.com") ||
        url.contains("youtube.com") ||
        url.contains("youtu.be") ||
        url.contains("short") ||      // agar aapka ad-shortlink domain kuch aur hai toh uska naam yahan add kar dein
        url.contains("gplinks") ||
        url.contains("shrinkme")) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
        return true;
    }

    // Baaki SAB (Tests, Courses, PDFs, Website) app ke andar hi khulega
    view.loadUrl(url);
    return true;
}

        // Register Live Download Progress Receiver
        IntentFilter filter = new IntentFilter(VideoDownloadService.ACTION_PROGRESS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadProgressReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(downloadProgressReceiver, filter);
        }

        handleIntent(getIntent());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(downloadProgressReceiver); } catch (Exception ignored) {}
    }

    private String checkSecurityEnvironment() {
        String[] rootPaths = {
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su", "/sbin/magisk"
        };
        for (String path : rootPaths) {
            if (new File(path).exists()) {
                return "Rooted device detected! RoyalWorld cannot run on rooted devices to protect course content.";
            }
        }
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

            // Route 1: HLS Offline Vault (/hls/{classId}/index.m3u8 or seg_X.rwts)
            if (pathPart.startsWith("/hls/")) {
                String rel = pathPart.substring(5);
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
                for (int i = 0; i < read; i++) buf[i] = (byte) (buf[i] ^ XOR_KEY);
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
        public int getAppVersionCode() {
            try {
                PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    return (int) pi.getLongVersionCode();
                } else {
                    return pi.versionCode;
                }
            } catch (Exception e) {
                return 4;
            }
        }

        // In-App APK Updater: Downloads new APK with Notification progress bar & launches Android Installer
        @JavascriptInterface
        public void downloadAndInstallUpdate(String apkUrl) {
            if (apkUrl == null || apkUrl.trim().isEmpty()) return;
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "🚀 Downloading RoyalWorld Update...", Toast.LENGTH_LONG).show());
            new Thread(() -> {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel ch = new NotificationChannel("royalworld_updates", "App Updates", NotificationManager.IMPORTANCE_LOW);
                    nm.createNotificationChannel(ch);
                }
                int updNotifId = 5005;
                try {
                    File outFile = new File(getExternalFilesDir(null), "RoyalWorld-Update.apk");
                    HttpURLConnection conn = (HttpURLConnection) new URL(apkUrl.trim()).openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    if (conn.getResponseCode() >= 400) throw new Exception("HTTP " + conn.getResponseCode());

                    long totalLen = conn.getContentLengthLong();
                    InputStream is = conn.getInputStream();
                    FileOutputStream fos = new FileOutputStream(outFile);
                    byte[] buf = new byte[16384];
                    int len;
                    long downloaded = 0;
                    long lastTick = 0;

                    while ((len = is.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                        downloaded += len;
                        long now = System.currentTimeMillis();
                        if (now - lastTick > 350) {
                            lastTick = now;
                            int pct = totalLen > 0 ? (int) ((downloaded * 100L) / totalLen) : 50;
                            NotificationCompat.Builder b = new NotificationCompat.Builder(MainActivity.this, "royalworld_updates")
                                .setSmallIcon(android.R.drawable.stat_sys_download)
                                .setContentTitle("Updating RoyalWorld")
                                .setContentText(pct + "% downloaded...")
                                .setOnlyAlertOnce(true)
                                .setOngoing(true)
                                .setProgress(100, pct, totalLen <= 0);
                            nm.notify(updNotifId, b.build());
                            final int fp = pct;
                            runOnUiThread(() -> webView.evaluateJavascript("if(window.onAppUpdateProgress){window.onAppUpdateProgress(" + fp + ");}", null));
                        }
                    }
                    fos.close();
                    is.close();
                    nm.cancel(updNotifId);

                    runOnUiThread(() -> {
                        try {
                            Uri apkUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", outFile);
                            Intent installIntent = new Intent(Intent.ACTION_VIEW);
                            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(installIntent);
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "Open Downloads to install update: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception e) {
                    nm.cancel(updNotifId);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "❌ Update download failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        }

        // Instant Local Push Notification triggered from RoyalWorld-Admin.html via royal_data.enc
        @JavascriptInterface
        public void showLocalNotification(String noticeId, String title, String message) {
            if (noticeId == null || noticeId.isEmpty() || title == null || title.isEmpty()) return;
            android.content.SharedPreferences prefs = getSharedPreferences("rw_seen_notices", Context.MODE_PRIVATE);
            if (prefs.getBoolean(noticeId, false)) return;
            prefs.edit().putBoolean(noticeId, true).apply();

            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(PUSH_CHANNEL_ID, "RoyalWorld Announcements", NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(ch);
            }
            Intent intent = new Intent(MainActivity.this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(MainActivity.this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder b = new NotificationCompat.Builder(MainActivity.this, PUSH_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);
            nm.notify(noticeId.hashCode(), b.build());
        }

        @JavascriptInterface
        public String getDeviceId() {
            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            return androidId != null ? androidId : "RW_DEVICE";
        }

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
                android.content.SharedPreferences prefs = getSharedPreferences("rw_burned_keys", Context.MODE_PRIVATE);
                if (prefs.getBoolean(clean, false)) return false;

                long keyEpochSec = Long.parseLong(tsHex, 16);
                long nowSec = System.currentTimeMillis() / 1000L;
                if (nowSec - keyEpochSec > 900L || keyEpochSec - nowSec > 120L) return false;

                String devId = getDeviceId().toUpperCase(Locale.ROOT);
                String expectedSig = computeKeySignature(devId, tsHex, nonce);
                if (expectedSig.equals(sig)) {
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
            for (int i = 0; i < 4; i++) hex.append(String.format("%02X", hash[i]));
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

        @JavascriptInterface
        public void saveOfflineVideo(String classId, String title, String videoUrl) {
            saveOfflineVideoWithQuality(classId, title, videoUrl, "480p");
        }

        @JavascriptInterface
        public void saveOfflineVideoWithQuality(String classId, String title, String videoUrl, String quality) {
            if (videoUrl == null || videoUrl.trim().isEmpty()) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "⚠️ Video link not available.", Toast.LENGTH_SHORT).show());
                return;
            }
            Intent intent = new Intent(MainActivity.this, VideoDownloadService.class);
            intent.setAction(VideoDownloadService.ACTION_START);
            intent.putExtra(VideoDownloadService.EXTRA_CLASS_ID, classId);
            intent.putExtra(VideoDownloadService.EXTRA_TITLE, title);
            intent.putExtra(VideoDownloadService.EXTRA_URL, videoUrl.trim());
            intent.putExtra(VideoDownloadService.EXTRA_QUALITY, quality);
            ContextCompat.startForegroundService(MainActivity.this, intent);
        }

        @JavascriptInterface
        public void cancelOfflineDownload(String classId) {
            Intent intent = new Intent(MainActivity.this, VideoDownloadService.class);
            intent.setAction(VideoDownloadService.ACTION_CANCEL);
            intent.putExtra(VideoDownloadService.EXTRA_CLASS_ID, classId);
            startService(intent);
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
