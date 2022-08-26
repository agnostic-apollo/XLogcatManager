package dev.agnosticapollo.xlogcatmanager;

public class XLogcatManagerConstants {

    /** Dev Github organization name */
    public static final String DEV_GITHUB_ORGANIZATION_NAME = "agnostic-apollo"; // Default: "agnostic-apollo"
    /** App Github organization url */
    public static final String DEV_GITHUB_ORGANIZATION_URL = "https://github.com" + "/" + DEV_GITHUB_ORGANIZATION_NAME; // Default: "https://github.com/agnostic-apollo"

    /** F-Droid packages base url */
    public static final String FDROID_PACKAGES_BASE_URL = "https://f-droid.org/en/packages"; // Default: "https://f-droid.org/en/packages"

    /** App support email url */
    public static final String DEV_SUPPORT_EMAIL_URL = "agnosticapollo@gmail.com"; // Default: "agnosticapollo@gmail.com"
    /** App support email mailto url */
    public static final String DEV_SUPPORT_EMAIL_MAILTO_URL = "mailto:" + DEV_SUPPORT_EMAIL_URL; // Default: "mailto:agnosticapollo@gmail.com"


    /** App name */
    public static final String APP_NAME = "XLogcatManager"; // Default: "XLogcatManager"
    /** App package name */
    public static final String APP_PACKAGE_NAME = BuildConfig.APPLICATION_ID; // Default: "dev.agnosticapollo.xlogcatmanager"
    /** App name */
    public static final String DEFAULT_LOG_TAG = "XLogcatM"; // Default: "XLogcatM"
    /** App Github repo name */
    public static final String APP_GITHUB_REPO_NAME = "XLogcatManager"; // Default: "XLogcatManager"
    /** App Github repo url */
    public static final String APP_GITHUB_REPO_URL = DEV_GITHUB_ORGANIZATION_URL + "/" + APP_GITHUB_REPO_NAME; // Default: "https://github.com/agnostic-apollo/XLogcatManager"
    /** App Github issues repo url */
    public static final String APP_GITHUB_ISSUES_REPO_URL = APP_GITHUB_REPO_URL + "/issues"; // Default: "https://github.com/agnostic-apollo/XLogcatManager/issues"
    /** App F-Droid package url */
    public static final String APP_FDROID_PACKAGE_URL = FDROID_PACKAGES_BASE_URL + "/" + APP_PACKAGE_NAME; // Default: "https://f-droid.org/en/packages/dev.agnosticapollo.xlogcatmanager"



    /** Github Actions APK release */
    public static final String APK_RELEASE_GITHUB_ACTIONS = "Github Actions"; // Default: "Github Actions"

    /** Github Actions APK release signing certificate SHA-256 digest */
    public static final String APK_RELEASE_GITHUB_ACTIONS_SIGNING_CERTIFICATE_SHA256_DIGEST = "E6FE36B1561507E90EA4CD910871205BF211156ABF5BAE15061FFADF4671C2A9"; // Default: "E6FE36B1561507E90EA4CD910871205BF211156ABF5BAE15061FFADF4671C2A9"

    /** Github Releases APK release */
    public static final String APK_RELEASE_GITHUB_RELEASES = "Github Releases"; // Default: "Github Releases"

    /** Github Releases APK release signing certificate SHA-256 digest */
    public static final String APK_RELEASE_GITHUB_RELEASES_SIGNING_CERTIFICATE_SHA256_DIGEST = "44DE931B0E6937CC560F3E75F3FC951BA730E19ECAEB2DEAC65102DAD144251B"; // Default: "44DE931B0E6937CC560F3E75F3FC951BA730E19ECAEB2DEAC65102DAD144251B"



    /** Xposed module SharedPreferences file basename without extension */
    public static final String XPOSED_MODULE_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = "xposed_module_preferences"; // Default: "xposed_module_preferences"

    /** Xposed module SharedPreferences device encrypted storage file path  */
    public static final String XPOSED_MODULE_PREFERENCES_DE_FILE_PATH = "/data/user_de/0/" + APP_PACKAGE_NAME + "/shared_prefs/" + XPOSED_MODULE_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION + ".xml"; // Default: "/data/user_de/0/dev.agnosticapollo.xlogcatmanager/shared_prefs/xposed_module_preferences.xml"

    /** The Uri authority for SharedPreferences provider. */
    public static final String SHARED_PREFERENCES_PROVIDER_AUTHORITY = APP_PACKAGE_NAME + ".preferences"; // Default: "dev.agnosticapollo.xlogcatmanager.preferences"

}
