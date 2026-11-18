package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.databinding.DialogPlaybackPanelBinding;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Map;

/**
 * 播放内容面板：标题、来源、集数、地址与请求头，并带上一集 / 选集 / 下一集 / 复制地址。
 */
public class PlaybackPanel {

    private final DialogPlaybackPanelBinding binding;
    private AlertDialog dialog;
    private CharSequence title;
    private String meta;
    private String url;
    private String headers;
    private Runnable prevAction;
    private Runnable nextAction;
    private Runnable episodeAction;
    private Runnable retryAction;
    private Runnable shareAction;

    public static PlaybackPanel create(Activity activity) {
        return new PlaybackPanel(activity);
    }

    private PlaybackPanel(Activity activity) {
        this.binding = DialogPlaybackPanelBinding.inflate(LayoutInflater.from(activity));
    }

    public PlaybackPanel title(CharSequence title) {
        this.title = title;
        return this;
    }

    public PlaybackPanel meta(String meta) {
        this.meta = meta;
        return this;
    }

    public PlaybackPanel url(String url) {
        this.url = url;
        return this;
    }

    public PlaybackPanel headers(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        if (map != null) for (String key : map.keySet()) sb.append(key).append(": ").append(map.get(key)).append('\n');
        this.headers = sb.toString();
        return this;
    }

    public PlaybackPanel onPrev(Runnable action) {
        this.prevAction = action;
        return this;
    }

    public PlaybackPanel onNext(Runnable action) {
        this.nextAction = action;
        return this;
    }

    public PlaybackPanel onShare(Runnable action) {
        this.shareAction = action;
        return this;
    }

    public PlaybackPanel onRetry(Runnable action) {
        this.retryAction = action;
        return this;
    }

    public PlaybackPanel onEpisodes(Runnable action) {
        this.episodeAction = action;
        return this;
    }

    public void show() {
        binding.title.setText(TextUtils.isEmpty(title) ? "" : title);
        binding.meta.setText(TextUtils.isEmpty(meta) ? "" : meta);
        binding.url.setText(TextUtils.isEmpty(url) ? "" : url);
        binding.headers.setText(TextUtils.isEmpty(headers) ? "" : headers.trim());
        binding.headers.setVisibility(TextUtils.isEmpty(headers) ? View.GONE : View.VISIBLE);
        binding.url.setVisibility(TextUtils.isEmpty(url) ? View.GONE : View.VISIBLE);
        binding.copy.setOnClickListener(v -> {
            if (TextUtils.isEmpty(url)) return;
            Util.copy(url);
            Notify.show("已复制播放地址");
        });
        binding.title.setOnLongClickListener(v -> {
            if (!TextUtils.isEmpty(binding.title.getText())) {
                Util.copy(binding.title.getText().toString());
                Notify.show("已复制标题");
            }
            return true;
        });
        binding.share.setOnClickListener(v -> {
            if (shareAction != null) shareAction.run();
        });
        binding.retry.setOnClickListener(v -> {
            dismiss();
            if (retryAction != null) retryAction.run();
        });
        binding.prev.setEnabled(prevAction != null);
        binding.next.setEnabled(nextAction != null);
        binding.episodes.setEnabled(episodeAction != null);
        binding.prev.setOnClickListener(v -> {
            dismiss();
            if (prevAction != null) prevAction.run();
        });
        binding.next.setOnClickListener(v -> {
            dismiss();
            if (nextAction != null) nextAction.run();
        });
        binding.episodes.setOnClickListener(v -> {
            dismiss();
            if (episodeAction != null) episodeAction.run();
        });
        dialog = new MaterialAlertDialogBuilder(binding.getRoot().getContext()).setView(binding.getRoot()).create();
        dialog.show();
    }

    private void dismiss() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }
}
