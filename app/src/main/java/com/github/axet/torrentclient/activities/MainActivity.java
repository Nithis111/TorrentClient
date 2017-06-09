package com.github.axet.torrentclient.activities;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.Adapter;
import android.widget.HeaderViewListAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.github.axet.androidlibrary.widgets.HeaderGridView;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.app.Drawer;
import com.github.axet.torrentclient.app.EnginesManager;
import com.github.axet.torrentclient.app.MainApplication;
import com.github.axet.torrentclient.app.Storage;
import com.github.axet.torrentclient.app.TorrentPlayer;
import com.github.axet.torrentclient.dialogs.AddDialogFragment;
import com.github.axet.torrentclient.dialogs.CreateDialogFragment;
import com.github.axet.torrentclient.dialogs.OpenIntentDialogFragment;
import com.github.axet.torrentclient.dialogs.RatesDialogFragment;
import com.github.axet.torrentclient.dialogs.TorrentDialogFragment;
import com.github.axet.torrentclient.navigators.Torrents;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import libtorrent.Libtorrent;

public class MainActivity extends AppCompatActivity implements AbsListView.OnScrollListener,
        DialogInterface.OnDismissListener, SharedPreferences.OnSharedPreferenceChangeListener {
    public final static String TAG = MainActivity.class.getSimpleName();

    public int scrollState;

    Runnable refresh;
    Runnable refreshUI;
    TorrentFragmentInterface dialog;

    Torrents torrents;
    ProgressBar progress;
    HeaderGridView list;
    View empty;
    Handler handler;

    Drawer drawer;

    int themeId;

    // not delared locally - used from two places
    FloatingActionsMenu fab;
    FloatingActionButton create;
    FloatingActionButton add;

    TextView freespace;
    ImageView turtle;

    // delayedIntent delayedIntent
    Intent delayedIntent;
    Runnable delayedInit;

    BroadcastReceiver screenreceiver;

    EnginesManager engies;

    public long playerTorrent;
    TorrentPlayer.Receiver playerReceiver;
    View fab_panel;
    android.support.design.widget.FloatingActionButton fab_play;
    View fab_stop;
    TextView fab_status;

    public static void startActivity(Context context) {
        Intent i = new Intent(context, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(i);
    }

    public interface TorrentFragmentInterface {
        void update();

        void close();
    }

    public interface NavigatorInterface {
        void install(HeaderGridView list);

        void remove(HeaderGridView list);
    }

    public void checkTorrent(long t) {
        if (Libtorrent.torrentStatus(t) == Libtorrent.StatusChecking) {
            Libtorrent.stopTorrent(t);
            Toast.makeText(this, R.string.stop_checking, Toast.LENGTH_SHORT).show();
            return;
        }
        Libtorrent.checkTorrent(t);
        Toast.makeText(this, R.string.start_checking, Toast.LENGTH_SHORT).show();
    }

    public void renameDialog(final Long f) {
        final OpenFileDialog.EditTextDialog e = new OpenFileDialog.EditTextDialog(this);
        e.setTitle(getString(R.string.rename_torrent));
        e.setText(Libtorrent.torrentName(f));
        e.setPositiveButton(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = e.getText();
                // clear slashes
                name = new File(name).getName();
                if (name.isEmpty())
                    return;
                Libtorrent.torrentRename(f, name);
                torrents.notifyDataSetChanged();
            }
        });
        e.show();
    }

    public MainApplication getApp() {
        return (MainApplication) getApplication();
    }

    public void setAppTheme(int id) {
        super.setTheme(id);
        themeId = id;
    }

    int getAppTheme() {
        return MainApplication.getTheme(this, R.style.AppThemeLight_NoActionBar, R.style.AppThemeDark_NoActionBar);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setAppTheme(getAppTheme());
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(R.layout.app_bar_main);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawer = new Drawer(this, toolbar);

        engies = new EnginesManager(this);

        handler = new Handler();

        fab = (FloatingActionsMenu) findViewById(R.id.fab);

        create = (FloatingActionButton) findViewById(R.id.torrent_create_button);
        create.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final OpenFileDialog f = new OpenFileDialog(MainActivity.this, OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG);

                String path = "";

                final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

                if (path == null || path.isEmpty()) {
                    path = MainApplication.getPreferenceLastPath(MainActivity.this);
                }

                f.setCurrentPath(new File(path));
                f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File p = f.getCurrentPath();

                        shared.edit().putString(MainApplication.PREFERENCE_LAST_PATH, p.getParent()).commit();

                        final ProgressDialog progress = new ProgressDialog(MainActivity.this);
                        progress.setOwnerActivity(MainActivity.this);
                        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);

                        String path = p.getPath();
                        File parent = new File(path).getParentFile();
                        if (parent == null) {
                            parent = new File("/");
                            path = ".";
                        }
                        try {
                            File f = new File(path);
                            path = f.getCanonicalPath(); // resolve symlink
                        } catch (IOException e) {
                            // ignore
                        }
                        final String pp = parent.getPath();
                        final AtomicLong pieces = new AtomicLong(Libtorrent.createMetaInfo(path));
                        if (pieces.get() == -1) {
                            Error(Libtorrent.error());
                            return;
                        }
                        final AtomicLong i = new AtomicLong(0);
                        progress.setMax((int) pieces.get());

                        final Thread t = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                final MainActivity activity = MainActivity.this;

                                for (i.set(0); i.get() < pieces.get(); i.incrementAndGet()) {
                                    Thread.yield();

                                    if (Thread.currentThread().isInterrupted()) {
                                        Libtorrent.closeMetaInfo();
                                        handler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (activity.isFinishing())
                                                    return;
                                                progress.dismiss();
                                            }
                                        });
                                        return;
                                    }

                                    if (!Libtorrent.hashMetaInfo(i.get())) {
                                        activity.post(Libtorrent.error());
                                        Libtorrent.closeMetaInfo();
                                        handler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (activity.isFinishing())
                                                    return;
                                                progress.dismiss();
                                            }
                                        });
                                        return;
                                    }
                                }

                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (activity.isFinishing())
                                            return;
                                        MainActivity.this.dialog = null;
                                        activity.createTorrentFromMetaInfo(pp);
                                        Libtorrent.closeMetaInfo();
                                        progress.dismiss();
                                    }
                                });
                            }
                        }, "Create Torrent Thread");

                        if (MainActivity.this.dialog != null)
                            MainActivity.this.dialog.close();
                        MainActivity.this.dialog = new TorrentFragmentInterface() {
                            @Override
                            public void update() {
                                progress.setProgress((int) i.get());
                            }

                            @Override
                            public void close() {
                                t.interrupt();
                            }
                        };

                        progress.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                t.interrupt();
                            }
                        });
                        progress.setOnShowListener(new DialogInterface.OnShowListener() {
                            @Override
                            public void onShow(DialogInterface dialog) {
                                t.start();
                            }
                        });
                        progress.show();
                    }
                });
                f.show();
                fab.collapse();
            }
        });

        add = (FloatingActionButton) findViewById(R.id.torrent_add_button);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final OpenFileDialog f = new OpenFileDialog(MainActivity.this, OpenFileDialog.DIALOG_TYPE.FILE_DIALOG);

                String path = "";

                final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

                if (path == null || path.isEmpty()) {
                    path = MainApplication.getPreferenceLastPath(MainActivity.this);
                }

                f.setCurrentPath(new File(path));
                f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File p = f.getCurrentPath();

                        shared.edit().putString(MainApplication.PREFERENCE_LAST_PATH, p.getParent()).commit();

                        long t = Libtorrent.addTorrent(p.getPath());

                        if (t == -1) {
                            Error(Libtorrent.error());
                            return;
                        }

                        addTorrentDialog(t, p.getParent());
                    }
                });
                f.show();
                fab.collapse();
            }
        });

        FloatingActionButton magnet = (FloatingActionButton) findViewById(R.id.torrent_magnet_button);
        magnet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final OpenFileDialog.EditTextDialog f = new OpenFileDialog.EditTextDialog(MainActivity.this);
                f.setTitle(getString(R.string.add_magnet));
                f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String ff = f.getText();
                        addMagnet(ff);
                    }
                });
                f.show();
                fab.collapse();
            }
        });

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        progress = (ProgressBar) findViewById(R.id.progress);

        list = (HeaderGridView) findViewById(R.id.list);
        empty = findViewById(R.id.empty_list);

        fab.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
        fab.setVisibility(View.GONE);

        freespace = (TextView) findViewById(R.id.space_left);
        turtle = (ImageView) findViewById(R.id.turtle);

        freespace.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRates();
            }
        });
        turtle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                boolean b = shared.getBoolean(MainApplication.PREFERENCE_SPEEDLIMIT, false);
                b = !b;
                boolean bb = shared.getInt(MainApplication.PREFERENCE_UPLOAD, -1) == -1 && shared.getInt(MainApplication.PREFERENCE_DOWNLOAD, -1) == -1;
                if (b && bb) {
                    showRates();
                    return;
                }
                SharedPreferences.Editor edit = shared.edit();
                edit.putBoolean(MainApplication.PREFERENCE_SPEEDLIMIT, b);
                edit.commit();
            }
        });

        screenreceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    Log.d(TAG, "Screen OFF");
                    if (drawer.isDrawerOpen()) {
                        drawer.closeDrawer();
                    }
                    moveTaskToBack(true);
                }
            }
        };
        IntentFilter screenfilter = new IntentFilter();
        screenfilter.addAction(Intent.ACTION_SCREEN_ON);
        screenfilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenreceiver, screenfilter);

        delayedIntent = getIntent();

        // UI thread
        delayedInit = new Runnable() {
            @Override
            public void run() {
                progress.setVisibility(View.GONE);
                list.setVisibility(View.VISIBLE);
                fab.setVisibility(View.VISIBLE);

                invalidateOptionsMenu();

                list.setOnScrollListener(MainActivity.this);
                list.setEmptyView(empty);

                final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                shared.registerOnSharedPreferenceChangeListener(MainActivity.this);

                torrents = new Torrents(MainActivity.this, list);
                show(torrents);

                if (Storage.permitted(MainActivity.this, PERMISSIONS, 1)) {
                    try {
                        getStorage().migrateLocalStorage();
                    } catch (RuntimeException e) {
                        Error(e);
                    }
                } else {
                    // with no permission we can't choise files to 'torrent', or select downloaded torrent
                    // file, since we have no persmission to user files.
                    create.setVisibility(View.GONE);
                    add.setVisibility(View.GONE);
                }

                // update unread icon after torrents created
                drawer.updateUnread();

                drawer.updateManager();

                if (delayedIntent != null) {
                    openIntent(delayedIntent);
                    delayedIntent = null;
                }
            }
        };

        updateHeader(new Storage(this));

        getApp().createThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing())
                    return;
                if (delayedInit != null) {
                    delayedInit.run();
                    delayedInit = null;
                }
            }
        });


        fab_panel = findViewById(R.id.fab_panel);
        fab_status = (TextView) findViewById(R.id.fab_status);
        fab_play = (android.support.design.widget.FloatingActionButton) findViewById(R.id.fab_play);
        fab_stop = findViewById(R.id.fab_stop);
        fab_panel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dialog != null)
                    return;
                TorrentDialogFragment d = TorrentDialogFragment.create(playerTorrent);
                dialog = d;
                d.show(getSupportFragmentManager(), "");
            }
        });
        fab_play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getApp().player.pause();
            }
        });
        fab_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getApp().player.close();
            }
        });
        fab_panel.setVisibility(View.GONE);

        playerReceiver = new TorrentPlayer.Receiver(this) {
            @Override
            public void onReceive(Context context, Intent intent) {
                String a = intent.getAction();
                if (a.equals(TorrentPlayer.PLAYER_PROGRESS)) {
                    fab_panel.setVisibility(View.VISIBLE);
                    playerTorrent = intent.getLongExtra("t", -1);
                    int pos = intent.getIntExtra("pos", 0);
                    int dur = intent.getIntExtra("dur", 0);
                    boolean play = intent.getBooleanExtra("play", false);
                    fab_status.setText(TorrentPlayer.formatHeader(MainActivity.this, pos, dur));
                    if (play)
                        fab_play.setImageResource(R.drawable.ic_pause_24dp);
                    else
                        fab_play.setImageResource(R.drawable.ic_play_arrow_black_24dp);
                }
                if (a.equals(TorrentPlayer.PLAYER_NEXT)) {
                    fab_panel.setVisibility(View.VISIBLE);
                    playerTorrent = intent.getLongExtra("t", -1);
                    fab_status.setText(TorrentPlayer.formatHeader(MainActivity.this, 0, 0));
                    fab_play.setImageResource(R.drawable.ic_pause_24dp);
                }
                if (a.equals(TorrentPlayer.PLAYER_STOP)) {
                    fab_panel.setVisibility(View.GONE);
                }
            }
        };
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(MainApplication.PREFERENCE_ANNOUNCE)) {
            Libtorrent.setDefaultAnnouncesList(sharedPreferences.getString(MainApplication.PREFERENCE_ANNOUNCE, ""));
        }
        if (key.equals(MainApplication.PREFERENCE_WIFI)) {
            if (!sharedPreferences.getBoolean(MainApplication.PREFERENCE_WIFI, true)) {
                getStorage().resume(); // wifi only disabled
            } else { // wifi only enabed
                if (!Storage.isConnectedWifi(this)) // are we on wifi?
                    getStorage().pause(); // no, pause all
            }
        }
        if (key.equals(MainApplication.PREFERENCE_SPEEDLIMIT)) {
            updateHeader(getStorage());
            getStorage().updateRates();
        }
        if (key.equals(MainApplication.PREFERENCE_UPLOAD) || key.equals(MainApplication.PREFERENCE_DOWNLOAD)) {
            getStorage().updateRates();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.menu_main, menu);

        KeyguardManager myKM = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (myKM.inKeyguardRestrictedInputMode() || delayedInit != null) {
            menu.findItem(R.id.action_settings).setVisible(false);
            menu.findItem(R.id.action_show_folder).setVisible(false);
        }

        File path = getStorage().getStoragePath();
        Intent intent = openFolderIntent(path);
        if (intent.resolveActivityInfo(getPackageManager(), 0) == null) {
            menu.findItem(R.id.action_show_folder).setVisible(false);
        }

        return true;
    }

    public void close() {
        // prevent delayed delayedInit
        delayedInit = null;

        if (dialog != null) {
            dialog.close();
            dialog = null;
        }

        if (engies != null) {
            engies.save();
            engies.close();
            engies = null;
        }

        refreshUI = null;

        if (refresh != null) {
            handler.removeCallbacks(refresh);
            refresh = null;
        }

        if (torrents != null) {
            torrents.close();
            torrents = null;
        }

        Storage s = getStorage();
        if (s != null)
            s.save();

        if (screenreceiver != null) {
            unregisterReceiver(screenreceiver);
            screenreceiver = null;
        }

        if (playerReceiver != null) {
            playerReceiver.close();
            playerReceiver = null;
        }

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        shared.unregisterOnSharedPreferenceChangeListener(MainActivity.this);

        // do not close storage when mainactivity closes. it may be restarted due to theme change.
        // only close it on shutdown()
        // getApp().close();
    }

    public void shutdown() {
        close();
        getApp().close();
        if (Build.VERSION.SDK_INT >= 16)
            finishAffinity();
        else
            finish();
        ExitActivity.exitApplication(this);
    }

    public void post(final Throwable e) {
        Log.e(TAG, "Exception", e);
        Throwable t = e;
        while (t.getCause() != null)
            t = t.getCause();
        post(t.getClass().getCanonicalName() + ": " + t.getMessage());
    }

    public void post(final String msg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (isFinishing())
                    return;
                Error(msg);
            }
        });
    }

    public AlertDialog Error(Throwable e) {
        Log.e(TAG, "Exception", e);
        Throwable t = e;
        while (t.getCause() != null)
            t = t.getCause();
        return Error(t.getClass().getCanonicalName() + ": " + t.getMessage());
    }

    public AlertDialog Error(String err) {
        Log.e(TAG, Libtorrent.error());

        return new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.error)
                .setMessage(err)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar base clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        if (id == R.id.action_shutdown) {
            shutdown();
            return true;
        }

        if (id == R.id.action_show_folder) {
            openFolder(getStorage().getStoragePath());
        }

        return super.onOptionsItemSelected(item);
    }

    public static Intent openFolderIntent(File file) {
        Uri selectedUri = Uri.fromFile(file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(selectedUri, "resource/folder");
        return intent;
    }

    public void openFolder(File file) {
        Intent intent = openFolderIntent(file);
        if (intent.resolveActivityInfo(getPackageManager(), 0) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.no_folder_app, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        invalidateOptionsMenu();

        // update if keyguard enabled or not
        drawer.updateManager();

        KeyguardManager myKM = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (myKM.inKeyguardRestrictedInputMode()) {
            add.setVisibility(View.GONE);
            create.setVisibility(View.GONE);
        } else {
            if (Storage.permitted(this, PERMISSIONS)) {
                add.setVisibility(View.VISIBLE);
                create.setVisibility(View.VISIBLE);
            }
        }

        if (themeId != getAppTheme()) {
            finish();
            MainActivity.startActivity(this);
            return;
        }

        refreshUI = new Runnable() {
            @Override
            public void run() {
                torrents.notifyDataSetChanged();

                if (dialog != null)
                    dialog.update();

                ListAdapter a = list.getAdapter();
                if (a != null && a instanceof HeaderViewListAdapter) {
                    a = ((HeaderViewListAdapter) a).getWrappedAdapter();
                }
                if (a instanceof TorrentFragmentInterface) {
                    ((TorrentFragmentInterface) a).update();
                }
            }
        };

        refresh = new Runnable() {
            @Override
            public void run() {
                handler.removeCallbacks(refresh);
                handler.postDelayed(refresh, 1000);

                if (delayedInit != null)
                    return;

                Storage s = getStorage();

                if (s == null) { // sholud never happens, unless if onResume called after shutdown()
                    return;
                }

                s.update();
                updateHeader(s);

                torrents.updateStorage();

                if (refreshUI != null)
                    refreshUI.run();
            }
        };
        refresh.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");

        refreshUI = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                if (Storage.permitted(this, permissions)) {
                    try {
                        getStorage().migrateLocalStorage();
                    } catch (RuntimeException e) {
                        Error(e);
                    }
                    create.setVisibility(View.VISIBLE);
                    add.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(this, R.string.not_permitted, Toast.LENGTH_SHORT).show();
                }
        }
    }

    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        this.scrollState = scrollState;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen()) {
            drawer.closeDrawer();
        } else {
            moveTaskToBack(true);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (delayedInit == null)
                    list.smoothScrollToPosition(torrents.getSelected());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestory");
        close();
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }


    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        if (dialogInterface instanceof AddDialogFragment.Result) {
            AddDialogFragment.Result r = (AddDialogFragment.Result) dialogInterface;
            if (r.ok) {
                torrentUnread(getStorage().find(r.t));
                updateUnread();
            }
        }
        if (dialog != null)
            dialog.close();
        dialog = null;
        ListAdapter a = list.getAdapter();
        if (a != null && a instanceof HeaderGridView.HeaderViewGridAdapter) {
            a = ((HeaderGridView.HeaderViewGridAdapter) a).getWrappedAdapter();
        }
        if (a instanceof DialogInterface.OnDismissListener) {
            ((DialogInterface.OnDismissListener) a).onDismiss(dialogInterface);
        }
    }

    void updateHeader(Storage s) {
        freespace.setText(s.formatHeader());

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        if (shared.getBoolean(MainApplication.PREFERENCE_SPEEDLIMIT, false))
            turtle.setColorFilter(0xff017aff);
        else
            turtle.setColorFilter(Color.GRAY);

        drawer.updateHeader();
    }

    public Storage getStorage() {
        return getApp().getStorage();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (delayedInit == null)
            openIntent(intent);
        else
            this.delayedIntent = intent;
    }

    void openIntent(Intent intent) {
        if (intent == null)
            return;

        Uri openUri = intent.getData();
        if (openUri == null)
            return;

        OpenIntentDialogFragment dialog = new OpenIntentDialogFragment();

        Bundle args = new Bundle();
        args.putString("url", openUri.toString());

        dialog.setArguments(args);
        dialog.show(getSupportFragmentManager(), "");
    }

    public void addMagnet(String ff) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        addMagnet(ff, shared.getBoolean(MainApplication.PREFERENCE_DIALOG, false));
    }

    public void addMagnet(String ff, boolean dialog) {
        try {
            List<String> m = getStorage().splitMagnets(ff);

            if (dialog && m.size() == 1) {
                String s = m.get(0);

                if (engies.addManget(s))
                    return;

                String p = getStorage().getStoragePath().getPath();
                long t = Libtorrent.addMagnet(p, s);
                if (t == -1) {
                    throw new RuntimeException(Libtorrent.error());
                }

                addTorrentDialog(t, p);
                return;
            } else {
                for (String s : m) {
                    if (!engies.addManget(s)) {
                        Storage.Torrent tt = getStorage().addMagnet(s);
                        torrentUnread(tt);
                        Toast.makeText(MainActivity.this, getString(R.string.added) + " " + tt.name(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        } catch (RuntimeException e) {
            Error(e);
        }
        torrents.notifyDataSetChanged();
        updateUnread();
    }

    public void addTorrentFromBytes(byte[] buf) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        addTorrentFromBytes(buf, shared.getBoolean(MainApplication.PREFERENCE_DIALOG, false));
    }

    public void addTorrentFromBytes(byte[] buf, boolean dialog) {
        try {
            if (dialog) {
                String s = getStorage().getStoragePath().getPath();
                long t = Libtorrent.addTorrentFromBytes(s, buf);
                if (t == -1) {
                    throw new RuntimeException(Libtorrent.error());
                }
                addTorrentDialog(t, s);
            } else {
                Storage.Torrent tt = getStorage().addTorrentFromBytes(buf);
                torrentUnread(tt);
                Toast.makeText(MainActivity.this, getString(R.string.added) + " " + tt.name(), Toast.LENGTH_SHORT).show();
            }
        } catch (RuntimeException e) {
            Error(e);
        }
        torrents.notifyDataSetChanged();
        updateUnread();
    }

    void addTorrentDialog(long t, String path) {
        AddDialogFragment fragment = new AddDialogFragment();

        dialog = fragment;

        Bundle args = new Bundle();
        args.putLong("torrent", t);
        args.putString("path", path);

        fragment.setArguments(args);

        fragment.show(getSupportFragmentManager(), "");
    }

    void createTorrentDialog(long t, String path) {
        CreateDialogFragment fragment = new CreateDialogFragment();

        dialog = fragment;

        Bundle args = new Bundle();
        args.putLong("torrent", t);
        args.putString("path", path);

        fragment.setArguments(args);

        fragment.show(getSupportFragmentManager(), "");
    }

    public void createTorrentFromMetaInfo(String pp) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        final long t = Libtorrent.createTorrentFromMetaInfo();
        if (t == -1) {
            Error(Libtorrent.error());
            return;
        }
        if (shared.getBoolean(MainApplication.PREFERENCE_DIALOG, false)) {
            createTorrentDialog(t, pp);
        } else {
            Storage.Torrent tt = new Storage.Torrent(this, t, pp, true);
            getStorage().add(tt);
            torrentUnread(tt);
            torrents.notifyDataSetChanged();
            updateUnread();
        }
    }

    public boolean active(ListAdapter s) {
        Adapter a = list.getAdapter();
        if (a != null && a instanceof HeaderGridView.HeaderViewGridAdapter) {
            a = ((HeaderGridView.HeaderViewGridAdapter) a).getWrappedAdapter();
        }
        return s == a;
    }

    public void updateUnread() {
        drawer.updateUnread();
        drawer.updateManager();
    }

    void torrentUnread(Storage.Torrent tt) {
        if (active(torrents)) {
            tt.message = false;
        }
    }

    public Torrents getTorrents() {
        return torrents;
    }

    public EnginesManager getEngines() {
        return engies;
    }

    public void show(NavigatorInterface nav) {
        Adapter a = list.getAdapter();
        if (a != null && a instanceof HeaderGridView.HeaderViewGridAdapter) {
            a = ((HeaderGridView.HeaderViewGridAdapter) a).getWrappedAdapter();
        }

        if (a != null && a instanceof MainActivity.NavigatorInterface) {
            ((MainActivity.NavigatorInterface) a).remove(list);
        }

        empty.setVisibility(View.GONE);

        if (nav instanceof Torrents) {
            list.setEmptyView(empty);
        } else {
            list.setEmptyView(null);
        }

        engies.save();

        nav.install(list);
    }

    public Drawer getDrawer() {
        return drawer;
    }

    public void showRates() {
        RatesDialogFragment dialog = new RatesDialogFragment();
        Bundle args = new Bundle();
        dialog.setArguments(args);
        dialog.show(getSupportFragmentManager(), "");
    }

    public void openTorrents() {
        if (torrents == null)
            return; // delayed init
        show(torrents);
    }
}
