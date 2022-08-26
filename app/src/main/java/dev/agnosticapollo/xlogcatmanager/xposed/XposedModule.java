package dev.agnosticapollo.xlogcatmanager.xposed;

import android.os.Process;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import dev.agnosticapollo.xlogcatmanager.XposedApplication;
import dev.agnosticapollo.xlogcatmanager.xposed.logcat.XLogAccessDialogActivity;
import dev.agnosticapollo.xlogcatmanager.xposed.logcat.XLogcatManagerService;

/**
 * Class called by Xposed to hook into apps.
 * Make sure to enabled "Always install with package manager (disables deploy optimizations on Android 11 and later)"
 * in Android Studio app run config or disable "Instant Run" in older versions, otherwise changes will
 * not take effect on updates with run button.
 *
 * If hooking into "android" package, reboot device for changes to take effect.
 *
 * If running on avd, boot it, install magisk app and enable zygisk, then run
 * `./build.py emulator --skip` in Magisk git directory to reboot into rooted version, then install
 * LSPosed zygisk release, install module app, reboot avd and run `./build.py emulator --skip` again.
 * If hooking into "android" package, rebooting avd and running `./build.py emulator --skip` is
 * required for changes to take effect. In some cases when module isn't loading properly, repeat the same.
 *
 * https://github.com/rovo89/XposedBridge/wiki/Development-tutorial
 * https://github.com/LSPosed/LSPosed/wiki
 * https://github.com/LSPosed/LSPosed/releases
 * https://github.com/topjohnwu/Magisk/blob/master/scripts/avd_magisk.sh
 */
@Keep
public class XposedModule implements IXposedHookLoadPackage {

    private static final String LOG_TAG = "XposedModule";

    private static Method mDeoptimizeMethod;

    public static List<String> packagesToHook = Arrays.asList(
            "android"
    );

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName == null || !packagesToHook.contains(lpparam.packageName)) {
            Logger.logInfo(LOG_TAG, "Ignoring hooking into package=" + lpparam.packageName + ", process=" + lpparam.processName);
            return;
        } else {
            Logger.logInfo(LOG_TAG, "Hooking into package=" + lpparam.packageName + ", process=" + lpparam.processName);
        }

        Logger.logInfo(LOG_TAG, "uid=" + Process.myUid() + ", pid=" + Process.myPid());

        XposedApplication.setLogConfig();

       if (lpparam.packageName.equals("android")) {
           XLogcatManagerService.handleLoadPackage(lpparam);
           XLogAccessDialogActivity.handleLoadPackage(lpparam);
       }
    }

    /**
     * Deoptimize a method to avoid callee being inlined, in which case callee hook will not get
     * called even though hooking is successful. Inlining may happen when a method is only called
     * by only one method. Constructors can be inline as well.
     *
     * We use reflection to call XposedBridge.deoptimizeMethod() since its not part of original
     * de.robv.android.xposed:api implementation and is added by LSPosed.
     *
     * https://github.com/LSPosed/LSPosed/issues/1123
     * https://github.com/LSPosed/LSPosed/blob/061219363a74c05bdcddb94cf5f76815027402ab/core/src/main/java/de/robv/android/xposed/XposedBridge.java#L150
     * https://github.com/rovo89/XposedBridge/blob/v89/app/src/main/java/de/robv/android/xposed/XposedBridge.java
     *
     * @param deoptimizedMethod The method to deoptmize. Generally it should be a caller of a method
     *                          that is inlined but callee can be passed as well if not feasible to
     *                          inline the caller.
     * @return Returns {@code true} is deoptimization succeeded, otherwise {@code false}.
     */
    public static boolean deoptimizeMethod(@NonNull Member deoptimizedMethod) {
        try {
            if (mDeoptimizeMethod == null)
                mDeoptimizeMethod = XposedBridge.class.getDeclaredMethod("deoptimizeMethod", Member.class);
            mDeoptimizeMethod.invoke(null, deoptimizedMethod);
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Failed to deoptimize method: " + deoptimizedMethod);
            return false;
        }

        return true;
    }

}
