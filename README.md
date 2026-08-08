# Pico 4 Window Limit Unlock (LSPosed Module)

针对 **Pico 4 (A8110, 国行, Android 10 / API 29 / 固件 5.13.7)** 的 2D 悬浮窗数量上限解除模块。

把系统对 **2D 悬浮窗数量** 的硬编码限制从 **3 个** 提升到 **5 个稳定上限**（3–16 可调），通过 Zygisk Vector (LSPosed 兼容框架) 注入实现。

---

## 一、效果

| 项目 | 原厂 | 解锁后 |
|---|---|---|
| 2D 悬浮窗数量 | 3 个（超过自动销毁最旧窗口） | **5 个稳定上限**（3–16 可调） |

> ⚠️ **为什么是 5 而不是无上限/127？**
> Pico 的 3 窗口限制**不是产品阉割**，而是把提交给 OpenXR 的 composition layer 数量压在运行时硬顶以内。
> 实测：**5 个稳定，6 个必崩**（`XR_ERROR_LAYER_LIMIT_EXCEEDED` → `xrBeginFrame` 乱序 → `std::logic_error` → SIGABRT，
> 崩溃进程 `com.picoxr.xrshell`，`OpenXRLoader.cpp:64`）。
> 所以正确做法是"把上限抬到 OpenXR 天花板（5）"，不是"去掉上限"。

---

## 二、技术架构

```
Pico 4 (Android 10, API 29, 固件 5.13.7)
└─ Magisk 30.7 (已修补 boot.img 提权)
   └─ Zygisk Vector v2.2 (LSPosed 兼容框架 + 管理器)
      └─ 本模块 com.picoxr.winlimit  (mid=15)
           scope: com.picovr.systemext + android + system
```

### 为什么 hook 而不是 Magisk 整包替换？
- **SystemExt** 是 `sharedUserId="android.uid.system"` (uid 1000) 的 PERSISTENT 系统应用，
  自签名的替换 APK 会被 PackageManager 拒绝（签名不一致）。
- 所以改用 LSPosed hook（Zygisk Vector 自带框架，无需额外装 LSPosed.apk）。

### 注入竞态 + Magisk 兜底
Vector 在 t+1s 注入 framework，但模块映射在 t+3s 才就绪，PERSISTENT 的 SystemExt 正好在这个空档启动 → 漏注入。
兜底方案：`magisk_fix/` 是一个独立 Magisk 模块 `pico_winlimit_fix`（`/data/adb/modules/pico_winlimit_fix/`），
日志 `/data/local/tmp/pico_winlimit_fix.log`。**绝不改 `zygisk_vector/service.sh`，也拒绝用 `--late-inject`**（影响面太大）。

---

## 三、限制原理（根因分析）

`com.picovr.systemext` 的 `AppStack.updateVisible(ZZZI)` 里，对 `type 3002`（远平面板，即 2D 窗口层）
+ `FLAG_MULTI_WINDOW_ENABLE (0x200000)`，当 `appList.size() >= 3` 时销毁最旧的应用（`subList(3, size)`）。

原厂逻辑是**无条件全销毁超出部分**。本模块改成 **"销毁预算"模式**：
- `AppStack.updateVisible(ZZZI)Z` before：设 `IN_UPDATE_VISIBLE=TRUE` + `DESTROY_BUDGET = limit - 3`；after 清空
- `RootAppContainer.handleDestroyApp(AppContainer)` before：仅当在 updateVisible 栈内**且预算 > 0**
  才递减预算并 `setResult(null)` 跳过销毁
- 手动关窗发生在 updateVisible 之外，完全不受影响

---

## 四、配置项（system property，实时可调）

| 属性 | 默认 | 范围 | 作用 |
|---|---|---|---|
| `persist.pico.window.limit` | 5 | 3–16 | 窗口上限。改完 `pkill -f com.picovr.systemext`（PERSISTENT 自动重生） |

钳制范围是**保命**用的，防止填个离谱值进崩溃循环。

---

## 五、目录结构

