package com.douyinguard.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

/**
 * 无障碍"自动修复"。
 *
 * 背景：Android 不允许普通应用打开无障碍服务，系统（尤其是国产 ROM 的后台清理）
 * 会在某些时刻把它撤销。但有一条例外通道：
 *   若应用被授予 WRITE_SECURE_SETTINGS（保护级别含 development，可用 adb 一条命令授予，
 *   不需要 root），就可以直接改写 Settings.Secure 里的无障碍开关，
 *   系统的 AccessibilityManagerService 会通过 ContentObserver 收到变更并重新绑定服务。
 *
 * 因此：授权一次 → App 每次发现被关掉就自动写回去 → 效果等同于"一直开着"。
 *
 * 没授权时本类所有方法安全失败（返回 false），不影响其它功能。
 */
public final class AccessibilityKeeper {

    private AccessibilityKeeper() {
    }

    /** 是否已拿到写系统设置权限（需用 adb 授予一次） */
    public static boolean isGranted(Context c) {
        try {
            return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 本应用的无障碍服务当前是否真的处于开启状态 */
    public static boolean isServiceEnabled(Context c) {
        try {
            ContentResolver cr = c.getContentResolver();
            int on = Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            if (on != 1) {
                return false;
            }
            String list = Settings.Secure.getString(cr,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return list != null && list.contains(c.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 尝试把本应用的无障碍服务"写回开启"。
     *
     * @return true 表示写入成功（或本来就已开启）
     */
    public static boolean restore(Context c) {
        if (!isGranted(c)) {
            return false;
        }
        try {
            ContentResolver cr = c.getContentResolver();
            String flat = c.getPackageName() + "/" + DouyinGuardService.class.getName();

            String cur = Settings.Secure.getString(cr,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur == null) {
                cur = "";
            }

            boolean present = false;
            for (String item : cur.split(":")) {
                if (item.trim().equalsIgnoreCase(flat)) {
                    present = true;
                    break;
                }
            }

            // 只追加自己，绝不覆盖其它已开启的无障碍服务（否则会影响别的 App）
            if (!present) {
                String next = cur.isEmpty() ? flat : cur + ":" + flat;
                Settings.Secure.putString(cr,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next);
            }
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 组合动作：没开就自动开。返回是否处于开启状态 */
    public static boolean ensureEnabled(Context c) {
        if (isServiceEnabled(c)) {
            return true;
        }
        restore(c);
        return isServiceEnabled(c);
    }

    /** 给用户看的授权命令 */
    public static String grantCommand(Context c) {
        return "adb shell pm grant " + c.getPackageName()
                + " android.permission.WRITE_SECURE_SETTINGS";
    }
}
