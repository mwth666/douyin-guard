package com.douyinguard.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * 主界面：今日数据、规则设置、无障碍权限状态。
 */
public class MainActivity extends Activity {

    private TextView tvStatus;
    private TextView tvToday;
    private TextView tvLockedCount;
    private TextView tvSessionHint;
    private TextView tvWatchValue;
    private TextView tvLockValue;
    private TextView tvPermission;
    private TextView tvPermDiag;
    private TextView tvBatteryState;
    private TextView tvKeeperState;
    private ProgressBar progressSession;

    private int watchMin;
    private int lockMin;
    private long lastBarBucket = -1;   // 上次画柱状图时的分钟数，避免每秒重绘
    private String lastRaw = "";       // 无障碍检测的原始值，用于自诊断

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvToday = findViewById(R.id.tvToday);
        tvLockedCount = findViewById(R.id.tvLockedCount);
        tvSessionHint = findViewById(R.id.tvSessionHint);
        tvWatchValue = findViewById(R.id.tvWatchValue);
        tvLockValue = findViewById(R.id.tvLockValue);
        tvPermission = findViewById(R.id.tvPermission);
        tvPermDiag = findViewById(R.id.tvPermDiag);
        tvBatteryState = findViewById(R.id.tvBatteryState);
        tvKeeperState = findViewById(R.id.tvKeeperState);
        progressSession = findViewById(R.id.progressSession);

        watchMin = StatsStore.getWatchMin(this);
        lockMin = StatsStore.getLockMin(this);

        bindSteppers();

        // 常驻服务：降低被系统清理（进而关掉无障碍）的概率
        GuardForegroundService.start(this);
        requestNotificationPermissionIfNeeded();

