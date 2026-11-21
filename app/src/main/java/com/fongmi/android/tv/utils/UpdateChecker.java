package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.github.catvod.net.OkHttp;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于本仓库 GitHub Release 的更新检查。
 * 只做版本比对与跳转，不自动下载安装，避免拉取到非本仓库的安装包。
 */
public class UpdateChecker {

    private static final String API = "https://api.github.com/repos/luanmuc/beacon/releases/latest";
    private static final String RELEASE_PAGE = "https://github.com/luanmuc/beacon/releases/latest";
    private static final Pattern VERSION = Pattern.compile("v(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final long CHECK_COOLDOWN = 60 * 1000L;
    private static volatile long lastCheck;
    private static volatile boolean checking;

    public static void check(Activity activity) {
        long now = System.currentTimeMillis();
        if (checking) {
            Notify.show("正在检查更新…");
            return;
        }
        if (now - lastCheck < CHECK_COOLDOWN) {
            Notify.show("刚刚已检查过，请稍后再试");
            return;
        }
        checking = true;
        lastCheck = now;
        Task.execute(() -> {
            try {
                JSONObject object = new JSONObject(OkHttp.string(API));
                String tag = object.optString("tag_name", "");
                String url = object.optString("html_url", RELEASE_PAGE);
                int remote = parseCode(tag);
                int local = parseCode("v" + BuildConfig.VERSION_NAME);
                if (remote > local) {
                    App.post(() -> Notify.show("发现新版本 " + tag + "，正在打开发布页"));
                    App.post(() -> open(activity, url));
                } else {
                    App.post(() -> Notify.show("已是最新版本 " + BuildConfig.VERSION_NAME));
                }
            } catch (Exception e) {
                App.post(() -> Notify.show("检查更新失败：" + e.getMessage()));
            } finally {
                checking = false;
            }
        });
    }

    private static int parseCode(String tag) {
        Matcher matcher = VERSION.matcher(tag);
        if (!matcher.find()) return -1;
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));
        return major * 100000 + minor * 1000 + patch;
    }

    private static void open(Activity activity, String url) {
        try {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Notify.show("打开失败");
        }
    }
}
