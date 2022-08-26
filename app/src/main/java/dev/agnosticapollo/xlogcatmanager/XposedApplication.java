package dev.agnosticapollo.xlogcatmanager;

import android.app.Application;

import com.termux.shared.logger.Logger;

public class XposedApplication extends Application {

    public void onCreate() {
        super.onCreate();

        // Set log config for the app
        setLogConfig();

        Logger.logDebug("Starting Application");
    }

    public static void setLogConfig() {
        Logger.setDefaultLogTag(XLogcatManagerConstants.DEFAULT_LOG_TAG);
        Logger.setLogLevel(null, Logger.MAX_LOG_LEVEL);
    }

}
