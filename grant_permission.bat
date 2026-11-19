@echo off
chcp 65001 >nul
setlocal

set PKG=com.douyinguard.app
set PERM=android.permission.WRITE_SECURE_SETTINGS
set ADB="C:\Users\68335\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if not exist %ADB% set ADB=adb

echo ============================================
echo  DouyinGuard - one-time permission grant
echo ============================================
echo.

%ADB% devices
echo.
echo If your phone is listed as "unauthorized":
echo   unlock the phone and tap "Allow USB debugging", then run this again.
echo.
pause

echo Granting %PERM% ...
%ADB% shell pm grant %PKG% %PERM%

echo.
echo Verifying ...
%ADB% shell dumpsys package %PKG% | findstr /C:"WRITE_SECURE_SETTINGS"

echo.
echo If the line above appears, it succeeded.
echo Open the app: the "auto keep on" card should now show granted.
echo If nothing appeared, check USB debugging and try again.
echo.
pause
