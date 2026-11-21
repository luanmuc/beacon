package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.provider.Settings;
import android.text.Html;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.github.catvod.utils.Shell;

import java.net.NetworkInterface;
import java.util.Formatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Util {

    private static final Pattern EPISODE = Pattern.compile("(?i)(?:ep|第|e|[\\-\\.\\s])\\s?(\\d{1,4})");

    public static void toggleFullscreen(Activity activity, boolean fullscreen) {
        if (fullscreen) hideSystemUI(activity);
        else showSystemUI(activity);
    }

    public static void hideSystemUI(Activity activity) {
        hideSystemUI(activity.getWindow());
    }

    public static void hideSystemUI(Window window) {
        WindowInsetsControllerCompat insets = WindowCompat.getInsetsController(window, window.getDecorView());
        insets.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        insets.hide(WindowInsetsCompat.Type.systemBars());
    }

    public static void showSystemUI(Activity activity) {
        showSystemUI(activity.getWindow());
    }

    public static void showSystemUI(Window window) {
        WindowCompat.getInsetsController(window, window.getDecorView()).show(WindowInsetsCompat.Type.systemBars());
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    public static void showKeyboard(View view) {
        if (!view.requestFocus()) return;
        InputMethodManager imm = (InputMethodManager) App.get().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) view.postDelayed(() -> imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT), 250);
    }

    public static void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) App.get().getSystemService(Context.INPUT_METHOD_SERVICE);
        IBinder windowToken = view.getWindowToken();
        if (imm == null || windowToken == null) return;
        imm.hideSoftInputFromWindow(windowToken, 0);
    }

    public static float getBrightness(Activity activity) {
        try {
            float value = activity.getWindow().getAttributes().screenBrightness;
            if (WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL >= value && value >= WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF) return value;
            return Settings.System.getFloat(activity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255;
        } catch (Exception e) {
            return 0.5f;
        }
    }

    public static CharSequence getClipText() {
        ClipboardManager manager = (ClipboardManager) App.get().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = manager == null ? null : manager.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() == 0) return "";
        return clipData.getItemAt(0).getText();
    }

    public static void copy(String text) {
        try {
            ClipboardManager manager = (ClipboardManager) App.get().getSystemService(Context.CLIPBOARD_SERVICE);
            manager.setPrimaryClip(ClipData.newPlainText("", text));
            Notify.show(R.string.copied);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int getNumber(String text) {
        try {
            text = text.replaceAll("\\[.*?\\]|\\(.*?\\)", "");
            text = text.replaceAll("\\b(19|20)\\d{2}\\b", "");
            text = text.toLowerCase().replaceAll("2160p|1080p|720p|480p|4k|h26[45]|x26[45]|mp4", "");
            Matcher matcher = EPISODE.matcher(text);
            if (matcher.find()) return Integer.parseInt(matcher.group(1));
            String number = text.replaceAll("\\D+", "");
            return number.isEmpty() ? -1 : Integer.parseInt(number);
        } catch (Exception e) {
            return -1;
        }
    }

    public static String clean(String text) {
        if (!text.contains("<")) return text;
        StringBuilder sb = new StringBuilder();
        text = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().replace("\u00A0", " ").replace("\u3000", " ");
        for (String line : text.split("\\r?\\n")) sb.append(line.trim()).append("\n");
        return substring(sb.toString()).trim();
    }

    public static String getAndroidId() {
        try {
            String id = Settings.Secure.getString(App.get().getContentResolver(), Settings.Secure.ANDROID_ID);
            if (TextUtils.isEmpty(id)) throw new NullPointerException();
            return id;
        } catch (Exception e) {
            return "0000000000000000";
        }
    }

    public static String getSerial() {
        return Shell.exec("getprop ro.serialno").replace("\n", "");
    }

    public static String getMac(String name) {
        try {
            StringBuilder sb = new StringBuilder();
            NetworkInterface nif = NetworkInterface.getByName(name);
            if (nif.getHardwareAddress() == null) return "";
            for (byte b : nif.getHardwareAddress()) sb.append(String.format("%02X:", b));
            return substring(sb.toString());
        } catch (Exception e) {
            return "";
        }
    }

    public static String getDeviceName() {
        String model = Build.MODEL;
        String manufacturer = Build.MANUFACTURER;
        return model.startsWith(manufacturer) ? model : manufacturer + " " + model;
    }

    public static String substring(String text) {
        return substring(text, 1);
    }

    public static String substring(String text, int num) {
        if (text != null && text.length() > num) return text.substring(0, text.length() - num);
        return text;
    }


    public static boolean isLowRamDevice() {
        ActivityManager am = (ActivityManager) App.get().getSystemService(Context.ACTIVITY_SERVICE);
        return am != null && am.isLowRamDevice();
    }

    public static String getDeviceReport() {
        StringBuilder sb = new StringBuilder();
        ActivityManager am = (ActivityManager) App.get().getSystemService(Context.ACTIVITY_SERVICE);
        DisplayMetrics dm = App.get().getResources().getDisplayMetrics();
        sb.append("厂商：").append(Build.MANUFACTURER).append('\n');
        sb.append("型号：").append(Build.MODEL).append('\n');
        sb.append("设备/主板：").append(Build.DEVICE).append(" / ").append(Build.BOARD).append('\n');
        sb.append("系统：Android ").append(Build.VERSION.RELEASE).append("（SDK ").append(Build.VERSION.SDK_INT).append("）\n");
        sb.append("ABI：").append(TextUtils.join(", ", Build.SUPPORTED_ABIS)).append('\n');
        if (Build.VERSION.SDK_INT >= 31) sb.append("SoC：").append(Build.SOC_MANUFACTURER).append(' ').append(Build.SOC_MODEL).append(" / ").append(Build.HARDWARE).append('\n');
        else sb.append("SoC/硬件：").append(Build.HARDWARE).append('\n');
        if (am != null) sb.append("内存档：memoryClass=").append(am.getMemoryClass()).append("MB，largeMemoryClass=").append(am.getLargeMemoryClass()).append("MB，lowRam=").append(am.isLowRamDevice()).append('\n');
        sb.append("屏幕：").append(dm.widthPixels).append('x').append(dm.heightPixels).append("，density=").append(dm.densityDpi).append('\n');
        sb.append("解码器：\n").append(getVideoCodecReport());
        return sb.toString();
    }

    public static String getVideoCodecReport() {
        StringBuilder sb = new StringBuilder();
        String[] mimes = {"video/avc", "video/hevc", "video/av01", "video/x-vnd.on2.vp9"};
        String[] names = {"H.264", "H.265", "AV1", "VP9"};
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            for (int i = 0; i < mimes.length; i++) {
                String codec = "";
                boolean hardware = false;
                for (MediaCodecInfo info : list.getCodecInfos()) {
                    if (info.isEncoder()) continue;
                    for (String type : info.getSupportedTypes()) {
                        if (!type.equalsIgnoreCase(mimes[i])) continue;
                        codec = info.getName();
                        if (Build.VERSION.SDK_INT >= 29) hardware = info.isHardwareAccelerated();
                        break;
                    }
                    if (!codec.isEmpty()) break;
                }
                sb.append(names[i]).append("：").append(codec.isEmpty() ? "无解码器" : codec + (hardware ? "（硬解）" : "（软解/未知）")).append('\n');
            }
        } catch (Throwable e) {
            sb.append("读取失败：").append(e.getMessage()).append('\n');
        }
        return sb.toString();
    }

    public static boolean isLeanback() {
        return "leanback".equals(BuildConfig.FLAVOR_mode);
    }

    public static boolean isMobile() {
        return "mobile".equals(BuildConfig.FLAVOR_mode);
    }

    public static boolean isFullscreen(Activity activity) {
        if (activity == null || activity.getWindow() == null) return false;
        return isLeanback() || (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
    }

    public static boolean isFullscreenLand(Activity activity) {
        return isFullscreen(activity) && !isLeanback() && ResUtil.isLand(activity);
    }

    public static String format(StringBuilder builder, Formatter formatter, long timeMs) {
        try {
            return androidx.media3.common.util.Util.getStringForTime(builder, formatter, timeMs);
        } catch (Exception e) {
            return "";
        }
    }

    public static String timeMs(long timeMs) {
        StringBuilder sb = new StringBuilder();
        return format(sb, new Formatter(sb, Locale.getDefault()), timeMs);
    }
}
