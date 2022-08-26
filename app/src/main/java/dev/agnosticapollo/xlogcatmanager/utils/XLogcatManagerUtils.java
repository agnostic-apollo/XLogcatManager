package dev.agnosticapollo.xlogcatmanager.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.shared.android.AndroidUtils;
import com.termux.shared.android.PackageUtils;

import dev.agnosticapollo.xlogcatmanager.XLogcatManagerConstants;

public class XLogcatManagerUtils {

    /**
     * Get a markdown {@link String} for the app info.
     *
     * @param context The context for operations for the package.
     * @return Returns the markdown {@link String}.
     */
    public static String getAppInfoMarkdownString(@NonNull final Context context) {
        StringBuilder markdownString = new StringBuilder();

        markdownString.append((AndroidUtils.getAppInfoMarkdownString(context)));

        AndroidUtils.appendPropertyToMarkdown(markdownString, "FILES_DIR_PATH", context.getFilesDir().getAbsolutePath());


        String signingCertificateSHA256Digest = PackageUtils.getSigningCertificateSHA256DigestForPackage(context);
        if (signingCertificateSHA256Digest != null) {
            AndroidUtils.appendPropertyToMarkdown(markdownString,"APK_RELEASE", getAPKRelease(signingCertificateSHA256Digest));
            AndroidUtils.appendPropertyToMarkdown(markdownString,"SIGNING_CERTIFICATE_SHA256_DIGEST", signingCertificateSHA256Digest);
        }

        return markdownString.toString();
    }

    public static String getAPKRelease(String signingCertificateSHA256Digest) {
        if (signingCertificateSHA256Digest == null) return "null";

        switch (signingCertificateSHA256Digest.toUpperCase()) {
            case XLogcatManagerConstants.APK_RELEASE_GITHUB_ACTIONS_SIGNING_CERTIFICATE_SHA256_DIGEST:
                return XLogcatManagerConstants.APK_RELEASE_GITHUB_ACTIONS;
            case XLogcatManagerConstants.APK_RELEASE_GITHUB_RELEASES_SIGNING_CERTIFICATE_SHA256_DIGEST:
                return XLogcatManagerConstants.APK_RELEASE_GITHUB_RELEASES;
            default:
                return "Unknown";
        }
    }

}
