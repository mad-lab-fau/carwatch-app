package de.fau.cs.mad.carwatch.ui;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.orhanobut.logger.DiskLogAdapter;
import com.orhanobut.logger.Logger;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Objects;

import de.fau.cs.mad.carwatch.BuildConfig;
import de.fau.cs.mad.carwatch.Constants;
import de.fau.cs.mad.carwatch.R;
import de.fau.cs.mad.carwatch.alarmmanager.AlarmHandler;
import de.fau.cs.mad.carwatch.barcodedetection.BarcodeResultFragment;
import de.fau.cs.mad.carwatch.logger.GenericFileProvider;
import de.fau.cs.mad.carwatch.logger.LoggerUtil;
import de.fau.cs.mad.carwatch.subject.SubjectIdCheck;
import de.fau.cs.mad.carwatch.subject.SubjectMap;
import de.fau.cs.mad.carwatch.util.Utils;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int[] NAV_IDS = {R.id.navigation_wakeup, R.id.navigation_alarm, R.id.navigation_bedtime};

    public static DiskLogAdapter sAdapter;

    private SharedPreferences sharedPreferences;

    private CoordinatorLayout coordinatorLayout;

    private NavController navController;

    private int clickCounter = 0;
    private static final int CLICK_THRESHOLD_TOAST = 2;
    private static final int CLICK_THRESHOLD_KILL = 5;

    private AlertDialog notificationServiceDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (sharedPreferences.getBoolean(Constants.PREF_FIRST_RUN, true)) {
            // if user launched app for the first time (PREF_FIRST_RUN) => display Dialog to enter Subject ID
            showSubjectIdDialog();
        }

        clickCounter = 0;

        // disable night mode per default
        //AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        AppCompatDelegate delegate = getDelegate();
        AppCompatDelegate.setDefaultNightMode(sharedPreferences.getBoolean(Constants.PREF_NIGHT_MODE_ENABLED, false) ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        delegate.applyDayNight();

        coordinatorLayout = findViewById(R.id.coordinator);

        BottomNavigationView navView = findViewById(R.id.nav_view);

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(NAV_IDS).build();

        navController = Navigation.findNavController(this, R.id.nav_host_fragment);

        navigate();

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(navView, navController);

        if (sAdapter == null) {
            sAdapter = new DiskLogAdapter(LoggerUtil.getFormatStrategy(this)) {
                @Override
                public boolean isLoggable(int priority, @Nullable String tag) {
                    return true;
                }
            };
            Logger.addLogAdapter(sAdapter);
        }
    }

    public void navigate(int navId) {
        for (int id : NAV_IDS) {
            if (id == navId) {
                navController.navigate(navId);
                return;
            }
        }
    }

    public void navigate() {
        if (checkInterval(DateTime.now(), Constants.MORNING_TIMES)) {
            navController.navigate(R.id.navigation_wakeup);
        } else if (checkInterval(DateTime.now(), Constants.EVENING_TIMES)) {
            navController.navigate(R.id.navigation_bedtime);
        } else {
            navController.navigate(R.id.navigation_alarm);
        }
    }


    private boolean checkInterval(DateTime time, DateTime[] interval) {
        return new Interval(interval[0], interval[1]).contains(time);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Utils.allPermissionsGranted(this)) {
            Utils.requestRuntimePermissions(this);
        }

        if (isNotificationServiceEnabled()) {
            if (notificationServiceDialog != null) {
                notificationServiceDialog.dismiss();
            }
        } else {
            if (notificationServiceDialog == null) {
                notificationServiceDialog = buildNotificationServiceAlertDialog();
                notificationServiceDialog.show();
            }
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        BarcodeResultFragment.dismiss(getSupportFragmentManager());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_share:
                String studyName = sharedPreferences.getString(Constants.PREF_STUDY_NAME, null);
                String subjectId = sharedPreferences.getString(Constants.PREF_SUBJECT_ID, null);

                try {
                    File zipFile = LoggerUtil.zipDirectory(this, studyName, subjectId);
                    createFileShareDialog(zipFile);
                } catch (FileNotFoundException e) {
                    Snackbar.make(coordinatorLayout, Objects.requireNonNull(e.getMessage()), Snackbar.LENGTH_SHORT).show();
                }
                break;
            case R.id.menu_kill:
                clickCounter++;
                if (clickCounter >= CLICK_THRESHOLD_KILL) {
                    showKillWarningDialog();
                    clickCounter = 0;
                } else if (clickCounter >= CLICK_THRESHOLD_TOAST) {
                    Snackbar.make(coordinatorLayout, getString(R.string.hint_clicks_kill_alarms, (CLICK_THRESHOLD_KILL - clickCounter)), Snackbar.LENGTH_SHORT).show();
                }
                break;
            case R.id.menu_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case R.id.menu_app_info:
                showAppInfoDialog();
                break;


        }
        return super.onOptionsItemSelected(item);
    }


    @SuppressLint("InflateParams")
    private void showSubjectIdDialog() {
        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        final View dialogView = getLayoutInflater().inflate(R.layout.widget_subject_id_dialog, null);
        final EditText studyNameEditText = dialogView.findViewById(R.id.edit_text_study_name);
        final EditText subjectIdEditText = dialogView.findViewById(R.id.edit_text_subject_id);
        //subjectIdEditText.setText(sharedPreferences.getString(Constants.PREF_SUBJECT_ID, ""));

        AlertDialog warningDialog =
                new AlertDialog.Builder(this)
                        .setCancelable(false)
                        .setTitle(getString(R.string.title_invalid_subject_id))
                        .setMessage(getString(R.string.message_invalid_subject_id))
                        .setPositiveButton(R.string.ok, (dialog, which) -> {
                        })
                        .create();

        AlertDialog subjectIdDialog =
                dialogBuilder
                        .setCancelable(false)
                        .setTitle(getString(R.string.title_subject_id))
                        .setMessage(getString(R.string.message_subject_id))
                        .setView(dialogView)
                        .setPositiveButton(R.string.ok, null)
                        .create();

        subjectIdDialog.setOnShowListener(dialog -> ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String studyName = studyNameEditText.getText().toString().toLowerCase();
            String subjectId = subjectIdEditText.getText().toString().toLowerCase();

            if (SubjectIdCheck.isValidSubjectId(studyName, subjectId)) {
                sharedPreferences.edit()
                        .putBoolean(Constants.PREF_FIRST_RUN, false)
                        .putString(Constants.PREF_SUBJECT_ID, subjectId)
                        .putString(Constants.PREF_STUDY_NAME, studyName)
                        .putInt(Constants.PREF_DAY_COUNTER, 0)
                        .apply();

                try {
                    JSONObject json = new JSONObject();
                    json.put(Constants.LOGGER_EXTRA_STUDY_NAME, studyName);
                    json.put(Constants.LOGGER_EXTRA_SUBJECT_ID, subjectId);
                    json.put(Constants.LOGGER_EXTRA_SUBJECT_CONDITION, SubjectMap.getConditionForSubject(subjectId));
                    LoggerUtil.log(Constants.LOGGER_ACTION_SUBJECT_ID_SET, json);

                    logAppPhoneMetadata();
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                dialog.dismiss();
            } else {
                warningDialog.show();
            }
        }));

        subjectIdDialog.show();
    }

    private void logAppPhoneMetadata() {
        try {
            // App Metadata – Version Code and Version Name
            JSONObject json = new JSONObject();
            json.put(Constants.LOGGER_EXTRA_APP_VERSION_CODE, BuildConfig.VERSION_CODE);
            json.put(Constants.LOGGER_EXTRA_APP_VERSION_NAME, BuildConfig.VERSION_NAME);
            LoggerUtil.log(Constants.LOGGER_ACTION_APP_METADATA, json);

            // Phone Metadata – Brand, Manufacturer, Model, Android SDK level, Security Patch (if applicable), Build Release
            json = new JSONObject();
            json.put(Constants.LOGGER_EXTRA_PHONE_BRAND, Build.BRAND);
            json.put(Constants.LOGGER_EXTRA_PHONE_MANUFACTURER, Build.MANUFACTURER);
            json.put(Constants.LOGGER_EXTRA_PHONE_MODEL, Build.MODEL);
            json.put(Constants.LOGGER_EXTRA_PHONE_VERSION_SDK_LEVEL, Build.VERSION.SDK_INT);
            json.put(Constants.LOGGER_EXTRA_PHONE_VERSION_SECURITY_PATCH, Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? Build.VERSION.SECURITY_PATCH : ""); // this
            json.put(Constants.LOGGER_EXTRA_PHONE_VERSION_RELEASE, Build.VERSION.RELEASE); // this

            LoggerUtil.log(Constants.LOGGER_ACTION_PHONE_METADATA, json);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    private void showAppInfoDialog() {
        AppInfoDialog dialog = new AppInfoDialog();
        dialog.show(getSupportFragmentManager(), "app_info");
    }

    public void showKillWarningDialog() {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(getString(R.string.title_kill_alarms))
                .setMessage(getString(R.string.message_kill_alarms))
                .setPositiveButton(R.string.yes, (dialog, which) -> AlarmHandler.killAll(getApplication()))
                .setNegativeButton(R.string.cancel, ((dialog, which) -> {
                }))
                .show();
    }

    private void createFileShareDialog(File zipFile) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        Uri uri = GenericFileProvider.getUriForFile(this,
                getApplicationContext().getPackageName() +
                        ".logger.fileprovider",
                zipFile);
        sharingIntent.setType("application/octet-stream");
        sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);
        sharingIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{Constants.SHARE_EMAIL_ADDRESS});
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, zipFile.getName());
        startActivity(Intent.createChooser(sharingIntent, getString(R.string.title_share_dialog)));
    }


    /**
     * Is Notification Service Enabled.
     * Verifies if the notification listener service is enabled.
     * Got it from: https://github.com/kpbird/NotificationListenerService-Example/blob/master/NLSExample/src/main/java/com/kpbird/nlsexample/NLService.java
     *
     * @return True if enabled, false otherwise.
     */
    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(),
                Constants.SETTINGS_ENABLED_NOTIFICATION_LISTENERS);
        if (!TextUtils.isEmpty(flat)) {
            for (String name : flat.split(":")) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Build Notification Listener Alert Dialog.
     * Builds the alert dialog that pops up if the user has not turned
     * the Notification Listener Service on yet.
     *
     * @return An alert dialog which leads to the notification enabling screen
     */
    private AlertDialog buildNotificationServiceAlertDialog() {
        AlertDialog.Builder alertDialogBuilder =
                new AlertDialog.Builder(this)
                        .setCancelable(false)
                        .setTitle(R.string.notification_listener_service)
                        .setMessage(R.string.notification_listener_service_explanation)
                        .setPositiveButton(
                                getString(R.string.ok), (dialog, which) -> startActivityForResult(
                                        new Intent(Constants.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                                        Constants.REQUEST_CODE_NOTIFICATION_ACCESS)
                        );

        return alertDialogBuilder.create();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == Constants.REQUEST_CODE_NOTIFICATION_ACCESS) {
            if (notificationServiceDialog != null) {
                notificationServiceDialog.dismiss();

                if (!isNotificationServiceEnabled()) {
                    notificationServiceDialog.show();
                }
            }
        }
    }
}
