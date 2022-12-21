package de.fau.cs.mad.carwatch.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragmentCompat;

import org.json.JSONException;
import org.json.JSONObject;

import de.fau.cs.mad.carwatch.Constants;
import de.fau.cs.mad.carwatch.R;
import de.fau.cs.mad.carwatch.logger.LoggerUtil;
import de.fau.cs.mad.carwatch.subject.SubjectIdCheck;

public class SettingsActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        getSupportFragmentManager().beginTransaction().replace(R.id.settings_container, new SettingsFragment()).commit();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            EditTextPreference subjectIdPref = getPreferenceScreen().findPreference(Constants.PREF_SUBJECT_ID);
            if (subjectIdPref == null) {
                return;
            }
            EditTextPreference studyNamePref = getPreferenceScreen().findPreference(Constants.PREF_STUDY_NAME);
            if (studyNamePref == null) {
                return;
            }

            subjectIdPref.setOnPreferenceChangeListener(listener);
            studyNamePref.setOnPreferenceChangeListener(listener);
        }


        @Override
        public void onStart() {
            super.onStart();
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onStop() {
            super.onStop();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (Constants.PREF_SUBJECT_ID.equals(key)) {
                sharedPreferences
                        .edit()
                        .putString(key, sharedPreferences.getString(key, "").toUpperCase())
                        .apply();
            }
        }

        final OnPreferenceChangeListener listener = (preference, newValue) -> {
            if (!(newValue instanceof String)) {
                return false;
            }

            String subjectId = ((String) newValue).toUpperCase();
            String studyName = PreferenceManager.getDefaultSharedPreferences(getContext()).getString(Constants.PREF_STUDY_NAME, "");

            boolean isValid = SubjectIdCheck.isValidSubjectId(studyName, subjectId);

            if (!isValid) {
                if (getContext() == null) {
                    return false;
                }
                new AlertDialog.Builder(getContext())
                        .setCancelable(false)
                        .setTitle(getString(R.string.title_invalid_subject_id))
                        .setMessage(getString(R.string.message_invalid_subject_id))
                        .setPositiveButton(R.string.ok, (dialog, which) -> {
                        })
                        .show();
            } else {
                try {
                    JSONObject json = new JSONObject();
                    json.put(Constants.LOGGER_EXTRA_SUBJECT_ID, subjectId);
                    LoggerUtil.log(Constants.LOGGER_ACTION_SUBJECT_ID_SET, json);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return isValid;
        };
    }

}