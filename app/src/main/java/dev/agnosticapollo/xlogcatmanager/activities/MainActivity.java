package dev.agnosticapollo.xlogcatmanager.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import dev.agnosticapollo.xlogcatmanager.XLogcatManagerConstants;
import dev.agnosticapollo.xlogcatmanager.R;
import dev.agnosticapollo.xlogcatmanager.utils.XLogcatManagerUtils;

import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.android.AndroidUtils;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.models.ReportInfo;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Logger.logDebug(LOG_TAG, "onCreate");

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // Delete ReportInfo serialized object files from cache older than 14 days
        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false);

        AppCompatActivityUtils.setToolbar(this, R.id.toolbar);
        AppCompatActivityUtils.setToolbarTitle(this, R.id.toolbar, XLogcatManagerConstants.APP_NAME, 0);

        TextView appInfo = findViewById(R.id.textview_app_info);
        appInfo.setText(getString(R.string.app_info, XLogcatManagerConstants.APP_GITHUB_REPO_URL,
                XLogcatManagerConstants.APP_GITHUB_ISSUES_REPO_URL, XLogcatManagerConstants.DEV_SUPPORT_EMAIL_URL));
    }

    @Override
    protected void onResume() {
        Logger.logDebug(LOG_TAG, "onResume");

        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_info) {
            showInfo();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    private void showInfo() {
        new Thread() {
            @Override
            public void run() {
                String title = "About";

                StringBuilder aboutString = new StringBuilder();
                aboutString.append(XLogcatManagerUtils.getAppInfoMarkdownString(MainActivity.this));
                aboutString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(MainActivity.this));

                ReportInfo reportInfo = new ReportInfo(title,
                        LOG_TAG, title);
                reportInfo.setReportString(aboutString.toString());
                reportInfo.setReportSaveFileLabelAndPath(title,
                        Environment.getExternalStorageDirectory() + "/" +
                                FileUtils.sanitizeFileName(XLogcatManagerConstants.APP_NAME + "-" + title + ".log", true, true));

                ReportActivity.startReportActivity(MainActivity.this, reportInfo);
            }
        }.start();
    }

}
