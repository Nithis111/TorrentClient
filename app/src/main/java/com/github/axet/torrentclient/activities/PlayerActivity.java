package com.github.axet.torrentclient.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.app.MainApplication;
import com.github.axet.torrentclient.app.TorrentPlayer;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class PlayerActivity extends AppCompatActivity {
    public static String CLOSE = PlayerActivity.class.getCanonicalName() + ".CLOSE";

    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private SimpleExoPlayerView mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
            close.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    TorrentPlayer.Receiver playerReceiver;
    long playerTorrent;
    View close;
    SurfaceHolder h;

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    public static void startActivity(Context context) {
        Intent intent = new Intent(context, PlayerActivity.class);
        context.startActivity(intent);
    }

    public static void closeActivity(Context context) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.setAction(CLOSE);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_player);

        String a = getIntent().getAction();
        if (a != null) {
            if (a.equals(CLOSE)) {
                finish();
                return;
            }
        }

        close = findViewById(R.id.player_close);
        final TextView playerPos = (TextView) findViewById(R.id.player_pos);
        final TextView playerDur = (TextView) findViewById(R.id.player_dur);
        final ImageView fab_play = (ImageView) findViewById(R.id.player_play);
        final SeekBar seek = (SeekBar) findViewById(R.id.player_seek);

        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                close();
            }
        });

        playerReceiver = new TorrentPlayer.Receiver(this) {
            @Override
            public void onReceive(Context context, Intent intent) {
                String a = intent.getAction();
                if (a.equals(TorrentPlayer.PLAYER_PROGRESS)) {
                    playerTorrent = intent.getLongExtra("t", -1);
                    int pos = intent.getIntExtra("pos", 0);
                    int dur = intent.getIntExtra("dur", 0);
                    boolean play = intent.getBooleanExtra("play", false);
                    playerPos.setText(MainApplication.formatDuration(context, pos));
                    playerDur.setText(MainApplication.formatDuration(context, dur));
                    if (play)
                        fab_play.setImageResource(R.drawable.ic_pause_24dp);
                    else
                        fab_play.setImageResource(R.drawable.ic_play_arrow_black_24dp);
                    seek.setMax(dur);
                    seek.setProgress(pos);
                }
                if (a.equals(TorrentPlayer.PLAYER_NEXT)) {
                    playerTorrent = intent.getLongExtra("t", -1);
                    playerPos.setText(MainApplication.formatDuration(context, 0));
                    playerDur.setText(MainApplication.formatDuration(context, 0));
                    fab_play.setImageResource(R.drawable.ic_pause_24dp);
                }
                if (a.equals(TorrentPlayer.PLAYER_STOP)) {
                    finish();
                }
            }
        };

        final MainApplication app = (MainApplication) getApplicationContext();

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = (SimpleExoPlayerView) findViewById(R.id.fullscreen_content);
        app.player.play(mContentView);

        fab_play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                app.player.pause();
            }
        });

        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (playerReceiver != null) {
            playerReceiver.close();
            playerReceiver = null;
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        close.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        close();
    }

    void close() {
        final MainApplication app = (MainApplication) getApplicationContext();
        if (app.player.isPlaying())
            app.player.pause();
        finish();
    }
}
