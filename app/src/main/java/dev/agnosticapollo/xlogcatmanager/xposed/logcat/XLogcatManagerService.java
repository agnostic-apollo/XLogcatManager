package dev.agnosticapollo.xlogcatmanager.xposed.logcat;

import android.app.ActivityManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.ArrayMap;

import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

import java.lang.reflect.Method;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import dev.agnosticapollo.xlogcatmanager.xposed.XposedModule;

public class XLogcatManagerService {

    private static final String LOG_TAG = "XLogcatManagerService";

    private static boolean mInvalidState = false;

    private static final String LOGCAT_SERVER_PACKAGE_NAME = "com.android.server.logcat";
    private static final String LOGCAT_MANAGER_SERVICE_CLASS_NAME = LOGCAT_SERVER_PACKAGE_NAME + ".LogcatManagerService";
    private static final String LOGCAT_MANAGER_SERVICE_INTERNAL_CLASS_NAME = LOGCAT_MANAGER_SERVICE_CLASS_NAME + "$LogcatManagerServiceInternal";
    private static final String LOG_ACCESS_CLIENT_CLASS_NAME = LOGCAT_MANAGER_SERVICE_CLASS_NAME + "$LogAccessClient";
    private static final String LOG_ACCESS_REQUEST_CLASS_NAME = LOGCAT_MANAGER_SERVICE_CLASS_NAME + "$LogAccessRequest";
    private static final String LOG_ACCESS_REQUEST_HANDLER_CLASS_NAME = LOGCAT_MANAGER_SERVICE_CLASS_NAME + "$LogAccessRequestHandler";

    private static Object mLogcatManagerService;
    private static ArrayMap<String, Object> mLogAccessClients;

    private static Handler mHandler;
    private static Map<?, ?> mLogAccessStatus;
    private static Object mActivityManagerInternal;

    private static Object mClock;
    private static Method mClockGetMethod;

    private static Method mGetUidProcessStateMethod;

    private static int MSG_APPROVE_LOG_ACCESS; // LogcatManagerService.MSG_APPROVE_LOG_ACCESS
    private static int MSG_DECLINE_LOG_ACCESS; // LogcatManagerService.MSG_DECLINE_LOG_ACCESS
    private static int MSG_PENDING_TIMEOUT; // LogcatManagerService.MSG_PENDING_TIMEOUT
    private static int MSG_LOG_ACCESS_STATUS_EXPIRED; // LogcatManagerService.MSG_LOG_ACCESS_STATUS_EXPIRED
    private static int STATUS_APPROVED; // LogcatManagerService.STATUS_APPROVED
    private static int STATUS_DECLINED; // LogcatManagerService.STATUS_DECLINED

    // https://cs.android.com/android/platform/superproject/+/android-13.0.0_r3:frameworks/base/core/java/android/app/ProcessStateEnum.aidl;l=34
    private static int PROCESS_STATE_TOP; // ActivityManager.PROCESS_STATE_TOP
    private static int PROCESS_STATE_FOREGROUND_SERVICE; // ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE

