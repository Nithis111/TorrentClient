package com.github.axet.torrentclient.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
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
    public static String RESUME = PlayerActivity.class.getCanonicalName() + ".RESUME";

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
    private final Handler handler = new Handler();
    private SimpleExoPlayerView exoplayer;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            frame.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    controls.setBackgroundColor(Color.BLACK);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            controls.setBackgroundColor(Color.TRANSPARENT);
                        }
                    });
                }
            }, 1000);
        }
    };
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            controls2.setVisibility(View.VISIBLE);
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
    View controls;
    View controls2;
    View frame;
    TorrentPlayer player;
    int playingIndex;

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
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    public static void closeActivity(Context context) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.setAction(CLOSE);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(R.layout.activity_player);

        String a = getIntent().getAction();
        if (a != null) {
            if (a.equals(CLOSE)) {
                player = null;
                finish();
                return;
            }
        }

        final MainApplication app = (MainApplication) getApplicationContext();

        player = app.player;
        playingIndex = player.getPlaying();

        exoplayer = (SimpleExoPlayerView) findViewById(R.id.fullscreen_content);
        close = findViewById(R.id.player_close);
        controls = findViewById(R.id.player_controls);
        controls2 = findViewById(R.id.player_controls2);

        final TextView playerPos = (TextView) findViewById(R.id.player_pos);
        final TextView playerDur = (TextView) findViewById(R.id.player_dur);
        final ImageView fab_prev = (ImageView) findViewById(R.id.player_prev);
        final ImageView fab_next = (ImageView) findViewById(R.id.player_next);
        final ImageView fab_play = (ImageView) findViewById(R.id.player_play);
        final SeekBar seek = (SeekBar) findViewById(R.id.player_seek);

        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        fab_prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TorrentPlayer p = player;
                int i = p.getPlaying();
                i = i - 1;
                if (i < 0)
                    i = p.getSize() - 1;
                p.play(i);
                p.notifyNext();
            }
        });

        fab_next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TorrentPlayer p = player;
                int i = p.getPlaying();
                i = i + 1;
                if (i >= p.getSize())
                    i = 0;
                p.play(i);
                p.notifyNext();
            }
        });

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    player.seek(progress);
                    playerPos.setText(MainApplication.formatDuration(PlayerActivity.this, progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        playerReceiver = new TorrentPlayer.Receiver(this) {
            @Override
            public void onReceive(Context context, Intent intent) {
                String a = intent.getAction();
                if (a.equals(TorrentPlayer.PLAYER_PROGRESS)) {
                    playerTorrent = intent.getLongExtra("t", -1);
                    long pos = intent.getLongExtra("pos", 0);
                    long dur = intent.getLongExtra("dur", 0);
                    boolean play = intent.getBooleanExtra("play", false);
                    playerPos.setText(MainApplication.formatDuration(context, pos));
                    playerDur.setText(MainApplication.formatDuration(context, dur));
                    if (play)
                        fab_play.setImageResource(R.drawable.ic_pause_24dp);
                    else
                        fab_play.setImageResource(R.drawable.ic_play_arrow_black_24dp);
                    seek.setMax((int) dur);
                    seek.setProgress((int) pos);
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

        mVisible = true;
        player.play(exoplayer);

        fab_play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (player.isPlaying()) {
                    player.pause();
                } else {
                    player.resume();
                }
            }
        });

        frame = findViewById(R.id.player_frame);
        frame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        close();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
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
        controls2.setVisibility(View.GONE);
        mVisible = false;
        // Schedule a runnable to remove the status and navigation bar after a delay
        handler.removeCallbacks(mShowPart2Runnable);
        handler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        frame.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        handler.removeCallbacks(mHidePart2Runnable);
        handler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        handler.removeCallbacks(mHideRunnable);
        handler.postDelayed(mHideRunnable, delayMillis);
    }

    @Override
    public void finish() {
        super.finish();
        MainActivity.startActivity(this);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        String a = intent.getAction();
        if (a != null) {
            if (a.equals(CLOSE)) {
                player = null;
                finish();
                return;
            }
        }

        if (player != null)
            player.play(exoplayer);
        delayedHide(100);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null)
            player.hide(exoplayer);
    }

    void close() {
        if (player != null) {
            player.close(exoplayer);
            player = null;
        }
        if (playerReceiver != null) {
            playerReceiver.close();
            playerReceiver = null;
        }
    }
}
