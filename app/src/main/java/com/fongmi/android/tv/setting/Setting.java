package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

public class Setting {

    public static String getDoh() {
        return Prefers.getString("doh");
    }

    public static void putDoh(String doh) {
        Prefers.put("doh", doh);
    }

    public static String getKeyword() {
        return Prefers.getString("keyword");
    }

    public static void putKeyword(String keyword) {
        Prefers.put("keyword", keyword);
    }

    public static String getHot() {
        return Prefers.getString("hot");
    }

    public static void putHot(String hot) {
        Prefers.put("hot", hot);
    }

    public static String getUa() {
        return Prefers.getString("ua");
    }

    public static void putUa(String ua) {
        Prefers.put("ua", ua);
    }

    public static int getWall() {
        return Prefers.getInt("wall", 1);
    }

    public static void putWall(int wall) {
        Prefers.put("wall", wall);
    }

    public static int getWallType() {
        return Prefers.getInt("wall_type", 0);
    }

    public static void putWallType(int type) {
        Prefers.put("wall_type", type);
    }

    public static int getReset() {
        return Prefers.getInt("reset", 0);
    }

    public static void putReset(int reset) {
        Prefers.put("reset", reset);
    }

    public static int getSiteMode() {
        return Prefers.getInt("site_mode");
    }

    public static void putSiteMode(int mode) {
        Prefers.put("site_mode", mode);
    }

    public static int getSyncMode() {
        return Prefers.getInt("sync_mode");
    }

    public static void putSyncMode(int mode) {
        Prefers.put("sync_mode", mode);
    }

    public static boolean isIncognito() {
        return Prefers.getBoolean("incognito");
    }

    public static void putIncognito(boolean incognito) {
        Prefers.put("incognito", incognito);
    }

    public static boolean getUpdate() {
        return Prefers.getBoolean("update", true);
    }

    public static void putUpdate(boolean update) {
        Prefers.put("update", update);
    }

    public static boolean isAdblock() {
        return Prefers.getBoolean("adblock", true);
    }

    public static void putAdblock(boolean adblock) {
        Prefers.put("adblock", adblock);
    }

    public static boolean isZhuyin() {
        return Prefers.getBoolean("zhuyin");
    }

    public static void putZhuyin(boolean zhuyin) {
        Prefers.put("zhuyin", zhuyin);
    }
    public static boolean getGuide() {
        return Prefers.getBoolean("guide_empty", false);
    }

    public static void putGuide(boolean guide) {
        Prefers.put("guide_empty", guide);
    }

    public static long getHealthTs() {
        return Prefers.getLong("health_ts", 0L);
    }

    public static void putHealthTs(long ts) {
        Prefers.put("health_ts", ts);
    }

    public static String getRecent() {
        return Prefers.getString("recent_channels");
    }

    public static void putRecent(String json) {
        Prefers.put("recent_channels", json);
    }


    public static String getSeedVer() {
        return com.github.catvod.utils.Prefers.getString("seed_ver");
    }

    public static void putSeedVer(String ver) {
        com.github.catvod.utils.Prefers.put("seed_ver", ver);
    }

    public static boolean getLan() {
        return com.github.catvod.utils.Prefers.getBoolean("lan_access", false);
    }

    public static void putLan(boolean lan) {
        com.github.catvod.utils.Prefers.put("lan_access", lan);
    }

    public static boolean getPairRequired() {
        return com.github.catvod.utils.Prefers.getBoolean("pair_required", false);
    }

    public static void putPairRequired(boolean required) {
        com.github.catvod.utils.Prefers.put("pair_required", required);
    }

    public static String getPairToken() {
        String token = com.github.catvod.utils.Prefers.getString("pair_token");
        if (token == null || token.isEmpty()) {
            token = java.util.UUID.randomUUID().toString().replace("-", "");
            com.github.catvod.utils.Prefers.put("pair_token", token);
        }
        return token;
    }

    public static String newPairCode() {
        String code = String.format(java.util.Locale.US, "%06d", new java.util.Random().nextInt(1000000));
        com.github.catvod.utils.Prefers.put("pair_code", code);
        com.github.catvod.utils.Prefers.put("pair_code_ts", System.currentTimeMillis());
        return code;
    }

    public static String getPairCode() {
        return com.github.catvod.utils.Prefers.getString("pair_code");
    }

    public static boolean verifyPairCode(String code) {
        if (code == null || code.isEmpty()) return false;
        String saved = getPairCode();
        long ts = com.github.catvod.utils.Prefers.getLong("pair_code_ts", 0L);
        return saved != null && saved.equals(code) && System.currentTimeMillis() - ts < 5 * 60 * 1000L;
    }

}
