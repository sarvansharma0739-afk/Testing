package com.royalworld.app;

import android.app.Dialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
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
    private Dialog adDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ANTI-PIRACY: Block Screenshots & Screen Recording completely
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Color.parseColor("#080b12"));
        getWindow().setNavigationBarColor(Color.parseColor("#080b12"));

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
                // Intercept ad completion redirect BEFORE it loads the website!
                if (url.contains("ad_success=1") || url.contains("unlock=") || url.startsWith("royalworld://")) {
                    triggerUnlockFromUrl(url);
                    return true;
                }
                // Open ShrinkMe ad inside In-App Ad Browser so user NEVER leaves the APK!
                if (url.contains("shrinkme") || url.contains("gplinks") || url.contains("droplink")) {
                    openInAppAdBrowser(url);
                    return true;
                }
                if (url.contains("t.me") || url.contains("whatsapp.com")) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                }
                return false;
            }
        });

        handleIntent(getIntent());
    }

    private void openInAppAdBrowser(String adUrl) {
        runOnUiThread(() -> {
            if (adDialog != null && adDialog.isShowing()) {
                adDialog.dismiss();
            }
            adDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            adDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(Color.parseColor("#080b12"));

            // Sleek Top Bar
            LinearLayout topBar = new LinearLayout(this);
            topBar.setOrientation(LinearLayout.HORIZONTAL);
            topBar.setBackgroundColor(Color.parseColor("#111722"));
            topBar.setPadding(32, 24, 32, 24);
            topBar.setGravity(Gravity.CENTER_VERTICAL);

            TextView title = new TextView(this);
            title.setText("🎁 Complete Step to Unlock Batch");
            title.setTextColor(Color.parseColor("#f7d98c"));
            title.setTextSize(14f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            title.setLayoutParams(lp);

            Button closeBtn = new Button(this);
            closeBtn.setText("✕ Close");
            closeBtn.setTextColor(Color.WHITE);
            closeBtn.setBackgroundColor(Color.parseColor("#263244"));
            closeBtn.setPadding(24, 8, 24, 8);
            closeBtn.setOnClickListener(v -> adDialog.dismiss());

            topBar.addView(title);
            topBar.addView(closeBtn);

            WebView adWeb = new WebView(this);
            WebSettings ws = adWeb.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setSupportMultipleWindows(false);

            adWeb.setWebChromeClient(new WebChromeClient());
            adWeb.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return checkAdRedirect(request.getUrl().toString());
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return checkAdRedirect(url);
                }

                private boolean checkAdRedirect(String url) {
                    // As soon as ShrinkMe finishes and redirects to royalworldofficial.vercel.app/?ad_success=1 or royalworld://unlock
                    // Close the ad dialog immediately and unlock the batch inside the APK!
                    if (url.contains("ad_success=1") || url.contains("unlock=") || url.startsWith("royalworld://") || url.contains("royalworldofficial.vercel.app")) {
                        adDialog.dismiss();
                        triggerUnlockFromUrl(url);
                        return true;
                    }
                    return false;
                }
            });

            root.addView(topBar);
            root.addView(adWeb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            adDialog.setContentView(root);
            adDialog.show();
            adWeb.loadUrl(adUrl);
        });
    }

    private void triggerUnlockFromUrl(String url) {
        runOnUiThread(() -> {
            String courseId = "";
            try {
                Uri uri = Uri.parse(url);
                if (uri.getQueryParameter("unlock") != null) courseId = uri.getQueryParameter("unlock");
                else if (uri.getQueryParameter("unlock_course") != null) courseId = uri.getQueryParameter("unlock_course");
                else if (url.contains("unlock=")) {
                    courseId = url.split("unlock=")[1].split("&")[0];
                }
            } catch (Exception ignored) {}
            final String cid = courseId;
            webView.evaluateJavascript("if(window.onNativeAdSuccess){ window.onNativeAdSuccess('" + cid + "'); } else if(window.onRewardedAdCompleted){ window.onRewardedAdCompleted(); }", null);
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getData() != null) {
            String url = intent.getData().toString();
            if (url.contains("ad_success=1") || url.contains("unlock=") || url.startsWith("royalworld://")) {
                if (webView.getUrl() != null) {
                    triggerUnlockFromUrl(url);
                    return;
                }
            }
        }
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
        public void openAdUrl(String url) {
            openInAppAdBrowser(url);
        }

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
                        Toast.makeText(MainActivity.this, "✅ Saved to Offline Vault!", Toast.LENGTH_LONG).show();
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
