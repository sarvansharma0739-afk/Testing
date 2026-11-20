package com.royalworld.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoDownloadService extends Service {
    public static final String ACTION_START = "com.royalworld.app.ACTION_START_DOWNLOAD";
    public static final String ACTION_CANCEL = "com.royalworld.app.ACTION_CANCEL_DOWNLOAD";
    public static final String ACTION_PROGRESS = "com.royalworld.app.ACTION_DOWNLOAD_PROGRESS";

    public static final String EXTRA_CLASS_ID = "classId";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_QUALITY = "quality";
    public static final String EXTRA_PERCENT = "percent";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_STATE = "state"; // "downloading", "done", "error", "cancelled"

    private static final String CHANNEL_ID = "royalworld_downloads";
    private static final int NOTIF_ID = 4001;
    private static final byte XOR_KEY = (byte) 0x5A;
    private static final String BUNNY_CDN_HOST = "vz-0d9ed9ee-956.b-cdn.net";

    private NotificationManager notifManager;
    private final ConcurrentHashMap<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();
    private final ExecutorService jobQueue = Executors.newSingleThreadExecutor();
    private final AtomicInteger activeJobs = new AtomicInteger(0);

    @Override
    public void onCreate() {
        super.onCreate();
        notifManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createChannel();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "Offline Lecture Downloads",
                NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("Shows live download progress for offline lectures");
            notifManager.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        String classId = intent.getStringExtra(EXTRA_CLASS_ID);

        if (ACTION_CANCEL.equals(action) && classId != null) {
            AtomicBoolean flag = cancelFlags.get(classId);
            if (flag != null) flag.set(true);
            sendProgressBroadcast(classId, 0, "Download Cancelled", "cancelled");
            return START_NOT_STICKY;
        }

        String title = intent.getStringExtra(EXTRA_TITLE);
        String url = intent.getStringExtra(EXTRA_URL);
        String quality = intent.getStringExtra(EXTRA_QUALITY);
        if (classId == null || url == null) return START_NOT_STICKY;

        AtomicBoolean cancelFlag = new AtomicBoolean(false);
        cancelFlags.put(classId, cancelFlag);
        activeJobs.incrementAndGet();

        startForegroundCompat(title != null ? title : "Lecture", "Connecting...", 0, classId);

        jobQueue.submit(() -> {
            try {
                runDownloadJob(classId, title != null ? title : "Lecture", url.trim(), "720p".equals(quality) ? "720p" : "480p", cancelFlag);
            } finally {
                cancelFlags.remove(classId);
                if (activeJobs.decrementAndGet() <= 0) {
                    stopForeground(STOP_FOREGROUND_DETACH);
                    stopSelf();
                }
            }
        });

        return START_NOT_STICKY;
    }

    private void startForegroundCompat(String title, String text, int progress, String classId) {
        NotificationCompat.Builder builder = buildProgressNotif(title, text, progress, classId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIF_ID, builder.build());
        }
    }

    private NotificationCompat.Builder buildProgressNotif(String title, String text, int progress, String classId) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent cancelIntent = new Intent(this, VideoDownloadService.class);
        cancelIntent.setAction(ACTION_CANCEL);
        cancelIntent.putExtra(EXTRA_CLASS_ID, classId);
        PendingIntent cancelPi = PendingIntent.getService(this, classId.hashCode(), cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading: " + title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPi);
    }

    private void notifyFinished(String title, String msg, boolean success, int idOffset) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(success ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(msg)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(openPi);
        notifManager.notify(NOTIF_ID + 100 + Math.abs(idOffset % 500), b.build());
    }

    private void sendProgressBroadcast(String classId, int percent, String status, String state) {
        Intent i = new Intent(ACTION_PROGRESS);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_CLASS_ID, classId);
        i.putExtra(EXTRA_PERCENT, percent);
        i.putExtra(EXTRA_STATUS, status);
        i.putExtra(EXTRA_STATE, state);
        sendBroadcast(i);
    }

    private void runDownloadJob(String classId, String title, String rawUrl, String requestedQuality, AtomicBoolean cancelled) {
        try {
            File vaultRoot = new File(getFilesDir(), "rw_vault");
            if (!vaultRoot.exists()) vaultRoot.mkdirs();

            sendProgressBroadcast(classId, 1, "Finding stream...", "downloading");

            // Case A: Direct MP4 Link
            if (rawUrl.endsWith(".mp4") || rawUrl.contains(".mp4?")) {
                downloadDirectMp4(vaultRoot, classId, title, requestedQuality, rawUrl, cancelled);
                return;
            }

            // Case B: Bunny.net HLS (.m3u8)
            String videoUuid = extractUuid(rawUrl);
            if (videoUuid == null) {
                throw new Exception("Invalid Bunny video link");
            }

            // Step 1: Resolve exact sub-playlist from master playlist.m3u8 first, then fallback to direct paths
            String[] resolved = resolveBunnyHlsPlaylist(videoUuid, requestedQuality);
            if (resolved == null) {
                throw new Exception("Stream not reachable on Bunny CDN");
            }

            String activeQuality = resolved[0];
            String playlistText = resolved[1];
            String baseSegmentUrl = resolved[2];

            // Parse segments
            String[] lines = playlistText.split("\\r?\\n");
            List<String> segmentUrls = new ArrayList<>();
            List<String> playlistOutputLines = new ArrayList<>();
            int segIdx = 0;

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.startsWith("#")) {
                    playlistOutputLines.add(trimmed);
                } else {
                    String segUrl = trimmed.startsWith("http") ? trimmed : (baseSegmentUrl + trimmed);
                    String localName = "seg_" + segIdx + ".rwts";
                    segmentUrls.add(segUrl);
                    playlistOutputLines.add(localName);
                    segIdx++;
                }
            }

            final int totalSegments = segmentUrls.size();
            if (totalSegments == 0) {
                throw new Exception("No video segments found");
            }

            File hlsDir = new File(vaultRoot, classId);
            if (!hlsDir.exists()) hlsDir.mkdirs();

            // Multi-threaded 6x parallel segment download!
            ExecutorService pool = Executors.newFixedThreadPool(6);
            AtomicInteger completed = new AtomicInteger(0);
            AtomicLong totalBytes = new AtomicLong(0);
            AtomicBoolean failed = new AtomicBoolean(false);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < totalSegments; i++) {
                final int idx = i;
                final String segUrl = segmentUrls.get(i);
                futures.add(pool.submit(() -> {
                    if (cancelled.get() || failed.get()) return;
                    File outSeg = new File(hlsDir, "seg_" + idx + ".rwts");
                    long bytes = downloadAndEncryptWithRetry(segUrl, outSeg, cancelled);
                    if (bytes <= 0) {
                        failed.set(true);
                        return;
                    }
                    totalBytes.addAndGet(bytes);
                    int done = completed.incrementAndGet();
                    int pct = Math.min(99, (int) ((done * 100L) / totalSegments));
                    if (done == 1 || done % 3 == 0 || done == totalSegments) {
                        String mb = String.format(Locale.US, "%.1f MB", totalBytes.get() / (1024.0 * 1024.0));
                        String status = pct + "% • " + mb + " (" + activeQuality + ")";
                        notifManager.notify(NOTIF_ID, buildProgressNotif(title, status, pct, classId).build());
                        sendProgressBroadcast(classId, pct, status, "downloading");
                    }
                }));
            }

            for (Future<?> f : futures) {
                try { f.get(); } catch (Exception ignored) {}
            }
            pool.shutdownNow();

            if (cancelled.get()) {
                deleteRecursive(hlsDir);
                notifManager.cancel(NOTIF_ID);
                return;
            }
            if (failed.get()) {
                deleteRecursive(hlsDir);
                throw new Exception("Network interrupted during download");
            }

            // Write local index.m3u8
            StringBuilder sb = new StringBuilder();
            for (String l : playlistOutputLines) sb.append(l).append("\n");
            FileOutputStream pFos = new FileOutputStream(new File(hlsDir, "index.m3u8"));
            pFos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            pFos.close();

            String sizeMb = String.format(Locale.US, "%.1f MB", totalBytes.get() / (1024.0 * 1024.0));
            File metaFile = new File(vaultRoot, classId + ".json");
            JSONObject meta = new JSONObject();
            meta.put("id", classId);
            meta.put("type", "hls");
            meta.put("title", title + " (" + activeQuality + ")");
            meta.put("sizeMb", sizeMb);
            FileOutputStream mFos = new FileOutputStream(metaFile);
            mFos.write(meta.toString().getBytes(StandardCharsets.UTF_8));
            mFos.close();

            notifManager.cancel(NOTIF_ID);
            notifyFinished("✅ Saved Offline: " + title, activeQuality + " • " + sizeMb + " (Ready to watch offline)", true, classId.hashCode());
            sendProgressBroadcast(classId, 100, "Saved (" + sizeMb + ")", "done");

        } catch (Exception e) {
            notifManager.cancel(NOTIF_ID);
            notifyFinished("❌ Download Failed: " + title, e.getMessage(), false, classId.hashCode());
            sendProgressBroadcast(classId, 0, "Failed: " + e.getMessage(), "error");
        }
    }

    private void downloadDirectMp4(File vaultRoot, String classId, String title, String qLabel, String mp4Url, AtomicBoolean cancelled) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(mp4Url).openConnection();
        conn.setRequestProperty("Referer", "https://iframe.mediadelivery.net/");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(25000);
        if (conn.getResponseCode() >= 400) throw new Exception("HTTP " + conn.getResponseCode());

        long contentLength = conn.getContentLengthLong();
        File outFile = new File(vaultRoot, classId + ".rwvid");
        InputStream is = conn.getInputStream();
        FileOutputStream fos = new FileOutputStream(outFile);
        byte[] buf = new byte[16384];
        int len;
        long total = 0;
        long lastUpdate = 0;

        while ((len = is.read(buf)) != -1) {
            if (cancelled.get()) {
                fos.close(); is.close(); outFile.delete();
                notifManager.cancel(NOTIF_ID);
                return;
            }
            for (int i = 0; i < len; i++) buf[i] = (byte) (buf[i] ^ XOR_KEY);
            fos.write(buf, 0, len);
            total += len;
            long now = System.currentTimeMillis();
            if (now - lastUpdate > 400) {
                lastUpdate = now;
                int pct = contentLength > 0 ? (int) ((total * 100L) / contentLength) : 50;
                String mb = String.format(Locale.US, "%.1f MB", total / (1024.0 * 1024.0));
                String status = pct + "% • " + mb;
                notifManager.notify(NOTIF_ID, buildProgressNotif(title, status, pct, classId).build());
                sendProgressBroadcast(classId, pct, status, "downloading");
            }
        }
        fos.close();
        is.close();

        String sizeMb = String.format(Locale.US, "%.1f MB", total / (1024.0 * 1024.0));
        File metaFile = new File(vaultRoot, classId + ".json");
        JSONObject meta = new JSONObject();
        meta.put("id", classId);
        meta.put("type", "mp4");
        meta.put("title", title + " (" + qLabel + ")");
        meta.put("sizeMb", sizeMb);
        FileOutputStream mFos = new FileOutputStream(metaFile);
        mFos.write(meta.toString().getBytes(StandardCharsets.UTF_8));
        mFos.close();

        notifManager.cancel(NOTIF_ID);
        notifyFinished("✅ Saved Offline: " + title, qLabel + " • " + sizeMb, true, classId.hashCode());
        sendProgressBroadcast(classId, 100, "Saved (" + sizeMb + ")", "done");
    }

    private String[] resolveBunnyHlsPlaylist(String videoUuid, String requestedQuality) {
        String baseRoot = "https://" + BUNNY_CDN_HOST + "/" + videoUuid + "/";
        // 1. Try master playlist.m3u8 first to discover exact available resolutions
        String master = fetchTextUrl(baseRoot + "playlist.m3u8");
        if (master != null && master.contains("#EXTM3U")) {
            String[] lines = master.split("\\r?\\n");
            List<String> variants = new ArrayList<>();
            for (String l : lines) {
                String t = l.trim();
                if (!t.isEmpty() && !t.startsWith("#") && t.contains(".m3u8")) {
                    variants.add(t);
                }
            }
            String[] prefs = "720p".equals(requestedQuality)
                ? new String[]{"720p", "480p", "360p", "1080p", "240p"}
                : new String[]{"480p", "360p", "720p", "240p", "1080p"};

            for (String p : prefs) {
                for (String v : variants) {
                    if (v.contains(p)) {
                        String subUrl = v.startsWith("http") ? v : (baseRoot + v);
                        String subText = fetchTextUrl(subUrl);
                        if (subText != null && subText.contains("#EXTM3U")) {
                            String subBase = subUrl.substring(0, subUrl.lastIndexOf('/') + 1);
                            return new String[]{p, subText, subBase};
                        }
                    }
                }
            }
            // If no named resolution matched, pick first valid variant
            for (String v : variants) {
                String subUrl = v.startsWith("http") ? v : (baseRoot + v);
                String subText = fetchTextUrl(subUrl);
                if (subText != null && subText.contains("#EXTM3U")) {
                    String subBase = subUrl.substring(0, subUrl.lastIndexOf('/') + 1);
                    return new String[]{requestedQuality, subText, subBase};
                }
            }
        }

        // 2. Fallback to direct quality paths
        String[] tryQ = "720p".equals(requestedQuality)
            ? new String[]{"720p", "480p", "360p"}
            : new String[]{"480p", "360p", "720p"};
        for (String q : tryQ) {
            String subUrl = baseRoot + q + "/video.m3u8";
            String subText = fetchTextUrl(subUrl);
            if (subText != null && subText.contains("#EXTM3U")) {
                return new String[]{q, subText, baseRoot + q + "/"};
            }
        }
        return null;
    }

    private String extractUuid(String rawUrl) {
        Pattern p = Pattern.compile("([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");
        Matcher m = p.matcher(rawUrl);
        return m.find() ? m.group(1) : null;
    }

    private String fetchTextUrl(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestProperty("Referer", "https://iframe.mediadelivery.net/");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36");
            conn.setConnectTimeout(12000);
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

    private long downloadAndEncryptWithRetry(String urlStr, File outFile, AtomicBoolean cancelled) {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (cancelled.get()) return -1;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestProperty("Referer", "https://iframe.mediadelivery.net/");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(20000);
                if (conn.getResponseCode() >= 400) continue;
                InputStream is = conn.getInputStream();
                FileOutputStream fos = new FileOutputStream(outFile);
                byte[] buf = new byte[16384];
                int len;
                long total = 0;
                while ((len = is.read(buf)) != -1) {
                    if (cancelled.get()) {
                        fos.close(); is.close(); outFile.delete();
                        return -1;
                    }
                    for (int i = 0; i < len; i++) buf[i] = (byte) (buf[i] ^ XOR_KEY);
                    fos.write(buf, 0, len);
                    total += len;
                }
                fos.close();
                is.close();
                if (total > 0) return total;
            } catch (Exception ignored) {}
        }
        return -1;
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursive(c);
            }
        }
        f.delete();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
