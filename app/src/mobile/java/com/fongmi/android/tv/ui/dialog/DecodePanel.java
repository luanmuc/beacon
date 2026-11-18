package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.databinding.DialogDecodePanelBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 解码设置面板：当前方式展示 + 硬解 / 软解切换。
 */
public class DecodePanel {

    private final DialogDecodePanelBinding binding;
    private AlertDialog dialog;
    private boolean hard;
    private String current;
    private Runnable onHard;
    private Runnable onSoft;

    public static DecodePanel create(Activity activity) {
        return new DecodePanel(activity);
    }

    private DecodePanel(Activity activity) {
        this.binding = DialogDecodePanelBinding.inflate(LayoutInflater.from(activity));
    }

    public DecodePanel current(String text, boolean hard) {
        this.current = text;
        this.hard = hard;
        return this;
    }

    public DecodePanel onHard(Runnable action) {
        this.onHard = action;
        return this;
    }

    public DecodePanel onSoft(Runnable action) {
        this.onSoft = action;
        return this;
    }

    public void show() {
        binding.current.setText("当前：" + (current == null ? "未知" : current));
        binding.hard.setEnabled(!hard);
        binding.soft.setEnabled(hard);
        binding.hard.setOnClickListener(v -> {
            dismiss();
            if (onHard != null) onHard.run();
        });
        binding.soft.setOnClickListener(v -> {
            dismiss();
            if (onSoft != null) onSoft.run();
        });
        dialog = new MaterialAlertDialogBuilder(binding.getRoot().getContext()).setView(binding.getRoot()).create();
        dialog.show();
    }

    private void dismiss() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }
}
