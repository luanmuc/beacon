package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.databinding.ActivityLiveBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigCallback;
import com.fongmi.android.tv.impl.CustomTarget;
import com.fongmi.android.tv.impl.LiveCallback;
import com.fongmi.android.tv.impl.PassCallback;
import com.fongmi.android.tv.model.LiveViewModel;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.LiveSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.adapter.ChannelAdapter;
import com.fongmi.android.tv.ui.adapter.EpgDataAdapter;
import com.fongmi.android.tv.ui.adapter.GroupAdapter;
import com.fongmi.android.tv.ui.custom.CustomKeyDownLive;
import com.fongmi.android.tv.ui.custom.CustomLiveListView;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.PassDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Traffic;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LiveActivity extends PlaybackActivity implements GroupAdapter.OnClickListener, ChannelAdapter.OnClickListener, EpgDataAdapter.OnClickListener, CustomKeyDownLive.Listener, CustomLiveListView.Callback, TrackDialog.Listener, PassCallback, ConfigCallback, LiveCallback {

    private ActivityLiveBinding mBinding;
    private ChannelAdapter mChannelAdapter;
    private EpgDataAdapter mEpgDataAdapter;
    private GroupAdapter mGroupAdapter;
    private Observer<Result> mObserveUrl;
    private CustomKeyDownLive mKeyDown;
    private Observer<Epg> mObserveEpg;
    private LiveViewModel mViewModel;
    private List<Group> mHides;
    private String mPlaybackKey;
    private Channel mChannel;
    private int lineRetry;
    private boolean sameLineRetry;
    private View mOldView;
    private Group mGroup;
    private Runnable mR0;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Clock mClock;
    private View mFocus2;
    private int count;

    public static void start(Context context) {
        context.startActivity(new Intent(context, LiveActivity.class).putExtra("empty", LiveConfig.isEmpty()));
    }

    private boolean isEmpty() {
        return getIntent().getBooleanExtra("empty", true);
    }

    private Group getKeep() {
        return mGroupAdapter.get(0);
    }

    private Live getHome() {
        return LiveConfig.get().getHome();
    }

    @Override
    protected boolean customWall() {
        return false;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityLiveBinding.inflate(getLayoutInflater());
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
    }

    @Override
    protected String getPlaybackKey() {
        return mPlaybackKey;
    }

    @Override
    protected PlayerView getExoView() {
        return mBinding.exo;
    }

    @Override
    protected CustomSeekView getSeekView() {
        return mBinding.control.seek;
    }

    @Override
    protected void onServiceConnected() {
        player().setDanmakuController(mBinding.exo.getDanmakuController());
        mBinding.control.action.decode.setText(player().getDecodeText());
        mBinding.control.action.speed.setText(player().getSpeedText());
        checkLive();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        mClock = Clock.create(mBinding.widget.clock);
        mKeyDown = CustomKeyDownLive.create(this);
        mObserveEpg = this::setEpg;
        mObserveUrl = this::start;
        mHides = new ArrayList<>();
        mR0 = this::setActivated;
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::hideInfo;
        mR4 = this::hideUI;
        setRecyclerView();
        setVideoView();
        setViewModel();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.group.setListener(this);
        mBinding.channel.setListener(this);
        mBinding.epgData.setListener(this);
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.speed.setUpListener(this::onSpeedAdd);
        mBinding.control.action.speed.setDownListener(this::onSpeedSub);
        mBinding.control.action.text.setUpListener(this::onSubtitleClick);
        mBinding.control.action.text.setDownListener(this::onSubtitleClick);
        mBinding.control.action.home.setOnClickListener(view -> onHome());
        mBinding.group.setOnLongClickListener(view -> { showGroupMenu(); return true; });
        mBinding.control.action.home.setOnLongClickListener(view -> { switchChannelLine(); return true; });
        mBinding.control.action.line.setOnClickListener(view -> onLine());
        mBinding.control.action.line.setOnLongClickListener(view -> { showLineList(); return true; });
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.config.setOnClickListener(view -> onConfig());
        mBinding.control.action.action.setOnClickListener(view -> onAction());
        mBinding.control.action.invert.setOnClickListener(view -> onInvert());
        mBinding.control.action.across.setOnClickListener(view -> onAcross());
        mBinding.control.action.change.setOnClickListener(view -> onChange());
        mBinding.control.action.player.setOnClickListener(view -> onChoose());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.group.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mGroupAdapter.getItemCount() > 0) onChildSelected(child, mGroup = mGroupAdapter.get(position));
            }
        });
    }

    private void setRecyclerView() {
        mBinding.group.setItemAnimator(null);
        mBinding.channel.setItemAnimator(null);
        mBinding.epgData.setItemAnimator(null);
        mBinding.group.setAdapter(mGroupAdapter = new GroupAdapter(this));
        mBinding.channel.setAdapter(mChannelAdapter = new ChannelAdapter(this));
        mBinding.epgData.setAdapter(mEpgDataAdapter = new EpgDataAdapter(this));
    }

    private void setVideoView() {
        setScale(LiveSetting.getScale());
        findViewById(R.id.timeBar).setNextFocusUpId(R.id.config);
        mBinding.control.action.invert.setActivated(LiveSetting.isInvert());
        mBinding.control.action.across.setActivated(LiveSetting.isAcross());
        mBinding.control.action.change.setActivated(LiveSetting.isChange());
    }

    @Override
    protected void onPlayerRebuilt() {
        setDecode();
    }

    private void setDecode() {
        mBinding.control.action.decode.setText(player().getDecodeText());
    }

    private void setScale(int scale) {
        LiveSetting.putScale(scale);
        mBinding.exo.setResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(LiveViewModel.class);
        mViewModel.url().observeForever(mObserveUrl);
        mViewModel.xml().observe(this, this::setEpg);
        mViewModel.epg().observeForever(mObserveEpg);
        mViewModel.live().observe(this, live -> {
            mViewModel.parseXml(live);
            setGroup(live);
            setWidth(live);
        });
    }

    private void checkLive() {
        if (isEmpty()) {
            LiveConfig.get().init().load(getCallback());
        } else {
            getLive();
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                if (com.fongmi.android.tv.api.config.LiveConfig.get().getLives() == null || com.fongmi.android.tv.api.config.LiveConfig.get().getLives().isEmpty()) {
                    com.fongmi.android.tv.utils.Notify.show("直播订阅为空，请在设置中填写直播源");
                }
                getLive();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    private void getLive() {
        mBinding.control.action.home.setText(LiveConfig.isOnly() ? getString(R.string.live_refresh) : getHome().getName());
        mViewModel.parse(getHome());
        showProgress();
    }

    private void setGroup(Live live) {
        List<Group> items = new ArrayList<>();
        for (Group group : live.getGroups()) (group.isHidden() ? mHides : items).add(group);
        mGroupAdapter.addAll(items);
        setPosition(LiveConfig.get().findKeepPosition(items));
    }

    private void setWidth(Live live) {
        int padding = ResUtil.dp2px(52);
        if (live.getWidth() == 0) for (Group item : live.getGroups()) live.setWidth(Math.max(live.getWidth(), ResUtil.getTextWidth(item.getName(), 16)));
        int width = live.getWidth() == 0 ? 0 : Math.min(live.getWidth() + padding, ResUtil.getScreenWidth() / 4);
        setWidth(mBinding.group, width);
    }

    private Group setWidth(Group group) {
        int logo = ResUtil.dp2px(60);
        int padding = ResUtil.dp2px(64);
        if (group.isKeep()) group.setWidth(0);
        if (group.getWidth() == 0) for (Channel item : group.getChannel()) group.setWidth(Math.max(group.getWidth(), (item.getLogo().isEmpty() ? 0 : logo) + ResUtil.getTextWidth(item.getNumber() + item.getName(), 16)));
        int width = group.getWidth() == 0 ? 0 : Math.min(group.getWidth() + padding, ResUtil.getScreenWidth() / 2);
        setWidth(mBinding.channel, width);
        return group;
    }

    private void setWidth(Epg epg) {
        int padding = ResUtil.dp2px(52);
        if (epg.getList().isEmpty()) return;
        int minWidth = ResUtil.getTextWidth(epg.getList().get(0).getTime(), 14);
        if (epg.getWidth() == 0) for (EpgData item : epg.getList()) epg.setWidth(Math.max(epg.getWidth(), ResUtil.getTextWidth(item.getTitle(), 16)));
        int width = epg.getWidth() == 0 ? 0 : Math.min(Math.max(epg.getWidth(), minWidth) + padding, ResUtil.getScreenWidth() / 2);
        setWidth(mBinding.epgData, width);
    }

    private void setWidth(View view, int width) {
        view.post(() -> {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params.width == width) return;
            params.width = width;
            view.setLayoutParams(params);
        });
    }

    private void setPosition(int[] position) {
        if (position[0] == -1) return;
        int size = mGroupAdapter.getItemCount();
        if (size == 1 || position[0] >= size) return;
        mGroup = mGroupAdapter.get(position[0]);
        mBinding.group.setSelectedPosition(position[0]);
        mGroup.setPosition(position[1]);
        onItemClick(mGroup);
        onItemClick(mGroup.current());
    }

    private void setPosition() {
        if (mChannel == null) return;
        mGroup = mChannel.getGroup();
        int position = mGroupAdapter.indexOf(mGroup);
        boolean change = mBinding.group.getSelectedPosition() != position;
        if (change) mBinding.group.setSelectedPosition(position);
        if (change) mChannelAdapter.addAll(mGroup.getChannel());
        mBinding.channel.setSelectedPosition(mGroup.getPosition());
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child, Group group) {
        if (mOldView != null) mOldView.setSelected(false);
        if ((mOldView = child != null ? child.itemView : null) == null) return;
        mOldView.setSelected(true);
        onItemClick(group);
        resetPass();
    }

    private void setActivated() {
        mChannelAdapter.setSelected(mChannel);
        notifyItemChanged(mBinding.channel, mChannelAdapter);
        fetch();
    }

    private void setActivated(EpgData item) {
        mEpgDataAdapter.setSelected(item);
        notifyItemChanged(mBinding.epgData, mEpgDataAdapter);
    }

    private void checkPlay() {
        if (player().isPlaying()) onPaused();
        else onPlay();
    }

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).show(this);
        hideControl();
    }

    private void onHome() {
        if (LiveConfig.isOnly()) setLive(getHome());
        else LiveDialog.create(this).show();
        hideControl();
    }

    private void onLine() {
        nextLine(false);
    }

    private void onScale() {
        int index = LiveSetting.getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        setScale(index == array.length - 1 ? 0 : ++index);
    }

    private void onSpeed() {
        mBinding.control.action.speed.setText(player().addSpeed());
    }

    private void onSpeedAdd() {
        mBinding.control.action.speed.setText(player().addSpeed(0.25f));
    }

    private void onSpeedSub() {
        mBinding.control.action.speed.setText(player().subSpeed(0.25f));
    }

    private boolean onSpeedLong() {
        mBinding.control.action.speed.setText(player().toggleSpeed());
        return true;
    }

    private void onConfig() {
        HistoryDialog.create(this).readOnly().type(1).show();
        hideControl();
    }

    private void onAction() {
        checkPlay();
    }

    private void onInvert() {
        LiveSetting.putInvert(!LiveSetting.isInvert());
        mBinding.control.action.invert.setActivated(LiveSetting.isInvert());
    }

    private void onAcross() {
        LiveSetting.putAcross(!LiveSetting.isAcross());
        mBinding.control.action.across.setActivated(LiveSetting.isAcross());
    }

    private void onChange() {
        LiveSetting.putChange(!LiveSetting.isChange());
        mBinding.control.action.change.setActivated(LiveSetting.isChange());
    }

    private void onChoose() {
        PlayerHelper.choose(this, player().getUrl(), player().getHeaders(), player().isVod(), player().getPosition(), mBinding.widget.title.getText());
        setRedirect(true);
    }

    private void onDecode() {
        player().toggleDecode();
        setDecode();
    }

    private void hideUI() {
        App.removeCallbacks(mR4);
        if (isGone(mBinding.recycler)) return;
        mBinding.recycler.setVisibility(View.GONE);
        setPosition();
    }

    private void showUI() {
        if (isVisible(mBinding.recycler) || mGroupAdapter.getItemCount() == 0) return;
        mBinding.recycler.setVisibility(View.VISIBLE);
        setPosition();
        setUITimer();
        hideEpg();
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {
        @Override
        public void onNext() {
            nextChannel();
        }

        @Override
        public void onPrev() {
            prevChannel();
        }

        @Override
        public void onStop() {
            finish();
        }
    };

    @Override
    protected void onPrepare() {
        setDecode();
    }

    @Override
    protected void onTracksChanged() {
        setTrackVisible();
    }

    @Override
    protected void onError(String msg) {
        if (mChannel != null && mChannel.getUrls() != null && !sameLineRetry) {
            sameLineRetry = true;
            Notify.show("播放失败，正在重试当前线路…");
            mViewModel.getUrl(mChannel);
            return;
        }
        sameLineRetry = false;
        if (mChannel != null && mChannel.getUrls() != null && mChannel.getUrls().size() > 1 && lineRetry < mChannel.getUrls().size() - 1) {
            lineRetry++;
            mChannel.switchLine(true);
            Notify.show("当前线路失败，已切换到第 " + (mChannel.getIndex() + 1) + " 条");
            mViewModel.getUrl(mChannel);
            return;
        }
        Track.delete(player().getKey());
        player().resetTrack();
        player().reset();
        player().stop();
        showError(msg + "（可长按刷新键换线）");
        startFlow();
    }

    @Override
    protected void onReclaim() {
        Result result = mViewModel.url().getValue();
        if (result != null) start(result);
    }

    @Override
    protected void onStateChanged(int state) {
        switch (state) {
            case Player.STATE_BUFFERING:
                showProgress();
                break;
            case Player.STATE_READY:
                hideProgress();
                player().reset();
                break;
            case Player.STATE_ENDED:
                checkEnded();
                break;
        }
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        if (isPlaying) {
            mBinding.control.action.action.setText(R.string.pause);
        } else if (isPaused()) {
            mBinding.control.action.action.setText(R.string.play);
        }
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        mBinding.widget.size.setText(player().getSizeText());
    }

    @Override
    public void showEpg(Channel item) {
        if (mChannel == null || mChannel.getData(mViewModel.getZoneId()).getList().isEmpty() || mEpgDataAdapter.getItemCount() == 0 || !mChannel.equals(item) || !mChannel.getGroup().equals(mGroup)) return;
        mBinding.epgData.setSelectedPosition(mChannel.getData(mViewModel.getZoneId()).getSelected());
        mBinding.epgData.setVisibility(View.VISIBLE);
        mBinding.channel.setVisibility(View.GONE);
        mBinding.group.setVisibility(View.GONE);
        mBinding.epgData.requestFocus();
    }

    @Override
    public void hideEpg() {
        mBinding.channel.setVisibility(View.VISIBLE);
        mBinding.group.setVisibility(View.VISIBLE);
        mBinding.epgData.setVisibility(View.GONE);
        mBinding.channel.requestFocus();
    }

    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        App.post(mR2, 0);
        hideCenter();
        hideError();
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR2);
        Traffic.reset();
    }

    private void showError(String text) {
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void showControl(View view) {
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        mBinding.widget.top.setVisibility(View.VISIBLE);
        App.post(view::requestFocus, 25);
        setR1Callback();
        hideInfo();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        mBinding.widget.top.setVisibility(View.GONE);
        App.removeCallbacks(mR1);
    }

    private void hideCenter() {
        mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
        mBinding.widget.center.setVisibility(View.GONE);
    }

    private void showInfo() {
        mBinding.widget.bottom.setVisibility(View.VISIBLE);
        setR3Callback();
        setInfo();
    }

    private void hideInfo() {
        mBinding.widget.bottom.setVisibility(View.GONE);
        App.removeCallbacks(mR3);
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.progress.traffic);
        App.post(mR2, 1000);
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR3Callback() {
        App.post(mR3, Constant.INTERVAL_HIDE);
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else if (isVisible(mBinding.recycler)) hideUI();
        else showUI();
        hideInfo();
    }

    private void resetPass() {
        this.count = 0;
    }

    private void setArtwork() {
        ImgUtil.load(this, mChannel.getLogo(), new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                mBinding.exo.setDefaultArtwork(resource);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                mBinding.exo.setDefaultArtwork(errorDrawable);
            }
        });
    }

    @Override
    public void onItemClick(Group item) {
        channelFiltering = false;
        globalKeep = false;
        mChannelAdapter.addAll(setWidth(item).getChannel());
        mBinding.channel.setSelectedPosition(Math.max(item.getPosition(), 0));
        if (!item.isKeep() || ++count < 5 || mHides.isEmpty()) return;
        PassDialog.create().show(this);
        App.removeCallbacks(mR4);
        resetPass();
    }

    @Override
    public void onItemClick(Channel item) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastChannelClick < 500) return;
        lastChannelClick = now;
        if (globalKeep && item.getGroup() != null) { mGroup = item.getGroup(); globalKeep = false; }
        saveRecent(item);
        if (!item.getData(mViewModel.getZoneId()).getList().isEmpty() && item.isSelected() && mChannel != null && mChannel.equals(item) && mChannel.getGroup().equals(mGroup)) {
            showEpg(item);
        } else if (mGroup != null) {
            mGroup.setPosition(mBinding.channel.getSelectedPosition());
            setChannel(item.group(mGroup));
            hideUI();
        }
    }

    @Override
    public boolean onLongClick(Channel item) {
        if (mGroup.isHidden()) return false;
        boolean exist = Keep.exist(item.getName());
        String[] items = {exist ? "取消收藏" : "收藏频道", "频道信息", "自动选最快线路"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(item.getName())
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        if (exist) delKeep(item);
                        else addKeep(item);
                        Notify.show(exist ? "已取消收藏" : "已收藏");
                    } else if (which == 2) autoFastLine(item);
                    else showChannelInfo(item);
                })
                .show();
        return true;
    }


    private void autoFastLine(Channel item) {
        try {
            final java.util.List<String> urls = item.getUrls();
            if (urls == null || urls.size() < 2) { Notify.show("当前频道只有一条线路"); return; }
            Notify.show("正在测速 " + urls.size() + " 条线路…");
            com.fongmi.android.tv.utils.Task.execute(() -> {
                final long[] lat = new long[urls.size()];
                java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(Math.min(4, urls.size()));
                for (int i = 0; i < urls.size(); i++) {
                    final int idx = i;
                    pool.execute(() -> lat[idx] = ping(urls.get(idx).split("\\$")[0]));
                }
                pool.shutdown();
                try { pool.awaitTermination(8, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) {}
                int best = -1;
                for (int i = 0; i < lat.length; i++) if (lat[i] >= 0 && (best < 0 || lat[i] < lat[best])) best = i;
                final int bestIdx = best;
                com.fongmi.android.tv.App.post(() -> {
                    if (bestIdx < 0) { Notify.show("所有线路测速失败，建议手动选择"); return; }
                    item.setIndex(bestIdx);
                    if (mChannel != null && mChannel.getName() != null && mChannel.getName().equals(item.getName())) { setInfo(); fetch(); }
                    Notify.show("已选最快线路 " + (bestIdx + 1) + "（" + lat[bestIdx] + "ms）");
                });
            });
        } catch (Exception e) {
            Notify.show("自动选线失败：" + e.getMessage());
        }
    }

    private void showChannelInfo(Channel item) {
        int lines = item.getUrls() == null ? 0 : item.getUrls().size();
        String group = item.getGroup() == null ? "" : item.getGroup().getName();
        String tmp = "";
        try { tmp = item.getCurrent(); } catch (Exception ignored) {}
        final String cur = tmp;
        String info = "分组：" + group + "\n收藏：" + (com.fongmi.android.tv.bean.Keep.exist(item.getName()) ? "是" : "否") + "\n线路：" + lines + " 条"
                + "\n当前线路：" + (item.getLine() == null || item.getLine().isEmpty() ? "1" : item.getLine())
                + "\nEPG：" + (item.getEpg() == null || item.getEpg().isEmpty() ? "无" : item.getEpg())
                + (cur.isEmpty() ? "" : "\n地址：" + cur);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(item.getName())
                .setMessage(info)
                .setPositiveButton("关闭", null)
                .setNegativeButton("复制频道名", (d, w) -> { com.fongmi.android.tv.utils.Util.copy(item.getName()); com.fongmi.android.tv.utils.Notify.show("已复制频道名"); })
                .setNeutralButton("复制地址", (d, w) -> {
                    if (cur.isEmpty()) Notify.show("没有可复制的地址");
                    else { com.fongmi.android.tv.utils.Util.copy(cur); Notify.show("已复制"); }
                })
                .show();
    }

    @Override
    public void onItemClick(EpgData item) {
        if (item.isSelected()) {
            fetch(item);
        } else if (mChannel.hasCatchup() || mChannel.isRtsp()) {
            mBinding.widget.title.setText(getString(R.string.detail_title, mChannel.getShow(), item.getTitle()));
            Notify.show(getString(R.string.play_ready, item.getTitle()));
            setActivated(item);
            fetch(item);
        }
    }

    private void addKeep(Channel item) {
        getKeep().add(item);
        Keep keep = new Keep();
        keep.setKey(item.getName());
        keep.setType(1);
        keep.save();
    }

    private void delKeep(Channel item) {
        if (mGroup.isKeep()) mChannelAdapter.remove(item);
        if (mChannelAdapter.getItemCount() == 0) mBinding.group.requestFocus();
        getKeep().getChannel().remove(item);
        Keep.delete(item.getName());
    }

    private void setChannel(Channel item) {
        App.post(mR0, 100);
        mChannel = item;
        lineRetry = 0;
        sameLineRetry = false;
        setArtwork();
        showInfo();
    }

    private void setInfo() {
        mViewModel.getEpg(mChannel);
        mBinding.widget.play.setText("");
        mBinding.widget.name.setMaxEms(48);
        mChannel.loadLogo(mBinding.widget.logo);
        mBinding.widget.title.setSelected(true);
        mBinding.widget.line.setText(mChannel.getLine());
        mBinding.widget.name.setText(mChannel.getShow());
        mBinding.widget.title.setText(mChannel.getShow() + (mChannel.getUrls().size() > 1 ? "（线路 " + (mChannel.getIndex() + 1) + "/" + mChannel.getUrls().size() + "）" : ""));
        mBinding.control.action.line.setText(mChannel.getLine());
        mBinding.widget.number.setText(mChannel.getNumber());
        mBinding.widget.line.setVisibility(mChannel.getLineVisible());
        mBinding.control.action.line.setVisibility(mChannel.getLineVisible());
    }

    private void setEpg(Epg epg) {
        if (mChannel == null || !mChannel.getTvgId().equals(epg.getKey())) return;
        EpgData data = epg.getEpgData();
        boolean hasTitle = !data.getTitle().isEmpty();
        mEpgDataAdapter.addAll(epg.getList());
        if (hasTitle) mBinding.widget.title.setText(getString(R.string.detail_title, mChannel.getShow(), data.getTitle()));
        mBinding.widget.name.setMaxEms(hasTitle ? 12 : 48);
        mBinding.widget.play.setText(data.format());
        setWidth(epg);
        setMetadata();
    }

    private void setEpg(boolean success) {
        if (mChannel != null && success) mViewModel.getEpg(mChannel);
    }

    private void fetch(EpgData item) {
        if (mChannel == null) return;
        mViewModel.getUrl(mChannel, item);
        player().clear();
        player().stop();
        hideUI();
    }

    private void fetch() {
        if (mChannel == null) return;
        LiveConfig.get().setKeep(mChannel);
        mViewModel.getUrl(mChannel);
        player().clear();
        player().stop();
        showProgress();
    }

    private void start(Result result) {
        mPlaybackKey = result.getRealUrl();
        startPlayer(mPlaybackKey, result, false, getHome().getTimeout(), buildMetadata());
    }

    private void resetAdapter() {
        mBinding.control.action.line.setVisibility(View.GONE);
        mBinding.widget.title.setText("");
        mEpgDataAdapter.clear();
        mChannelAdapter.clear();
        mGroupAdapter.clear();
        mHides.clear();
        mChannel = null;
        mGroup = null;
    }

    @Override
    public void onSubtitleClick() {
        SubtitleDialog.create().view(mBinding.exo.getSubtitleView()).show(this);
        hideControl();
    }

    @Override
    public void setConfig(Config config) {
        Config current = LiveConfig.get().getConfig();
        LiveConfig.load(config, getCallback(current));
    }

    private Callback getCallback(Config config) {
        return new Callback() {
            @Override
            public void start() {
                showProgress();
            }

            @Override
            public void success() {
                setLive(getHome());
            }

            @Override
            public void error(String msg) {
                LiveConfig.load(config, new Callback());
                Notify.show(msg);
                hideProgress();
            }
        };
    }

    @Override
    public void setLive(Live item) {
        if (item.isActivated()) item.getGroups().clear();
        LiveConfig.get().setHome(item);
        player().reset();
        player().clear();
        player().stop();
        resetAdapter();
        hideControl();
        getLive();
    }

    @Override
    public void setPass(String pass) {
        unlock(pass);
    }

    private void unlock(String pass) {
        boolean first = true;
        int position = mGroupAdapter.getItemCount();
        Iterator<Group> iterator = mHides.iterator();
        while (iterator.hasNext()) {
            Group item = iterator.next();
            if (pass != null && !pass.equals(item.getPass())) continue;
            mGroupAdapter.add(mGroupAdapter.getItemCount(), item);
            if (first) mBinding.group.setSelectedPosition(position);
            if (first) onItemClick(mGroup = item);
            iterator.remove();
            first = false;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case LIVE:
                setLive(getHome());
                break;
            case PLAYER:
                fetch();
                break;
        }
    }

    private void checkEnded() {
        if (player().isLive()) {
            checkNext();
        } else {
            nextChannel();
        }
    }

    private void setTrackVisible() {
        mBinding.control.action.text.setVisibility(player().haveTrack(C.TRACK_TYPE_TEXT) || player().isVod() ? View.VISIBLE : View.GONE);
        mBinding.control.action.audio.setVisibility(player().haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        mBinding.control.action.video.setVisibility(player().haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
        mBinding.control.action.speed.setVisibility(player().isVod() ? View.VISIBLE : View.GONE);
    }

    private MediaMetadata buildMetadata() {
        String artist = mBinding.widget.play.getText().toString();
        return PlayerManager.buildMetadata(mChannel.getShow(), artist, mChannel.getLogo());
    }

    private void setMetadata() {
        player().setMetadata(buildMetadata());
    }

    private void startFlow() {
        if (!LiveSetting.isChange()) return;
        if (!mChannel.isLast()) nextLine(true);
    }

    private void prevChannel() {
        if (mGroup == null) return;
        int position = mGroup.getPosition() - 1;
        boolean limit = position < 0;
        if (LiveSetting.isAcross() & limit) prevGroup();
        else mGroup.setPosition(limit ? mChannelAdapter.getItemCount() - 1 : position);
        if (!mGroup.isEmpty()) setChannel(mGroup.current());
    }

    private void nextChannel() {
        if (mGroup == null) return;
        int position = mGroup.getPosition() + 1;
        boolean limit = position > mChannelAdapter.getItemCount() - 1;
        if (LiveSetting.isAcross() && limit) nextGroup();
        else mGroup.setPosition(limit ? 0 : position);
        if (!mGroup.isEmpty()) setChannel(mGroup.current());
    }

    private boolean nextGroup() {
        int position = mBinding.group.getSelectedPosition() + 1;
        if (position > mGroupAdapter.getItemCount() - 1) position = 0;
        if (mGroup.equals(mGroupAdapter.get(position))) return false;
        mGroup = mGroupAdapter.get(position);
        mBinding.group.setSelectedPosition(position);
        if (mGroup.skip()) return nextGroup();
        mChannelAdapter.addAll(mGroup.getChannel());
        mGroup.setPosition(0);
        return true;
    }

    private boolean prevGroup() {
        int position = mBinding.group.getSelectedPosition() - 1;
        if (position < 0) position = mGroupAdapter.getItemCount() - 1;
        if (mGroup.equals(mGroupAdapter.get(position))) return false;
        mGroup = mGroupAdapter.get(position);
        mBinding.group.setSelectedPosition(position);
        if (mGroup.skip()) return prevGroup();
        mChannelAdapter.addAll(mGroup.getChannel());
        mGroup.setPosition(mGroup.getChannel().size() - 1);
        return true;
    }

    private void checkNext() {
        int current = mChannel.getData(mViewModel.getZoneId()).getInRange();
        int position = mChannel.getData(mViewModel.getZoneId()).getSelected() + 1;
        boolean hasNext = position <= current && position > 0;
        if (hasNext) onItemClick(mChannel.getData(mViewModel.getZoneId()).getList().get(position));
        else fetch();
    }

    private void prevLine() {
        if (mChannel == null || mChannel.isOnly()) return;
        mChannel.switchLine(false);
        showInfo();
        fetch();
    }

    private void nextLine(boolean show) {
        if (mChannel == null || mChannel.isOnly()) return;
        mChannel.switchLine(true);
        if (show) showInfo();
        else setInfo();
        fetch();
    }

    private void seek(long time) {
        mKeyDown.reset();
        seekTo(time);
    }

    private void onPaused() {
        controller().pause();
    }

    private void onPlay() {
        controller().play();
    }

    private View getFocus2() {
        return mFocus2 == null || mFocus2.getVisibility() != View.VISIBLE ? mBinding.control.action.config : mFocus2;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isVisible(mBinding.control.getRoot())) setR1Callback();
        if (isVisible(mBinding.control.getRoot())) mFocus2 = getCurrentFocus();
        if (mKeyDown.hasEvent(event) && service() != null) mKeyDown.onKeyDown(event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void setUITimer() {
        App.post(mR4, Constant.INTERVAL_HIDE);
    }

    @Override
    public boolean dispatch(boolean check) {
        return !check || isGone(mBinding.recycler) && isGone(mBinding.control.getRoot());
    }

    @Override
    public void onShow(String number) {
        mBinding.widget.digital.setText(number);
        mBinding.widget.digital.setVisibility(View.VISIBLE);
    }

    @Override
    public void onFind(String number) {
        mBinding.widget.digital.setVisibility(View.GONE);
        setPosition(LiveConfig.get().findByChannelNumber(number, mGroupAdapter.unmodifiableList()));
    }

    @Override
    public void onSeeking(long time) {
        if (player().isLive()) return;
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.widget.duration.setText(player().getDurationTime());
        mBinding.widget.position.setText(player().getPositionTime(time));
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        hideProgress();
    }

    @Override
    public void onKeyUp() {
        if (LiveSetting.isInvert()) nextChannel();
        else prevChannel();
    }

    @Override
    public void onKeyDown() {
        if (LiveSetting.isInvert()) prevChannel();
        else nextChannel();
    }

    @Override
    public void onKeyLeft(long time) {
        if (player().isLive()) prevLine();
        else App.post(() -> seek(time), 250);
    }

    @Override
    public void onKeyRight(long time) {
        if (player().isLive()) nextLine(true);
        else App.post(() -> seek(time), 250);
    }

    @Override
    public void onKeyCenter() {
        hideInfo();
        showUI();
    }

    @Override
    public void onMenu() {
        showControl(getFocus2());
    }

    @Override
    public void onSingleTap() {
        onToggle();
    }

    @Override
    public void onDoubleTap() {
        if (isVisible(mBinding.recycler)) hideUI();
        else if (isVisible(mBinding.control.getRoot())) hideControl();
        else onMenu();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
    }

    @Override
    protected void onBackInvoked() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.bottom)) {
            hideInfo();
        } else if (isVisible(mBinding.recycler)) {
            hideUI();
        } else {
            if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            super.onBackInvoked();
        }
    }

    @Override
    public void onBackPressed() {
        if (channelFiltering) {
            resetChannelFilter();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        mClock.release();
        Source.get().exit();
        App.removeCallbacks(mR0, mR1, mR2, mR3, mR4);
        mViewModel.url().removeObserver(mObserveUrl);
        mViewModel.epg().removeObserver(mObserveEpg);
        super.onDestroy();
    }
    private void switchChannelLine() {
        if (mChannel == null || mChannel.getUrls() == null || mChannel.getUrls().size() <= 1) {
            com.fongmi.android.tv.utils.Notify.show("当前频道只有一条线路");
            return;
        }
        mChannel.switchLine(true);
        lineRetry = 0;
        com.fongmi.android.tv.utils.Notify.show("已切到线路 " + (mChannel.getIndex() + 1) + "/" + mChannel.getUrls().size());
        mViewModel.getUrl(mChannel);
    }


    private void showGroupMenu() {
        String[] items = {"搜索频道", "全部收藏频道", "最近观看", "清空最近观看", "重置筛选"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(mGroup == null ? "频道" : groupTitle())
                .setItems(items, (d, which) -> {
                    if (which == 0) showChannelSearch();
                    else if (which == 1) showGlobalKeeps();
                    else if (which == 2) showRecentChannels();
                    else if (which == 3) { com.fongmi.android.tv.setting.Setting.putRecent(""); com.fongmi.android.tv.utils.Notify.show("已清空最近观看"); }
                    else resetChannelFilter();
                })
                .show();
    }

    private void saveRecent(com.fongmi.android.tv.bean.Channel item) {
        try {
            java.util.List<String> list = new java.util.ArrayList<>();
            String json = com.fongmi.android.tv.setting.Setting.getRecent();
            if (json != null && !json.isEmpty()) {
                java.util.List<String> old = com.fongmi.android.tv.App.gson().fromJson(json, new com.google.gson.reflect.TypeToken<java.util.List<String>>() {}.getType());
                if (old != null) list.addAll(old);
            }
            list.remove(item.getName());
            list.add(0, item.getName());
            while (list.size() > 12) list.remove(list.size() - 1);
            com.fongmi.android.tv.setting.Setting.putRecent(com.fongmi.android.tv.App.gson().toJson(list));
        } catch (Exception ignored) {
        }
    }

    private void showRecentChannels() {
        try {
            String json = com.fongmi.android.tv.setting.Setting.getRecent();
            if (json == null || json.isEmpty()) {
                com.fongmi.android.tv.utils.Notify.show("还没有观看记录");
                return;
            }
            java.util.List<String> names = com.fongmi.android.tv.App.gson().fromJson(json, new com.google.gson.reflect.TypeToken<java.util.List<String>>() {}.getType());
            if (names == null || names.isEmpty()) {
                com.fongmi.android.tv.utils.Notify.show("还没有观看记录");
                return;
            }
            java.util.List<com.fongmi.android.tv.bean.Channel> channels = new java.util.ArrayList<>();
            java.util.List<com.fongmi.android.tv.bean.Group> groups = com.fongmi.android.tv.api.config.LiveConfig.get().getHome().getGroups();
            if (groups != null) for (com.fongmi.android.tv.bean.Group group : groups) {
                if (group.getChannel() == null) continue;
                for (com.fongmi.android.tv.bean.Channel channel : group.getChannel()) {
                    if (names.contains(channel.getName()) && !channels.contains(channel)) channels.add(channel);
                }
            }
            if (channels.isEmpty()) {
                com.fongmi.android.tv.utils.Notify.show("最近观看的频道已不在当前订阅中");
                return;
            }
            globalKeep = true;
            channelFiltering = true;
            mChannelAdapter.addAll(channels);
            com.fongmi.android.tv.utils.Notify.show("最近观看：" + channels.size() + " 个频道（按返回键恢复）");
        } catch (Exception e) {
            com.fongmi.android.tv.utils.Notify.show("读取最近观看失败");
        }
    }

    private void showGlobalKeeps() {
        try {
            java.util.List<com.fongmi.android.tv.bean.Channel> keeps = new java.util.ArrayList<>();
            java.util.List<com.fongmi.android.tv.bean.Group> groups = com.fongmi.android.tv.api.config.LiveConfig.get().getHome().getGroups();
            if (groups != null) for (com.fongmi.android.tv.bean.Group group : groups) {
                if (group.getChannel() == null) continue;
                for (com.fongmi.android.tv.bean.Channel channel : group.getChannel()) {
                    if (com.fongmi.android.tv.bean.Keep.exist(channel.getName())) keeps.add(channel);
                }
            }
            if (keeps.isEmpty()) {
                com.fongmi.android.tv.utils.Notify.show("还没有收藏频道，长按频道即可收藏");
                return;
            }
            globalKeep = true;
            channelFiltering = true;
            mChannelAdapter.addAll(keeps);
            com.fongmi.android.tv.utils.Notify.show("全部收藏：" + keeps.size() + " 个频道（按返回键恢复）");
        } catch (Exception e) {
            com.fongmi.android.tv.utils.Notify.show("读取收藏失败");
        }
    }

    private void showChannelSearch() {
        if (mGroup == null) return;
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("输入频道关键词（留空即仅按条件筛选）");
        android.widget.CheckBox only = new android.widget.CheckBox(this);
        only.setText("仅看收藏频道");
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(48, 16, 48, 0);
        box.addView(input);
        box.addView(only);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("搜索频道")
                .setView(box)
                .setPositiveButton("筛选", (d, w) -> filterChannels(input.getText().toString(), only.isChecked()))
                .setNeutralButton("重置", (d, w) -> resetChannelFilter())
                .setNegativeButton("取消", null)
                .show();
    }

    private void filterChannels(String key) {
        filterChannels(key, false);
    }

    private boolean channelFiltering;
    private boolean globalKeep;
    private long lastChannelClick;

    private void filterChannels(String key, boolean onlyKeep) {
        String kw = key == null ? "" : key.trim();
        if (kw.isEmpty() && !onlyKeep) {
            resetChannelFilter();
            return;
        }
        java.util.List<com.fongmi.android.tv.bean.Channel> items = new java.util.ArrayList<>();
        for (com.fongmi.android.tv.bean.Channel channel : mGroup.getChannel()) {
            if (channel.getName() != null && channel.getName().contains(kw)) items.add(channel);
        }
        mChannelAdapter.addAll(items);
        com.fongmi.android.tv.utils.Notify.show("匹配 " + items.size() + " 个频道");
    }

    private void resetChannelFilter() {
        channelFiltering = false;
        globalKeep = false;
        mChannelAdapter.addAll(mGroup.getChannel());
        com.fongmi.android.tv.utils.Notify.show("已显示全部 " + mGroup.getChannel().size() + " 个频道");
    }


    private void showLineList() {
        try {
            if (mChannel == null || mChannel.isOnly()) {
                com.fongmi.android.tv.utils.Notify.show("当前频道只有一条线路");
                return;
            }
            final java.util.List<String> urls = mChannel.getUrls();
            final String[] names = new String[urls.size()];
            for (int i = 0; i < urls.size(); i++) {
                String[] sp = urls.get(i).split("\\$");
                String title = sp.length > 1 && !sp[1].isEmpty() ? sp[1] : ("线路 " + (i + 1));
                String host = "";
                try { host = new java.net.URI(sp[0]).getHost(); } catch (Exception ignored) {}
                String raw = sp[0].toLowerCase();
                String proto = raw.startsWith("rtsp") ? "RTSP" : raw.contains(".m3u8") ? "HLS" : raw.contains(".flv") ? "FLV" : "HTTP";
                if (host != null && !host.isEmpty()) title = title + " · " + proto + " · " + host;
                names[i] = (i == mChannel.getIndex() ? "✓ " : "") + title;
            }
            com.fongmi.android.tv.utils.Task.execute(() -> {
                final long[] lat = new long[urls.size()];
                java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(Math.min(4, Math.max(1, urls.size())));
                for (int i = 0; i < urls.size(); i++) {
                    final int idx = i;
                    pool.execute(() -> lat[idx] = ping(urls.get(idx).split("\\$")[0]));
                }
                pool.shutdown();
                try { pool.awaitTermination(8, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) {}
                for (int i = 0; i < names.length; i++) names[i] = names[i] + " · " + (lat[i] < 0 ? "测速超时" : lat[i] + "ms");
                com.fongmi.android.tv.App.post(() -> new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("选择线路（含延迟）")
                        .setItems(names, (d, which) -> {
                            mChannel.setIndex(which);
                            setInfo();
                            fetch();
                            com.fongmi.android.tv.utils.Notify.show("已切换到 " + names[which].replace("✓ ", "").split(" · ")[0]);
                        })
                        .setNegativeButton("取消", null)
                        .show());
            });
        } catch (Exception e) {
            com.fongmi.android.tv.utils.Notify.show("线路列表不可用");
        }
    }

    private long ping(String url) {
        java.net.HttpURLConnection c = null;
        try {
            long start = System.currentTimeMillis();
            c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(1500);
            c.setReadTimeout(1500);
            c.setRequestMethod("GET");
            c.setRequestProperty("Range", "bytes=0-0");
            c.getResponseCode();
            return System.currentTimeMillis() - start;
        } catch (Exception e) {
            return -1;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private String groupTitle() {
        try {
            int total = mGroup.getChannel() == null ? 0 : mGroup.getChannel().size();
            int keeps = 0;
            if (mGroup.getChannel() != null) for (com.fongmi.android.tv.bean.Channel channel : mGroup.getChannel()) if (com.fongmi.android.tv.bean.Keep.exist(channel.getName())) keeps++;
            int all = com.fongmi.android.tv.bean.Keep.getLive().size();
            return mGroup.getName() + "（" + total + " 个频道 · 本组收藏 " + keeps + " · 全站收藏 " + all + "）";
        } catch (Exception e) {
            return mGroup.getName();
        }
    }


}
