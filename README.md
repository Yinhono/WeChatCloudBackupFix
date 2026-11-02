# WeChatCloudBackupFix

让小米云备份重新备份 Play 版微信和 QQ 的数据。

小米云备份里有个不大讲理的判定：微信只要是分体包（split APK）安装的，就被判为「不支持备份」，备份列表里连微信数据这一项都不出现。从 Google Play 装的微信走的是 App Bundle 分发，装机必然带一串配置分体包，于是永远被误伤；国内渠道的单 APK 版本则一切正常。这个模块就是把这个误判绕过去。

已在真机验证可用：小米云备份 1.12.1.6.62.3 + Play 版微信。

## 要求

- 已 root 的安卓设备，装有 LSPosed（或兼容的 Xposed 框架）
- 小米云备份（`com.miui.cloudbackup`）

## 安装

1. 去 [Releases](https://github.com/Yinhono/WeChatCloudBackupFix/releases) 下载最新的 `WeChatCloudBackupFix.apk` 并安装。
2. 打开 LSPosed，启用本模块，作用域勾选「云备份」（模块已在清单里声明，一般会被自动推荐）。
3. 强行停止一次云备份，再重新打开。
4. 进入云备份的备份项管理，「微信/QQ 数据」一组里应该能重新看到微信数据，勾上即可备份。

## 原理

对云备份 1.12.1.6.62.3 的逆向结论：

备份项列表生成时，微信数据项是否出现由支持性判定 `Z0.d.g(Context, String)` 决定，勾选状态的维护和账号侧开关用的也是同一处判定，不过时还会把已保存的勾选清掉。这个判定最后落到一条规则上：应用不能带分体包，即 `PackageInfo.splitNames` 必须为空。这个检查本是给 APK 备份/恢复用的，而微信数据项的类型只有 DATA，备的是数据目录，和分体包毫无关系，判定被共用之后就成了误伤。

模块做了两层处理，任一层生效即可：

1. **精确层**：直接 hook `Z0.d` 的 `g` / `f`，对 `com.tencent.mm` 和 `com.tencent.mobileqq` 返回「支持」。类名来自 1.12.1.6.62.3 的混淆结果，云备份升级改名后这层自动失效。
2. **通用层**：hook 云备份进程内的 `PackageManager.getPackageInfo`，把微信和 QQ 查询结果里的 `splitNames` 置空，让原判定自己走通。不依赖目标应用的混淆名，版本适应性更好。

作用域只有云备份一个应用，不碰微信本身，也不改任何备份数据。

## 构建

常规方式：用 Android Studio 打开工程直接构建，或命令行 `./gradlew :app:assembleRelease`（需要 Android SDK 35 和 JDK 17，Xposed API 依赖为 `compileOnly`，不会打包进 APK）。

工程还附带一个不依赖 Gradle 的脚本，适合只有裸工具链的环境：

```bash
./build-manual.sh   # 产物在 build-manual/WeChatCloudBackupFix.apk，调试证书签名
```

## 兼容性

- 已验证：云备份 1.12.1.6.62.3。其他版本有通用层兜底，理论上可用，但没有逐一测过。
- 如果云备份升级后微信数据项还是不出现，多半是判定逻辑变了，把新版云备份 APK 提个 issue 附上，我再对一遍。
- 恢复微信数据时注意版本兼容：新版微信的数据恢复到旧版上可能出错，这是微信数据本身的特性，小米在恢复界面也有同样提示，和本模块无关。

## 免责声明

本模块通过 Xposed 框架修改系统应用的运行时行为，仅供个人设备上的学习与自救使用。使用前请自行确认重要数据另有备份，因使用本模块产生的任何数据问题与作者无关。

## License

[MIT](LICENSE)
