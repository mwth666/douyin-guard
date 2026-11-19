# 戒抖助手（DouyinGuard）

一个 Android 小工具：**监控你刷抖音的时长，刷满就自动把抖音关掉，并且一段时间内打不开。**

- 刷满 N 分钟 → 自动跳回桌面，抖音进入冷却
- 冷却期内只要打开抖音 → 1 秒内被再次踢回桌面，等于"暂时打不开"
- 手机息屏不计时，不会靠挂着刷时长
- 刷多久 / 锁多久，App 里按 +/- 自己调
- 数据只存在手机本地，不联网、不上传、不用登录

安装包：`DouyinGuard.apk`（约 30 KB）

---

## 一、安装

1. 把 `DouyinGuard.apk` 传到手机（微信文件传输助手 / QQ / 数据线都行）。
2. 在手机上点开安装。系统提示"未知来源应用"时，允许一次即可。
3. 打开 App，点 **「去开启无障碍权限」**。
4. 在系统无障碍列表里找到 **戒抖助手**，打开开关，弹窗点「允许」。

> 无障碍权限是这类工具唯一可行的实现方式——Android 不允许普通 App 关掉别的 App，
> 只有拿到无障碍权限，才能识别"当前前台是抖音"并执行"返回桌面"。

### 各家手机找不到无障碍入口时

| 品牌 | 路径 |
|---|---|
| 小米 / Redmi | 设置 → 更多设置 → 无障碍 → 已下载的应用 |
| 华为 / 荣耀 | 设置 → 辅助功能 → 无障碍 → 已安装的服务 |
| OPPO / 一加 / realme | 设置 → 系统设置 → 无障碍 → 已安装的服务 |
| vivo / iQOO | 设置 → 快捷与辅助 → 无障碍 → 已安装的服务 |
| 三星 | 设置 → 辅助功能 → 已安装的应用 |

**重要**：进入无障碍页面后，建议再进「戒抖助手」，把「电池优化 / 后台限制」设为**不限制**，
否则系统可能在你不用手机时把服务杀掉，管控就失效了。

---

## 二、使用

打开 App 主界面：

- **顶部状态**：管控是否生效、当前是否在刷、剩余休息时间
- **今日数据**：今天累计刷了多少分钟、被强制中断了几次
- **进度条**：本轮已刷进度（满了就锁）
- **规则设置**：`单次可刷 N 分` / `强制休息 M 分`，点 +/- 调整，改完立即生效

默认规则：**刷 10 分钟 → 锁 10 分钟**。

规则说明：

- 你连续刷抖音满 N 分钟，App 会直接把你弹回桌面，并开始计时 M 分钟。
- 这 M 分钟内，你每次打开抖音都会被 1 秒内弹回，并且弹出「还在休息中，还剩 X 分 Y 秒」。
- 如果你离开抖音超过 5 分钟（比如去干别的事），连刷计时会重置，回来重新开始累计。
- 计时只在**抖音处于前台**时进行，看别的应用、刷微信都不计。

---

## 三、数据存在哪

全部存在手机本地（App 私有目录的 SharedPreferences），卸载 App 即彻底删除。

App 只做两件事：读"当前前台应用包名"、执行"返回桌面"。**不读取任何页面内容、不截屏、不联网。**

---

## 四、无障碍总是自己变回"关闭"？（1.1 版重点解决）

这是这类工具最顽固的问题。原因不在 App 的开关逻辑，而在**国产 ROM 的后台清理策略**：
系统把没有前台通知的应用当"可随时清理的后台应用"，清理时会把它的无障碍服务一并撤销。
Android 出于安全考虑，**不允许任何应用自己打开无障碍**（否则恶意软件可以自我授权），
所以唯一可行的方向是"别被系统清掉"。

1.1 版做了三件事：

1. **常驻通知保活**：App 会挂一条「戒抖助手正在管控 · 今日 42 分钟」的低优先级通知。
   不是为了骚扰，而是让系统认为它有前台任务，显著降低被清理概率。（Android 13+ 首次会询问通知权限，请点允许）
2. **申请忽略电池优化**：主界面新增「关闭电池优化（保活，重要）」按钮，点它会弹系统确认框，选「允许」。
   下方会显示当前状态，绿字才是真的生效了。
3. **开机 / 更新后自动拉起**：重启手机、覆盖安装新版本后，常驻服务会自动恢复。

装好 1.1 版后，请务必再手动做两件事（各品牌差异，App 无法代劳）：

- **锁定最近任务**：打开多任务界面，找到戒抖助手，下拉或长按给它上锁（小米/OPPO 都有这个手势），
  这样"一键清理"不会杀掉它。
- **允许自启动**：设置里搜「自启动」，把戒抖助手打开（小米的「自启动」、华为的「应用启动管理」、OPPO 的「允许自启动」）。

如果还是会被关，主界面在"未开启"时会出现一行灰色小字 `检测值：enabled=... list=...`。
把这一行截图发我，就能直接判断是**系统真的把它撤了**，还是**只是没识别到**——这两种情况的修法完全不同。

---

## 四之二、1.2 版：一次性授权，让它"除非你手动关，否则一直开着"

上面那些保活手段只能降低概率，不能保证。真正能做到"被关掉自动恢复"的办法只有一个：
让 App 拿到 `WRITE_SECURE_SETTINGS` 权限，直接改写系统里的无障碍开关。这条权限：
- **不需要 root**，用电脑 adb 一条命令授予，只需做一次；
- 授权后 App 每 10 秒巡检一次，发现被系统撤销就立刻写回去（一般几秒内恢复原状）；
- 只有你**手动**在设置里关掉时才不会再开——因为那是你的主动意图。