        findViewById(R.id.btnOpenSettings).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                Toast.makeText(this, "请在列表中找到「戒抖助手」并开启", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "无法打开设置，请手动到 系统设置 → 无障碍 中开启", Toast.LENGTH_LONG).show();
            }
        });

        findViewById(R.id.btnBattery).setOnClickListener(v -> requestIgnoreBatteryOptimization());

        findViewById(R.id.btnKeeper).setOnClickListener(v -> showKeeperDialog());
    }

    /** 说明"自动保持开启"的授权方法，并支持一键复制命令 */
    private void showKeeperDialog() {
        final String cmd = AccessibilityKeeper.grantCommand(this);
        String msg = "Android 不允许任何应用自己打开无障碍，所以要让 App 拿到「写系统设置」的权限，"
                + "它才能在你被系统关掉后自动写回去。这条权限不需要 root，用电脑一条命令即可，只需做一次。\n\n"
                + "操作步骤：\n"
                + "1. 手机用数据线连电脑，设置里打开「开发者选项 → USB 调试」\n"
                + "2. 电脑上双击项目文件夹里的 grant_permission.bat\n"
                + "3. 手机上确认授权弹窗，然后回本页\n\n"
                + "手动命令：\n" + cmd;
        new AlertDialog.Builder(this)
                .setTitle("自动保持开启")
                .setMessage(msg)
                .setPositiveButton("复制命令", (d, w) -> {
                    try {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("adb", cmd));
                            Toast.makeText(this, "命令已复制", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Throwable ignored) {
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    /** Android 13+ 前台服务通知需要运行时授权，否则通知不显示（服务仍能跑） */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        try {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 10);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 申请忽略电池优化：国产 ROM 上这是"无障碍被自动关闭"最常见的根因 */
    private void requestIgnoreBatteryOptimization() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    Toast.makeText(this, "已经是「不限制」状态了", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        } catch (Exception e) {
            // 部分 ROM 屏蔽了这个入口，退回到电池优化列表
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                Toast.makeText(this, "请找到「戒抖助手」，选择「不限制」", Toast.LENGTH_LONG).show();
            } catch (Exception e2) {
                Toast.makeText(this, "请手动到 设置 → 电池 → 应用耗电管理 中设为不限制", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void bindSteppers() {
        updateSettingLabels();

        findViewById(R.id.btnMinusWatch).setOnClickListener(v -> {
            watchMin = Math.max(1, watchMin - 1);
            StatsStore.setWatchMin(this, watchMin);
            updateSettingLabels();
        });
        findViewById(R.id.btnPlusWatch).setOnClickListener(v -> {
            watchMin = Math.min(60, watchMin + 1);
            StatsStore.setWatchMin(this, watchMin);
            updateSettingLabels();
        });
        findViewById(R.id.btnMinusLock).setOnClickListener(v -> {
            lockMin = Math.max(1, lockMin - 1);
            StatsStore.setLockMin(this, lockMin);
            updateSettingLabels();
        });
        findViewById(R.id.btnPlusLock).setOnClickListener(v -> {
            lockMin = Math.min(120, lockMin + 1);
            StatsStore.setLockMin(this, lockMin);
            updateSettingLabels();
        });
    }

    private void updateSettingLabels() {
        tvWatchValue.setText(String.format(Locale.US, "%d 分", watchMin));
        tvLockValue.setText(String.format(Locale.US, "%d 分", lockMin));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到界面都重新读取配置（可能在别处改过）
        watchMin = StatsStore.getWatchMin(this);
        lockMin = StatsStore.getLockMin(this);
        updateSettingLabels();
        lastBarBucket = -1;   // 回到界面时强制重画柱状图
        GuardForegroundService.start(this);   // 万一被系统杀了，回到界面就重新拉起
        AccessibilityKeeper.ensureEnabled(this);   // 已授权则顺手把被关掉的无障碍写回去
        handler.removeCallbacks(refresher);
        handler.post(refresher);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresher);
    }

    /** 刷新界面数据 */
    private void render() {
        boolean on = isAccessibilityOn();
        tvPermission.setText(on ? "无障碍服务：已开启 ✓" : "无障碍服务：未开启");
        tvPermission.setTextColor(on ? 0xFF23B36D : 0xFFF0A020);

        // 诊断信息：未开启时显示原始检测值，把这一行截图发我就能定位原因
        if (!on) {
            tvPermDiag.setVisibility(View.VISIBLE);
            tvPermDiag.setText("检测值：" + lastRaw);
        } else {
            tvPermDiag.setVisibility(View.GONE);
        }

        boolean batteryOk = isIgnoringBatteryOptimizations();
        tvBatteryState.setText(batteryOk
                ? "电池优化：已设为「不限制」，系统不会随意清理 ✓"
                : "电池优化：仍是「受限」状态 —— 这是无障碍被自动关闭的头号原因，请点上面的按钮");
        tvBatteryState.setTextColor(batteryOk ? 0xFF23B36D : 0xFFF0A020);

        boolean keeperGranted = AccessibilityKeeper.isGranted(this);
        if (keeperGranted) {
            tvKeeperState.setText("自动保活：已授权 ✓ 无障碍被系统关掉后，10 秒内会自动写回。");
            tvKeeperState.setTextColor(0xFF23B36D);
        } else {
            tvKeeperState.setText("自动保活：未授权。没有它，系统一清理就会把无障碍关掉，只能手动再开一次。"
                    + "点下面按钮，用电脑花 1 分钟授权即可永久生效。");
            tvKeeperState.setTextColor(0xFFF0A020);
        }

        long todayMs = StatsStore.todayMs(this);
        long todayMin = Math.round(todayMs / 60000.0);
        tvToday.setText(todayMin + " 分钟");
        tvLockedCount.setText(StatsStore.todayLocked(this) + " 次");

        long now = System.currentTimeMillis();
        long cooldownUntil = StatsStore.getCooldownUntil(this);
        long sessMs = StatsStore.getSessionMs(this);
        int limitMs = watchMin * 60_000;

        if (!on) {
            tvStatus.setText("未开启无障碍 · 管控未生效");
            tvStatus.setTextColor(0xFFF0A020);
        } else if (now < cooldownUntil) {
            long leftMin = (cooldownUntil - now) / 60000 + 1;
            tvStatus.setText("强制休息中 · 还剩约 " + leftMin + " 分钟");
            tvStatus.setTextColor(0xFFFF4C66);
        } else if ("watching".equals(StatsStore.getStatus(this))) {
            long curMin = sessMs / 60000;
            tvStatus.setText("正在刷抖音 · 已 " + curMin + " / " + watchMin + " 分");
            tvStatus.setTextColor(0xFFFF4C66);
        } else {
            tvStatus.setText("管控中 · 当前没在刷抖音");
            tvStatus.setTextColor(0xFF23B36D);
        }

        int pct = limitMs > 0 ? (int) Math.min(100, sessMs * 100 / limitMs) : 0;
        progressSession.setProgress(pct);

        if (now < cooldownUntil) {
            tvSessionHint.setText("休息中，本轮已累计 " + (sessMs / 60000) + " 分钟");
        } else {
            tvSessionHint.setText("本次已刷 " + (sessMs / 60000) + " 分钟 / 上限 " + watchMin + " 分钟");
        }

        // 柱状图按分钟粒度刷新即可，不必每秒重画
        long minuteBucket = todayMs / 60000;
        if (minuteBucket != lastBarBucket) {
            lastBarBucket = minuteBucket;
            renderWeek();
        }
    }

    /** 画最近 7 天的柱状对比 */
    private void renderWeek() {
        LinearLayout box;
        try {
            box = findViewById(R.id.dayBars);
        } catch (Exception e) {
            return;
        }
        if (box == null) {
            return;
        }
        box.removeAllViews();

        final int N = 7;
        long[] vals = new long[N];
        long max = 1;
        for (int i = 0; i < N; i++) {
            vals[i] = StatsStore.dayMs(this, StatsStore.dayKeyOffset(i - (N - 1)));
            if (vals[i] > max) {
                max = vals[i];
            }
        }

        float dp = getResources().getDisplayMetrics().density;
        int barMaxPx = (int) (58 * dp);   // 柱子最高高度
        int barMinPx = (int) (3 * dp);    // 有数据时最低高度
        int barWPx = (int) (16 * dp);
        int accent = getResources().getColor(R.color.accent, null);
        int empty = 0xFFE9EDF2;

        for (int i = 0; i < N; i++) {
            boolean isToday = (i == N - 1);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            col.setLayoutParams(colLp);

            // 分钟数
            TextView tvMin = new TextView(this);
            tvMin.setText(vals[i] > 0 ? String.valueOf(Math.round(vals[i] / 60000.0)) : "");
            tvMin.setTextSize(9);
            tvMin.setTextColor(0xFF98A2B3);
            tvMin.setGravity(Gravity.CENTER);
            col.addView(tvMin);

            // 柱子
            View bar = new View(this);
            int h = vals[i] > 0
                    ? Math.max(barMinPx, (int) (barMaxPx * (vals[i] / (double) max)))
                    : (int) (2 * dp);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(barWPx, h);
            barLp.topMargin = (int) (2 * dp);
            barLp.bottomMargin = (int) (4 * dp);
            bar.setLayoutParams(barLp);
            bar.setBackgroundColor(vals[i] > 0 ? accent : empty);
            col.addView(bar);

            // 星期
            TextView tvDay = new TextView(this);
            tvDay.setText(StatsStore.shortDayLabel(StatsStore.dayKeyOffset(i - (N - 1)), isToday));
            tvDay.setTextSize(10);
            tvDay.setTextColor(isToday ? accent : 0xFF98A2B3);
            tvDay.setGravity(Gravity.CENTER);
            col.addView(tvDay);

            box.addView(col);
        }
    }

    /** 判断本应用的无障碍服务是否已开启（兼容不同 ROM 的组件字符串写法） */
    private boolean isAccessibilityOn() {
        String pkg = getPackageName();
        String cls = DouyinGuardService.class.getName();
        String flatFull = pkg + "/" + cls;                                   // pkg/pkg.Cls
        String flatShort = pkg + "/." + cls.substring(pkg.length() + 1);     // pkg/.Cls
        try {
            int enabled = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            String list = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            lastRaw = "enabled=" + enabled + " · list=" + (list == null ? "null" : list);
            if (enabled != 1 || list == null) {
                return false;
            }
            for (String item : list.split(":")) {
                String s = item.trim();
                if (s.equalsIgnoreCase(flatFull)
                        || s.equalsIgnoreCase(flatShort)
                        || s.equalsIgnoreCase(cls)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            lastRaw = "读取失败：" + e;
            return false;
        }
    }

    /** 是否已被系统排除在电池优化之外（保活前提） */
    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }
}
