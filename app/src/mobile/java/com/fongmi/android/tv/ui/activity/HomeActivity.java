package com.fongmi.android.tv.ui.activity;

import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.event.StateEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.receiver.ShortcutReceiver;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.FragmentStateManager;
import com.fongmi.android.tv.ui.fragment.SettingFragment;
import com.fongmi.android.tv.ui.fragment.SettingPlayerFragment;
import com.fongmi.android.tv.ui.fragment.VodFragment;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.google.android.material.navigation.NavigationBarView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class HomeActivity extends BaseActivity implements NavigationBarView.OnItemSelectedListener {

    private FragmentStateManager mManager;
    private ActivityHomeBinding mBinding;
    private int orientation;

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkAction(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        orientation = getResources().getConfiguration().orientation;
        initFragment(savedInstanceState);
        initConfig();
    }

    @Override
    protected void initEvent() {
        mBinding.navigation.setOnItemSelectedListener(this);
        mBinding.navigation.setOnLongClickListener(v -> { continueWatching(); return true; });
        mBinding.navigation.findViewById(R.id.live).setOnLongClickListener(this::addShortcut);
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            PermissionUtil.requestFile(this, allGranted -> checkType(intent));
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String keyword = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(keyword)) SearchActivity.start(this, keyword);
        }
    }

    private void checkType(Intent intent) {
        if ("text/plain".equals(intent.getType()) || UrlUtil.path(intent.getData()).endsWith(".m3u")) {
            loadLive("file:/" + FileChooser.getPathFromUri(intent.getData()));
        } else {
            VideoActivity.push(this, intent.getData().toString());
        }
    }

    private void initFragment(Bundle savedInstanceState) {
        mManager = new FragmentStateManager(mBinding.container, getSupportFragmentManager()) {
            @Override
            public Fragment getItem(int position) {
                if (position == 0) return VodFragment.newInstance();
                if (position == 1) return SettingFragment.newInstance();
                if (position == 2) return SettingPlayerFragment.newInstance();
                return null;
            }
        };
        if (savedInstanceState == null) mManager.change(0);
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                checkEmpty();
                checkAction(getIntent());
            }

            @Override
            public void error(String msg) {
                checkAction(getIntent());
                StateEvent.empty();
                Notify.show(msg);
            }
        };
    }

    private void loadLive(String url) {
        LiveConfig.load(Config.find(url, 1), new Callback() {
            @Override
            public void success() {
                openLive();
            }
        });
    }

    private void setNavigation() {
        mBinding.navigation.getMenu().findItem(R.id.vod).setVisible(true);
        mBinding.navigation.getMenu().findItem(R.id.setting).setVisible(true);
        mBinding.navigation.getMenu().findItem(R.id.live).setVisible(true);
    }

    private boolean openLive() {
        LiveActivity.start(this);
        return false;
    }

    private boolean addShortcut(View view) {
        ShortcutInfoCompat info = new ShortcutInfoCompat.Builder(this, getString(R.string.nav_live)).setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher)).setIntent(new Intent(Intent.ACTION_VIEW, null, this, LiveActivity.class)).setShortLabel(getString(R.string.nav_live)).build();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(this, ShortcutReceiver.class).setAction(ShortcutReceiver.ACTION), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        ShortcutManagerCompat.requestPinShortcut(this, info, pendingIntent.getIntentSender());
        return true;
    }

    public void change(int position) {
        mManager.change(position);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case VOD:
                RefreshEvent.home();
                break;
            case COMMON:
                setNavigation();
                break;
            case BOOT:
                LiveActivity.start(this);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() == ServerEvent.Type.PUSH) VideoActivity.push(this, event.text());
        if (event.type() == ServerEvent.Type.SEARCH) SearchActivity.start(this, event.text());
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (mBinding.navigation.getSelectedItemId() == item.getItemId()) return false;
        if (item.getItemId() == R.id.setting) return mManager.change(1);
        if (item.getItemId() == R.id.vod) return mManager.change(0);
        if (item.getItemId() == R.id.live) return openLive();
        return false;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        App.post(() -> checkOrientation(newConfig), 100);
    }

    private void checkOrientation(Configuration newConfig) {
        if (orientation != newConfig.orientation) {
            orientation = newConfig.orientation;
            RefreshEvent.home();
        }
    }

    @Override
    protected void onBackInvoked() {
        if (!mBinding.navigation.getMenu().findItem(R.id.vod).isVisible()) {
            setNavigation();
        } else if (mManager.isVisible(2)) {
            change(1);
        } else if (mManager.isVisible(1)) {
            mBinding.navigation.setSelectedItemId(R.id.vod);
        } else if (mManager.canBack(0)) {
            if (PlaybackService.isRunning()) moveTaskToBack(true);
            else super.onBackInvoked();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        showCrashDialog();
    }

    @Override
    protected void onDestroy() {
        LiveConfig.get().clear();
        VodConfig.get().clear();
        AppDatabase.backup();
        OkHttp.get().clear();
        Source.get().exit();
        Server.get().stop();
        super.onDestroy();
    }
    private void checkEmpty() {
        try {
            if (com.fongmi.android.tv.api.config.VodConfig.get().getSites().isEmpty()) {
                com.fongmi.android.tv.utils.Notify.show("影视订阅为空：设置 → 影视订阅 → 填写地址");
            }
            if (com.fongmi.android.tv.api.config.LiveConfig.get().getLives() == null || com.fongmi.android.tv.api.config.LiveConfig.get().getLives().isEmpty()) {
                com.fongmi.android.tv.utils.Notify.show("直播订阅为空：设置 → 直播订阅 → 填写地址");
                try { if (!com.fongmi.android.tv.setting.Setting.getGuide()) App.post(() -> { try { mManager.change(1); } catch (Exception ignored) {} }); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {
        }
        promptEmpty();
        showStatusOnce();
    }


    private boolean emptyPrompted;
    private boolean statusShown;

    private void promptEmpty() {
        if (emptyPrompted) return;
        emptyPrompted = true;
        boolean vodEmpty = com.fongmi.android.tv.api.config.VodConfig.get().getSites().isEmpty();
        boolean liveEmpty = com.fongmi.android.tv.api.config.LiveConfig.get().getLives() == null || com.fongmi.android.tv.api.config.LiveConfig.get().getLives().isEmpty();
        if (!vodEmpty && !liveEmpty) return;
        String msg = (vodEmpty ? "影视订阅为空" : "") + (vodEmpty && liveEmpty ? "；" : "") + (liveEmpty ? "直播订阅为空" : "");
        boolean first = !com.fongmi.android.tv.setting.Setting.getGuide();
        String tips = first
                ? "\n\n首次使用建议：\n1. 点“使用默认订阅”即可自动填入内置影视与直播源；\n2. 想看电视台直播，进直播页后长按频道可收藏；\n3. 播放页长按“信息”键可复制地址、查看内容详情。"
                : "";
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("还没有订阅")
                .setMessage(msg + tips + "\n\n也可以前往设置手动填写订阅地址。")
                .setPositiveButton("使用默认订阅", (d, w) -> {
                    com.fongmi.android.tv.setting.Setting.putGuide(true);
                    fillDefault(vodEmpty, liveEmpty);
                })
                .setNeutralButton("稍后", null)
                .setNegativeButton("去设置", (d, w) -> { try { mManager.change(1); } catch (Exception ignored) {} })
                .show();
    }

    private void fillDefault(boolean vod, boolean live) {
        try {
            com.fongmi.android.tv.utils.Notify.show("正在填入默认订阅…");
            if (vod) com.fongmi.android.tv.api.config.VodConfig.load(com.fongmi.android.tv.bean.Config.create(0, com.fongmi.android.tv.bean.Config.SEED_VOD, "默认影视"), new com.fongmi.android.tv.impl.Callback() {
                @Override public void start() {}
                @Override public void success() {}
                @Override public void error(String msg) {}
            });
            if (live) com.fongmi.android.tv.api.config.LiveConfig.load(com.fongmi.android.tv.bean.Config.create(1, com.fongmi.android.tv.bean.Config.SEED_LIVE, "默认直播"), new com.fongmi.android.tv.impl.Callback() {
                @Override public void start() {}
                @Override public void success() {}
                @Override public void error(String msg) {}
            });
            com.fongmi.android.tv.utils.Notify.show("已填入默认订阅，如未生效请重启应用");
        } catch (Exception e) {
            com.fongmi.android.tv.utils.Notify.show("填入失败：" + e.getMessage());
        }
    }


    private void showCrashDialog() {
        String log = com.fongmi.android.tv.App.crashLog();
        if (log.isEmpty()) return;
        com.fongmi.android.tv.App.clearCrashLog();
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("上次异常退出")
                .setMessage(log.length() > 1200 ? log.substring(0, 1200) + "…" : log)
                .setPositiveButton("复制日志", (d, w) -> { com.fongmi.android.tv.utils.Util.copy(log); com.fongmi.android.tv.utils.Notify.show("已复制崩溃日志"); })
                .setNegativeButton("忽略", null)
                .show();
    }


    private void showStatusOnce() {
        if (statusShown || emptyPrompted) return;
        statusShown = true;
        try {
            int sites = com.fongmi.android.tv.api.config.VodConfig.get().getSites().size();
            int lives = com.fongmi.android.tv.api.config.LiveConfig.get().getLives() == null ? 0 : com.fongmi.android.tv.api.config.LiveConfig.get().getLives().size();
            if (sites == 0 && lives == 0) return;
            com.fongmi.android.tv.utils.Notify.show("影视 " + sites + " 个站点 · 直播 " + lives + " 个源");
        } catch (Exception ignored) {
        }
    }


    private void continueWatching() {
        try {
            java.util.List<com.fongmi.android.tv.bean.History> list = com.fongmi.android.tv.bean.History.get(com.fongmi.android.tv.api.config.VodConfig.getCid());
            if (list == null || list.isEmpty()) {
                com.fongmi.android.tv.utils.Notify.show("还没有观看记录");
                return;
            }
            com.fongmi.android.tv.bean.History item = list.get(0);
            com.fongmi.android.tv.ui.activity.VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
        } catch (Exception e) {
            com.fongmi.android.tv.utils.Notify.show("没有可继续观看的内容");
        }
    }


}