做法（1 分钟）：

1. 手机：设置 → 关于手机 → 连点「版本号」7 次打开开发者选项 → 打开 **USB 调试**
2. 手机用数据线连电脑，弹窗点「允许 USB 调试」
3. 电脑上双击 `E:\DouyinGuard\grant_permission.bat`
   （或手动执行：`adb shell pm grant com.douyinguard.app android.permission.WRITE_SECURE_SETTINGS`）
4. 回到 App，主界面「自动保持开启」卡片显示 **已授权 ✓** 即成功

> 注意：手动关闭一次后，App 会把它当作你的意图，**不会立刻强行打开**；但下次你清理后台/重启后它仍会尝试恢复。
> 如果你希望"连我手动关也要给我开回来"，那就不是防护工具而是流氓软件了，这个我不做。

**如果你的手机没法连电脑 / 不想开 USB 调试**：
那这条"永远不掉"的路就走不通了——Android 在系统层面就不允许应用自己打开无障碍，没有任何第三方 App 能绕过。
这种情况下更稳的替代是**用系统自带的应用限时**：小米「专注模式/应用限额」、华为「屏幕时间管理」、
OPPO/vivo「应用限时」。它们是系统级功能，后台清理影响不到，缺点是到点弹遮挡页而不是直接把你踢出去。

---

## 五、常见问题

**Q：开了无障碍但完全不生效？**
1. 确认无障碍列表里「戒抖助手」是**开启**状态。
2. 按上面第四节的"保活三步"设置一遍。
3. 抖音用的是极速版？已内置支持 `com.ss.android.ugc.aweme.lite`。

**Q：能不能防止我关掉它？**
不能。这是**防手滑、防惯性**的工具，不是防决心。你随时可以关掉无障碍开关或卸载 App。
真要靠它戒掉，建议把「开发者选项 / 应用管理」这类入口也交给家人保管——但这就超出这个 App 的能力了。

**Q：iPhone 能用吗？**
不能。iOS 不允许任何第三方 App 自动关闭或切走别的 App，只能用「屏幕使用时间」的遮挡页，还需付费开发者账号（¥688/年），且输密码就能绕过。这个 APK 只适用于 Android。

**Q：为什么是"跳回桌面"而不是"关闭抖音"？**
普通应用无权强杀别的应用（那是系统权限）。跳回桌面在体验上等同于"被赶出来"，且不会清掉抖音的后台，下次打开还在原处——但对戒断来说，被打断的效果是一样的。

---

## 六、重新构建（想改规则/改文案时）

工程在 `E:\DouyinGuard`，用 SDK 自带工具链手动打包，**不需要 Android Studio、不需要联网**：

```bash
# Git Bash 下执行
export JAVA_HOME="E:\Android\jbr"
export PATH="/e/Android/jbr/bin:$PATH"
BT="/c/Users/68335/AppData/Local/Android/Sdk/build-tools/35.0.1"
PLATFORM="/c/Users/68335/AppData/Local/Android/Sdk/platforms/android-35/android.jar"

# 1) 编译资源
"$BT/aapt2.exe" compile --dir app/src/main/res -o build/compiled/res.zip
"$BT/aapt2.exe" link -o build/base.apk -I "$PLATFORM" \
  --manifest app/src/main/AndroidManifest.xml --java build/gen \
  --min-sdk-version 23 --target-sdk-version 34 --version-code 3 --version-name 1.2 \
  build/compiled/res.zip

# 2) 编译 Java（release 8，兼容所有安卓）
"$JAVA_HOME/bin/javac.exe" -encoding UTF-8 --release 8 -nowarn \
  -classpath "$PLATFORM" -d build/classes \
  $(find app/src/main/java -name "*.java") build/gen/com/douyinguard/app/R.java

# 3) 转 dex
"$BT/d8.bat" --min-api 23 --lib "$PLATFORM" --output build/dex $(find build/classes -name "*.class")

# 4) 打包 + 对齐 + 签名
python -c "
import zipfile,shutil
shutil.copy2('build/base.apk','build/unsigned.apk')
with zipfile.ZipFile('build/unsigned.apk','a',zipfile.ZIP_DEFLATED) as z: z.write('build/dex/classes.dex','classes.dex')
"
"$BT/zipalign.exe" -f -p 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner.bat" sign --ks debug.keystore --ks-pass pass:android --ks-key-alias douyinguard \
  --min-sdk-version 23 --out DouyinGuard.apk build/aligned.apk
```

源码结构：

```
app/src/main/
├── AndroidManifest.xml
├── java/com/douyinguard/app/
│   ├── DouyinGuardService.java   核心：前台识别、计时、到点踢出、冷却拦截
│   ├── GuardForegroundService.java  保活：常驻通知，降低被系统清理概率
│   ├── AccessibilityKeeper.java  自动修复：被系统关掉后把无障碍开关写回去
│   ├── BootReceiver.java         开机 / 更新后拉起常驻服务
│   ├── MainActivity.java         主界面：数据展示 + 规则调整 + 权限引导
│   └── StatsStore.java           本地存储：配置、今日统计、运行状态
└── res/
    ├── layout/activity_main.xml  界面布局
    ├── values/                   文案、颜色、主题
    └── xml/accessibility_service_config.xml   无障碍服务声明
```

签名用的是工程里的 `debug.keystore`（密码 `android`）。**如果你以后想覆盖安装新版本，
必须用同一个 keystore**，否则系统会报"签名不一致"、要求先卸载旧版（卸载会清掉统计数据）。
