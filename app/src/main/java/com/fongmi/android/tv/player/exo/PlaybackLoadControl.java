package com.fongmi.android.tv.player.exo;

import androidx.media3.common.Timeline;
import androidx.media3.common.util.NullableType;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;

import com.fongmi.android.tv.setting.PlayerSetting;

/**
 * Wraps the normal LoadControl and lets the player stop preloading an old source
 * while a new source is being prepared. Cached/network errors are not involved.
 */
public final class PlaybackLoadControl implements LoadControl {

    private static volatile boolean preloadingEnabled = true;

    private final DefaultLoadControl delegate;

    public PlaybackLoadControl(DefaultLoadControl delegate) {
        this.delegate = delegate;
    }

    public static void setPreloadingEnabled(boolean enabled) {
        preloadingEnabled = enabled;
    }

    @Override
    public void onPrepared(PlayerId playerId) {
        delegate.onPrepared(playerId);
    }

    @Override
    public void onTracksSelected(Parameters parameters, TrackGroupArray trackGroups, @NullableType ExoTrackSelection[] trackSelections) {
        delegate.onTracksSelected(parameters, trackGroups, trackSelections);
    }

    @Override
    public void onStopped(PlayerId playerId) {
        delegate.onStopped(playerId);
    }

    @Override
    public void onReleased(PlayerId playerId) {
        delegate.onReleased(playerId);
    }

    @Override
    public Allocator getAllocator(PlayerId playerId) {
        return delegate.getAllocator(playerId);
    }

    @Override
    public long getBackBufferDurationUs(PlayerId playerId) {
        return delegate.getBackBufferDurationUs(playerId);
    }

    @Override
    public boolean retainBackBufferFromKeyframe(PlayerId playerId) {
        return delegate.retainBackBufferFromKeyframe(playerId);
    }

    @Override
    public boolean shouldContinueLoading(Parameters parameters) {
        // Never starve the initial buffer; only stop growing an old source once
        // it already has enough data to survive a source switch.
        boolean preload = preloadingEnabled && PlayerSetting.isPreload();
        if (!preload && parameters.bufferedDurationUs >= 10_000_000L) return false;
        return delegate.shouldContinueLoading(parameters);
    }

    @Override
    public boolean shouldContinuePreloading(PlayerId playerId, Timeline timeline, MediaSource.MediaPeriodId mediaPeriodId, long bufferedDurationUs) {
        return preloadingEnabled && PlayerSetting.isPreload() && delegate.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs);
    }

    @Override
    public boolean shouldStartPlayback(Parameters parameters) {
        return delegate.shouldStartPlayback(parameters);
    }
}
