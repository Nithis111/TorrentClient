package com.github.axet.torrentclient.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.services.TorrentService;

import java.io.File;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MainApplication extends com.github.axet.androidlibrary.app.MainApplication {
    final String TAG = MainApplication.class.getSimpleName();

    public static final String UTF8 = "UTF8";

    public static final String PREFERENCE_STORAGE = "storage_path";
    public static final String PREFERENCE_THEME = "theme";
    public static final String PREFERENCE_ANNOUNCE = "announces_list";
    public static final String PREFERENCE_START = "start_at_boot";
    public static final String PREFERENCE_WIFI = "wifi";
    public static final String PREFERENCE_LAST_PATH = "lastpath";
    public static final String PREFERENCE_DIALOG = "dialog";
    public static final String PREFERENCE_RUN = "run";
    public static final String PREFERENCE_PROXY = "proxy";
    public static final String PREFERENCE_UPLOAD = "upload_rate";
    public static final String PREFERENCE_DOWNLOAD = "download_rate";
    public static final String PREFERENCE_SPEEDLIMIT = "speedlimit";
    public static final String PREFERENCE_OPTIMIZATION = "optimization";
    public static final String PREFERENCE_PLAYER = "player";

    public static final String SAVE_STATE = MainApplication.class.getName() + ".SAVE_STATE";

    OptimizationPreferenceCompat.ApplicationReceiver optimization;

    Storage storage;

    public TorrentPlayer player;

    SaveState savestate;

    final ArrayList<Runnable> initArray = new ArrayList<>();
    Thread initThread;
    Handler handler = new Handler();

    class SaveState extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive" + intent);
            if (intent.getAction().equals(SAVE_STATE)) {
                Storage s = getStorage();
                if (s != null)
                    s.save();
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        PreferenceManager.setDefaultValues(this, R.xml.pref_general, false);

        Context context = this;
        context.setTheme(getUserTheme());
    }

    public void playerLoad() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        String uri = shared.getString(MainApplication.PREFERENCE_PLAYER, "");
        if (!uri.isEmpty()) {
            Uri u = Uri.parse(uri);
            String p = u.getPath();
            String[] pp = p.split("/");
            String hash = pp[1];
            String v = u.getQueryParameter("t");
            int q = Integer.parseInt(v);
            Uri.Builder b = u.buildUpon();
            b.clearQuery();
            Storage.Torrent t = storage.find(hash);
            if (t == null)
                return;
            if (player != null)
                player.close();
            player = new TorrentPlayer(this, t.t);
            player.open(b.build());
            player.seek(q);
        }
    }

    public TorrentPlayer openPlayer(long t) {
        if (player != null) {
            if (player.torrent.t == t)
                return player;
            player.close();
        }
        player = new TorrentPlayer(this, t);
        return player;
    }

    public void pausePlayer() {
        player.pause();
    }

    public void closePlayer() {
        if (player == null)
            return;
        player.close();
        player = null;
    }

    public void playerSave() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit = shared.edit();
        if (player != null) {
            edit.putString(MainApplication.PREFERENCE_PLAYER, player.getUri().toString());
        } else {
            edit.remove(MainApplication.PREFERENCE_PLAYER);
        }
        edit.commit();
    }

    public void createThread(Runnable run) {
        synchronized (initArray) {
            if (run != null)
                initArray.add(run);
        }
        if (initThread != null)
            return;
        initThread = new Thread(new Runnable() {
            @Override
            public void run() {
                create();
                synchronized (initArray) {
                    for (Runnable r : initArray) {
                        handler.post(r);
                    }
                    initArray.clear();
                    initThread = null;
                }
            }
        });
        initThread.start();
    }

    public void create() {
        Log.d(TAG, "create");
        if (optimization == null) {
            optimization = new OptimizationPreferenceCompat.ApplicationReceiver(this, TorrentService.class);
        }
        if (storage == null) {
            storage = new Storage(this);
            storage.create();
        }
        if (savestate == null) {
            savestate = new SaveState();
            IntentFilter filter = new IntentFilter();
            filter.addAction(SAVE_STATE);
            filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            filter.addAction(Intent.ACTION_MY_PACKAGE_REPLACED);
            filter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
            registerReceiver(savestate, filter);
        }
        playerLoad();
    }

    public void close() {
        Log.d(TAG, "close");
        if (initThread != null) {
            try {
                initThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            initThread = null;
        }
        if (optimization != null) {
            optimization.close();
            optimization = null;
        }
        if (storage != null) {
            storage.close();
            storage = null;
        }
        if (savestate != null) {
            unregisterReceiver(savestate);
            savestate = null;
        }
        playerSave();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.d(TAG, "onTerminate");
        playerSave();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.d(TAG, "onLowMemory");
        playerSave();
    }

    public static String onTrimString(int level) {
        switch (level) {
            case TRIM_MEMORY_COMPLETE:
                return "TRIM_MEMORY_COMPLETE";
            case TRIM_MEMORY_MODERATE:
                return "TRIM_MEMORY_MODERATE";
            case TRIM_MEMORY_BACKGROUND:
                return "TRIM_MEMORY_BACKGROUND";
            case TRIM_MEMORY_UI_HIDDEN:
                return "TRIM_MEMORY_UI_HIDDEN";
            case TRIM_MEMORY_RUNNING_CRITICAL:
                return "TRIM_MEMORY_RUNNING_CRITICAL";
            case TRIM_MEMORY_RUNNING_LOW:
                return "TRIM_MEMORY_RUNNING_LOW";
            case TRIM_MEMORY_RUNNING_MODERATE:
                return "TRIM_MEMORY_RUNNING_MODERATE";
        }
        return "unknown";
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.d(TAG, "onTrimMemory: " + onTrimString(level));
        playerSave();
    }

    public static int getTheme(Context context, int light, int dark) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String theme = shared.getString(PREFERENCE_THEME, "");
        if (theme.equals("Theme_Dark")) {
            return dark;
        } else {
            return light;
        }
    }

    public static int getActionbarColor(Context context) {
        int colorId = MainApplication.getTheme(context, R.attr.colorPrimary, R.attr.secondBackground);
        int color = ThemeUtils.getThemeColor(context, colorId);
        return color;
    }

    public int getUserTheme() {
        return getTheme(this, R.style.AppThemeLight, R.style.AppThemeDark);
    }

    public static String formatFree(Context context, long free, long d, long u) {
        return context.getString(R.string.free, formatSize(context, free),
                formatSize(context, d) + context.getString(R.string.per_second),
                formatSize(context, u) + context.getString(R.string.per_second));
    }

    static public void setTextNA(View v, String text) {
        TextView t = (TextView) v;
        if (text.isEmpty()) {
            t.setEnabled(false);
            t.setText(R.string.n_a);
        } else {
            t.setEnabled(true);
            t.setText(text);
        }
    }

    static public void setDate(View v, long d) {
        String s = formatDate(d);
        setTextNA(v, s);
    }

    public static String formatDate(long d) {
        if (d == 0)
            return "";

        SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        return s.format(new Date(d / 1000000));
    }

    public Storage getStorage() {
        return storage;
    }

    public static String getPreferenceLastPath(Context context) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String def = Environment.getExternalStorageDirectory().getPath();
        String path = shared.getString(MainApplication.PREFERENCE_LAST_PATH, def);
        if (!new File(path).canRead()) {
            return def;
        }
        return path;
    }

    public static List<String> splitPath(String s) {
        return new ArrayList<>(Arrays.asList(s.split("[//\\\\]")));
    }
}
