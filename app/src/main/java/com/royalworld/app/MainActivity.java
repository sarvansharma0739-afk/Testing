package com.royalworld.app;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ANTI-PIRACY: Block Screenshots & Screen Recording completely
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        webView = new WebView(this);
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
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    if (url.contains("shrinkme") || url.contains("t.me") || url.contains("whatsapp.com")) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    }
                }
                return false;
            }
        });

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        String startUrl = "file:///android_asset/index.html";
        if (intent != null && intent.getData() != null) {
            Uri data = intent.getData();
            String query = data.getQuery();
            if (query != null && !query.isEmpty()) {
                startUrl += "?" + query;
            }
        }
        webView.loadUrl(startUrl);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    public class RoyalBridge {
        @JavascriptInterface
        public void openExternal(String url) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
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
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "📥 Downloading PDF to Phone Storage...", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                openExternal(url);
            }
        }

        @JavascriptInterface
        public void saveOfflineVideo(String classId, String title, String videoUrl) {
            if (videoUrl == null || videoUrl.isEmpty() || videoUrl.contains("player.mediadelivery.net/play")) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "⚠️ Direct MP4 link required for offline save!", Toast.LENGTH_LONG).show());
                return;
            }
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "📥 Saving Offline inside App...", Toast.LENGTH_SHORT).show());
            new Thread(() -> {
                try {
                    File privateDir = new File(getFilesDir(), "rw_vault");
                    if (!privateDir.exists()) privateDir.mkdirs();
                    File outFile = new File(privateDir, classId + ".rwvid");

                    URL url = new URL(videoUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.connect();
                    InputStream is = conn.getInputStream();
                    FileOutputStream fos = new FileOutputStream(outFile);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    is.close();

                    File metaFile = new File(privateDir, classId + ".json");
                    JSONObject meta = new JSONObject();
                    meta.put("id", classId);
                    meta.put("title", title);
                    meta.put("path", outFile.getAbsolutePath());
                    FileOutputStream mFos = new FileOutputStream(metaFile);
                    mFos.write(meta.toString().getBytes());
                    mFos.close();

                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "✅ Saved Offline! Check 'Offline Videos' tab.", Toast.LENGTH_LONG).show();
                        webView.evaluateJavascript("if(window.refreshOfflineList) window.refreshOfflineList();", null);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "❌ Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
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
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "🗑️ Offline video deleted", Toast.LENGTH_SHORT).show());
        }
    }
}
