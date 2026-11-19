package com.douyinguard.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 核心管控服务。
 *
 * 工作方式：
 *  1. 每秒检查一次当前前台应用（读取根窗口包名）。
 *  2. 若前台是抖音：累计本次连刷时长、累加今日总时长。
 *  3. 达到设定时长 → 执行"返回桌面"把抖音切走，并写入冷却截止时间。
 *  4. 冷却期内只要抖音回到前台，立刻再次切走（等于打不开）。
 *  5. 本次离开抖音超过 5 分钟，视为休息够了，连刷计时归零。
 *
 * 不读取任何页面内容，只判断"当前是哪个应用"。
 */
public class DouyinGuardService extends AccessibilityService {

    /** 抖音主程序 + 极速版 */
    private static final Set<String> DOUYIN_PKGS = new HashSet<>(Arrays.asList(
            "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.aweme.lite"
    ));

    private static final long TICK_MS = 1000L;              // 每秒一次
    private static final long AWAY_RESET_MS = 5 * 60 * 1000L; // 离开 5 分钟重置连刷计时
    private static final long TOAST_GAP_MS = 2500L;          // 提示防刷屏

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager powerManager;

    private long sessionAccumMs = 0L;   // 本次连刷累计
    private long lastInDouyinMs = 0L;   // 最近一次检测到抖音在前台
    private long lastToastAt = 0L;
    private String lastStatus = "";     // 上次落盘的状态，用于避免重复写入

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            try {
                tick();
            } catch (Throwable t) {
                // 任何异常都不能让服务挂掉
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        // 恢复上次未结算的连刷进度（服务被系统重启时）
        sessionAccumMs = StatsStore.getSessionMs(this);
        lastStatus = StatsStore.getStatus(this);
        lastInDouyinMs = 0L;
        // 无障碍已连上，说明我们活着；顺手把常驻服务拉起来，别让系统把进程回收
        GuardForegroundService.start(this);
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkgCs = event.getPackageName();
            if (pkgCs != null && DOUYIN_PKGS.contains(pkgCs.toString())) {
                lastInDouyinMs = System.currentTimeMillis();
            }
        }
    }

    @Override
    public void onInterrupt() {
        // 无需处理
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    /** 每秒主逻辑 */
    private void tick() {
        // 屏幕关闭时不计时（避免息屏挂机刷时长）
        if (powerManager != null && !powerManager.isInteractive()) {
            return;
        }

        long now = System.currentTimeMillis();
        String fg = currentPackage();
        boolean inDouyin = fg != null && DOUYIN_PKGS.contains(fg);
        long cooldownUntil = StatsStore.getCooldownUntil(this);

        if (inDouyin) {
            // 冷却期内：一开就踢
            if (now < cooldownUntil) {
                goHome();
                if (!"locked".equals(lastStatus)) {
                    lastStatus = "locked";
                    StatsStore.setStatus(this, "locked");
                }
                toast("还在休息中，还剩 " + mmss(cooldownUntil - now));
                return;
            }

            // 离开超过阈值 → 连刷计时归零
            if (lastInDouyinMs > 0 && now - lastInDouyinMs > AWAY_RESET_MS) {
                sessionAccumMs = 0;
            }
            lastInDouyinMs = now;

            sessionAccumMs += TICK_MS;
            StatsStore.recordTick(this, TICK_MS, sessionAccumMs, "watching");

            int watchMin = StatsStore.getWatchMin(this);
            long limitMs = watchMin * 60_000L;

            if (sessionAccumMs >= limitMs) {
                int lockMin = StatsStore.getLockMin(this);
                StatsStore.recordLock(this, now + lockMin * 60_000L);
                sessionAccumMs = 0;
                lastStatus = "locked";
                goHome();
                toast("刷满 " + watchMin + " 分钟啦，休息 " + lockMin + " 分钟");
            }
        } else {
            String st = now < cooldownUntil ? "locked" : "idle";
            // 状态没变就不重复落盘
            if (!st.equals(lastStatus)) {
                lastStatus = st;
                StatsStore.setStatus(this, st);
            }
        }
    }

    /** 读取当前前台应用包名（仅包名，不读内容） */
    private String currentPackage() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return null;
        }
        CharSequence p = root.getPackageName();
        return p == null ? null : p.toString();
    }

    /** 返回桌面，把抖音切走 */
    private void goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    private void toast(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastToastAt < TOAST_GAP_MS) {
            return;
        }
        lastToastAt = now;
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private String mmss(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%d分%02d秒", s / 60, s % 60);
    }
}
