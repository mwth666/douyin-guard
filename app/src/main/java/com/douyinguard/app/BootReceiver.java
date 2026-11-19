package com.douyinguard.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机 / 应用更新后拉起常驻服务。
 *
 * 注意：Android 不允许普通应用自己"打开无障碍服务"（那是系统级安全限制），
 * 所以这里只能保证进程常驻、降低被系统清理的概率，不能代替用户在设置里手动开启。
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            GuardForegroundService.start(context);
        }
    }
}
