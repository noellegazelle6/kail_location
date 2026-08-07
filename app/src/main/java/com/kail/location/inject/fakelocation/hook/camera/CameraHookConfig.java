package com.kail.location.inject.fakelocation.hook.camera;


import com.kail.location.utils.KailLog;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the virtual-camera config that the controller app drops at
 * {@link #CONFIG_PATH}. The controller (kail app, running as shell/root)
 * writes a small flat JSON object there; injected app processes only read it.
 *
 * Kept dependency-free (hand-rolled parser) so the slim inject dex does not
 * need org.json. Field set intentionally mirrors the subset of CamSwap's
 * config that the ported hooks honour.
 */
public final class CameraHookConfig {

    private static final String TAG = "CameraHookCfg";

    /** World-readable (chmod 666) by the controller; inside /data/kail-loc. */
    public static final String CONFIG_PATH = "/data/kail-loc/camera_config.json";

    public boolean enabled;
    public String videoPath = "";
    /** Extra clockwise rotation applied to frames, degrees (0/90/180/270). */
    public int rotationOffset;
    /** Whether takePicture / JPEG readers should be fed a frame of the video. */
    public boolean photoFakeEnabled;
    public final Set<String> targetPackages = new HashSet<>();

    /** Play the replacement video's audio track through the preview player. */
    public boolean videoSound;
    /** Pick a random video from {@link #videoDir} per camera session. */
    public boolean randomPlay;
    /** Directory holding the pushed video library (random-play pool). */
    public String videoDir = "";
    /** "video" | "image". */
    public String replaceMode = "video";
    /** Replacement image path (image mode). */
    public String imagePath = "";
    /** "off" | "mute" | "replace" | "video_sync". */
    public String micMode = "off";
    /** Replacement audio file path (mic_mode=replace). */
    public String audioPath = "";
    /** "local" | "stream". */
    public String mediaSource = "local";
    /** Stream URL (media_source=stream): RTSP/HLS/HTTP. */
    public String streamUrl = "";

    private static volatile CameraHookConfig cached;
    private static volatile long cachedAtMs;

    private CameraHookConfig() {
    }

    /** Returns the latest config, re-reading the file at most every 1.5s. */
    public static CameraHookConfig get() {
        long now = System.currentTimeMillis();
        CameraHookConfig c = cached;
        if (c != null && now - cachedAtMs < 1500L) {
            return c;
        }
        CameraHookConfig fresh = load();
        cached = fresh;
        cachedAtMs = now;
        return fresh;
    }

    /** Drops the cache so the next {@link #get()} re-reads the file. */
    public static void invalidate() {
        cached = null;
    }

    public static CameraHookConfig load() {
        CameraHookConfig cfg = new CameraHookConfig();
        try {
            File f = new File(CONFIG_PATH);
            if (!f.exists()) {
                return cfg;
            }
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[(int) Math.min(f.length(), 1 << 20)];
            int off = 0;
            int r;
            while (off < buf.length && (r = in.read(buf, off, buf.length - off)) != -1) {
                off += r;
            }
            in.close();
            String json = new String(buf, 0, off, "UTF-8").trim();
            parseInto(json, cfg);
        } catch (Throwable th) {
            KailLog.INSTANCE.e(null, TAG, "load config failed", th);
        }
        return cfg;
    }

    public boolean appliesTo(String packageName) {
        return enabled && packageName != null && targetPackages.contains(packageName);
    }

    public boolean hasVideo() {
        return videoPath != null && !videoPath.isEmpty() && new File(videoPath).exists();
    }

    public boolean isImageMode() {
        return "image".equals(replaceMode);
    }

    public boolean hasImage() {
        return imagePath != null && !imagePath.isEmpty() && new File(imagePath).exists();
    }

    public boolean isStreamMode() {
        return "stream".equals(mediaSource);
    }

    public boolean hasStream() {
        return streamUrl != null && !streamUrl.isEmpty();
    }

    public boolean hasRandomPool() {
        if (!randomPlay || videoDir == null || videoDir.isEmpty()) return false;
        File[] files = new File(videoDir).listFiles();
        return files != null && files.length > 0;
    }

    /** mic hook active for this config at all (any non-off mode). */
    public boolean micHookEnabled() {
        return micMode != null && !"off".equals(micMode);
    }

    // ------------------------------------------------------------------
    // Minimal flat-JSON parser ("key":value pairs, values string|number|
    // boolean|string-array). Escapes inside strings are handled for \" \\ \n.
    // ------------------------------------------------------------------
    private static void parseInto(String json, CameraHookConfig cfg) {
        if (json == null || json.length() < 2) return;
        int i = 0;
        int n = json.length();
        if (json.charAt(0) == '{') i = 1;
        while (i < n) {
            i = skipWs(json, i);
            if (i >= n || json.charAt(i) == '}') break;
            if (json.charAt(i) == ',') { i++; continue; }
            if (json.charAt(i) != '"') { i++; continue; }
            int[] end = new int[1];
            String key = readString(json, i, end);
            i = skipWs(json, end[0]);
            if (i >= n || json.charAt(i) != ':') continue;
            i = skipWs(json, i + 1);
            if (i >= n) break;
            char c = json.charAt(i);
            if (c == '"') {
                String v = readString(json, i, end);
                i = end[0];
                applyString(cfg, key, v);
            } else if (c == '[') {
                List<String> arr = new ArrayList<>();
                i++;
                while (i < n && json.charAt(i) != ']') {
                    i = skipWs(json, i);
                    if (i < n && json.charAt(i) == '"') {
                        arr.add(readString(json, i, end));
                        i = end[0];
                    } else if (i < n && json.charAt(i) == ',') {
                        i++;
                    } else {
                        i++;
                    }
                }
                if (i < n) i++; // consume ']'
                if ("target_packages".equals(key)) {
                    cfg.targetPackages.addAll(arr);
                }
            } else {
                // number / boolean / null — read until delimiter
                int j = i;
                while (j < n && ",} \t\r\n".indexOf(json.charAt(j)) < 0) j++;
                String raw = json.substring(i, j);
                i = j;
                applyScalar(cfg, key, raw);
            }
        }
    }

    private static void applyString(CameraHookConfig cfg, String key, String v) {
        switch (key) {
            case "video_path": cfg.videoPath = v; break;
            case "video_dir": cfg.videoDir = v; break;
            case "replace_mode": cfg.replaceMode = v; break;
            case "image_path": cfg.imagePath = v; break;
            case "mic_mode": cfg.micMode = v; break;
            case "audio_path": cfg.audioPath = v; break;
            case "media_source": cfg.mediaSource = v; break;
            case "stream_url": cfg.streamUrl = v; break;
            default: break;
        }
    }

    private static void applyScalar(CameraHookConfig cfg, String key, String raw) {
        try {
            switch (key) {
                case "enabled":
                    cfg.enabled = "true".equals(raw);
                    break;
                case "rotation_offset":
                    cfg.rotationOffset = Integer.parseInt(raw);
                    break;
                case "photo_fake":
                    cfg.photoFakeEnabled = "true".equals(raw);
                    break;
                case "video_sound":
                    cfg.videoSound = "true".equals(raw);
                    break;
                case "random_play":
                    cfg.randomPlay = "true".equals(raw);
                    break;
                default:
                    break;
            }
        } catch (Throwable ignored) {
        }
    }

    private static int skipWs(String s, int i) {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') i++; else break;
        }
        return i;
    }

    private static String readString(String s, int i, int[] end) {
        StringBuilder sb = new StringBuilder();
        i++; // opening quote
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char e = s.charAt(i + 1);
                if (e == 'n') sb.append('\n');
                else if (e == 't') sb.append('\t');
                else sb.append(e);
                i += 2;
            } else if (c == '"') {
                i++;
                break;
            } else {
                sb.append(c);
                i++;
            }
        }
        end[0] = i;
        return sb.toString();
    }
}
