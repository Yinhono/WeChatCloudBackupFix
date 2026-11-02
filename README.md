# 微信云备份修复（Xposed 模块）

让小米云备份可以备份 Play 版微信 / QQ 的数据。

## 根因

逆向自 `com.miui.cloudbackup` 1.12.1.6.62.3（萤提供的 APK）：

- 云备份在生成备份项列表时（`z1.I0`），只有 `Z0.d.g(Context, "com.tencent.mm")` 返回 true，才会把「微信数据」项加进列表。勾选状态维护（`Z0.s`）和账号侧开关（`H1.A`）用的也是同一个判定，不过时会把已保存的勾选直接清掉。
- `g()` 的判定链是 `f(Context, PackageInfo)` → `h(...) && !hasSplits(packageInfo)`，其中 `hasSplits` 检查 `PackageInfo.splitNames` 是否非空（`com.miui.cloudbackup.utils.a.g`）。
- 国内版微信是单个 APK 安装，`splitNames` 为空，通过。Play 版微信经 App Bundle 分发，装机带配置分体包，`splitNames` 非空，被判成「不支持」，备份项直接消失。
- 微信数据备份项本身只有 DATA 类型，备份的是数据目录，和分体包没有关系。这个判定本来是给 APK 备份/恢复用的，被数据项共用后成了误伤。

## 模块做法

作用域只有一个：`com.miui.cloudbackup`。两层 hook，任一层生效即可：

1. 精确层：hook `Z0.d` 的 `g` / `f`，对 `com.tencent.mm`、`com.tencent.mobileqq` 直接返回 true。类名是 1.12.1.6.62.3 的混淆名，云备份升级改名后这层会自动失效。
2. 通用层：hook 云备份进程内的 `PackageManager.getPackageInfo`，把微信/QQ 返回结果的 `splitNames` 置空，让原判定自己通过。不依赖目标应用混淆名。

## 使用

1. 安装 `WeChatCloudBackupFix.apk`。
2. 在 LSPosed 里启用模块，作用域勾选「云备份」（`com.miui.cloudbackup`），模块已在清单里声明该作用域，一般会被自动推荐。
3. 强行停止一次云备份再重新打开，进备份项管理，「微信/QQ 数据」里应该能看到微信数据并可勾选备份。

工程里带了一份不依赖 Gradle 的 `build-manual.sh`，用 JDK + Android SDK 工具链手动打包，产物在 `build-manual/WeChatCloudBackupFix.apk`，用调试证书签名（Xposed 模块不需要正式签名）。也可以直接用 Android Studio / Gradle 打开工程构建。

## 注意

- 结论和 hook 点来自对 1.12.1.6.62.3 的静态分析，APK 已实际编译打包并核验过清单与 dex 内容。2026-11-02 已在真机验证可用（云备份 1.12.1.6.62.3，Play 版微信数据项恢复显示并可备份）。其他云备份版本未逐一验证。
- 恢复微信数据时，小米自己就有版本兼容出错的提示，跨版本恢复请谨慎。
- 云备份升级到新版本后，如果两层都失效，把新版 APK 发我，我重新对一遍判定点。
