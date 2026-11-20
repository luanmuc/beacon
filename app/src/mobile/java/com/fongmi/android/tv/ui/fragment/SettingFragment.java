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
        mBinding.cache.setOnLongClickListener(v -> { openAppSettings(); return true; });
        mBinding.cache.setOnClickListener(this::onCache);
        mBinding.backup.setOnClickListener(this::onBackup);
        mBinding.player.setOnClickListener(this::onPlayer);
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
        com.fongmi.android.tv.utils.Task.execute(() -> {
            try {
                String text = com.github.catvod.net.OkHttp.string("https://raw.githubusercontent.com/luanmuc/beacon-seed/main/live-report.json");
                org.json.JSONObject object = new org.json.JSONObject(text);
                int channels = object.optInt("channels");
                int lines = object.optInt("lines");
                com.fongmi.android.tv.App.post(() -> {
                    try {
                        CharSequence base = mBinding.liveUrl.getText();
                        String at = object.optString("generated_at", "");
                        mBinding.liveUrl.setText(base + " · 存活 " + channels + " 频道/" + lines + " 线路" + (at.isEmpty() ? "" : " · " + at.replace("T", " ").replace("Z", "")));
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
        String[] items = {"查看存活统计", "打开 Actions 探测页", "恢复默认直播订阅"};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("直播源维护")
                .setItems(items, (d, which) -> {
                    if (which == 0) showHealth();
                    else if (which == 1) openActions();
                    else restoreDefaultLive();
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
        String[] items = {"复制订阅地址", "恢复默认影视订阅"};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("影视订阅")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        com.fongmi.android.tv.utils.Util.copy(com.fongmi.android.tv.api.config.VodConfig.getDesc());
                        com.fongmi.android.tv.utils.Notify.show("已复制影视订阅地址");
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
        String info = "版本：" + BuildConfig.VERSION_NAME + "\n构建号：" + BuildConfig.VERSION_CODE
                + "\n渠道：" + BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi
                + "\n仓库：github.com/luanmuc/beacon";
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("构建信息")
                .setMessage(info)
                .setPositiveButton("打开项目主页", (d, w) -> openRepo())
                .setNeutralButton("检查更新", (d, w) -> com.fongmi.android.tv.utils.UpdateChecker.check(requireActivity()))
                .setNegativeButton("关闭", null)
                .show();
    }


}
