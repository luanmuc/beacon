package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.DialogDeviceBinding;
import com.fongmi.android.tv.dlna.DLNACast;
import com.fongmi.android.tv.dlna.DLNACastManager;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.activity.ScanActivity;
import com.fongmi.android.tv.ui.adapter.DeviceAdapter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ScanTask;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Util;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class CastDialog extends BaseBottomSheetDialog implements DeviceAdapter.OnClickListener, ScanTask.Listener, DLNACastManager.DeviceListener, Callback {

    private final FormBody.Builder body;
    private final OkHttpClient client;

    private DialogDeviceBinding binding;
    private DeviceAdapter adapter;
    private ScanTask scanTask;
    private Listener listener;
    private CastVideo video;
    private boolean fm;

    public static CastDialog create() {
        return new CastDialog();
    }

    public CastDialog() {
        scanTask = new ScanTask(this);
        body = new FormBody.Builder();
        body.add("device", Device.get().toString());
        body.add("config", Config.vod().toString());
        client = OkHttp.client(Constant.TIMEOUT_SYNC);
    }

    public CastDialog history(History history) {
        String id = history.getVodId();
        String fd = history.getVodId();
        if (fd.startsWith("/")) fd = Server.get().getAddress() + "/file" + fd.replace(Path.rootPath(), "");
        if (fd.startsWith("file")) fd = Server.get().getAddress() + "/" + fd.replace(Path.rootPath(), "").replace("://", "");
        if (fd.contains("127.0.0.1")) fd = fd.replace("127.0.0.1", Util.getIp());
        body.add("history", history.toString().replace(id, fd));
        return this;
    }

    public CastDialog video(CastVideo video) {
        this.video = video;
        return this;
    }

    public CastDialog fm(boolean fm) {
        this.fm = fm;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof CastDialog) return;
        show(activity.getSupportFragmentManager(), null);
        this.listener = (Listener) activity;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDeviceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.scan.setVisibility(fm ? View.VISIBLE : View.GONE);
        DLNACastManager.get().init(requireActivity());
        DLNACastManager.get().setDeviceListener(this);
        setRecyclerView();
        getDevice();
    }

    @Override
    protected void initEvent() {
        binding.scan.setOnClickListener(v -> onScan());
        binding.refresh.setOnClickListener(v -> onRefresh());
    }

    private void setRecyclerView() {
        binding.recycler.setHasFixedSize(false);
        binding.recycler.setAdapter(adapter = new DeviceAdapter(this));
    }

    private void getDevice() {
        adapter.setItems(Device.getAll(), () -> {
            adapter.sort(DLNACastManager.get().getRegistered());
            if (adapter.getItemCount() == 0) onRefresh();
            else DLNACastManager.get().search();
        });
    }

    private void onScan() {
        launcher.launch(new Intent(requireActivity(), ScanActivity.class));
    }

    private void onRefresh() {
        adapter.clear(() -> {
            Device.delete();
            if (fm) scanTask.start();
            adapter.sort(DLNACastManager.get().getRegistered());
            DLNACastManager.get().search();
        });
    }

    private void onCasted() {
        listener.onCasted();
        dismiss();
    }

    @Override
    public void onDeviceAdded(Device device) {
        adapter.sort(device);
    }

    @Override
    public void onDeviceRemoved(Device device) {
        adapter.remove(device);
    }

    @Override
    public void onFind(Device device) {
        adapter.sort(device);
    }

    @Override
    public void onFailure(@NonNull Call call, @NonNull IOException e) {
        App.post(() -> Notify.show(e.getMessage()));
    }

    @Override
    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
        try (Response res = response) {
            String text = res.body().string();
            if (text.equals("OK")) App.post(this::onCasted);
            else if (text.contains("PAIR_REQUIRED")) App.post(() -> Notify.show("电视要求配对：长按设备，选择“配对电视”"));
            else App.post(() -> Notify.show(R.string.device_offline));
        }
    }

    @Override
    public void onItemClick(Device item) {
        if (item.isDLNA()) {
            new DLNACast(video, this::onCasted).cast(item);
            return;
        }
        String url = item.getIp().concat("/action?do=cast");
        String token = token(item);
        if (!TextUtils.isEmpty(token)) url += "&token=" + token;
        OkHttp.newCall(client, url, body.build()).enqueue(this);
    }

    @Override
    public boolean onLongClick(Device item) {
        if (!item.isApp()) return false;
        String[] items = {"配对电视（输入电视上的 6 位码）", "遥控：播放", "遥控：暂停", "遥控：上一集", "遥控：下一集", "遥控：停止"};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(item.getName())
                .setItems(items, (d, which) -> {
                    if (which == 0) pairDialog(item);
                    else if (which == 1) control(item, "play");
                    else if (which == 2) control(item, "pause");
                    else if (which == 3) control(item, "prev");
                    else if (which == 4) control(item, "next");
                    else control(item, "stop");
                })
                .show();
        return true;
    }

    private String token(Device item) {
        String key = TextUtils.isEmpty(item.getUuid()) ? item.getIp() : item.getUuid();
        return Prefers.getString("cast_token_" + key);
    }

    private void pairDialog(Device item) {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("电视上显示的 6 位配对码");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("配对 " + item.getName())
                .setMessage("电视端：设置 → 播放器设置长按 → 配对手机遥控，获取 6 位码。")
                .setView(input)
                .setPositiveButton("配对", (d, w) -> requestPair(item, input.getText().toString().trim()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void requestPair(Device item, String code) {
        if (code.length() != 6) {
            Notify.show("请输入 6 位配对码");
            return;
        }
        String url = item.getIp().concat("/action?do=pair&code=" + code);
        client.newCall(new okhttp3.Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                App.post(() -> Notify.show("配对失败：" + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response res = response) {
                    String text = res.body().string().trim();
                    if (text.equals("PAIR_FAILED")) {
                        App.post(() -> Notify.show("配对码错误或已过期，请在电视上刷新"));
                    } else if (!text.isEmpty() && !text.contains("PAIR_REQUIRED")) {
                        String key = TextUtils.isEmpty(item.getUuid()) ? item.getIp() : item.getUuid();
                        Prefers.put("cast_token_" + key, text);
                        App.post(() -> Notify.show("配对成功，已记住电视，不用再配对"));
                    } else {
                        App.post(() -> Notify.show("配对失败：" + text));
                    }
                }
            }
        });
    }

    private void control(Device item, String type) {
        String token = token(item);
        if (TextUtils.isEmpty(token)) {
            Notify.show("请先长按电视，选择“配对电视”");
            return;
        }
        String url = item.getIp().concat("/action?do=control&type=" + type + "&token=" + token);
        client.newCall(new okhttp3.Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                App.post(() -> Notify.show("控制失败：" + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response res = response) {
                    if (!res.isSuccessful()) App.post(() -> Notify.show("电视拒绝了控制，请重新配对"));
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        DLNACastManager.get().setDeviceListener(null);
        DLNACastManager.get().release(requireActivity());
        scanTask.stop();
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) scanTask.start(result.getData().getStringExtra("address"));
    });

    public interface Listener {

        void onCasted();
    }
}
