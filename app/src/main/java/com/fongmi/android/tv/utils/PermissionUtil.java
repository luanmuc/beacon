package com.fongmi.android.tv.utils;

import android.Manifest;
import android.os.Build;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.impl.PermissionCallback;
import com.permissionx.guolindev.PermissionX;

import java.util.function.Consumer;

public class PermissionUtil {

    public static void requestAudio(FragmentActivity activity, Consumer<Boolean> callback) {
        PermissionX.init(activity).permissions(Manifest.permission.RECORD_AUDIO).request(new PermissionCallback(callback));
    }

    public static void requestFile(FragmentActivity activity, Consumer<Boolean> callback) {
        PermissionX.init(activity).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request(new PermissionCallback(callback));
    }

    public static void requestFile(Fragment fragment, Consumer<Boolean> callback) {
        PermissionX.init(fragment).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request(new PermissionCallback(callback));
    }

    public static void requestNotification(FragmentActivity activity, Consumer<Boolean> callback) {
        if (Build.VERSION.SDK_INT >= 33) {
            PermissionX.init(activity).permissions(Manifest.permission.POST_NOTIFICATIONS).request(new PermissionCallback(callback));
        }
    }
}