```
pico4-winlimit/
├── build_mod.bat                 # 构建脚本
│                                 #   build_mod.bat mod_window com\picoxr\winlimit winlimit
├── mod_window/                   # 模块源码
│   ├── AndroidManifest.xml
│   ├── apktool.yml
│   ├── assets/xposed_init        # 入口类声明
│   ├── res/values/arrays.xml     # xposedscope
│   └── src/com/picoxr/winlimit/
│       ├── WindowLimit.java      # 入口
│       └── hook/{BeforeUpdateVisible, AfterUpdateVisible, BlockDestroy}.java
├── magisk_fix/                   # Magisk 开机竞态兜底模块
│   ├── module.prop
│   ├── service.sh
│   └── install.sh
└── lsp_mod/                      # 数据库运维脚本
    ├── db_syncpath.py            #   同步 apk_path（重装后必跑）
    ├── db_restore.sh             #   推回数据库
    ├── db_scope.py               #   改 scope
    ├── logs.sh / logs_full.sh    #   抓 Vector 日志
    └── check_tracker.sh
```

---

## 六、构建环境（无 Android SDK 纯命令行）

这台机器**没有安装 Android SDK / android.jar**，平台类要手写 stub（`stub/android/view/View.java`，仅编译用，不打进 dex）。

- **JDK 26**：`C:\Program Files\Java\jdk-26.0.1`
- **javac 必须 `--release 8`**（Java 26 默认出 class v52，d8 拒绝）
- **r8.jar**（含 d8）：`java -cp r8.jar com.android.tools.r8.D8 --min-api 29 --output <dir> <classes>`
- **Xposed API 用自写 stub**（`stub/de/robv/android/xposed/*`），d8 只 dex 模块自己的 class
- **apktool** 打包 + **jarsigner** 自签（无 native lib 的模块 APK 不需要 zipalign）

### ⚠️ 混淆版 Vector 框架的真实 Xposed API 签名
Vector 对 Xposed API 做了混淆（类名如 `J.LWAmWX.cJwqEr.pds.yD.XposedHelpers`）。
- `findAndHookMethod` 真实签名：**返回 `XC_MethodHook.Unhook`，不是 void！**
- 必须用 `XposedHelpers.findAndHookMethod`，且 stub 要声明返回 `XC_MethodHook.Unhook`，否则运行时 `NoSuchMethodError`。

---

## 七、部署流程（改完代码如何上线）

1. **构建**：`cmd /c "build_mod.bat mod_window com\picoxr\winlimit winlimit 2>&1"`
2. **安装**：`adb install -r build\apk\winlimit.apk`
3. **同步 apk_path**（重装后必跑，否则 Vector 加载旧 dex）：`python lsp_mod\db_syncpath.py`
4. **推回数据库**：`adb push` 后跑 `lsp_mod\db_restore.sh`（自动 chown/chmod + 清 wal/shm）
5. **重启**：`adb reboot`

### ⚠️ 崩溃循环止血
`handleLoadPackage` 阶段**一行宿主类代码都不能执行**（会触发 `ClassLoader` 重入 → `ExceptionInInitializerError` → 无限崩溃重启）。
止血：先在数据库把模块 `enabled=0`，重启，再改代码重建。

---

## 八、验证状态

- ✅ 5 窗口稳定，6 崩溃（已确认 OpenXR 硬顶）
- ✅ 干净重启后两模块注入一次、零崩溃、进程稳定
- ✅ 兜底模块日志：`module already injected, no restart needed`

---

## 九、日志与诊断

- Vector 日志：`/data/adb/lspd/log/{verbose_*,modules_*,kmsg}.log`
- 崩溃：`adb logcat -d -b crash`
- 兜底模块日志：`/data/local/tmp/pico_winlimit_fix.log`

---

## 十、网络环境备注（下载依赖时）

- **GitHub release 资源被墙**：直连 + `ghfast.top` + `gh-proxy.com` + `mirror.ghproxy.com` 都返回 9 字节 "Not Found"
- ✅ **`ghproxy.net` 可用**（唯一能下 GitHub release 的镜像）
- F-Droid / IzzyOnDroid 不通，GitHub API 被限流

---

## 十一、致谢

- **more-picohaxx** (typlo) — bootloader 解锁工具
- **FallenAngel** — 解锁流程社区指导
- **Zygisk Vector** (JingMatrix) — LSPosed 兼容框架