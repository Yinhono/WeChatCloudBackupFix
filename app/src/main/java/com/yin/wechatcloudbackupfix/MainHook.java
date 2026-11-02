package com.yin.wechatcloudbackupfix;

import android.content.pm.PackageInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 让小米云备份（com.miui.cloudbackup）可以备份 Play 版微信 / QQ 的数据。
 *
 * 原理（逆向自 com.miui.cloudbackup 1.12.1.6.62.3）：
 * 云备份在列出“微信/QQ 数据”备份项之前，会调用 Z0.d.g(Context, String) 判断应用是否受支持。
 * g() 内部走 f(Context, PackageInfo)，最终判定是 h(...) && !hasSplits(packageInfo)。
 * hasSplits 检查的是 PackageInfo.splitNames 是否非空：
 * - 国内版微信是单个 APK 安装，splitNames 为空，通过；
 * - Play 版微信通过 App Bundle 分发，装机时带配置分体包（CPU/密度/语言），splitNames 非空，被判为“不支持”，
 *   备份项直接不出现，已保存的勾选状态还会被 Z0.s 清掉。
 * 微信数据备份项本身只有 DATA 类型，和分体包毫无关系，这个判定对数据备份是误伤。
 *
 * 本模块两层处理（任一层生效即可）：
 * 1) 精确层：hook Z0.d 的 g / f，对 com.tencent.mm、com.tencent.mobileqq 直接返回 true。
 *    类名来自 1.12.1.6.62.3 的混淆结果，其他版本若改名会自动失效并落到第 2 层。
 * 2) 通用层：hook 本进程的 PackageManager.getPackageInfo，把微信/QQ 的 splitNames 置空，
 *    让原判定逻辑自己得出“通过”。不依赖目标应用的混淆名。
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "WeChatCloudBackupFix";
    private static final String TARGET_PACKAGE = "com.miui.cloudbackup";
    private static final Set<String> FIXED_APPS = new HashSet<>(Arrays.asList(
            "com.tencent.mm",
            "com.tencent.mobileqq"
    ));

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        hookSupportChecker(lpparam);
        hookSplitNames(lpparam);
    }

    private void hookSupportChecker(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> checker = XposedHelpers.findClass("Z0.d", lpparam.classLoader);
            XposedBridge.hookAllMethods(checker, "g", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length == 2 && FIXED_APPS.contains(param.args[1])) {
                        param.setResult(Boolean.TRUE);
                    }
                }
            });
            XposedBridge.hookAllMethods(checker, "f", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length == 2 && param.args[1] instanceof PackageInfo) {
                        PackageInfo packageInfo = (PackageInfo) param.args[1];
                        if (FIXED_APPS.contains(packageInfo.packageName)) {
                            param.setResult(Boolean.TRUE);
                        }
                    }
                }
            });
            XposedBridge.log(TAG + ": support checker hooked in " + TARGET_PACKAGE);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Z0.d not hooked (" + t + "), generic layer only");
        }
    }

    private void hookSplitNames(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> packageManager = XposedHelpers.findClass(
                    "android.app.ApplicationPackageManager", lpparam.classLoader);
            XposedBridge.hookAllMethods(packageManager, "getPackageInfo", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object result = param.getResult();
                    if (result instanceof PackageInfo) {
                        PackageInfo packageInfo = (PackageInfo) result;
                        if (FIXED_APPS.contains(packageInfo.packageName)
                                && packageInfo.splitNames != null) {
                            packageInfo.splitNames = null;
                        }
                    }
                }
            });
            XposedBridge.log(TAG + ": getPackageInfo hooked in " + TARGET_PACKAGE);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": getPackageInfo hook failed: " + t);
        }
    }
}
