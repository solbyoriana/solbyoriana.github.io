package com.solbyoriana.screensaver;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps a copy of the website's nail-art photos on the TV and refreshes it every 3 days. */
final class PhotoStore {
    private static final String TAG = "SolPhotos";
    private static final String SITE = "https://solbyoriana.github.io/";
    private static final long CAP_BYTES = 2L * 1024 * 1024 * 1024;
    private static final long SYNC_EVERY_MS = 3L * 24 * 60 * 60 * 1000;
    private static final Pattern SHORTCODE = Pattern.compile("/(?:p|reel|tv)/([^/?#]+)");
    private static final AtomicBoolean syncing = new AtomicBoolean();

    private final File dir;
    private final SharedPreferences prefs;

    PhotoStore(Context context) {
        dir = new File(context.getFilesDir(), "photos");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE);
    }

    List<File> photos() {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".jpg"));
        return files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
    }

    void forceNextSync() {
        prefs.edit().putLong("last", 0).apply();
    }

    /** Starts a quiet background refresh if the last one was 3+ days ago (or nothing is saved yet). */
    void syncInBackground() {
        boolean due = photos().isEmpty() || System.currentTimeMillis() - prefs.getLong("last", 0) > SYNC_EVERY_MS;
        if (!due || !syncing.compareAndSet(false, true)) return;
        Thread t = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            try {
                sync();
                prefs.edit().putLong("last", System.currentTimeMillis()).apply();
            } catch (Throwable e) {
                Log.w(TAG, "Refresh failed, keeping the saved photos", e);
            } finally {
                syncing.set(false);
            }
        }, "sol-sync");
        t.start();
    }

    private void sync() throws Exception {
        JSONArray posts = new JSONObject(get("content/posts.json")).getJSONArray("posts");
        Set<String> hidden = codes(optArray("content/hidden.json"));
        Set<String> pinned = codes(optArray("content/pinned.json"));
        JSONObject curation = optObject("content/curation.json");
        Set<String> approved = codes(curation.optJSONArray("approved"));
        List<String> keywords = new ArrayList<>();
        JSONArray kw = curation.optJSONArray("autoHideKeywords");
        for (int i = 0; kw != null && i < kw.length(); i++) keywords.add(kw.getString(i).toLowerCase(Locale.ROOT));

        // Same rules as site/js/app.js: drop hidden posts and promo/tutorial captions unless pinned or approved.
        // Then keep photo posts only (any video in the post skips the whole post), newest first.
        List<String[]> wanted = new ArrayList<>(); // {timestamp, file name}
        for (int i = 0; i < posts.length(); i++) {
            JSONObject post = posts.getJSONObject(i);
            String code = shortcodeOf(post.optString("permalink"));
            if (hidden.contains(post.optString("id")) || hidden.contains(code)) continue;
            if (!approved.contains(code) && !pinned.contains(code)) {
                String caption = post.optString("caption").toLowerCase(Locale.ROOT);
                boolean promo = false;
                for (String k : keywords) if (caption.contains(k)) { promo = true; break; }
                if (promo) continue;
            }
            JSONArray media = post.optJSONArray("media");
            if (media == null || media.length() == 0) continue;
            List<String> names = new ArrayList<>();
            boolean hasVideo = false;
            for (int j = 0; j < media.length(); j++) {
                JSONObject m = media.getJSONObject(j);
                if (!"image".equals(m.optString("type"))) { hasVideo = true; break; }
                String src = m.optString("src");
                if (src.endsWith(".jpg")) names.add(src.substring(src.lastIndexOf('/') + 1));
            }
            if (hasVideo) continue;
            for (String n : names) wanted.add(new String[]{post.optString("timestamp"), n});
        }
        if (wanted.isEmpty()) throw new IOException("Website returned no photos; not touching the saved ones");
        wanted.sort((a, b) -> b[0].compareTo(a[0]));

        // Newest first until 2 GB, so the oldest are the ones left out once the stick's share is full.
        Set<String> keep = new HashSet<>();
        long total = 0;
        for (String[] w : wanted) {
            File f = new File(dir, w[1]);
            if (!f.exists()) {
                try {
                    download("media/" + w[1], f);
                } catch (IOException e) {
                    Log.w(TAG, "Skipping " + w[1], e);
                    continue;
                }
            }
            if (total + f.length() > CAP_BYTES) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                break;
            }
            total += f.length();
            keep.add(w[1]);
        }
        File[] all = dir.listFiles();
        for (File f : all == null ? new File[0] : all) {
            //noinspection ResultOfMethodCallIgnored
            if (!keep.contains(f.getName())) f.delete();
        }
        Log.i(TAG, "Saved " + keep.size() + " photos, " + (total >> 20) + " MB");
    }

    private static String shortcodeOf(String s) {
        Matcher m = SHORTCODE.matcher(s);
        return m.find() ? m.group(1) : s.trim();
    }

    private static Set<String> codes(JSONArray a) throws Exception {
        Set<String> out = new HashSet<>();
        for (int i = 0; a != null && i < a.length(); i++) out.add(shortcodeOf(a.getString(i)));
        return out;
    }

    private static JSONArray optArray(String path) {
        try { return new JSONArray(get(path)); } catch (Exception e) { return new JSONArray(); }
    }

    private static JSONObject optObject(String path) {
        try { return new JSONObject(get(path)); } catch (Exception e) { return new JSONObject(); }
    }

    private static HttpURLConnection open(String path) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(SITE + path).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        c.setUseCaches(false);
        if (c.getResponseCode() != 200) throw new IOException(path + " -> HTTP " + c.getResponseCode());
        return c;
    }

    private static String get(String path) throws IOException {
        HttpURLConnection c = open(path);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(in, out);
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            c.disconnect();
        }
    }

    private static void download(String path, File dest) throws IOException {
        File tmp = new File(dest.getPath() + ".part");
        HttpURLConnection c = open(path);
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
            copy(in, out);
        } finally {
            c.disconnect();
        }
        if (!tmp.renameTo(dest)) throw new IOException("Could not save " + dest.getName());
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[16 * 1024];
        for (int n; (n = in.read(buf)) != -1; ) out.write(buf, 0, n);
    }
}
