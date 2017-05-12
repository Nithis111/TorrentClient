package com.github.axet.torrentclient.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.github.axet.torrentclient.app.MainApplication;

public class BootActivity extends Activity {

    public static void createApplication(Context context) {
        Intent intent = new Intent(context, BootActivity.class);

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        context.startActivity(intent);
    }

    Thread initThread;
    Handler handler = new Handler();

    public MainApplication getApp() {
        return (MainApplication) getApplication();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initThread = new Thread(new Runnable() {
            @Override
            public void run() {
                getApp().create();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing())
                            return;
                        moveTaskToBack(true);
                    }
                });
            }
        });
        initThread.start();
    }
}