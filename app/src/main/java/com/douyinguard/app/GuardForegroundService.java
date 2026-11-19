package com.douyinguard.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * 常驻前台服务。
 *
 * 作用不是"管控"（管控由无障碍服务负责），而是保活：
 * 国内 ROM（小米/华为/OPPO/vivo 等）会把没有前台通知的应用当作可随时清理的后台应用，
 * 清理时往往会顺带把无障碍服务一起关掉。挂一个常驻通知能显著降低被清理的概率。
 *
 * 通知里实时显示今日时长，也让用户随时能确认"它还在工作"。
 */
public class GuardForegroundService extends Service {

    private static final String CHANNEL_ID = "guard_status";
    private static final int NOTI_ID = 1001;
    private static final long REFRESH_GRANTED_MS = 10_000L;   // 已授权：10 秒巡检一次，被关就写回
    private static final long REFRESH_NORMAL_MS = 30_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            boolean granted = false;
            try {
                granted = AccessibilityKeeper.isGranted(GuardForegroundService.this);
                if (granted) {
                    // 关键：发现无障碍被系统撤销，立刻写回去
                    AccessibilityKeeper.ensureEnabled(GuardForegroundService.this);
                }
                updateNotification();
            } catch (Throwable t) {
                // 巡检失败不能影响服务本身
            }
            handler.postDelayed(this, granted ? REFRESH_GRANTED_MS : REFRESH_NORMAL_MS);
        }
    };

    /** 幂等启动：重复调用不会产生多个服务实例 */
    public static void start(Context c) {
        try {
            Intent i = new Intent(c, GuardForegroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, GuardForegroundService.class));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        try {
            startForeground(NOTI_ID, buildNotification());
        } catch (Throwable ignored) {
        }
        handler.post(refresher);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startForeground(NOTI_ID, buildNotification());
        } catch (Throwable ignored) {
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(refresher);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "管控状态", NotificationManager.IMPORTANCE_MIN);
        ch.setDescription("显示戒抖助手的运行状态与今日数据");
        ch.setShowBadge(false);
        ch.enableLights(false);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, piFlags);

        long todayMs = StatsStore.todayMs(this);
        int locked = StatsStore.todayLocked(this);
        long cooldownUntil = StatsStore.getCooldownUntil(this);
        long now = System.currentTimeMillis();

        String text;
        boolean enabled = AccessibilityKeeper.isServiceEnabled(this);
        boolean granted = AccessibilityKeeper.isGranted(this);
        if (!enabled) {
            text = granted ? "无障碍被系统关闭，正在自动恢复…" : "无障碍已关闭，请手动开启（并完成授权）";
        } else if (now < cooldownUntil) {
            long left = (cooldownUntil - now) / 1000;
            text = "强制休息中，还剩 " + (left / 60) + " 分 " + (left % 60) + " 秒";
        } else {
            text = "今日 " + Math.round(todayMs / 60000.0) + " 分钟 · 被锁 " + locked + " 次"
                    + (granted ? " · 自动保活已启用" : "");
        }

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        return b.setSmallIcon(R.drawable.ic_stat_guard)
                .setContentTitle("戒抖助手正在管控")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        nm.notify(NOTI_ID, buildNotification());
    }
}
