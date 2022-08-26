package dev.agnosticapollo.xlogcatmanager.xposed.logcat;

import android.app.Activity;
import android.app.Dialog;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;

import com.termux.shared.logger.Logger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import dev.agnosticapollo.xlogcatmanager.xposed.XposedModule;

public class XLogAccessDialogActivity {

    private static final String LOG_TAG = "XLogAccessDialogActivity";

    private static boolean mInvalidState = false;

    private static final String LOGCAT_SERVER_PACKAGE_NAME = "com.android.server.logcat";
    private static final String LOG_ACCESS_DIALOG_ACTIVITY_CLASS_NAME = LOGCAT_SERVER_PACKAGE_NAME + ".LogAccessDialogActivity";

    private static Handler mHandler;

    private static int MSG_DISMISS_DIALOG; // LogAccessDialogActivity.MSG_DISMISS_DIALOG

    public static void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        Logger.logInfo(LOG_TAG, "handleLoadPackage()");

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Logger.logInfo(LOG_TAG, "Cannot hook on Android < 13");
            return;
        }

        try {
            Class<?> logAccessDialogActivityClazz = XposedHelpers.findClass(LOG_ACCESS_DIALOG_ACTIVITY_CLASS_NAME, lpparam.classLoader);

            // https://cs.android.com/android/platform/superproject/+/android-13.0.0_r3:frameworks/base/services/core/java/com/android/server/logcat/LogAccessDialogActivity.java;l=64
            XposedHelpers.findAndHookMethod(LOG_ACCESS_DIALOG_ACTIVITY_CLASS_NAME,
                    lpparam.classLoader, "onCreate", Bundle.class, onCreateMethodHook());

            // https://cs.android.com/android/platform/superproject/+/android-13.0.0_r3:frameworks/base/services/core/java/com/android/server/logcat/LogAccessDialogActivity.java;l=194
            XposedHelpers.findAndHookMethod(LOG_ACCESS_DIALOG_ACTIVITY_CLASS_NAME,
                    lpparam.classLoader, "onClick", View.class, onClickMethodHook());

            // This method is not overridden by LogAccessDialogActivity so we hook method of super class android.app.Activity
            if (!XposedModule.deoptimizeMethod(logAccessDialogActivityClazz.getSuperclass().getDeclaredMethod("onStop")))
                return;
            XposedHelpers.findAndHookMethod(logAccessDialogActivityClazz.getSuperclass().getName(),
                    lpparam.classLoader, "onStop", onStopMethodHook());

        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to hook LogAccessDialogActivity methods", t);
            mInvalidState = true;
        }

        Logger.logInfo(LOG_TAG, "Done");
    }


    public static XC_MethodHook onCreateMethodHook() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (mInvalidState) return;

                    Logger.logInfo(LOG_TAG, "after:onCreate()");

                    Activity activity = (Activity) param.thisObject;

                    mHandler = (Handler) XposedHelpers.getObjectField(activity, "mHandler");
                    MSG_DISMISS_DIALOG = XposedHelpers.getIntField(activity, "MSG_DISMISS_DIALOG");

                    // Reset has_clicked for usage in onStop()
                    XposedHelpers.setAdditionalInstanceField(activity, "has_clicked", false);

                    Logger.logInfo(LOG_TAG, "MSG_DISMISS_DIALOG=" + MSG_DISMISS_DIALOG);
                } catch (Throwable t) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to override after:onCreate()", t);
                    mInvalidState = true;
                }
            }
        };
    }

    public static XC_MethodHook onClickMethodHook() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (mInvalidState) return;

                    Logger.logInfo(LOG_TAG, "after:onClick()");
                    Activity activity = (Activity) param.thisObject;

                    // Store has_clicked for usage in onStop()
                    XposedHelpers.setAdditionalInstanceField(activity, "has_clicked", true);
                } catch (Throwable t) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to override after:onClick()", t);
                    mInvalidState = true;
                }
            }
        };
    }

    public static XC_MethodHook onStopMethodHook() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (mInvalidState) return;

                    // We use afterHookedMethod() to allow super.onStop() to run
                    // We are not replacing onDestroy() currently, since no easy way to call
                    // super.onDestroy(), which if not called, throws an exception.
                    // The original method is only dismissing the dialog if its showing, but it
                    // won't run since we are doing the same here.
                    Activity activity = (Activity) param.thisObject;
                    String activityClazzName = activity.getClass().getName();

                    // Ensure we don't run code in any other inherited activity since we hooked method of android.app.Activity
                    if (!LOG_ACCESS_DIALOG_ACTIVITY_CLASS_NAME.equals(activityClazzName))
                        return;

                    Logger.logInfo(LOG_TAG, "after:onStop()");

                    int uid = XposedHelpers.getIntField(activity, "mUid");
                    String packageName = (String) XposedHelpers.getObjectField(activity, "mPackageName");

                    boolean isChangingConfigurations = activity.isChangingConfigurations();

                    // Dismiss dialog to prevent WindowLeaked exception
                    Dialog mAlert = (Dialog) XposedHelpers.getObjectField(activity, "mAlert");
                    if (mAlert != null && mAlert.isShowing()) {
                        if (isChangingConfigurations)
                            mAlert.setOnDismissListener(null);
                        mAlert.dismiss();
                    }
                    XposedHelpers.setObjectField(activity, "mAlert", null);


                    // Decline access on home button press, two apps having PROCESS_STATE_TOP like
                    // in split screen or having PROCESS_STATE_FOREGROUND_SERVICE triggering a new
                    // dialog which closes previous.
                    // We need to ensure dialog buttons was not clicked before declining access
                    // otherwise access granted by clickView() would get declined again (for future).
                    if (!isChangingConfigurations) {
                        Boolean hasClicked = (Boolean) XposedHelpers.getAdditionalInstanceField(activity, "has_clicked");
                        if(hasClicked != null && hasClicked) {
                            // If dialog was actually clicked, then remove messages for MSG_DISMISS_DIALOG
                            // sent by onCreate()
                            if  (mHandler != null) {
                                Logger.logInfo(LOG_TAG, "Removing MSG_DISMISS_DIALOG messages since dialog was clicked for uid=" + uid + ", package=" + packageName);
                                mHandler.removeMessages(MSG_DISMISS_DIALOG);
                            }
                        } else {
                            // If dialog was not clicked, then automatically decline access
                            Logger.logInfo(LOG_TAG, "Declining access since dialog not clicked for uid=" + uid + ", package=" + packageName);
                            XposedHelpers.callMethod(activity, "declineLogAccess");
                        }

                        // Finish activity hosting dialog
                        activity.finish();
                    }
                } catch (Throwable t) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to override after:onStop()", t);
                    mInvalidState = true;
                }
            }
        };
    }

}
