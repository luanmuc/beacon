package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.databinding.ActivityKeepBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.adapter.KeepAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.SyncDialog;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class KeepActivity extends BaseActivity implements KeepAdapter.OnClickListener {

    private ActivityKeepBinding mBinding;
    private KeepAdapter mAdapter;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, KeepActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityKeepBinding.inflate(getLayoutInflater());
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setSupportActionBar(mBinding.toolbar);
        setRecyclerView();
        getKeep();
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn(this)));
        mBinding.recycler.setAdapter(mAdapter = new KeepAdapter(this));
        mAdapter.setSize(Product.getSpec(this));
    }

    private void getKeep() {
        mAdapter.setItems(Keep.getVod(), () -> mBinding.progressLayout.showContent(true, mAdapter.getItemCount()));
    }

    private void onSync() {
        SyncDialog.create().keep().show(this);
    }

    private void onDelete() {
        if (mAdapter.isDelete()) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.dialog_delete_record).setMessage(R.string.dialog_delete_keep).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> mAdapter.clear()).show();
        } else if (mAdapter.getItemCount() > 0) {
            mAdapter.setDelete(true);
        }
    }

    private void loadConfig(Config config, Keep item) {
        VodConfig.load(config, new Callback() {
            @Override
            public void success() {
                VideoActivity.start(getActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType().equals(RefreshEvent.Type.KEEP)) getKeep();
    }

    @Override
    public void onItemClick(Keep item) {
        Config config = Config.find(item.getCid());
        if (config == null) SearchActivity.start(this, item.getVodName());
        else if (item.getCid() != VodConfig.getCid()) loadConfig(config, item);
        else VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(Keep item) {
        mAdapter.remove(item.delete(), () -> {
            if (mAdapter.getItemCount() == 0) mAdapter.setDelete(false);
        });
    }

    @Override
    public boolean onLongClick() {
        mAdapter.setDelete(!mAdapter.isDelete());
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_keep, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) onBackInvoked();
        else if (item.getItemId() == R.id.delete) onDelete();
        else if (item.getItemId() == R.id.filter_site) { showSiteFilter(); }
        else if (item.getItemId() == R.id.live_keep) {
            java.util.List<com.fongmi.android.tv.bean.Keep> lives = com.fongmi.android.tv.bean.Keep.getLive();
            if (lives.isEmpty()) { com.fongmi.android.tv.utils.Notify.show("还没有直播收藏"); return true; }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lives.size(); i++) sb.append(i + 1).append(". ").append(lives.get(i).getVodName()).append("\n");
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("直播收藏（" + lives.size() + "）").setMessage(sb.toString().trim()).setPositiveButton("复制", (d, w) -> { com.fongmi.android.tv.utils.Util.copy(sb.toString().trim()); com.fongmi.android.tv.utils.Notify.show("已复制"); }).setNegativeButton("关闭", null).show();
        } else if (item.getItemId() == R.id.clear) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("清空收藏").setMessage("确定删除全部收藏频道与影视收藏？").setNegativeButton("取消", null).setPositiveButton("清空", (d, w) -> { com.fongmi.android.tv.bean.Keep.deleteAll(); getKeep(); com.fongmi.android.tv.utils.Notify.show("已清空收藏"); }).show();
        } else if (item.getItemId() == R.id.batch) { mAdapter.setDelete(!mAdapter.isDelete()); com.fongmi.android.tv.utils.Notify.show(mAdapter.isDelete() ? "批量管理：点击条目删除，返回键退出" : "已退出批量管理"); }
        else if (item.getItemId() == R.id.sync) onSync();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onBackInvoked() {
        if (mAdapter.isDelete()) mAdapter.setDelete(false);
        else super.onBackInvoked();
    }
    private void showSiteFilter() {
        try {
            java.util.List<com.fongmi.android.tv.bean.Keep> all = com.fongmi.android.tv.bean.Keep.getVod();
            java.util.LinkedHashSet<String> sites = new java.util.LinkedHashSet<>();
            for (com.fongmi.android.tv.bean.Keep keep : all) if (keep.getSiteName() != null && !keep.getSiteName().isEmpty()) sites.add(keep.getSiteName());
            if (sites.isEmpty()) { com.fongmi.android.tv.utils.Notify.show("还没有收藏内容"); return; }
            java.util.List<String> names = new java.util.ArrayList<>();
            names.add("全部站点");
            names.addAll(sites);
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("按站点筛选")
                    .setItems(names.toArray(new String[0]), (d, which) -> {
                        if (which == 0) { getKeep(); com.fongmi.android.tv.utils.Notify.show("已显示全部收藏"); return; }
                        String site = names.get(which);
                        java.util.List<com.fongmi.android.tv.bean.Keep> filtered = new java.util.ArrayList<>();
                        for (com.fongmi.android.tv.bean.Keep keep : all) if (site.equals(keep.getSiteName())) filtered.add(keep);
                        mAdapter.setItems(filtered, () -> mBinding.progressLayout.showContent(true, mAdapter.getItemCount()));
                        com.fongmi.android.tv.utils.Notify.show("仅显示：" + site + "（" + filtered.size() + "）");
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) { com.fongmi.android.tv.utils.Notify.show("筛选失败：" + e.getMessage()); }
    }


}
