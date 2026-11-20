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
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.ActivityHistoryBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.ui.adapter.HistoryAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.SyncDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class HistoryActivity extends BaseActivity implements HistoryAdapter.OnClickListener {

    private ActivityHistoryBinding mBinding;
    private HistoryAdapter mAdapter;
    private String filterSite;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, HistoryActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHistoryBinding.inflate(getLayoutInflater());
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
        getHistory();
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn(this)));
        mBinding.recycler.setAdapter(mAdapter = new HistoryAdapter(this));
        mAdapter.setSize(Product.getSpec(this));
    }

    private void getHistory() {
        mAdapter.setItems(History.get(), (hasChange) -> {
            mBinding.progressLayout.showContent(true, mAdapter.getItemCount());
            if (hasChange) mBinding.recycler.scrollToPosition(0);
        });
    }

    private void onSync() {
        SyncDialog.create().history().show(this);
    }

    private void onDelete() {
        if (mAdapter.isDelete()) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.dialog_delete_record).setMessage(R.string.dialog_delete_history).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> mAdapter.clear()).show();
        } else if (mAdapter.getItemCount() > 0) {
            mAdapter.setDelete(true);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType().equals(RefreshEvent.Type.HISTORY)) getHistory();
    }

    @Override
    public void onItemClick(History item) {
        VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(History item) {
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
        getMenuInflater().inflate(R.menu.menu_history, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) onBackInvoked();
        else if (item.getItemId() == R.id.delete) onDelete();
        else if (item.getItemId() == R.id.filter_site_h) showSiteFilter();
        else if (item.getItemId() == R.id.export_history) {
            java.util.List<com.fongmi.android.tv.bean.History> all = com.fongmi.android.tv.bean.History.get();
            if (all.isEmpty()) { com.fongmi.android.tv.utils.Notify.show("还没有观看记录"); return true; }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < all.size(); i++) sb.append(i + 1).append(". ").append(all.get(i).getVodName()).append(" - ").append(all.get(i).getSiteName()).append("\n");
            String msg = sb.toString().trim();
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("导出观看记录（" + all.size() + "）").setMessage(msg.length() > 1200 ? msg.substring(0, 1200) + "…" : msg).setPositiveButton("复制", (d, w) -> { com.fongmi.android.tv.utils.Util.copy(msg); com.fongmi.android.tv.utils.Notify.show("已复制观看记录"); }).setNegativeButton("关闭", null).show();
        }

        else if (item.getItemId() == R.id.sync) onSync();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onBackInvoked() {
        if (filterSite != null) {
            filterSite = null;
            try { if (getSupportActionBar() != null) getSupportActionBar().setSubtitle(null); } catch (Exception ignored) {}
            getHistory();
        } else if (mAdapter.isDelete()) mAdapter.setDelete(false);
        else super.onBackInvoked();
    }
    private void showSiteFilter() {
        try {
            java.util.List<com.fongmi.android.tv.bean.History> all = com.fongmi.android.tv.bean.History.get();
            java.util.LinkedHashSet<String> sites = new java.util.LinkedHashSet<>();
            for (com.fongmi.android.tv.bean.History item : all) if (item.getSiteName() != null && !item.getSiteName().isEmpty()) sites.add(item.getSiteName());
            if (sites.isEmpty()) { com.fongmi.android.tv.utils.Notify.show("还没有观看记录"); return; }
            java.util.List<String> names = new java.util.ArrayList<>();
            names.add("全部站点");
            names.addAll(sites);
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("按站点筛选")
                    .setItems(names.toArray(new String[0]), (d, which) -> {
                        if (which == 0) {                         filterSite = null;
                        try { if (getSupportActionBar() != null) getSupportActionBar().setSubtitle(null); } catch (Exception ignored) {} getHistory(); com.fongmi.android.tv.utils.Notify.show("已显示全部记录"); return; }
                        String site = names.get(which);
                        java.util.List<com.fongmi.android.tv.bean.History> filtered = new java.util.ArrayList<>();
                        for (com.fongmi.android.tv.bean.History item : all) if (site.equals(item.getSiteName())) filtered.add(item);
                        filterSite = site;
                        try { if (getSupportActionBar() != null) getSupportActionBar().setSubtitle("筛选：" + site); } catch (Exception ignored) {}
                        mAdapter.setItems(filtered, () -> mBinding.progressLayout.showContent(true, mAdapter.getItemCount()));
                        com.fongmi.android.tv.utils.Notify.show("仅显示：" + site + "（" + filtered.size() + "）");
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) { com.fongmi.android.tv.utils.Notify.show("筛选失败：" + e.getMessage()); }
    }


}
