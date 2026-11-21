package com.fongmi.android.tv;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.hook.Hook;
import com.github.catvod.Init;
import com.google.android.material.color.DynamicColors;
import com.google.gson.Gson;

public class App extends Application implements Application.ActivityLifecycleCallbacks {

    private static volatile App instance;

    private final Handler handler;
    private final Gson gson;
    private final long time;

    private Activity activity;
    private Hook hook;

    public App() {
        instance = this;
        gson = new Gson();
        time = System.currentTimeMillis();
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
    }

    public static App get() {
        return instance;
    }

    public static Gson gson() {
        return get().gson;
    }

    public static long time() {
        return get().time;
    }

    public static Activity activity() {
        return get().activity;
    }

    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void post(Runnable runnable, long delayMillis) {
        get().handler.removeCallbacks(runnable);
        if (delayMillis >= 0) get().handler.postDelayed(runnable, delayMillis);
    }

    public static void removeCallbacks(Runnable runnable) {
        get().handler.removeCallbacks(runnable);
    }

    public static void removeCallbacks(Runnable... runnable) {
        for (Runnable r : runnable) get().handler.removeCallbacks(r);
    }

    public void setHook(Hook hook) {
        this.hook = hook;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Init.set(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notify.createChannel();
        initGlide();
        registerActivityLifecycleCallbacks(this);
        DynamicColors.applyToActivitiesIfAvailable(this);
        installCrashHandler();
    }

    private void initGlide() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        boolean lowRam = manager != null && manager.isLowRamDevice();
        int memoryClass = manager == null ? 128 : manager.getMemoryClass();
        int memoryCache = Math.max(8, memoryClass / 8) * 1024 * 1024;
        int bitmapPool = Math.max(4, memoryClass / 16) * 1024 * 1024;
        int diskCache = (lowRam ? 128 : 256) * 1024 * 1024;
        GlideBuilder builder = new GlideBuilder()
                .setMemoryCache(new LruResourceCache(memoryCache))
                .setBitmapPool(new LruBitmapPool(bitmapPool))
                .setDiskCache(new InternalCacheDiskCacheFactory(this, diskCache));
        Glide.init(this, builder);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        try {
            Glide.get(this).trimMemory(level);
        } catch (Throwable ignored) {
        }
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) com.fongmi.android.tv.browse.VodBrowse.clearMemory();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        try {
            Glide.get(this).clearMemory();
        } catch (Throwable ignored) {
        }
        com.fongmi.android.tv.browse.VodBrowse.clearMemory();
    }

    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler origin = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                java.io.File file = new java.io.File(getFilesDir(), "last-crash.log");
                java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.StringWriter());
                throwable.printStackTrace(writer);
                String text = "time=" + new java.util.Date() + "\n" + writer.toString();
                java.io.FileWriter fw = new java.io.FileWriter(file, false);
                fw.write(text.length() > 4000 ? text.substring(0, 4000) : text);
                fw.close();
            } catch (Exception ignored) {
            }
            if (origin != null) origin.uncaughtException(thread, throwable);
        });
    }

    public static String crashLog() {
        try {
            java.io.File file = new java.io.File(get().getFilesDir(), "last-crash.log");
            if (!file.exists()) return "";
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static void clearCrashLog() {
        try {
            java.io.File file = new java.io.File(get().getFilesDir(), "last-crash.log");
            if (file.exists()) file.delete();
        } catch (Exception ignored) {
        }
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity != activity()) this.activity = activity;
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (activity == activity()) this.activity = null;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }
}