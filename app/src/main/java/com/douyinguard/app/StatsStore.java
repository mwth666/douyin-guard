package com.douyinguard.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 本地存储：配置 + 今日统计 + 运行态。
 * 全部使用 SharedPreferences，不依赖网络、不上传任何数据。
 */
public final class StatsStore {

    private static final String PREF = "douyin_guard_prefs";

    private StatsStore() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 今天的日期键，例如 20261119 */
    public static String dayKey() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    /** 相对今天偏移 offsetDays 天的日期键（-1 = 昨天） */
    public static String dayKeyOffset(int offsetDays) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, offsetDays);
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.getTime());
    }

    /** 读取某一天的累计观看毫秒数 */
    public static long dayMs(Context c, String key) {
        return sp(c).getLong("d_" + key + "_sec", 0L);
    }

    /** 日期键 → 短标签：昨天之前显示"一二三…日"，最后一天显示"今天" */
    public static String shortDayLabel(String key, boolean isToday) {
        if (isToday) {
            return "今天";
        }
        try {
            Date d = new SimpleDateFormat("yyyyMMdd", Locale.US).parse(key);
            String w = new SimpleDateFormat("E", Locale.CHINA).format(d);
            return w.length() > 1 ? w.substring(w.length() - 1) : w;
        } catch (Exception e) {
            return "";
        }
    }

    /* ---------------- 配置 ---------------- */

    public static int getWatchMin(Context c) {
        return sp(c).getInt("cfg_watch_min", 10);
    }

    public static void setWatchMin(Context c, int v) {
        sp(c).edit().putInt("cfg_watch_min", v).apply();
    }

    public static int getLockMin(Context c) {
        return sp(c).getInt("cfg_lock_min", 10);
    }

    public static void setLockMin(Context c, int v) {
        sp(c).edit().putInt("cfg_lock_min", v).apply();
    }

    /* ---------------- 今日统计 ---------------- */

    private static String kSeconds() {
        return "d_" + dayKey() + "_sec";
    }

    private static String kLocked() {
        return "d_" + dayKey() + "_lock";
    }

    public static void addSecond(Context c, long ms) {
        SharedPreferences p = sp(c);
        p.edit().putLong(kSeconds(), p.getLong(kSeconds(), 0L) + ms).apply();
    }

    /**
     * 一次写入完成「今日时长累加 + 连刷进度 + 运行状态」。
     * 服务每秒 tick 一次，合并成单次 IO，避免反复落盘耗电。
     */
    public static void recordTick(Context c, long addMs, long sessionMs, String status) {
        SharedPreferences p = sp(c);
        String ks = kSeconds();
        p.edit()
                .putLong(ks, p.getLong(ks, 0L) + addMs)
                .putLong("rt_session_ms", sessionMs)
                .putLong("rt_session_at", System.currentTimeMillis())
                .putString("rt_status", status)
                .apply();
    }

    /** 到达上限被锁：冷却时间、锁定次数 +1、连刷进度归零，一次写完 */
    public static void recordLock(Context c, long cooldownUntil) {
        SharedPreferences p = sp(c);
        String kl = kLocked();
        p.edit()
                .putLong("rt_cooldown_until", cooldownUntil)
                .putInt(kl, p.getInt(kl, 0) + 1)
                .putLong("rt_session_ms", 0L)
                .putLong("rt_session_at", 0L)
                .putString("rt_status", "locked")
                .apply();
    }

    public static void addLocked(Context c) {
        SharedPreferences p = sp(c);
        p.edit().putInt(kLocked(), p.getInt(kLocked(), 0) + 1).apply();
    }

    public static long todayMs(Context c) {
        return sp(c).getLong(kSeconds(), 0L);
    }

    public static int todayLocked(Context c) {
        return sp(c).getInt(kLocked(), 0);
    }

    /* ---------------- 运行态 ---------------- */

    public static long getCooldownUntil(Context c) {
        return sp(c).getLong("rt_cooldown_until", 0L);
    }

    public static void setCooldownUntil(Context c, long v) {
        sp(c).edit().putLong("rt_cooldown_until", v).apply();
    }

    public static long getSessionMs(Context c) {
        SharedPreferences p = sp(c);
        long at = p.getLong("rt_session_at", 0L);
        // 超过 10 分钟没更新，说明是很久以前的残留（服务重启/关机），直接归零
        if (at > 0 && System.currentTimeMillis() - at > 10 * 60 * 1000L) {
            p.edit().putLong("rt_session_ms", 0L).putLong("rt_session_at", 0L).apply();
            return 0L;
        }
        return p.getLong("rt_session_ms", 0L);
    }

    public static void setSessionMs(Context c, long v) {
        sp(c).edit().putLong("rt_session_ms", v).apply();
    }

    public static String getStatus(Context c) {
        return sp(c).getString("rt_status", "idle");
    }

    public static void setStatus(Context c, String v) {
        sp(c).edit().putString("rt_status", v).apply();
    }
}
