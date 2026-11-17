package com.fongmi.android.tv.api;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Config;
import com.github.catvod.utils.Prefers;

/**
 * 首启种子：库为空时写入默认订阅链接（点播/直播共用同一 URL）。
 * 一旦用户自己换过配置，这里不再覆盖。
 */
public class SubDefaults {

    /** 固定订阅链接：源在云端自动维护，App 端只认这一条。自用时按需替换。 */
    public static final String URL = "https://raw.githubusercontent.com/luanmuc/tv-src/main/tvbox.json";
    private static final String NAME = "自订源";

    public static void apply() {
        try {
            Config vod = Config.vod();
            if (TextUtils.isEmpty(vod.getUrl())) {
                vod.setType(0);
                vod.setUrl(URL);
                vod.setName(NAME);
                vod.save();
            }
            Config live = Config.live();
            if (TextUtils.isEmpty(live.getUrl())) {
                live.setType(1);
                live.setUrl(URL);
                live.setName(NAME);
                live.save();
                Prefers.put("config_1", URL);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
