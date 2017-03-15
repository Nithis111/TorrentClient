package com.github.axet.torrentclient.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.github.axet.torrentclient.activities.BootActivity;
import com.github.axet.torrentclient.app.MainApplication;

public class OnBootReceiver extends BroadcastReceiver {
    String TAG = OnBootReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent i) {
        Log.d(TAG, "onReceive");
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (shared.getBoolean(MainApplication.PREFERENCE_START, false))
            BootActivity.createApplication(context);
    }
}
