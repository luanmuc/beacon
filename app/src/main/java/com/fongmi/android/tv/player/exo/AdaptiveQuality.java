package com.fongmi.android.tv.player.exo;

import androidx.media3.common.MediaItem;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

import com.fongmi.android.tv.setting.PlayerSetting;

/**
 * Conservative runtime downgrade: two 5-second windows with >=60 dropped
 * frames each cap the video height to the next lower common step. It never
 * upgrades during the same media item; a new item restores the original
 * track-selector parameters.
 */
public final class AdaptiveQuality implements AnalyticsListener {

    private static final long WINDOW_MS = 5000;
    private static final int PRESSURE_FRAMES = 60;

    private final ExoPlayer player;
    private final DefaultTrackSelector selector;
    private final DefaultTrackSelector.Parameters original;

    private long windowStartMs;
    private int droppedInWindow;
    private int pressureWindows;
    private boolean firstFrameRendered;
    private boolean downgraded;

    public AdaptiveQuality(ExoPlayer player) {
        this.player = player;
        this.selector = (DefaultTrackSelector) player.getTrackSelector();
        this.original = selector.getParameters();
    }

    public void attach() {
        player.addAnalyticsListener(this);
    }

    public void detach() {
        player.removeAnalyticsListener(this);
    }

    @Override
    public void onRenderedFirstFrame(EventTime eventTime, Object output, long renderTimeMs) {
        firstFrameRendered = true;
    }

    @Override
    public void onMediaItemTransition(EventTime eventTime, MediaItem mediaItem, int reason) {
        reset();
    }

    @Override
    public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
        if (droppedFrames <= 0 || !firstFrameRendered || downgraded) return;
        if (player.isCurrentMediaItemLive() || PlayerSetting.isForce4K()) return;

        if (windowStartMs == 0) windowStartMs = eventTime.realtimeMs;
        droppedInWindow += droppedFrames;
        if (eventTime.realtimeMs - windowStartMs < WINDOW_MS) return;

        boolean pressure = droppedInWindow >= PRESSURE_FRAMES;
        windowStartMs = eventTime.realtimeMs;
        droppedInWindow = 0;
        pressureWindows = pressure ? pressureWindows + 1 : 0;
        if (pressureWindows >= 2) downgrade();
    }

    private void downgrade() {
        VideoSize size = player.getVideoSize();
        int current = size == null ? 0 : size.height;
        if (current <= 0) return;
        int cap = current > 1080 ? 1080 : current > 720 ? 720 : 480;
        if (current <= cap) return;
        selector.setParameters(selector.buildUponParameters().setMaxVideoSize(4096, cap));
        downgraded = true;
        pressureWindows = 0;
    }

    private void reset() {
        windowStartMs = 0;
        droppedInWindow = 0;
        pressureWindows = 0;
        firstFrameRendered = false;
        downgraded = false;
        selector.setParameters(original);
    }
}
