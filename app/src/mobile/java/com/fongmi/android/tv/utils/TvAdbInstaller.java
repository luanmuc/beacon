package com.fongmi.android.tv.utils;

import android.content.Context;
import android.text.TextUtils;

import com.fongmi.android.tv.App;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;

/**
 * 通过局域网 ADB 给电视安装/更新 APK。
 * 支持：无线调试配对码、已配对的 TLS 连接、老盒子 5555 端口。
 */
public class TvAdbInstaller {

    public interface Listener {
        void onStatus(String text);

        void onDone(boolean success, String message);
    }

    private static final String REMOTE_PATH = "/data/local/tmp/beacon-install.apk";

    public static void install(Context context, String host, int pairPort, String code, boolean armv7, Listener listener) {
        new Thread(() -> {
            try {
                status(listener, "正在准备安装包…");
                File apk = download(context, armv7, listener);
                status(listener, "正在连接电视…");
                AbsAdbConnectionManager manager = TvAdbManager.get(context);
                if (!TextUtils.isEmpty(code) && pairPort > 0) {
                    status(listener, "正在配对（端口 " + pairPort + "）…");
                    if (!manager.pair(host, pairPort, code)) throw new IOException("配对失败，请确认配对码和端口");
                }
                boolean connected = false;
                try {
                    connected = manager.autoConnect(context, 5000);
                } catch (Throwable ignored) {
                }
                if (!connected) connected = manager.connect(host, 5555);
                if (!connected) throw new IOException("连接失败，请确认电视已开启 ADB 调试并允许本机调试");
                status(listener, "已连接电视，正在安装…");
                String result;
                try {
                    result = streamInstall(manager, apk, listener);
                } catch (Throwable streamError) {
                    status(listener, "流式安装不可用，改用推送方式…");
                    push(manager, apk);
                    result = shell(manager, "pm install -r -t " + REMOTE_PATH);
                    shell(manager, "rm -f " + REMOTE_PATH);
                }
                boolean ok = result != null && result.toLowerCase().contains("success");
                done(listener, ok, ok ? "安装完成" : ("安装返回：" + trim(result)));
            } catch (Throwable e) {
                done(listener, false, "安装失败：" + e.getMessage());
            }
        }, "tv-adb-install").start();
    }

    private static File download(Context context, boolean armv7, Listener listener) throws Exception {
        String suffix = armv7 ? "电视版-armv7.apk" : "电视版-arm64-v8a.apk";
        String oldSuffix = armv7 ? "leanback-armeabi_v7a.apk" : "leanback-arm64_v8a.apk";
        HttpURLConnection api = open("https://api.github.com/repos/luanmuc/beacon/releases/latest");
        String json;
        try (InputStream in = api.getInputStream()) {
            json = readAll(in);
        } finally {
            api.disconnect();
        }
        org.json.JSONObject release = new org.json.JSONObject(json);
        String url = null;
        org.json.JSONArray assets = release.optJSONArray("assets");
        if (assets != null) for (int i = 0; i < assets.length(); i++) {
            org.json.JSONObject asset = assets.optJSONObject(i);
            if (asset != null && (asset.optString("name", "").endsWith(suffix) || asset.optString("name", "").endsWith(oldSuffix))) {
                url = asset.optString("browser_download_url");
                break;
            }
        }
        if (TextUtils.isEmpty(url)) throw new IOException("没有找到 " + suffix);
        File file = new File(context.getCacheDir(), "tv-install.apk");
        status(listener, "正在下载 " + suffix + "…");
        HttpURLConnection conn = open(url);
        long total = conn.getContentLengthLong();
        try (InputStream in = new BufferedInputStream(conn.getInputStream()); OutputStream out = new FileOutputStream(file)) {
            byte[] buf = new byte[8192];
            long read = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                read += n;
                if (total > 0 && read % (1024 * 1024) < 8192) status(listener, "下载中 " + (read * 100 / total) + "%");
            }
        } finally {
            conn.disconnect();
        }
        return file;
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Beacon-TV-Installer");
        return conn;
    }

    /**
     * 与 dadb 的成熟实现一致：把 APK 直接流给包管理器，不在电视上留临时文件。
     * 服务名 exec:cmd package install -S <size>，写完数据后读取 Success/Failure。
     */
    private static String streamInstall(AbsAdbConnectionManager manager, File apk, Listener listener) throws Exception {
        AdbStream stream = manager.openStream("exec:cmd package install -S " + apk.length() + " -r -t");
        try (OutputStream out = stream.openOutputStream(); InputStream in = stream.openInputStream(); FileInputStream file = new FileInputStream(apk)) {
            byte[] buf = new byte[65536];
            long read = 0;
            int n;
            while ((n = file.read(buf)) > 0) {
                out.write(buf, 0, n);
                read += n;
                if (read % (1024 * 1024) < 65536) status(listener, "写入电视 " + (read * 100 / Math.max(1, apk.length())) + "%");
            }
            out.flush();
            status(listener, "写入完成，等待电视安装结果…");
            return readAll(in);
        } finally {
            stream.close();
        }
    }

    private static void push(AbsAdbConnectionManager manager, File apk) throws Exception {
        AdbStream stream = manager.openStream("sync:");
        try (OutputStream out = stream.openOutputStream(); InputStream in = stream.openInputStream(); FileInputStream file = new FileInputStream(apk)) {
            write(out, "SEND");
            writeInt(out, REMOTE_PATH.getBytes(StandardCharsets.UTF_8).length);
            write(out, REMOTE_PATH);
            byte[] buf = new byte[65536];
            int n;
            while ((n = file.read(buf)) > 0) {
                write(out, "DATA");
                writeInt(out, n);
                out.write(buf, 0, n);
            }
            write(out, "DONE");
            writeInt(out, 0);
            out.flush();
            String id = readId(in);
            if (!"OKAY".equals(id)) {
                int len = readInt(in);
                byte[] msg = new byte[Math.max(0, Math.min(len, 4096))];
                if (msg.length > 0) in.read(msg);
                throw new IOException("推送失败：" + new String(msg, StandardCharsets.UTF_8));
            }
        } finally {
            stream.close();
        }
    }

    private static String shell(AbsAdbConnectionManager manager, String command) throws Exception {
        AdbStream stream = manager.openStream("shell:" + command);
        try (InputStream in = stream.openInputStream()) {
            return readAll(in);
        } finally {
            stream.close();
        }
    }

    private static void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeInt(OutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }

    private static int readInt(InputStream in) throws IOException {
        int b1 = in.read(), b2 = in.read(), b3 = in.read(), b4 = in.read();
        if (b1 < 0 || b2 < 0 || b3 < 0 || b4 < 0) throw new IOException("ADB 连接已断开");
        return b1 | (b2 << 8) | (b3 << 16) | (b4 << 24);
    }

    private static String readId(InputStream in) throws IOException {
        byte[] id = new byte[4];
        new DataInputStream(in).readFully(id);
        return new String(id, StandardCharsets.UTF_8);
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    private static String trim(String text) {
        if (text == null) return "无输出";
        String s = text.trim();
        return s.length() > 300 ? s.substring(0, 300) : s;
    }

    private static void status(Listener listener, String text) {
        if (listener != null) App.post(() -> listener.onStatus(text));
    }

    private static void done(Listener listener, boolean ok, String text) {
        if (listener != null) App.post(() -> listener.onDone(ok, text));
    }
}
