package com.fongmi.android.tv.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.FragmentSettingBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigCallback;
import com.fongmi.android.tv.impl.LiveCallback;
import com.fongmi.android.tv.impl.SiteCallback;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.RestoreDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class SettingFragment extends BaseFragment implements ConfigCallback, SiteCallback, LiveCallback {

    private FragmentSettingBinding mBinding;
    private String[] size;
    private int type;

    public static SettingFragment newInstance() {
        return new SettingFragment();
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    private HomeActivity getRoot() {
        return (HomeActivity) requireActivity();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        mBinding.vodUrl.setText(VodConfig.getDesc() + " · " + (VodConfig.get().getSites() == null ? 0 : VodConfig.get().getSites().size()) + " 个站点");
        loadHealthSummary();
        mBinding.liveUrl.setText(LiveConfig.getDesc() + " · " + (LiveConfig.get().getLives() == null ? 0 : LiveConfig.get().getLives().size()) + " 个直播源");
        mBinding.wallUrl.setText(WallConfig.getDesc());
        mBinding.versionText.setText(BuildConfig.VERSION_NAME + "（#" + BuildConfig.VERSION_CODE + "，点击检查更新）");
        setOtherText();
        setCacheText();
    }

    private void setOtherText() {
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
        mBinding.incognitoText.setText(getSwitch(Setting.isIncognito()));
        mBinding.sizeText.setText((size = ResUtil.getStringArray(R.array.select_size))[PlayerSetting.getSize()]);
    }

    private void setCacheText() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                mBinding.cacheText.setText(result);
            }
        });
    }

    @Override
    protected void initEvent() {
        mBinding.vod.setOnClickListener(this::onVod);
        mBinding.doh.setOnClickListener(this::setDoh);
        mBinding.live.setOnClickListener(this::onLive);
        mBinding.wall.setOnClickListener(this::onWall);
        mBinding.size.setOnClickListener(this::setSize);
        mBinding.cache.setOnLongClickListener(v -> { showCacheMenu(); return true; });
        mBinding.cache.setOnClickListener(this::onCache);
        mBinding.backup.setOnClickListener(this::onBackup);
        mBinding.player.setOnClickListener(this::onPlayer);
        mBinding.player.setOnLongClickListener(v -> { showPlayerMenu(); return true; });
        mBinding.restore.setOnClickListener(this::onRestore);
        mBinding.version.setOnClickListener(this::onVersion);
        mBinding.version.setOnLongClickListener(v -> { showBuildInfo(); return true; });
        mBinding.vod.setOnLongClickListener(this::onVodEdit);
        mBinding.vodHome.setOnClickListener(this::onVodHome);
        mBinding.liveUrl.setOnClickListener(v -> checkLiveSubscription());
        mBinding.liveUrl.setOnLongClickListener(v -> { showProbeMenu(); return true; });
        mBinding.vodUrl.setOnClickListener(v -> checkAllSubscriptions());
        mBinding.vodUrl.setOnLongClickListener(v -> { showVodMenu(); return true; });
        mBinding.live.setOnLongClickListener(this::onLiveEdit);
        mBinding.liveHome.setOnClickListener(this::onLiveHome);
        mBinding.wall.setOnLongClickListener(this::onWallEdit);
        mBinding.incognito.setOnClickListener(this::setIncognito);
        mBinding.vodHistory.setOnClickListener(this::onVodHistory);
        mBinding.liveHistory.setOnClickListener(this::onLiveHistory);
        mBinding.wallDefault.setOnClickListener(this::setWallDefault);
        mBinding.wallRefresh.setOnClickListener(this::setWallRefresh);
        mBinding.wallRefresh.setOnLongClickListener(this::onWallHistory);
    }

    @Override
    public void setConfig(Config config) {
        if (config.getUrl().startsWith("file")) {
            PermissionUtil.requestFile(this, allGranted -> load(config));
        } else {
            load(config);
        }
    }

    private void load(Config config) {
        switch (config.getType()) {
            case 0:
                VodConfig.load(config, getCallback());
                break;
            case 1:
                LiveConfig.load(config, getCallback());
                break;
            case 2:
                Setting.putWall(0);
                WallConfig.load(config, getCallback());
                break;
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(requireActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                setCacheText();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    private void onVod(View view) {
        ConfigDialog.create(this).launcher(launcher).type(type = 0).show();
    }

    private void onLive(View view) {
        ConfigDialog.create(this).launcher(launcher).type(type = 1).show();
    }

    private void onWall(View view) {
        ConfigDialog.create(this).launcher(launcher).type(type = 2).show();
    }

    private boolean onVodEdit(View view) {
        ConfigDialog.create(this).launcher(launcher).type(type = 0).edit().show();
        return true;
    }

    private boolean onLiveEdit(View view) {
        ConfigDialog.create(this).launcher(launcher).type(type = 1).edit().show();
        return true;
    }

    private boolean onWallEdit(View view) {
        ConfigDialog.create(this).launcher(launcher).type(type = 2).edit().show();
        return true;
    }

    private void onVodHome(View view) {
        SiteDialog.create(this).all().show();
    }

    private void onLiveHome(View view) {
        LiveDialog.create(this).action().show();
    }

    private void onVodHistory(View view) {
        HistoryDialog.create(this).type(type = 0).show();
    }

    private void onLiveHistory(View view) {
        HistoryDialog.create(this).type(type = 1).show();
    }

    private void onPlayer(View view) {
        getRoot().change(2);
    }

    private void onVersion(View view) {
        openRelease();
    }

    private void setWallDefault(View view) {
        Setting.putWall(Setting.getWall() == 4 ? 1 : Setting.getWall() + 1);
        Setting.putWallType(0);
        ConfigEvent.wall();
    }

    private void setWallRefresh(View view) {
        Setting.putWall(0);
        WallConfig.get().load(getCallback());
    }

    private boolean onWallHistory(View view) {
        HistoryDialog.create(this).type(type = 2).show();
        return true;
    }

    private void setIncognito(View view) {
        Setting.putIncognito(!Setting.isIncognito());
        mBinding.incognitoText.setText(getSwitch(Setting.isIncognito()));
    }

    private void setSize(View view) {
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_size).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(size, PlayerSetting.getSize(), (dialog, which) -> {
            mBinding.sizeText.setText(size[which]);
            PlayerSetting.putSize(which);
            RefreshEvent.size();
            dialog.dismiss();
        }).show();
    }

    private void setDoh(View view) {
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_doh).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(getDohList(), getDohIndex(), (dialog, which) -> {
            setDoh(VodConfig.get().getDoh().get(which));
            dialog.dismiss();
        }).show();
    }

    private void setDoh(Doh doh) {
        OkHttp.dns().setDoh(doh);
        Setting.putDoh(doh.toString());
        mBinding.dohText.setText(doh.getName());
    }

    private void onCache(View view) {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                setCacheText();
            }
        });
    }

    private void onBackup(View view) {
        PermissionUtil.requestFile(this, allGranted -> AppDatabase.backup(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.backup_success);
            }

            @Override
            public void error() {
                Notify.show(R.string.backup_fail);
            }
        }));
    }

    private void onRestore(View view) {
        PermissionUtil.requestFile(this, allGranted -> RestoreDialog.create().show(requireActivity(), new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.restore_success);
                setOtherText();
                initConfig();
            }

            @Override
            public void error() {
                Notify.show(R.string.restore_fail);
            }
        }));
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init().load();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() != ConfigEvent.Type.COMMON) return;
        mBinding.vodUrl.setText(VodConfig.getDesc() + " · " + (VodConfig.get().getSites() == null ? 0 : VodConfig.get().getSites().size()) + " 个站点");
        mBinding.liveUrl.setText(LiveConfig.getDesc() + " · " + (LiveConfig.get().getLives() == null ? 0 : LiveConfig.get().getLives().size()) + " 个直播源");
        mBinding.wallUrl.setText(WallConfig.getDesc());
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) return;
        setCacheText();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus.getDefault().unregister(this);
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        setConfig(Config.find("file:/" + FileChooser.getPathFromUri(result.getData().getData()).replace(Path.rootPath(), ""), type));
    });
    private void openRelease() {
        com.fongmi.android.tv.utils.UpdateChecker.check(requireActivity());
    }


    private void openRepo() {
        try {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/luanmuc/beacon")));
        } catch (Exception e) {
            com.fongmi.android.tv.utils.Notify.show("打开失败");
        }
    }


    private void showHealth() {
        com.fongmi.android.tv.utils.Task.execute(() -> {
            try {
                String url = "https://raw.githubusercontent.com/luanmuc/beacon-seed/main/live-report.json";
                String text = com.github.catvod.net.OkHttp.string(url);
                org.json.JSONObject object = new org.json.JSONObject(text);
                int channels = object.optInt("channels");
                int lines = object.optInt("lines");
                String at = object.optString("generated_at", "");
                com.fongmi.android.tv.App.post(() -> com.fongmi.android.tv.utils.Notify.show("直播源存活：" + channels + " 频道 / " + lines + " 线路" + (at.isEmpty() ? "" : "（探测于 " + at.replace("T", " ").replace("Z", " UTC") + "）")));
            } catch (Exception e) {
                com.fongmi.android.tv.App.post(() -> com.fongmi.android.tv.utils.Notify.show("读取探测结果失败"));
            }
        });
    }


    private void checkSubscribers() {
        String url = com.fongmi.android.tv.api.config.VodConfig.getDesc();
        if (url == null || url.isEmpty()) {
            com.fongmi.android.tv.utils.Notify.show("当前没有影视订阅地址");
            return;
        }
        com.fongmi.android.tv.utils.Notify.show("正在检测订阅连通性…");
        com.fongmi.android.tv.utils.Task.execute(() -> {
            try {
                String text = com.github.catvod.net.OkHttp.string(url);
                com.fongmi.android.tv.App.post(() -> com.fongmi.android.tv.utils.Notify.show("订阅可访问，返回 " + (text == null ? 0 : text.length()) + " 字节"));
            } catch (Exception e) {
                com.fongmi.android.tv.App.post(() -> com.fongmi.android.tv.utils.Notify.show("订阅不可访问：" + e.getMessage()));
            }
        });
    }


    private void checkLiveSubscription() {
        String url = com.fongmi.android.tv.api.config.LiveConfig.getDesc();
        if (url == null || url.isEmpty()) {
            com.fongmi.android.tv.utils.Notify.show("当前没有直播订阅地址");
            return;
        }
        com.fongmi.android.tv.utils.Notify.show("正在检测直播订阅…");
        com.fongmi.android.tv.utils.Task.execute(() -> {
            try {
                String text = com.github.catvod.net.OkHttp.string(url);
                int lines = text == null ? 0 : text.split("\n").length;
                com.fongmi.android.tv.App.post(() -> com.fongmi.android.tv.utils.Notify.show("直播订阅可访问，" + lines + " 行"));
            } catch (Exception e) {
                com.fongmi.android.tv.App.post(() -> com.fongmi.android.tv.utils.Notify.show("直播订阅不可访问：" + e.getMessage()));
            }
        });
    }


    private void loadHealthSummary() {
        loadHealthSummary(false);
    }

    private void loadHealthSummary(boolean force) {
        com.fongmi.android.tv.utils.Task.execute(() -> {
            if (!force && System.currentTimeMillis() - com.fongmi.android.tv.setting.Setting.getHealthTs() < 30L * 60L * 1000L) return;
            try {
                String text = com.github.catvod.net.OkHttp.string("https://raw.githubusercontent.com/luanmuc/beacon-seed/main/live-report.json");
                org.json.JSONObject object = new org.json.JSONObject(text);
                int channels = object.optInt("channels");
                int lines = object.optInt("lines");
                com.fongmi.android.tv.setting.Setting.putHealthTs(System.currentTimeMillis());
                com.fongmi.android.tv.App.post(() -> {
                    try {
                        String raw = String.valueOf(mBinding.liveUrl.getText());
                        int cut = raw.indexOf(" · 存活 ");
                        String base = cut > 0 ? raw.substring(0, cut) : raw;
                        String at = object.optString("generated_at", "");
                        mBinding.liveUrl.setText(base + " · 存活 " + channels + " 频道/" + lines + " 线路" + (at.isEmpty() ? "" : " · " + at.replace("T", " ").replace("Z", "")));
                        com.fongmi.android.tv.utils.Notify.show("存活统计已更新：" + channels + " 频道/" + lines + " 线路");
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
        });
    }


    private void openAppSettings() {
        try {
            android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + requireContext().getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            com.fongmi.android.tv.utils.Notify.show("打开系统设置失败");
        }
    }


    private void checkAllSubscriptions() {
        com.fongmi.android.tv.utils.Notify.show("正在自检影视与直播订阅…");
        com.fongmi.android.tv.utils.Task.execute(() -> {
            String vod = com.fongmi.android.tv.api.config.VodConfig.getDesc();
            String live = com.fongmi.android.tv.api.config.LiveConfig.getDesc();
            StringBuilder sb = new StringBuilder();
            sb.append(checkOne("影视", vod));
            sb.append("；");
            sb.append(checkOne("直播", live));
            String result = sb.toString();
            com.fongmi.android.tv.App.post(() -> new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("订阅自检结果")
                    .setMessage(result)
                    .setPositiveButton("复制结果", (d, w) -> { com.fongmi.android.tv.utils.Util.copy(result); com.fongmi.android.tv.utils.Notify.show("已复制"); })
                    .setNegativeButton("关闭", null)
                    .show());
        });
    }

    private String checkOne(String label, String url) {
        if (url == null || url.isEmpty()) return label + "：未配置";
        try {
            String text = com.github.catvod.net.OkHttp.string(url);
            return label + "：可访问（" + (text == null ? 0 : text.length()) + " 字节）";
        } catch (Exception e) {
            return label + "：不可访问（" + e.getMessage() + "）";
        }
    }


    private void showProbeMenu() {
        String[] items = {"刷新存活统计", "查看存活统计", "查看直播源列表", "检查源清单", "打开 Actions 探测页", "复制订阅地址", "恢复默认直播订阅"};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("直播源维护")
                .setItems(items, (d, which) -> {
                    if (which == 0) { loadHealthSummary(true); com.fongmi.android.tv.utils.Notify.show("正在刷新存活统计…"); }
                    else if (which == 1) showHealth();
                    else if (which == 2) showLiveList();
                    else if (which == 3) checkManifest();
                    else if (which == 4) openActions();
                    else if (which == 5) {
                        com.fongmi.android.tv.utils.Util.copy(com.fongmi.android.tv.api.config.LiveConfig.getDesc());
                        com.fongmi.android.tv.utils.Notify.show("已复制直播订阅地址");
                    } else restoreDefaultLive();
                })
                .show();
    }

    private void openActions() {
        try {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/luanmuc/beacon-feeds/actions")));
        } catch (Exception e) {
            com.fongmi.android.tv.utils.Notify.show("打开失败");
        }
    }


    private void showVodMenu() {
        String[] items = {"复制订阅地址", "查看站点列表（含健康分）", "查看站点详情", "恢复默认影视订阅"};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("影视订阅")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        com.fongmi.android.tv.utils.Util.copy(com.fongmi.android.tv.api.config.VodConfig.getDesc());
                        com.fongmi.android.tv.utils.Notify.show("已复制影视订阅地址");
                    } else if (which == 1) {
                        showSiteList();
                    } else if (which == 2) {
                        showSiteDetailList();
                    } else {
                        restoreDefaultVod();
                    }
                })
                .show();
    }

    private void restoreDefaultVod() {
        try {
            com.fongmi.android.tv.utils.Notify.show("正在恢复默认影视订阅…");
            com.fongmi.android.tv.bean.Config config = com.fongmi.android.tv.bean.Config.create(0, com.fongmi.android.tv.bean.Config.SEED_VOD, "默认影视");
            com.fongmi.android.tv.api.config.VodConfig.load(config, new com.fongmi.android.tv.impl.Callback() {
                @Override
                public void start() {
                }

                @Override
                public void success() {
                    com.fongmi.android.tv.utils.Notify.show("已恢复默认影视订阅");
                }

                @Override
                public void error(String msg) {
                    com.fongmi.android.tv.utils.Notify.show("恢复失败：" + msg);
                }
            });
        } catch (Exception e) {
            com.fongmi.android.tv.utils.Notify.show("恢复失败：" + e.getMessage());
        }
    }


    private void restoreDefaultLive() {
        try {
            com.fongmi.android.tv.utils.Notify.show("正在恢复默认直播订阅…");
            com.fongmi.android.tv.bean.Config config = com.fongmi.android.tv.bean.Config.create(1, com.fongmi.android.tv.bean.Config.SEED_LIVE, "默认直播");
            com.fongmi.android.tv.api.config.LiveConfig.load(config, new com.fongmi.android.tv.impl.Callback() {
                @Override
                public void success() {
                    com.fongmi.android.tv.utils.Notify.show("已恢复默认直播订阅");
                }

                @Override
                public void error(String msg) {
                    com.fongmi.android.tv.utils.Notify.show("恢复失败：" + msg);
                }
            });
        } catch (Exception e) {
            com.fongmi.android.tv.utils.Notify.show("恢复失败：" + e.getMessage());
        }
    }


    private void showBuildInfo() {
        int sites = 0;
        int lives = 0;
        try { sites = com.fongmi.android.tv.api.config.VodConfig.get().getSites().size(); } catch (Exception ignored) {}
        try { lives = com.fongmi.android.tv.api.config.LiveConfig.get().getLives() == null ? 0 : com.fongmi.android.tv.api.config.LiveConfig.get().getLives().size(); } catch (Exception ignored) {}
        String info = "版本：" + BuildConfig.VERSION_NAME + "\n构建号：" + BuildConfig.VERSION_CODE
                + "\n渠道：" + BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi
                + "\n影视站点：" + sites + " 个\n直播源：" + lives + " 个"
                + "\n收藏：" + com.fongmi.android.tv.bean.Keep.getVod().size() + " 部影视 / " + com.fongmi.android.tv.bean.Keep.getLive().size() + " 个频道"
                + "\n仓库：github.com/luanmuc/beacon"
                + "\n\n【设备兼容性】\n" + com.fongmi.android.tv.utils.Util.getDeviceReport()
                + "\n豆包定制 · 基于 GPL-3.0 开源项目二次开发\n长按消息可复制全部信息";
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("构建与设备信息")
                .setMessage(info)
                .setPositiveButton("复制兼容信息", (d, w) -> { com.fongmi.android.tv.utils.Util.copy(info); com.fongmi.android.tv.utils.Notify.show("已复制设备信息"); })
                .setNeutralButton("打开项目主页", (d, w) -> openRepo())
                .setNegativeButton("关闭", null)
                .create();
        dialog.setOnShowListener(d -> {
            android.widget.TextView message = dialog.findViewById(android.R.id.message);
            if (message != null) message.setOnLongClickListener(v -> { com.fongmi.android.tv.utils.Util.copy(info); com.fongmi.android.tv.utils.Notify.show("已复制设备信息"); return true; });
        });
        dialog.show();
    }


    private void showSiteList() {
        try {
            final java.util.List<com.fongmi.android.tv.bean.Site> sites = com.fongmi.android.tv.api.config.VodConfig.get().getSites();
            if (sites == null || sites.isEmpty()) {
                com.fongmi.android.tv.utils.Notify.show("当前没有可用站点");
                return;
            }
            com.fongmi.android.tv.utils.Task.execute(() -> {
                final java.util.Map<String, Integer> scores = new java.util.HashMap<>();
                final java.util.Map<String, String> states = new java.util.HashMap<>();
                int ok = 0, skip = 0, dead = 0, avg = 0;
                try {
                    String text = com.github.catvod.net.OkHttp.string("https://raw.githubusercontent.com/luanmuc/beacon-seed/stable/manifest.json");
                    org.json.JSONObject o = new org.json.JSONObject(text);
                    ok = o.optInt("vod_ok");
                    skip = o.optInt("vod_skip");
                    dead = o.optInt("vod_dead");
                    avg = o.optInt("vod_avg_ms");
                    org.json.JSONArray health = o.optJSONArray("vod_health");
                    if (health != null) for (int i = 0; i < health.length(); i++) {
                        org.json.JSONObject h = health.optJSONObject(i);
                        if (h == null) continue;
                        String name = h.optString("name");
                        scores.put(name, h.optInt("score"));
                        states.put(name, h.optString("state"));
                    }
                } catch (Exception ignored) {
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < sites.size(); i++) {
                    String name = sites.get(i).getName();
                    sb.append(i + 1).append(". ").append(name);
                    Integer sc = scores.get(name);
                    if (sc != null) {
                        String st = states.get(name);
                        sb.append(" · ").append(sc).append("分");
                        if ("dead".equals(st)) sb.append("（失效）");
                        else if ("skip".equals(st)) sb.append("（未探测）");
                    }
                    sb.append("\n");
                }
                String msg = sb.toString().trim();
                String title = "站点列表（" + sites.size() + "）";
                if (ok + skip + dead > 0) title += " · 可用 " + ok + " / 跳过 " + skip + " / 失效 " + dead;
                if (avg > 0) title += " · 平均 " + avg + "ms";
                final String title2 = title;
                com.fongmi.android.tv.App.post(() -> new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(title2)
                        .setMessage(msg)
                        .setPositiveButton("复制", (d, w) -> { com.fongmi.android.tv.utils.Util.copy(msg); com.fongmi.android.tv.utils.Notify.show("已复制站点列表"); })
                        .setNegativeButton("关闭", null)
                        .show());
            });
        } catch (Exception e) {
            com.fongmi.android.tv.utils.Notify.show("读取站点失败");
        }
    }



    private void showSiteDetailList() {
        try {
            final java.util.List<com.fongmi.android.tv.bean.Site> sites = com.fongmi.android.tv.api.config.VodConfig.get().getSites();
            if (sites == null || sites.isEmpty()) { com.fongmi.android.tv.utils.Notify.show("当前没有可用站点"); return; }
            String[] names = new String[sites.size()];
            for (int i = 0; i < sites.size(); i++) names[i] = (i + 1) + ". " + sites.get(i).getName();
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("站点详情")
                    .setItems(names, (d, which) -> showSiteDetail(sites.get(which)))
                    .setNegativeButton("关闭", null)
                    .show();
        } catch (Exception e) {
            com.fongmi.android.tv.utils.Notify.show("读取站点失败");
        }
    }

    private void showSiteDetail(com.fongmi.android.tv.bean.Site site) {
        String text = "名称：" + site.getName()
                + "\n标识：" + site.getKey()
                + "\n接口：" + site.getApi()
                + "\n搜索：" + (site.isSearchable() ? "支持" : "不支持")
                + "\n类型：" + site.getType();
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(site.getName())
                .setMessage(text)
                .setPositiveButton("复制接口", (d, w) -> { com.fongmi.android.tv.utils.Util.copy(site.getApi()); com.fongmi.android.tv.utils.Notify.show("已复制接口地址"); })
                .setNegativeButton("关闭", null)
                .show();
    }


    private void showPlayerMenu() {
        String[] items = {"手势与按键说明", "应用推荐播放配置", "局域网访问：" + (com.fongmi.android.tv.setting.Setting.getLan() ? "已开启" : "已关闭"), "配对手机遥控"};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("播放器设置")
                .setItems(items, (d, which) -> {
                    if (which == 0) showGestureHelp();
                    else if (which == 2) toggleLan();
                    else if (which == 3) showPairing();
                    else applyRecommended();
                })
                .show();
    }


    private void showPairing() {
        boolean required = com.fongmi.android.tv.setting.Setting.getPairRequired();
        String code = com.fongmi.android.tv.setting.Setting.newPairCode();
        String text = "配对码：" + code + "\n5 分钟内有效，过期可刷新。\n\n手机端：投屏设备列表长按电视 → 配对电视 → 输入此码。\n配对一次后长期记住，不用每次连。\n\n当前状态：" + (required ? "只允许已配对设备" : "允许未配对设备（更方便）");
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("配对手机遥控")
                .setMessage(text)
                .setPositiveButton("刷新配对码", (d, w) -> showPairing())
                .setNeutralButton(required ? "允许未配对" : "只允许已配对", (d, w) -> {
                    com.fongmi.android.tv.setting.Setting.putPairRequired(!required);
                    com.fongmi.android.tv.utils.Notify.show(!required ? "已开启：手机需配对后才能投屏/遥控" : "已关闭：允许未配对设备投屏");
                    showPairing();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void toggleLan() {
        boolean lan = !com.fongmi.android.tv.setting.Setting.getLan();
        com.fongmi.android.tv.setting.Setting.putLan(lan);
        com.fongmi.android.tv.server.Server.get().stop();
        com.fongmi.android.tv.server.Server.get().start();
        com.fongmi.android.tv.utils.Notify.show(lan ? "已开启局域网访问：手机可扫码推送/投屏（同一 WiFi）" : "已关闭局域网访问：仅本机访问");
    }


    private void applyRecommended() {
        try {
            com.fongmi.android.tv.setting.PlayerSetting.putSpeed(1.0f);
            com.fongmi.android.tv.setting.PlayerSetting.putTunnel(false);
            com.fongmi.android.tv.setting.Setting.putIncognito(false);
            com.fongmi.android.tv.utils.Notify.show("已应用推荐播放配置：常规速度、关闭隧道、关闭无痕");
        } catch (Exception e) {
            com.fongmi.android.tv.utils.Notify.show("应用失败：" + e.getMessage());
        }
    }

    private void showGestureHelp() {
        String text = "手机端：\n• 左右滑动：快进 / 快退\n• 上下滑动：调节亮度（左半屏）/ 音量（右半屏）\n• 双击：暂停 / 继续\n• 长按倍速键：选择 0.25x–4.0x\n• 长按信息键：复制当前播放地址\n• 长按剧名：切换站点\n\n电视端：\n• 左右键：快退 / 快进 10 秒\n• 上下键：呼出 / 收起控制栏\n• OK 键：暂停 / 继续，长按呼出菜单";
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("手势与按键说明")
                .setMessage(text)
                .setPositiveButton("知道了", null)
                .show();
    }


    private void showLiveList() {
        try {
            java.util.List<com.fongmi.android.tv.bean.Live> lives = com.fongmi.android.tv.api.config.LiveConfig.get().getLives();
            if (lives == null || lives.isEmpty()) { com.fongmi.android.tv.utils.Notify.show("当前没有直播源"); return; }
            String[] names = new String[lives.size()];
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lives.size(); i++) {
                boolean cur = lives.get(i).isActivated();
                names[i] = (cur ? "✓ " : "") + lives.get(i).getName() + "（" + (lives.get(i).getGroups() == null ? 0 : lives.get(i).getGroups().size()) + " 分组）";
                sb.append(i + 1).append(". ").append(names[i]).append("\n");
            }
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("直播源列表（" + lives.size() + "，点击切换）")
                    .setItems(names, (d, which) -> switchLiveSource(lives.get(which)))
                    .setPositiveButton("复制列表", (d, w) -> { com.fongmi.android.tv.utils.Util.copy(sb.toString().trim()); com.fongmi.android.tv.utils.Notify.show("已复制直播源列表"); })
                    .setNegativeButton("关闭", null)
                    .show();
        } catch (Exception e) { com.fongmi.android.tv.utils.Notify.show("读取直播源失败"); }
    }


    private void showCacheMenu() {
        String[] items = {"打开应用详情", "清理缓存", "关于应用"};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("应用")
                .setItems(items, (d, which) -> {
                    if (which == 0) openAppSettings();
                    else if (which == 1) onCache(null);
                    else showAbout();
                })
                .show();
    }

    private void showAbout() {
        String text = "版本：" + BuildConfig.VERSION_NAME + "（#" + BuildConfig.VERSION_CODE + "）\n"
                + "渠道：" + BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi + "\n\n"
                + "豆包定制，基于 GPL-3.0 开源电视播放器基座二次开发，遵循 GPL-3.0。\n"
                + "影视源来自公开 TVBox 配置聚合，直播源来自公开 iptv-org 清单，均已探测筛选。\n"
                + "本项目仅供学习与技术交流，内容版权归各源站所有。\n\n"
                + "维护仓库：\n"
                + "壳：github.com/luanmuc/beacon\n"
                + "源工厂：github.com/luanmuc/beacon-feeds\n"
                + "源发布：github.com/luanmuc/beacon-seed\n"
                + "试验壳：github.com/luanmuc/beacon-sketch\n"
                + "播放内核：github.com/luanmuc/media";
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("关于应用")
                .setMessage(text)
                .setPositiveButton("复制", (d, w) -> { com.fongmi.android.tv.utils.Util.copy(text); com.fongmi.android.tv.utils.Notify.show("已复制"); })
                .setNeutralButton("给电视安装", (d, w) -> showTvInstall())
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showTvInstall() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (20 * requireContext().getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);
        android.widget.TextView tip = new android.widget.TextView(requireContext());
        tip.setText("电视先打开开发者选项和 ADB 调试。\n无线调试：填电视显示的配对端口和 6 位配对码；\n老盒子：只填 IP，默认连 5555。");
        tip.setTextSize(13);
        layout.addView(tip);
        final android.widget.EditText ip = new android.widget.EditText(requireContext());
        ip.setHint("电视 IP，例如 192.168.1.50");
        ip.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        layout.addView(ip);
        final android.widget.EditText port = new android.widget.EditText(requireContext());
        port.setHint("配对端口（无线调试填，老盒子留空）");
        port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(port);
        final android.widget.EditText code = new android.widget.EditText(requireContext());
        code.setHint("6 位配对码（无线调试填）");
        code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(code);
        final android.widget.CheckBox old = new android.widget.CheckBox(requireContext());
        old.setText("电视是 32 位老盒子（armeabi-v7a）");
        layout.addView(old);
        final android.widget.TextView status = new android.widget.TextView(requireContext());
        status.setText("等待开始");
        status.setTextSize(13);
        layout.addView(status);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("给电视安装")
                .setView(layout)
                .setPositiveButton("开始安装", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> {
            android.widget.Button button = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(v -> {
                String host = ip.getText().toString().trim();
                if (host.isEmpty()) {
                    com.fongmi.android.tv.utils.Notify.show("请先填电视 IP");
                    return;
                }
                int pairPort = 0;
                try {
                    if (!port.getText().toString().trim().isEmpty()) pairPort = Integer.parseInt(port.getText().toString().trim());
                } catch (Exception e) {
                    com.fongmi.android.tv.utils.Notify.show("配对端口格式不对");
                    return;
                }
                String pairCode = code.getText().toString().trim();
                if (!pairCode.isEmpty() && pairPort <= 0) {
                    com.fongmi.android.tv.utils.Notify.show("填了配对码就要填配对端口");
                    return;
                }
                button.setEnabled(false);
                status.setText("开始…");
                com.fongmi.android.tv.utils.TvAdbInstaller.install(requireContext(), host, pairPort, pairCode, old.isChecked(), new com.fongmi.android.tv.utils.TvAdbInstaller.Listener() {
                    @Override
                    public void onStatus(String value) {
                        status.setText(value);
                    }

                    @Override
                    public void onDone(boolean success, String message) {
                        status.setText(message);
                        button.setEnabled(true);
                        com.fongmi.android.tv.utils.Notify.show(message);
                    }
                });
            });
        });
        dialog.show();
    }


    private void switchLiveSource(com.fongmi.android.tv.bean.Live live) {
        try {
            com.fongmi.android.tv.api.config.LiveConfig.get().setHome(live);
            com.fongmi.android.tv.utils.Notify.show("已切换直播源：" + live.getName() + "，返回直播页生效");
        } catch (Exception e) {
            com.fongmi.android.tv.utils.Notify.show("切换失败：" + e.getMessage());
        }
    }


    private void checkManifest() {
        com.fongmi.android.tv.utils.Task.execute(() -> {
            try {
                String text = com.github.catvod.net.OkHttp.string("https://raw.githubusercontent.com/luanmuc/beacon-seed/stable/manifest.json");
                org.json.JSONObject o = new org.json.JSONObject(text);
                String msg = "源版本：" + o.optString("version", "未知")
                        + "\n更新时间：" + o.optString("updated_at", "未知").replace("T", " ").replace("Z", "")
                        + "\n影视站点：" + o.optInt("vod_sites") + " 个"
                        + "\n站点健康：可用 " + o.optInt("vod_ok") + " / 跳过 " + o.optInt("vod_skip") + " / 失效 " + o.optInt("vod_dead")
                        + (o.optInt("vod_avg_ms") > 0 ? "\n平均延迟：" + o.optInt("vod_avg_ms") + "ms" : "")
                        + "\n直播频道：" + o.optInt("live_channels") + " 个 / 线路 " + o.optInt("live_lines") + " 条";
                com.fongmi.android.tv.App.post(() -> new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("源清单")
                        .setMessage(msg)
                        .setPositiveButton("复制", (d, w) -> { com.fongmi.android.tv.utils.Util.copy(msg); com.fongmi.android.tv.utils.Notify.show("已复制"); })
                        .setNegativeButton("关闭", null)
                        .show());
            } catch (Exception e) {
                com.fongmi.android.tv.App.post(() -> com.fongmi.android.tv.utils.Notify.show("检查源清单失败：" + e.getMessage()));
            }
        });
    }


}
