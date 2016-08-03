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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.Adapter;
import android.widget.HeaderViewListAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.app.EnginesManager;
import com.github.axet.torrentclient.app.MainApplication;
import com.github.axet.torrentclient.app.SearchEngine;
import com.github.axet.torrentclient.app.Storage;
import com.github.axet.torrentclient.dialogs.AddDialogFragment;
import com.github.axet.torrentclient.dialogs.CreateDialogFragment;
import com.github.axet.torrentclient.dialogs.OpenIntentDialogFragment;
import com.github.axet.torrentclient.navigators.Search;
import com.github.axet.torrentclient.navigators.Torrents;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import go.libtorrent.Libtorrent;

public class MainActivity extends AppCompatActivity implements AbsListView.OnScrollListener,
        DialogInterface.OnDismissListener, SharedPreferences.OnSharedPreferenceChangeListener,
        NavigationView.OnNavigationItemSelectedListener {
    public final static String TAG = MainActivity.class.getSimpleName();

    static final long INFO_MANUAL_REFRESH = 5 * 1000;
    static final long INFO_AUTO_REFRESH = 5 * 60 * 1000;
    static final long ENGINES_AUTO_REFRESH = 24 * 60 * 60 * 1000;

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    public int scrollState;

    Runnable refresh;
    Runnable refreshUI;
    TorrentFragmentInterface dialog;

    Torrents torrents;
    ProgressBar progress;
    ListView list;
    View empty;
    Handler handler;

    NavigationView navigationView;
    DrawerLayout drawerLayout;
    View navigationHeader;
    DrawerLayout drawer;

    Thread infoThread;
    List<String> infoOld;
    boolean infoPort;
    long infoTime; // last time checked

    int themeId;

    // not delared locally - used from two places
    FloatingActionsMenu fab;
    FloatingActionButton create;
    FloatingActionButton add;

    // delayedIntent delayedIntent
    Intent delayedIntent;
    Thread initThread;
    Runnable delayedInit;

    BroadcastReceiver screenreceiver;

    EnginesManager manager;
    Thread update;
    Thread updateOne;
    int updateOneIndex;

    public static void startActivity(Context context) {
        Intent i = new Intent(context, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(i);
    }

    public interface TorrentFragmentInterface {
        void update();
    }

    public void checkTorrent(long t) {
        if (Libtorrent.TorrentStatus(t) == Libtorrent.StatusChecking) {
            Libtorrent.StopTorrent(t);
            Toast.makeText(this, R.string.stop_checking, Toast.LENGTH_SHORT).show();
            return;
        }
        Libtorrent.CheckTorrent(t);
        Toast.makeText(this, R.string.start_checking, Toast.LENGTH_SHORT).show();
    }

    public void renameDialog(final Long f) {
        final OpenFileDialog.EditTextDialog e = new OpenFileDialog.EditTextDialog(this);
        e.setTitle(getString(R.string.rename_torrent));
        e.setText(Libtorrent.TorrentName(f));
        e.setPositiveButton(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = e.getText();
                // clear slashes
                name = new File(name).getName();
                if (name.isEmpty())
                    return;
                Libtorrent.TorrentRename(f, name);
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
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setAppTheme(getAppTheme());

        setContentView(R.layout.activity_main);

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setBackground(new ColorDrawable(MainApplication.getActionbarColor(this)));
        setSupportActionBar(toolbar);

        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        manager = new EnginesManager(this);
        updateManager();

        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                long time = System.currentTimeMillis();
                long t = manager.getTime();
                if (t + ENGINES_AUTO_REFRESH > time) {
                    if (update == null)
                        refreshEngines(true);
                }
            }

            @Override
            public void onDrawerClosed(View drawerView) {
            }

            @Override
            public void onDrawerStateChanged(int newState) {
            }
        });

        navigationHeader = navigationView.getHeaderView(0);

        TextView ver = (TextView) navigationHeader.findViewById(R.id.nav_version);
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = "v" + pInfo.versionName;
            ver.setText(version);
        } catch (PackageManager.NameNotFoundException e) {
            ver.setVisibility(View.GONE);
        }

        handler = new Handler();

        fab = (FloatingActionsMenu) findViewById(R.id.fab);

        create = (FloatingActionButton) findViewById(R.id.torrent_create_button);
        create.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final OpenFileDialog f = new OpenFileDialog(MainActivity.this);

                String path = "";

                final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

                if (path == null || path.isEmpty()) {
                    path = shared.getString(MainApplication.PREFERENCE_LAST_PATH, Environment.getExternalStorageDirectory().getPath());
                }

                f.setCurrentPath(new File(path));
                f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File p = f.getCurrentPath();

                        shared.edit().putString(MainApplication.PREFERENCE_LAST_PATH, p.getParent()).commit();

                        final ProgressDialog progress = new ProgressDialog(MainActivity.this);
                        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);

                        String path = p.getPath();
                        final String pp = new File(p.getPath()).getParentFile().getPath();
                        final AtomicLong pieces = new AtomicLong(Libtorrent.CreateMetaInfo(path));
                        final AtomicLong i = new AtomicLong(0);
                        progress.setMax((int) pieces.get());

                        MainActivity.this.dialog = new TorrentFragmentInterface() {
                            @Override
                            public void update() {
                                progress.setProgress((int) i.get());
                            }
                        };

                        final Thread t = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                final MainActivity activity = MainActivity.this;

                                for (i.set(0); i.get() < pieces.get(); i.incrementAndGet()) {
                                    Thread.yield();

                                    if (Thread.currentThread().isInterrupted()) {
                                        Libtorrent.CloseMetaInfo();
                                        progress.dismiss();
                                        return;
                                    }

                                    if (!Libtorrent.HashMetaInfo(i.get())) {
                                        activity.post(Libtorrent.Error());
                                        Libtorrent.CloseMetaInfo();
                                        progress.dismiss();
                                        return;
                                    }
                                }

                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        MainActivity.this.dialog = null;
                                        activity.createTorrentFromMetaInfo(pp);
                                        Libtorrent.CloseMetaInfo();
                                        progress.dismiss();
                                    }
                                });
                            }
                        });

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
                final OpenFileDialog f = new OpenFileDialog(MainActivity.this);

                String path = "";

                final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

                if (path == null || path.isEmpty()) {
                    path = shared.getString(MainApplication.PREFERENCE_LAST_PATH, Environment.getExternalStorageDirectory().getPath());
                }

                f.setCurrentPath(new File(path));
                f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File p = f.getCurrentPath();

                        shared.edit().putString(MainApplication.PREFERENCE_LAST_PATH, p.getParent()).commit();

                        long t = Libtorrent.AddTorrent(p.getPath());

                        if (t == -1) {
                            Error(Libtorrent.Error());
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

        list = (ListView) findViewById(R.id.list);
        empty = findViewById(R.id.empty_list);

        fab.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
        fab.setVisibility(View.GONE);

        screenreceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    Log.d(TAG, "Screen OFF");
                    if (drawer.isDrawerOpen(GravityCompat.START)) {
                        drawer.closeDrawer(GravityCompat.START);
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
                list.setAdapter(torrents);
                navigationView.setCheckedItem(R.id.nav_torrents);

                if (permitted()) {
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

                if (delayedIntent != null) {
                    openIntent(delayedIntent);
                    delayedIntent = null;
                }
            }
        };

        updateHeader(new Storage(this));

        initThread = new Thread(new Runnable() {
            @Override
            public void run() {
                getApp().create();

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        // acctivity can be destoryed already do not init
                        if (delayedInit != null) {
                            delayedInit.run();
                            delayedInit = null;
                        }
                    }
                });
            }
        });
        initThread.start();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(MainApplication.PREFERENCE_ANNOUNCE)) {
            Libtorrent.SetDefaultAnnouncesList(sharedPreferences.getString(MainApplication.PREFERENCE_ANNOUNCE, ""));
        }
        if (key.equals(MainApplication.PREFERENCE_WIFI)) {
            if (!sharedPreferences.getBoolean(MainApplication.PREFERENCE_WIFI, true))
                getStorage().resume(); // wifi only disabled
            else {
                // wifi only enabed
                if (!getStorage().isConnectedWifi()) // are we on wifi?
                    getStorage().pause(); // no, pause all
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.menu_main, menu);

        KeyguardManager myKM = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (myKM.inKeyguardRestrictedInputMode() || delayedInit != null) {
            menu.removeItem(R.id.action_settings);
            menu.removeItem(R.id.action_show_folder);
        }

        return true;
    }

    public void close() {
        if (initThread != null) {
            try {
                initThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // prevent delayed delayedInit
            delayedInit = null;
        }

        if (manager != null) {
            manager.save();
            manager = null;
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

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        shared.unregisterOnSharedPreferenceChangeListener(MainActivity.this);

        // do not close storage when mainactivity closes. it may be restarted due to theme change.
        // only close it on shutdown()
        // getApp().close();
    }

    public void shutdown() {
        close();
        getApp().close();
        finishAffinity();
        ExitActivity.exitApplication(this);
    }

    public void post(final Throwable e) {
        Log.e(TAG, "Exception", e);
        post(e.getMessage());
    }

    public void post(final String msg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Error(msg);
            }
        });
    }

    public void Error(Throwable e) {
        Log.e(TAG, "Exception", e);
        Error(e.getMessage());
    }

    public void Error(String err) {
        Log.e(TAG, Libtorrent.Error());

        new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.error)
                .setMessage(err)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    public void Fatal(String err) {
        Log.e(TAG, Libtorrent.Error());

        new AlertDialog.Builder(this)
                .setTitle(R.string.fatal)
                .setMessage(err)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        shutdown();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        shutdown();
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

    public void openFolder(File path) {
        Uri selectedUri = Uri.fromFile(path);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(selectedUri, "resource/folder");
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

        KeyguardManager myKM = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (myKM.inKeyguardRestrictedInputMode()) {
            add.setVisibility(View.GONE);
            create.setVisibility(View.GONE);
        } else {
            if (permitted(PERMISSIONS)) {
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

                torrents.update();

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
                if (permitted(permissions)) {
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

    boolean permitted(String[] ss) {
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    boolean permitted() {
        for (String s : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        this.scrollState = scrollState;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
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

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://com.github.axet.torrentclient/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }


    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://com.github.axet.torrentclient/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        dialog = null;
        ListAdapter a = list.getAdapter();
        if (a != null && a instanceof HeaderViewListAdapter) {
            a = ((HeaderViewListAdapter) a).getWrappedAdapter();
        }
        if (a instanceof DialogInterface.OnDismissListener) {
            ((DialogInterface.OnDismissListener) a).onDismiss(dialogInterface);
        }
    }

    void updateHeader(Storage s) {
        TextView text = (TextView) findViewById(R.id.space_left);
        text.setText(s.formatHeader());

        ArrayList<String> info = new ArrayList<>();
        long c = Libtorrent.PortCount();
        for (long i = 0; i < c; i++) {
            info.add(Libtorrent.Port(i));
        }

        String str = "";
        for (String ip : info) {
            str += ip + "\n";
        }
        str = str.trim();
        TextView textView = (TextView) navigationHeader.findViewById(R.id.torrent_ip);
        textView.setText(str);

        View portButton = navigationHeader.findViewById(R.id.torrent_port_button);
        ImageView portIcon = (ImageView) navigationHeader.findViewById(R.id.torrent_port_icon);
        TextView port = (TextView) navigationHeader.findViewById(R.id.torrent_port_text);

        portButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long time = System.currentTimeMillis();
                if (infoTime + INFO_MANUAL_REFRESH < time) {
                    infoTime = time;
                    infoOld = null;
                }
            }
        });

        long time = System.currentTimeMillis();
        if (infoTime + INFO_AUTO_REFRESH < time) {
            infoTime = time;
            infoOld = null;
        }

        if (infoOld == null || !Arrays.equals(info.toArray(), infoOld.toArray())) {
            if (drawer.isDrawerOpen(GravityCompat.START)) { // only probe port when drawer is open
                if (infoThread != null) {
                    return;
                }
                infoOld = info;
                infoThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final boolean b = Libtorrent.PortCheck();
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                infoPort = b;
                                infoThread = null;
                            }
                        });
                    }
                });
                infoThread.start();
                infoPort = false;
                portIcon.setImageResource(R.drawable.port_no);
                port.setText(R.string.port_checking);
            } else {
                portIcon.setImageResource(R.drawable.port_no);
                port.setText(R.string.port_closed);
            }
        } else {
            if (infoPort) {
                portIcon.setImageResource(R.drawable.port_ok);
                port.setText(R.string.port_open);
            } else {
                portIcon.setImageResource(R.drawable.port_no);
                port.setText(R.string.port_closed);
            }
        }
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

                if (manager.addManget(s))
                    return;

                String p = getStorage().getStoragePath().getPath();
                long t = Libtorrent.AddMagnet(p, s);
                if (t == -1) {
                    throw new RuntimeException(Libtorrent.Error());
                }

                addTorrentDialog(t, p);
            } else {
                for (String s : m) {
                    if (!manager.addManget(s))
                        getStorage().addMagnet(s);
                }
            }
        } catch (RuntimeException e) {
            Error(e);
        }
        torrents.notifyDataSetChanged();
    }

    public void addTorrentFromURL(String p) {
        try {
            getStorage().addTorrentFromURL(p);
        } catch (RuntimeException e) {
            Error(e);
        }
        torrents.notifyDataSetChanged();
    }

    public void addTorrentFromBytes(byte[] buf) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        addTorrentFromBytes(buf, shared.getBoolean(MainApplication.PREFERENCE_DIALOG, false));
    }

    public void addTorrentFromBytes(byte[] buf, boolean dialog) {
        try {
            if (dialog) {
                String s = getStorage().getStoragePath().getPath();
                long t = Libtorrent.AddTorrentFromBytes(s, buf);
                if (t == -1) {
                    throw new RuntimeException(Libtorrent.Error());
                }
                addTorrentDialog(t, s);
            } else {
                getStorage().addTorrentFromBytes(buf);
            }
        } catch (RuntimeException e) {
            Error(e);
        }
        torrents.notifyDataSetChanged();
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
        final long t = Libtorrent.CreateTorrentFromMetaInfo();
        if (t == -1) {
            Error(Libtorrent.Error());
            return;
        }
        if (shared.getBoolean(MainApplication.PREFERENCE_DIALOG, false)) {
            createTorrentDialog(t, pp);
        } else {
            getStorage().add(new Storage.Torrent(this, t, pp));
            torrents.notifyDataSetChanged();
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();

        // here only two types of adapters, so setup empty view manually here.

        Adapter a = list.getAdapter();
        if (a != null && a instanceof HeaderViewListAdapter) {
            a = ((HeaderViewListAdapter) a).getWrappedAdapter();
        }
        if (a != null && a instanceof Search) {
            ((Search) a).remove(list);
        }

        if (id == R.id.nav_torrents) {
            empty.setVisibility(View.GONE);
            list.setEmptyView(empty);

            list.setAdapter(torrents);
        }
        if (id > 0 && id < 0x00ffffff) {
            empty.setVisibility(View.GONE);
            list.setEmptyView(null);

            int pos = id - 1;

            Search search = manager.get(pos);
            search.install(list);
        }
        if (id == R.id.nav_add) {
            final OpenFileDialog f = new OpenFileDialog(MainActivity.this);

            String path = "";

            final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

            if (path == null || path.isEmpty()) {
                path = shared.getString(MainApplication.PREFERENCE_LAST_PATH, Environment.getExternalStorageDirectory().getPath());
            }

            f.setCurrentPath(new File(path));
            f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    File p = f.getCurrentPath();
                    shared.edit().putString(MainApplication.PREFERENCE_LAST_PATH, p.getParent()).commit();
                    Search search = null;
                    try {
                        search = manager.add(p);
                    } catch (RuntimeException e) {
                        Error(e);
                        return;
                    }
                    manager.save();
                    updateManager();
                    openDrawer(search);
                }
            });
            f.show();
            // prevent close drawer
            return true;
        }
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public void openDrawer(Search search) {
        drawer.openDrawer(GravityCompat.START);
        for (int i = 0; i < manager.getCount(); i++) {
            if (manager.get(i) == search) {
                int id = i + 1;
                navigationView.setCheckedItem(id);

                Adapter a = list.getAdapter();
                if (a != null && a instanceof HeaderViewListAdapter) {
                    a = ((HeaderViewListAdapter) a).getWrappedAdapter();
                }
                if (a != null && a instanceof Search) {
                    ((Search) a).remove(list);
                }
                empty.setVisibility(View.GONE);
                list.setEmptyView(null);
                search.install(list);
                return;
            }
        }
    }

    public void updateManager() {
        Menu menu = navigationView.getMenu();

        for (int i = menu.size() - 1; i >= 0; i--) {
            MenuItem m = menu.getItem(i);
            int id = m.getItemId();
            if (id > 0 && id < 0x00ffffff) {
                menu.removeItem(id);
            }
        }

        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < manager.getCount(); i++) {
            final Search search = manager.get(i);
            final SearchEngine engine = search.getEngine();
            // save to set < 0x00ffffff. check View.generateViewId()
            int id = i + 1;
            MenuItem item = menu.add(R.id.group_torrents, id, Menu.NONE, engine.getName());
            item.setIcon(R.drawable.share);
            final View view = inflater.inflate(R.layout.search_engine, null);
            final View release = view.findViewById(R.id.search_engine_new);
            View progress = view.findViewById(R.id.search_engine_progress);

            final int fi = i;
            release.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (updateOne != null) {
                        updateOne.interrupt();
                    }
                    updateOneIndex = fi;
                    updateOne = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            manager.update(fi);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    updateOne = null;
                                    updateManager();
                                    Search search = manager.get(fi);
                                    SearchEngine engine = search.getEngine();
                                    Toast.makeText(MainActivity.this, engine.getName() + getString(R.string.engine_updated) + engine.getVersion(), Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }, "Update One");
                    updateOne.start();
                }
            });

            if (updateOne != null && updateOneIndex == i) {
                progress.setVisibility(View.VISIBLE);
            } else {
                progress.setVisibility(View.INVISIBLE);
            }

            if (manager.getUpdate(i))
                release.setVisibility(View.VISIBLE);
            else
                release.setVisibility(View.INVISIBLE);

            final View icon = view.findViewById(R.id.search_engine_trash);
            icon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Context context = MainActivity.this;
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle(getString(R.string.delete_search));

                    String name = engine.getName();

                    builder.setMessage(name + "\n\n" + context.getString(R.string.are_you_sure));
                    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            manager.remove(search);
                            manager.save();
                            updateManager();
                        }
                    });
                    builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
                    builder.show();
                }
            });
            item.setActionView(view);
        }
        // reset group. add recent items to toggle group
        menu.setGroupCheckable(R.id.group_torrents, true, true);

        View update = inflater.inflate(R.layout.search_update, null);
        final ProgressBar progress = (ProgressBar) update.findViewById(R.id.search_update_progress);
        progress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (MainActivity.this.update != null) {
                    MainActivity.this.update.interrupt();
                    MainActivity.this.update = null;
                    updateManager();
                }
            }
        });
        View refresh = update.findViewById(R.id.search_update_refresh);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshEngines(false);
            }
        });

        if (MainActivity.this.update != null) {
            progress.setVisibility(View.VISIBLE);
            refresh.setVisibility(View.GONE);
        } else {
            progress.setVisibility(View.GONE);
            refresh.setVisibility(View.VISIBLE);
        }

        MenuItem add = menu.findItem(R.id.nav_add);
        add.setActionView(update);
    }

    void refreshEngines(final boolean auto) {
        if (update != null) {
            update.interrupt();
        }
        update = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean a = auto;

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateManager();
                    }
                });
                try {
                    manager.refresh();
                } catch (RuntimeException e) {
                    MainActivity.this.post(e);
                    a = true; // hide update toast on error
                }
                final boolean b = a;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        update = null;
                        if (!b) {
                            if (!manager.updates())
                                Toast.makeText(MainActivity.this, R.string.no_updates, Toast.LENGTH_LONG).show();
                        }
                        updateManager();
                    }
                });
            }
        }, "Engines Update");
        update.start();
    }
}
