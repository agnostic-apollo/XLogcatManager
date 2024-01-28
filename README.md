# XLogcatManager

[![Build status](https://github.com/agnostic-apollo/XLogcatManager/workflows/Build/badge.svg)](https://github.com/agnostic-apollo/XLogcatManager/actions)

XLogcatManager is an Android app to improve [`LogcatManager`](https://cs.android.com/android/platform/superproject/+/android-13.0.0_r3:frameworks/base/services/core/java/com/android/server/logcat) added in Android 13 using Xposed hooks. The app requires a **rooted device** to work.

Android `13` added [`LogcatManager`](https://cs.android.com/android/platform/superproject/+/android-13.0.0_r3:frameworks/base/services/core/java/com/android/server/logcat) which shows a dialog when an app runs `logcat` command that has `READ_LOGS` permission before it is allowed to read system wide logs and access is only allowed for the next `60s`, after which approval is required again. Access dialog will only be shown if the app is on the top, even if it has a foreground service, and access will be denied for all other background apps automatically. On previous android versions, the app only needed to be granted `READ_LOGS` permission once with `adb` or `root` and then could read logs whenever it wanted.

This affects automation apps like [Tasker](https://tasker.joaoapps.com) and terminal apps like [Termux](https://github.com/termux/termux-app) which won't be able to run `logcat` commands with ease anymore, unless granted `adb` or `root` access to run `logcat` commands, which doesn't show the dialog and access is automatically granted. The tasker app in its [recent beta `v6.1.3-beta`](https://www.reddit.com/r/tasker/comments/wqvt5b/dev_tasker_613beta_progress_bar_dialog_change/) has added support to use `adb` or `root` for `logcat` commands, like run for `Logcat Entry` event, because of issues with current design.

The `XLogcatManager` xposed module was created to allow rooted users to not have to grant `adb` or `root` access to apps that shouldn't require it just to read logs by solving `LogcatManager` design issues and some bugs.

**Check the issuetracker at https://issuetracker.google.com/issues/243904932 for more details of the `LogcatManager` design and current bugs.**

Other related links are

- https://issuetracker.google.com/issues/232206670

- https://www.reddit.com/r/tasker/comments/wpt39b/dev_warning_if_you_update_to_android_13_the

- https://twitter.com/MishaalRahman/status/1559930174598270976

The module is based on currently latest Android 13 avd and pixel builds for `July/Aug 2022` and may break depending on changes or fixups made in future builds, **so use at your own risk** and keep checking the github repo for updates. If you get into a bootloop, check [magisk guide](https://topjohnwu.github.io/Magisk/faq.html#q-i-installed-a-module-and-it-bootlooped-my-device-help) for how to disable modules.
##



### Contents
- [Installation](#installation)
- [Usage](#Usage)
- [Features](#features)
##



### Installation

Latest version is `v0.1.0`.

The APK files of different sources are signed with different signature keys. Do not attempt to mix them together, i.e do not try to install the app from `Github Releases` and then from a different source like `Github Actions`. Android Package Manager will also normally not allow installation of APKs with different signatures and you will get errors on installation like `App not installed`, `Failed to install due to an unknown error`, `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE`, `signatures do not match previously installed version`, etc. This restriction can be bypassed with root or with custom roms.

If you wish to install from a different source, then you must **uninstall the existing XLogcatManager APK** from your device first, then install APK from the new source.

### Github Releases

`XLogcatManager` application official releases can be obtained from [`Github Releases`](https://github.com/agnostic-apollo/XLogcatManager/releases).

The APKs for `Github Releases` will be listed under `Assets` drop-down of a release. These are automatically attached when a new version is released.

### Github Actions

`XLogcatManager` application beta releases can be obtained from [`Github Build`](https://github.com/agnostic-apollo/XLogcatManager/actions/workflows/debug_build.yml) action workflows.

The APKs for `Github Build` action workflows will be listed under `Artifacts` section of a workflow run. These are created for each commit/push done to the repository and can be used by users who don't want to wait for releases and want to try out the latest features immediately or want to test their pull requests. Note that for action workflows, you need to be [**logged into a `Github` account**](https://github.com/login) for the `Artifacts` links to be enabled/clickable. If you are using the [`Github` app](https://github.com/mobile), then make sure to open workflow link in a browser like Chrome or Firefox that has your Github account logged in since the in-app browser may not be logged in.

The APKs are [`debuggable`](https://developer.android.com/studio/debug).
##


### Usage

- Root device with [`Magisk`](https://github.com/topjohnwu/Magisk). Optionally enable [zygisk](https://topjohnwu.github.io/Magisk/guides.html#zygisk) in its settings.

- Install [`LSPosed`](https://github.com/LSPosed/LSPosed#install). [`EdXposed`](https://github.com/ElderDrivers/EdXposed#install) is untested. `EdXposed` and other implementations will not work if they don't have `XposedBridge.deoptimizeMethod()` method implemented, check [`LSPosed/LSPosed#1123`](https://github.com/LSPosed/LSPosed/issues/1123) and [`XposedModule.deoptimizeMethod()`](https://github.com/agnostic-apollo/XLogcatManager/blob/v0.1.0/app/src/main/java/dev/agnosticapollo/xlogcatmanager/xposed/XposedModule.java#L71) for details.

- [Install `XLogcatManager`](#installation) apk and enable it in `LSPosed` modules list. Only Android `System Framework` (`android`) and `System UI` (`com.android.systemui`) needs to enabled for module scope. For running on Android `avd`, check [`XposedModule` class javadocs](https://github.com/agnostic-apollo/XLogcatManager/blob/v0.1.0/app/src/main/java/dev/agnosticapollo/xlogcatmanager/xposed/XposedModule.java#L30).

- Reboot device. Rebooting will be required whenever module is installed/updated for changes to take effect.

- If module does not work or to debug issues, check [`logcat`](https://developer.android.com/studio/command-line/logcat). Take `logcat` dump by running `adb logcat -d > logcat.txt` from a PC over `ADB` or `su -c 'logcat -d > /sdcard/logcat.txt'` in an app granted `root` access, like [`Termux`](https://github.com/termux/termux-app). Hooking is done during boot time, so run `logcat` right after boot completes to get related log entries. The `XLogcatM` is used as log tag (prefix).
##



### Features

Current features are listed below. Further configuration options can be added in future when I get time.

- Allows access till next reboot to the app if users selects allow button in the allow access dialog instead of just for the next `60s`.
- No `60s` timeout to reshow dialog if access was (accidentally) denied by user.
- The dialog will also show for apps with foreground service instead of just top apps.
- Fixes the bugs mentioned in the [issuetracker issue](https://issuetracker.google.com/issues/243904932) based on the solutions provided, but implementation is slightly different in some cases due to method hooking limitations. The main classes that do the hooking are [`XLogcatManagerService.java`](https://github.com/agnostic-apollo/XLogcatManager/blob/master/app/src/main/java/dev/agnosticapollo/xlogcatmanager/xposed/logcat/XLogcatManagerService.java) and [`XLogAccessDialogActivity.java`](https://github.com/agnostic-apollo/XLogcatManager/blob/master/app/src/main/java/dev/agnosticapollo/xlogcatmanager/xposed/logcat/XLogAccessDialogActivity.java).
##
