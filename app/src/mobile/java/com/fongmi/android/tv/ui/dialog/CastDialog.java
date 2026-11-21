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
        String[] items = {"配对电视（输入电视上的 6 位码）", "遥控面板"};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(item.getName())
                .setItems(items, (d, which) -> {
                    if (which == 0) pairDialog(item);
                    else showRemote(item);
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

    private void showRemote(Device item) {
        if (TextUtils.isEmpty(token(item))) {
            Notify.show("请先长按电视，选择“配对电视”");
            return;
        }
        android.widget.LinearLayout root = new android.widget.LinearLayout(requireContext());
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (18 * requireContext().getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, 0);
        android.widget.TextView state = new android.widget.TextView(requireContext());
        state.setText("正在读取电视状态…");
        state.setTextSize(14);
        root.addView(state);
        android.widget.LinearLayout row1 = row();
        row1.addView(button("快退 10s", v -> control(item, "seek", -10000, () -> refreshState(item, state)));
        row1.addView(button("播放/暂停", v -> control(item, "play_pause", 0, () -> refreshState(item, state)));
        row1.addView(button("快进 10s", v -> control(item, "seek", 10000, () -> refreshState(item, state)));
        root.addView(row1);
        android.widget.LinearLayout row2 = row();
        row2.addView(button("上一集", v -> control(item, "prev", 0, () -> refreshState(item, state)));
        row2.addView(button("停止", v -> control(item, "stop", 0, () -> refreshState(item, state)));
        row2.addView(button("下一集", v -> control(item, "next", 0, () -> refreshState(item, state)));
        root.addView(row2);
        android.widget.LinearLayout row3 = row();
        row3.addView(button("音量 -", v -> control(item, "volume", -1, () -> refreshState(item, state)));
        row3.addView(button("刷新状态", v -> refreshState(item, state)));
        row3.addView(button("音量 +", v -> control(item, "volume", 1, () -> refreshState(item, state)));
        root.addView(row3);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(item.getName() + " 遥控")
                .setView(root)
                .setNegativeButton("关闭", null)
                .create();
        dialog.show();
        refreshState(item, state);
    }

    private android.widget.LinearLayout row() {
        android.widget.LinearLayout row = new android.widget.LinearLayout(requireContext());
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private android.widget.Button button(String text, android.view.View.OnClickListener listener) {
        android.widget.Button button = new android.widget.Button(requireContext());
        button.setText(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        button.setLayoutParams(params);
        return button;
    }

    private void refreshState(Device item, android.widget.TextView view) {
        String token = token(item);
        if (TextUtils.isEmpty(token)) {
            view.setText("未配对：请长按电视，选择“配对电视”");
            return;
        }
        String url = item.getIp().concat("/action?do=state&token=" + token);
        client.newCall(new okhttp3.Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                App.post(() -> view.setText("读取失败：" + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response res = response) {
                    String text = res.body().string();
                    if (!res.isSuccessful()) {
                        App.post(() -> view.setText(text.contains("PAIR_REQUIRED") ? "电视要求重新配对" : "电视拒绝了请求"));
                        return;
                    }
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(text);
                        String title = json.optString("title", "");
                        boolean playing = json.optBoolean("playing", false);
                        long position = json.optLong("position", 0);
                        long duration = json.optLong("duration", 0);
                        int volume = json.optInt("volume", 0);
                        int volumeMax = json.optInt("volumeMax", 0);
                        String line = title.isEmpty() ? "电视当前没有播放内容" : title;
                        String info = (playing ? "播放中" : "已暂停") + "  " + time(position) + " / " + time(duration);
                        String vol = volumeMax > 0 ? "\n音量 " + volume + "/" + volumeMax : "";
                        App.post(() -> view.setText(line + "\n" + info + vol));
                    } catch (Exception e) {
                        App.post(() -> view.setText("状态解析失败"));
                    }
                }
            }
        });
    }

    private String time(long ms) {
        if (ms <= 0) return "--:--";
        long total = ms / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0 ? String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s) : String.format(java.util.Locale.US, "%02d:%02d", m, s);
    }

    private void control(Device item, String type, long delta, Runnable after) {
        String token = token(item);
        if (TextUtils.isEmpty(token)) {
            Notify.show("请先长按电视，选择“配对电视”");
            return;
        }
        String url = item.getIp().concat("/action?do=control&type=" + type);
        if (delta != 0) url += "&delta=" + delta;
        url += "&token=" + token;
        client.newCall(new okhttp3.Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                App.post(() -> Notify.show("控制失败：" + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response res = response) {
                    if (!res.isSuccessful()) App.post(() -> Notify.show("电视拒绝了控制，请重新配对"));
                    else if (after != null) App.post(after);
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
