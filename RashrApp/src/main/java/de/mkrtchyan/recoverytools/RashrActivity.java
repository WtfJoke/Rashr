package de.mkrtchyan.recoverytools;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ads.AdRequest;
import com.google.ads.AdView;

import org.sufficientlysecure.donations.DonationsFragment;
import org.sufficientlysecure.rootcommands.Shell;
import org.sufficientlysecure.rootcommands.Toolbox;
import org.sufficientlysecure.rootcommands.util.FailedExecuteCommand;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

import de.mkrtchyan.utils.Common;
import de.mkrtchyan.utils.Downloader;
import de.mkrtchyan.utils.Notifyer;

/**
 * Copyright (c) 2015 Aschot Mkrtchyan
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
public class RashrActivity extends AppCompatActivity implements
        NavigationDrawerFragment.NavigationDrawerCallbacks {

    public static boolean isDark;
    static boolean FirstSession = true;
    static boolean LastLogExists = true;
    private final File Folder[] = {
            Const.PathToRashr, Const.PathToRecoveries, Const.PathToKernel,
            Const.PathToStockRecovery, Const.PathToCWM, Const.PathToTWRP,
            Const.PathToPhilz, Const.PathToXZDual, Const.PathToStockKernel,
            Const.PathToRecoveryBackups, Const.PathToKernelBackups, Const.PathToUtils,
            Const.PathToTmp
    };
    private final RashrActivity mActivity = this;
    private final Context mContext = this;
    /**
     * Declaring needed objects
     */
    private final ArrayList<String> mERRORS = new ArrayList<>();
    private Shell mShell;
    private Toolbox mToolbox;
    private Device mDevice;
    private Toolbar mToolbar;
    private NavigationDrawerFragment mNavigationDrawerFragment;

    private boolean mVersionChanged = false;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Const.FilesDir = mContext.getFilesDir();
        Const.RecoveryCollectionFile = new File(Const.FilesDir, "recovery_sums");
        Const.KernelCollectionFile = new File(Const.FilesDir, "kernel_sums");
        isDark = Common.getBooleanPref(mContext, Const.PREF_NAME, Const.PREF_KEY_DARK_UI);
        setTheme(!isDark ? R.style.Rashr : R.style.Rashr_Dark);
        setContentView(R.layout.loading_layout);

        final TextView tvLoading = (TextView) findViewById(R.id.tvLoading);

        final Thread StartThread = new Thread(new Runnable() {
            @Override
            public void run() {
                /** Checking if version has changed */
                final int previous_version = Common.getIntegerPref(mContext,
                        Const.PREF_NAME, Const.PREF_KEY_CUR_VER);
                final int current_version = BuildConfig.VERSION_CODE;
                mVersionChanged = current_version > previous_version;
                Common.setIntegerPref(mContext, Const.PREF_NAME,
                        Const.PREF_KEY_CUR_VER, current_version);
                /** Try to get root access */
                try {
                    startShell();
                } catch (IOException e) {
                    mActivity.addError(Const.RASHR_TAG, e, false);
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setContentView(R.layout.err_layout);
                        }
                    });
                    return;
                }

                /** Delete logs for new session */
                Common.deleteLogs(mContext);
                /** Creating needed folder and unpacking files */
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvLoading.setText(R.string.loading_data);
                    }
                });
                if (Const.PathToTmp.exists()) {
                    Common.deleteFolder(Const.PathToTmp, true);
                }
                for (File i : Folder) {
                    if (!i.exists()) {
                        if (!i.mkdir()) {
                            mActivity.addError(Const.RASHR_TAG,
                                    new IOException(i + " can't be created!"), false);
                        }
                    }
                }

                try {
                    extractFiles();
                } catch (IOException e) {
                    mActivity.addError(Const.RASHR_TAG, e, true);
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mContext, R.string.failed_unpack_files,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
                try {
                    File LogCopy = new File(mContext.getFilesDir(), Const.LastLog.getName() + ".txt");
                    mShell.execCommand(Const.Busybox + " chmod 777 " + Const.LastLog);
                    if (LogCopy.exists()) LogCopy.delete();
                    mToolbox.copyFile(Const.LastLog, LogCopy, false, false);
                    mShell.execCommand(Const.Busybox + " chmod 777 " + LogCopy);
                    LastLogExists = LogCopy.exists();
                } catch (Exception e) {
                    mActivity.addError(Const.RASHR_TAG, e, false);
                }
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        checkUpdates(current_version);
                        tvLoading.setText(R.string.reading_device);
                    }
                });
                if (mDevice == null)
                    mDevice = new Device(mActivity);

                /** If device is not supported, you can report it now or close the App */
                if (!mDevice.isRecoverySupported() && !mDevice.isKernelSupported()) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showDeviceNotSupportedDialog();
                        }
                    });
                } else {
                    Common.setBooleanPref(mContext, Const.PREF_NAME,
                            Const.PREF_KEY_SHOW_UNIFIED, true);
                    if (!Common.getBooleanPref(mContext, Const.PREF_NAME,
                            Const.PREF_KEY_FIRST_RUN)) {
                        /** Setting first start configuration */
                        Common.setBooleanPref(mContext, Const.PREF_NAME,
                                Const.PREF_KEY_HIDE_UPDATE_HINTS, false);
                        Common.setBooleanPref(mContext, Const.PREF_NAME, Const.PREF_KEY_ADS,
                                true);
                        Common.setBooleanPref(mContext, Shell.PREF_NAME, Shell.PREF_LOG, true);
                        Common.setBooleanPref(mContext, Const.PREF_NAME,
                                Const.PREF_KEY_CHECK_UPDATES, true);
                        Common.setBooleanPref(mContext, Const.PREF_NAME,
                                Const.PREF_KEY_SKIP_SIZE_CHECK, false);
                        mActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showUsageWarning();
                            }
                        });
                    }
                }
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        try {
                            View root = View.inflate(mContext, R.layout.activity_rashr, null);
                            root.startAnimation(AnimationUtils.loadAnimation(mContext,
                                    R.anim.abc_grow_fade_in_from_bottom));
                            setContentView(root);
                            mToolbar = (Toolbar) findViewById(R.id.toolbar);
                            setSupportActionBar(mToolbar);
                            //mDevice.downloadUtils(mContext);
                            mNavigationDrawerFragment = (NavigationDrawerFragment)
                                    getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
                            mNavigationDrawerFragment.setUp(R.id.navigation_drawer,
                                    (DrawerLayout) findViewById(R.id.RashrLayout));

                            AdView ads = (AdView) findViewById(R.id.ads);
                            if (ads != null) {
                                if (Common.getBooleanPref(mContext, Const.PREF_NAME,
                                        Const.PREF_KEY_ADS)) {
                                    ads.loadAd(new AdRequest()
                                            .addTestDevice("6400A1C06B921CB807E69EC539ADC588"));
                                }
                            }
                            if (getIntent().getAction().equals(Intent.ACTION_VIEW)) {
                                /** Rashr is opened by other app to flash supported files (.zip) or (.img) */
                                File file = new File(getIntent().getData().getPath());
                                if (file.exists()) {
                                    if (file.toString().endsWith(".zip")) {
                                        /** If it is a zip file open the ScriptManager */
                                        switchTo(ScriptManagerFragment.newInstance(mActivity, file));
                                    } else {
                                        /** If it is a img file open FlashAs to choose mode (recovery or kernel) */
                                        switchTo(FlashAsFragment.newInstance(mActivity, file, true));
                                    }
                                }
                            } else {
                                onNavigationDrawerItemSelected(0);
                            }
                        } catch (NullPointerException e) {
                            setContentView(R.layout.err_layout);
                            mActivity.addError(Const.RASHR_TAG, e, false);
                            AppCompatTextView tv = (AppCompatTextView) findViewById(R.id.tvErr);
                            try {
                                tv.setText(R.string.failed_setup_layout);
                            } catch (RuntimeException ex) {
                                mActivity.addError(Const.RASHR_TAG, e, true);
                                ex.printStackTrace();

                            }
                        }
                    }
                });
            }
        });
        StartThread.start();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (mNavigationDrawerFragment.isDrawerOpen()) {
                    mNavigationDrawerFragment.closeDrawer();
                } else {
                    mNavigationDrawerFragment.openDrawer();
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showUsageWarning() {
        if (mDevice.isRecoverySupported() || mDevice.isKernelSupported()) {
            final AlertDialog.Builder WarningDialog = new AlertDialog.Builder(mContext);
            WarningDialog.setTitle(R.string.warning);
            WarningDialog.setMessage(String.format(getString(R.string.bak_warning),
                    Const.PathToRecoveryBackups + " & " + Const.PathToKernelBackups));
            WarningDialog.setPositiveButton(R.string.backup, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    switchTo(BackupRestoreFragment.newInstance(mActivity));
                    Common.setBooleanPref(mContext, Const.PREF_NAME,
                            Const.PREF_KEY_FIRST_RUN, true);
                }
            });
            WarningDialog.setNegativeButton(R.string.risk, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Common.setBooleanPref(mContext, Const.PREF_NAME,
                            Const.PREF_KEY_FIRST_RUN, true);
                }
            });
            WarningDialog.setCancelable(false);
            WarningDialog.show();
        }
    }

    private void showDeviceNotSupportedDialog() {
        AlertDialog.Builder DeviceNotSupported = new AlertDialog.Builder(mContext);
        DeviceNotSupported.setTitle(R.string.warning);
        DeviceNotSupported.setMessage(R.string.not_supportded);
        DeviceNotSupported.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                exit();
            }
        });
        if (!Const.LastLog.exists()) {
            DeviceNotSupported.setNeutralButton(R.string.sReboot, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    try {
                        mToolbox.reboot(Toolbox.REBOOT_RECOVERY);
                    } catch (Exception e) {
                        mActivity.addError(Const.RASHR_TAG, e, false);
                    }
                }
            });
        }
        DeviceNotSupported.show();
    }

    private void extractFiles() throws IOException {
        Const.RecoveryCollectionFile = new File(mContext.getFilesDir(), "recovery_sums");
        Common.pushFileFromRAW(mContext, Const.RecoveryCollectionFile, R.raw.recovery_sums,
                mVersionChanged);
        Const.KernelCollectionFile = new File(mContext.getFilesDir(), "kernel_sums");
        Common.pushFileFromRAW(mContext, Const.KernelCollectionFile, R.raw.kernel_sums,
                mVersionChanged);
        Const.Busybox = new File(mContext.getFilesDir(), "busybox");
        Common.pushFileFromRAW(mContext, Const.Busybox, R.raw.busybox, mVersionChanged);
        try {
            mShell.execCommand("chmod 777 " + Const.Busybox);
        } catch (FailedExecuteCommand failedExecuteCommand) {
            failedExecuteCommand.printStackTrace();
        }
        File PartLayoutsZip = new File(mContext.getFilesDir(), "partlayouts.zip");
        Common.pushFileFromRAW(mContext, PartLayoutsZip, R.raw.partlayouts, mVersionChanged);
        File flash_image = new File(getFilesDir(), "flash_image");
        Common.pushFileFromRAW(mContext, flash_image, R.raw.flash_image, mVersionChanged);
        File dump_image = new File(getFilesDir(), "dump_image");
        Common.pushFileFromRAW(mContext, dump_image, R.raw.dump_image, mVersionChanged);
        Const.LokiPatch = new File(mContext.getFilesDir(), "loki_patch");
        Common.pushFileFromRAW(mContext, Const.LokiPatch, R.raw.loki_patch, mVersionChanged);
        Const.LokiFlash = new File(mContext.getFilesDir(), "loki_flash");
        Common.pushFileFromRAW(mContext, Const.LokiFlash, R.raw.loki_flash, mVersionChanged);
    }

    public void exit() {
        finish();
        System.exit(0);
    }

    /**
     * @return All Preferences as String
     */
    public String getAllPrefs() {
        SharedPreferences prefs = getSharedPreferences(Const.PREF_NAME, MODE_PRIVATE);
        String Prefs = "";
        Map<String, ?> prefsMap = prefs.getAll();
        try {
            for (Map.Entry<String, ?> entry : prefsMap.entrySet()) {
                /**
                 * Skip following Prefs (PREF_KEY_HISTORY, ...)
                 */
                try {
                    if (!entry.getKey().contains(Const.PREF_KEY_HISTORY)
                            && !entry.getKey().contains(Const.PREF_KEY_FLASH_COUNTER)) {
                        Prefs += entry.getKey() + ": " + entry.getValue().toString() + "\n";
                    }
                } catch (NullPointerException e) {
                    mActivity.addError(Const.RASHR_TAG, e, false);
                }
            }
        } catch (NullPointerException e) {
            mActivity.addError(Const.RASHR_TAG, e, false);
        }

        return Prefs;
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        position++;
        switch (position) {
            case 1:
                switchTo(FlashFragment.newInstance(this));
                break;
            case 2:
                switchTo(ScriptManagerFragment.newInstance(this, null));
                break;
            case 3:
                switchTo(DonationsFragment.newInstance(BuildConfig.DEBUG,
                        Const.GOOGLE_PUBKEY, Const.GOOGLE_CATALOG,
                        getResources().getStringArray(R.array.donation_google_catalog_values)));
                break;
            case 4:
                switchTo(SettingsFragment.newInstance());
                break;
        }
    }

    public Device getDevice() {
        return mDevice;
    }

    /**
     * Share instances with root access instance with all other Classes
     */
    public Shell getShell() {
        return mShell;
    }

    public Toolbox getToolbox() {
        return mToolbox;
    }

    public void addError(String TAG, final Exception e, final boolean serious) {
        mERRORS.add(TAG + ": " + (e != null ? e.toString() : ""));
        if (e != null) {
            if (serious) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ReportDialog dialog = new ReportDialog(mActivity, e.toString());
                        dialog.setCancelable(true);
                        dialog.show();
                        Notifyer.showExceptionToast(mContext, e);
                    }
                });
            }
        }
    }

    public void checkUpdates(final int currentVersion) {
        try {
            File versionsFile = new File(mContext.getFilesDir(), "version");
            Downloader downloader = new Downloader(new URL(Const.RASHR_VERSION_URL), versionsFile);
            downloader.setOverrideFile(true);
            downloader.setOnDownloadListener(new Downloader.OnDownloadListener() {
                @Override
                public void onSuccess(File file) {
                    try {
                        if (currentVersion < Integer.valueOf(Common.fileContent(file))) {
                            new AlertDialog.Builder(mContext)
                                    .setTitle(R.string.update_available)
                                    .setMessage(R.string.download_update)
                                    .setPositiveButton(R.string.open_playstore, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            startActivity(new Intent(Intent.ACTION_VIEW,
                                                    Uri.parse("market://details?id=" + getPackageName())));
                                        }
                                    })
                                    .setCancelable(false)
                                    .show();
                        } else {
                            if (Common.getBooleanPref(mContext, Const.PREF_NAME,
                                    Const.PREF_KEY_HIDE_UPDATE_HINTS)) {
                                Toast.makeText(mContext, R.string.app_uptodate, Toast.LENGTH_SHORT).show();
                            }
                        }
                    } catch (IOException ignore) {
                    }
                }

                @Override
                public void onFail(Exception e) {
                    Toast.makeText(mContext, R.string.failed_update, Toast.LENGTH_SHORT).show();
                }
            });
            downloader.download();
        } catch (MalformedURLException ignore) {
        }

    }

    public Toolbar getToolbar() {
        return mToolbar;
    }

    public void switchTo(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.abc_grow_fade_in_from_bottom,
                        R.anim.abc_shrink_fade_out_from_bottom)
                .replace(R.id.container, fragment)
                .commitAllowingStateLoss();
    }

    public ArrayList<String> getErrors() {
        return mERRORS;
    }

    private void startShell() throws IOException {
        try {
            mShell = Shell.startRootShell(mContext);
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                /** ignore root access error on Debug Rashr, use normal shell*/
                mShell = Shell.startShell();
            } else {
                throw e;
            }
        }
        mToolbox = new Toolbox(mShell);
    }
}