package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.os.Bundle;

/**
 * 桌面快捷方式的中间入口。
 * 快捷方式必须由外部启动，所以这个空壳保持 exported；真正的 LiveActivity 改为非导出。
 */
public class LiveShortcutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LiveActivity.start(this);
        finish();
    }
}
