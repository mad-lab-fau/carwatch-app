package de.fau.cs.mad.carwatch.alarmmanager;

import android.app.Application;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;

import de.fau.cs.mad.carwatch.Constants;
import de.fau.cs.mad.carwatch.db.Alarm;
import de.fau.cs.mad.carwatch.logger.LoggerUtil;
import de.fau.cs.mad.carwatch.ui.ScannerActivity;
import de.fau.cs.mad.carwatch.util.AlarmRepository;

/**
 * BroadcastReceiver to stop alarm ringing
 */
public class AlarmStopReceiver extends BroadcastReceiver {

    private final String TAG = AlarmStopReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        int alarmId = intent.getIntExtra(Constants.EXTRA_ALARM_ID, Constants.EXTRA_ALARM_ID_DEFAULT);
        int salivaId = intent.getIntExtra(Constants.EXTRA_SALIVA_ID, Constants.EXTRA_SALIVA_ID_DEFAULT);

        AlarmSource alarmSource = (AlarmSource) intent.getSerializableExtra(Constants.EXTRA_SOURCE);
        if (alarmSource == null) {
            // this should never happen!
            alarmSource = AlarmSource.SOURCE_UNKNOWN;
        }

        AlarmRepository repository = AlarmRepository.getInstance((Application) context.getApplicationContext());
        try {
            Alarm alarm = repository.getAlarmById(alarmId);
            if (alarm != null) {
                alarm.setActive(false);
                if (alarm.hasHiddenTime()) {
                    alarm.setHiddenDelta(0);
                }
                repository.updateActive(alarm);
            }
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }

        AlarmSoundControl alarmSoundControl = AlarmSoundControl.getInstance();
        alarmSoundControl.stopAlarmSound();

        try {
            // create Json object and log information
            JSONObject json = new JSONObject();
            json.put(Constants.LOGGER_EXTRA_ALARM_ID, alarmId);
            json.put(Constants.LOGGER_EXTRA_ALARM_SOURCE, alarmSource.ordinal());
            json.put(Constants.LOGGER_EXTRA_SALIVA_ID, salivaId);
            LoggerUtil.log(Constants.LOGGER_ACTION_ALARM_STOP, json);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "Stopping Alarm: " + alarmId);

        // Dismiss notification
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancelAll();
        }

        Intent scannerIntent = new Intent(context, ScannerActivity.class);
        scannerIntent.putExtra(Constants.EXTRA_ALARM_ID, alarmId);
        scannerIntent.putExtra(Constants.EXTRA_SALIVA_ID, salivaId);
        scannerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(scannerIntent);

        TimerHandler.scheduleSalivaCountdown(context, alarmId, salivaId);
    }
}