    public static void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        Logger.logInfo(LOG_TAG, "handleLoadPackage()");

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Logger.logInfo(LOG_TAG, "Cannot hook on Android < 13");
            return;
        }

        try {
            Class<?> logcatManagerServiceClazz = XposedHelpers.findClass(LOGCAT_MANAGER_SERVICE_CLASS_NAME, lpparam.classLoader);
            Class<?> logcatAccessClientClazz = XposedHelpers.findClass(LOG_ACCESS_CLIENT_CLASS_NAME, lpparam.classLoader);
            Class<?> logcatAccessRequestClazz = XposedHelpers.findClass(LOG_ACCESS_REQUEST_CLASS_NAME, lpparam.classLoader);
            Class<?> logAccessRequestHandlerClazz = XposedHelpers.findClass(LOG_ACCESS_REQUEST_HANDLER_CLASS_NAME, lpparam.classLoader);

            // Some methods are inlined and need to be deoptimized, otherwise their hooks will not
            // be called. Check XposedModule.deoptimizeMethod() for details.

            // https://cs.android.com/android/platform/superproject/+/android-13.0.0_r3:frameworks/base/services/core/java/com/android/server/logcat/LogcatManagerService.java;l=306
            XposedHelpers.findAndHookMethod(LOGCAT_MANAGER_SERVICE_CLASS_NAME,
                    lpparam.classLoader, "onStart", onStartMethodHook());

            // https://cs.android.com/android/platform/superproject/+/android-13.0.0_r3:frameworks/base/services/core/java/com/android/server/logcat/LogcatManagerService.java;l=207
            XposedHelpers.findAndHookMethod(LOGCAT_MANAGER_SERVICE_INTERNAL_CLASS_NAME,
                    lpparam.classLoader, "approveAccessForClient", int.class, String.class, approveAccessForClientMethodHook());

            // https://cs.android.com/android/platform/superproject/+/android-13.0.0_r3:frameworks/base/services/core/java/com/android/server/logcat/LogcatManagerService.java;l=216
            XposedHelpers.findAndHookMethod(LOGCAT_MANAGER_SERVICE_INTERNAL_CLASS_NAME,
                    lpparam.classLoader, "declineAccessForClient", int.class, String.class, declineAccessForClientMethodHook());

            // https://cs.android.com/android/platform/superproject/+/android-13.0.0_r3:frameworks/base/services/core/java/com/android/server/logcat/LogcatManagerService.java;l=476
            if (!XposedModule.deoptimizeMethod(logAccessRequestHandlerClazz.getDeclaredMethod("handleMessage", Message.class)))
                return;
            XposedHelpers.findAndHookMethod(LOGCAT_MANAGER_SERVICE_CLASS_NAME,
                    lpparam.classLoader, "onAccessStatusExpired", logcatAccessClientClazz, onAccessStatusExpiredMethodHook());

            // https://cs.android.com/android/platform/superproject/+/android-13.0.0_r3:frameworks/base/services/core/java/com/android/server/logcat/LogcatManagerService.java;l=328
            if (!XposedModule.deoptimizeMethod(logcatManagerServiceClazz.getDeclaredMethod("onLogAccessRequested", logcatAccessRequestClazz)))
                return;
            XposedHelpers.findAndHookMethod(LOGCAT_MANAGER_SERVICE_CLASS_NAME,
                    lpparam.classLoader, "getClientForRequest", logcatAccessRequestClazz, getClientForRequestMethodHook());

            // https://cs.android.com/android/platform/superproject/+/android-13.0.0_r3:frameworks/base/services/core/java/com/android/server/logcat/LogcatManagerService.java;l=462
            XposedHelpers.findAndHookMethod(LOGCAT_MANAGER_SERVICE_CLASS_NAME,
                    lpparam.classLoader, "scheduleStatusExpiry", logcatAccessClientClazz, scheduleStatusExpiryMethodHook());

            // https://cs.android.com/android/platform/superproject/+/android-13.0.0_r3:frameworks/base/services/core/java/com/android/server/logcat/LogcatManagerService.java;l=436
            XposedHelpers.findAndHookMethod(LOGCAT_MANAGER_SERVICE_CLASS_NAME,
                    lpparam.classLoader, "onAccessApprovedForClient", logcatAccessClientClazz, onAccessApprovedForClientMethodHook());

            // https://cs.android.com/android/platform/superproject/+/android-13.0.0_r3:frameworks/base/services/core/java/com/android/server/logcat/LogcatManagerService.java;l=449
            XposedHelpers.findAndHookMethod(LOGCAT_MANAGER_SERVICE_CLASS_NAME,
                    lpparam.classLoader, "onAccessDeclinedForClient", logcatAccessClientClazz, onAccessDeclinedForClientMethodHook());

            // https://cs.android.com/android/platform/superproject/+/android-13.0.0_r3:frameworks/base/services/core/java/com/android/server/logcat/LogcatManagerService.java;l=406
            if (!XposedModule.deoptimizeMethod(logcatManagerServiceClazz.getDeclaredMethod("processNewLogAccessRequest", logcatAccessClientClazz)))
                return;
            XposedHelpers.findAndHookMethod(LOGCAT_MANAGER_SERVICE_CLASS_NAME,
                    lpparam.classLoader, "shouldShowConfirmationDialog", logcatAccessClientClazz, shouldShowConfirmationDialogMethodHook());

            // https://cs.android.com/android/platform/superproject/+/android-13.0.0_r3:frameworks/base/services/core/java/com/android/server/logcat/LogcatManagerService.java;l=241
            XposedHelpers.findAndHookMethod(LOG_ACCESS_REQUEST_HANDLER_CLASS_NAME,
                    lpparam.classLoader, "handleMessage", Message.class, handleMessageMethodHook());
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to hook LogcatManagerService methods", t);
            mInvalidState = true;
        }

        Logger.logInfo(LOG_TAG, "Done");
    }

    private static XC_MethodHook onStartMethodHook() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (mInvalidState) return;

                    Logger.logInfo(LOG_TAG, "after:onStart()");

                    mLogcatManagerService = param.thisObject;

                    mLogAccessClients = new ArrayMap<>();

                    mHandler = (Handler) XposedHelpers.getObjectField(mLogcatManagerService, "mHandler");
                    mLogAccessStatus = (ArrayMap<?, ?>) XposedHelpers.getObjectField(mLogcatManagerService, "mLogAccessStatus");
                    mActivityManagerInternal = XposedHelpers.getObjectField(mLogcatManagerService, "mActivityManagerInternal");

                    mClock = XposedHelpers.getObjectField(mLogcatManagerService, "mClock");
                    mClockGetMethod = mClock.getClass().getDeclaredMethod("get");

                    mGetUidProcessStateMethod = mActivityManagerInternal.getClass().getDeclaredMethod("getUidProcessState", int.class);

                    MSG_APPROVE_LOG_ACCESS = XposedHelpers.getIntField(mLogcatManagerService, "MSG_APPROVE_LOG_ACCESS");
                    MSG_DECLINE_LOG_ACCESS = XposedHelpers.getIntField(mLogcatManagerService, "MSG_DECLINE_LOG_ACCESS");
                    MSG_PENDING_TIMEOUT = XposedHelpers.getIntField(mLogcatManagerService, "MSG_PENDING_TIMEOUT");
                    MSG_LOG_ACCESS_STATUS_EXPIRED = XposedHelpers.getIntField(mLogcatManagerService, "MSG_LOG_ACCESS_STATUS_EXPIRED");
                    STATUS_APPROVED = XposedHelpers.getIntField(mLogcatManagerService, "STATUS_APPROVED");
                    STATUS_DECLINED = XposedHelpers.getIntField(mLogcatManagerService, "STATUS_DECLINED");

                    PROCESS_STATE_TOP = XposedHelpers.getStaticIntField(ActivityManager.class, "PROCESS_STATE_TOP");
                    PROCESS_STATE_FOREGROUND_SERVICE = XposedHelpers.getStaticIntField(ActivityManager.class, "PROCESS_STATE_FOREGROUND_SERVICE");

                    Logger.logInfo(LOG_TAG, "MSG_APPROVE_LOG_ACCESS=" + MSG_APPROVE_LOG_ACCESS +
                            ", MSG_DECLINE_LOG_ACCESS=" + MSG_DECLINE_LOG_ACCESS +
                            ", MSG_PENDING_TIMEOUT=" + MSG_PENDING_TIMEOUT +
                            ", MSG_LOG_ACCESS_STATUS_EXPIRED=" + MSG_LOG_ACCESS_STATUS_EXPIRED +
                            ", STATUS_APPROVED=" + STATUS_APPROVED +
                            ", STATUS_DECLINED=" + STATUS_DECLINED +
                            ", PROCESS_STATE_TOP=" + PROCESS_STATE_TOP +
                            ", PROCESS_STATE_FOREGROUND_SERVICE=" + PROCESS_STATE_FOREGROUND_SERVICE);
                } catch (Throwable t) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to hook after:onStart()", t);
                    mInvalidState = true;
                }
            }
        };
    }

    private static XC_MethodHook approveAccessForClientMethodHook() {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (mInvalidState) return;

                    Logger.logInfo(LOG_TAG, "before:approveAccessForClient()");

                    if (mLogcatManagerService == null || mHandler == null) return;

                    onAccessChangeForClient(param, MSG_APPROVE_LOG_ACCESS);

                    // Prevent original method from being called
                    param.setResult(null);
                } catch (Throwable t) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to override before:approveAccessForClient()", t);
                    mInvalidState = true;
                }
            }
        };
    }

    private static XC_MethodHook declineAccessForClientMethodHook() {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (mInvalidState) return;

                    Logger.logInfo(LOG_TAG, "before:declineAccessForClient()");

                    onAccessChangeForClient(param, MSG_DECLINE_LOG_ACCESS);

                    // Prevent original method from being called
                    param.setResult(null);
                } catch (Throwable t) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to override before:declineAccessForClient()", t);
                    mInvalidState = true;
                }
            }
        };
    }

    private static void onAccessChangeForClient(XC_MethodHook.MethodHookParam param, int what) throws Throwable {
        int uid = (int) param.args[0];
        String packageName = (String) param.args[1];

        Logger.logInfo(LOG_TAG, "On access change " + what + " for uid=" + uid + ", package=" + packageName);

        Object client = getExistingClient(uid, packageName);
        if (client == null) {
            Logger.logError(LOG_TAG, "Ignoring access change " + what + " for unmanaged client");
            return;
        }

        final Message msg = mHandler.obtainMessage(what, client);
        mHandler.sendMessageAtTime(msg, (long) mClockGetMethod.invoke(mClock));
    }

    private static XC_MethodHook onAccessStatusExpiredMethodHook() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (mInvalidState) return;

                    Logger.logInfo(LOG_TAG, "after:onAccessStatusExpired()");
                    Object client = param.args[0];

                    // Remove client created in onLogAccessRequested() since its no longer needed
                    removeClient(client);
                } catch (Throwable t) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to override after:onAccessStatusExpired()", t);
                    mInvalidState = true;
                }
            }
        };
    }

    private static void removeClient(Object client) throws Throwable {
        if (client == null) return;
        int uid = XposedHelpers.getIntField(client, "mUid");
        String packageName = (String) XposedHelpers.getObjectField(client, "mPackageName");

        Logger.logInfo(LOG_TAG, "Removing existing client since status expired for it for uid=" + uid + ", package=" + packageName);
        mLogAccessClients.remove(uid + ":" + packageName);
    }

    @Nullable
    private static Object getExistingClient(int uid, String packageName) {
        return mLogAccessClients.get(uid + ":" + packageName);
    }

    private static XC_MethodHook getClientForRequestMethodHook() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (mInvalidState) return;

                    Logger.logInfo(LOG_TAG, "after:getClientForRequest()");

                    Object client = param.getResult();

                    if (client == null || mLogAccessClients == null) return;

                    int uid = XposedHelpers.getIntField(client, "mUid");
                    String packageName = (String) XposedHelpers.getObjectField(client, "mPackageName");
                    if (packageName == null || packageName.isEmpty()) return;

                    // If client already exists in mLogAccessClients, return its instance, otherwise
                    // return the new client instance being returned by original getClientForRequest() method.
                    // Handler removes messages based on `p.obj == object` instead of `equals()` check,
                    // i.e both `client` objects must have the instance/reference and not just the same
                    // values. So we don't allow multiple instances for that same uid and package combo.
                    Object existingClient = getExistingClient(uid, packageName);
                    if (existingClient != null) {
                        Logger.logInfo(LOG_TAG, "Returning existing client for uid=" + uid + ", package=" + packageName);
                        param.setResult(existingClient);
                    } else {
                        Logger.logInfo(LOG_TAG, "Returning new client for uid=" + uid + ", package=" + packageName);
                        mLogAccessClients.put(uid + ":" + packageName, client);
                    }
                } catch (Throwable t) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to override after:getClientForRequest()", t);
                    mInvalidState = true;
                }
            }
        };
    }

    private static XC_MethodHook scheduleStatusExpiryMethodHook() {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (mInvalidState) return;

                    Logger.logInfo(LOG_TAG, "before:scheduleStatusExpiry()");

                    Object client = param.args[0];
                    if (client == null || mHandler == null) return;

                    int uid = XposedHelpers.getIntField(client, "mUid");
                    String packageName = (String) XposedHelpers.getObjectField(client, "mPackageName");

                    //Logger.logInfo(LOG_TAG, "Removing existing MSG_PENDING_TIMEOUT messages for uid=" + uid + ", package=" + packageName);
                    mHandler.removeMessages(MSG_PENDING_TIMEOUT, client);

                    //Logger.logInfo(LOG_TAG, "Removing existing MSG_LOG_ACCESS_STATUS_EXPIRED messages for uid=" + uid + ", package=" + packageName);
                    mHandler.removeMessages(MSG_LOG_ACCESS_STATUS_EXPIRED, client);

                    // We prevent call to following that will revoke access after STATUS_EXPIRATION_TIMEOUT_MILLIS passes
                    // mHandler.sendMessageAtTime(mHandler.obtainMessage(MSG_LOG_ACCESS_STATUS_EXPIRED, client),
                    //     mClock.get() + STATUS_EXPIRATION_TIMEOUT_MILLIS);

                    // Prevent original method from being called
                    param.setResult(null);
                } catch (Throwable t) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to override before:scheduleStatusExpiry()", t);
                    mInvalidState = true;
                }
            }
        };
    }

    private static XC_MethodHook onAccessApprovedForClientMethodHook() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (mInvalidState) return;

                    Logger.logInfo(LOG_TAG, "after:onAccessApprovedForClient()");

                    Object client = param.args[0];
                    if (client == null || mLogAccessStatus == null) return;

                    int uid = XposedHelpers.getIntField(client, "mUid");
                    String packageName = (String) XposedHelpers.getObjectField(client, "mPackageName");

                    Object logAccessStatus = mLogAccessStatus.get(client);
                    if (logAccessStatus == null) return;
                    int status = XposedHelpers.getIntField(logAccessStatus, "mStatus");
                    if (status == STATUS_APPROVED) {
                        Logger.logInfo(LOG_TAG, "Approving logcat access for uid=" + uid + ", package=" + packageName);
                    }
                } catch (Throwable t) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to override after:onAccessApprovedForClient()", t);
                    mInvalidState = true;
                }
            }
        };
    }

    private static XC_MethodHook onAccessDeclinedForClientMethodHook() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (mInvalidState) return;

                    Logger.logInfo(LOG_TAG, "after:onAccessDeclinedForClient()");

                    Object client = param.args[0];
                    if (client == null || mHandler == null || mLogAccessStatus == null) return;

                    int uid = XposedHelpers.getIntField(client, "mUid");
                    String packageName = (String) XposedHelpers.getObjectField(client, "mPackageName");

                    // Since scheduleStatusExpiry() hook will prevent call to mHandler.sendMessageAtTime(),
                    // to permanently allow access, the dialog would also not show again if access
                    // was denied, so we send message immediately but only if access was denied,
                    // so that apps can immediately try to request access again instead of having to
                    // wait 60s. This could be abused by apps to spam, but users should only given
                    // READ_LOGS permission to trusted apps over adb/root.
                    Object logAccessStatus = mLogAccessStatus.get(client);
                    if (logAccessStatus == null) return;
                    int status = XposedHelpers.getIntField(logAccessStatus, "mStatus");
                    if (status == STATUS_DECLINED) {
                        Logger.logInfo(LOG_TAG, "Denying logcat access for uid=" + uid + ", package=" + packageName);

                        Logger.logInfo(LOG_TAG, "Removing timeout for reshowing confirmation dialog again");
                        mHandler.sendMessageAtTime(mHandler.obtainMessage(MSG_LOG_ACCESS_STATUS_EXPIRED, client), (long) mClockGetMethod.invoke(mClock));
                    }
                } catch (Throwable t) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to override after:onAccessDeclinedForClient()", t);
                    mInvalidState = true;
                }
            }
        };
    }

    private static XC_MethodHook shouldShowConfirmationDialogMethodHook() {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (mInvalidState) return;

                    Logger.logInfo(LOG_TAG, "before:shouldShowConfirmationDialog()");

                    Object client = param.args[0];

                    if (client == null || mActivityManagerInternal == null || mGetUidProcessStateMethod == null) return;

                    int uid = XposedHelpers.getIntField(client, "mUid");
                    String packageName = (String) XposedHelpers.getObjectField(client, "mPackageName");

                    int procState = (int) mGetUidProcessStateMethod.invoke(mActivityManagerInternal, uid);
                    boolean shouldShow = (procState == PROCESS_STATE_TOP || procState == PROCESS_STATE_FOREGROUND_SERVICE);

                    Logger.logInfo(LOG_TAG, "Show confirmation dialog " + (shouldShow ? "enabled" : "disabled") + " for uid=" + uid + ", package=" + packageName + ", procState=" + procState);

                    param.setResult(shouldShow);
                } catch (Throwable t) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to override before:shouldShowConfirmationDialog()", t);
                    mInvalidState = true;
                }
            }
        };
    }

    public static XC_MethodHook handleMessageMethodHook() {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (mInvalidState) return;

                    Logger.logInfo(LOG_TAG, "before:handleMessage: " + ((Message) param.args[0]).what);
                } catch (Throwable t) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to override before:handleMessage()", t);
                    mInvalidState = true;
                }
            }
        };
    }

}
