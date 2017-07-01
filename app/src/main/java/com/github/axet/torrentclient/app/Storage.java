package com.github.axet.torrentclient.app;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.util.Base64;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.github.axet.androidlibrary.app.AlarmManager;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.services.TorrentService;
import com.github.axet.wget.SpeedInfo;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.MulticastSocket;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import libtorrent.Buffer;
import libtorrent.BytesInfo;
import libtorrent.FileStorageTorrent;
import libtorrent.Libtorrent;
import libtorrent.StatsTorrent;

public class Storage extends com.github.axet.androidlibrary.app.Storage implements FileStorageTorrent {
    public static final String TAG = Storage.class.getSimpleName();

    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    public static final long SAVE_INTERVAL = AlarmManager.MIN1;
    public static final int HASH_LEN = 40;

    SpeedInfo downloaded = new SpeedInfo();
    SpeedInfo uploaded = new SpeedInfo();

    final ArrayList<Torrent> torrents = new ArrayList<>();
    final HashMap<String, Torrent> hashs = new HashMap<>();

    Handler handler;

    // refresh title
    Runnable refresh;

    // save state every 5 min
    Runnable save = new Runnable() {
        @Override
        public void run() {
            save();
            if (!active())
                return;
            saveDelay();
        }
    };
    ;

    BroadcastReceiver wifiReciver;

    WifiManager.MulticastLock mcastLock;
    protected static MulticastSocket socket;

    public static class Torrent {
        Context context;

        public long t; // libtorrent handler
        public Uri path; // path to where torrent data located
        public String hash; // torrent hash hex string
        public boolean message; // highlight torrent
        public boolean check; // force check required, files were altered
        public boolean readonly; // readonly files or target path, show warning
        public boolean done; // done notification

        SpeedInfo downloaded = new SpeedInfo();
        SpeedInfo uploaded = new SpeedInfo();

        public Torrent(Context context, long t, Uri path, boolean message) {
            this.context = context;
            this.t = t;
            this.path = path;
            this.message = message;
            this.hash = Libtorrent.torrentHash(t);
        }

        public void close() {
            long d = t; // prevent debugger to crash
            t = -1;
            Libtorrent.removeTorrent(d);
        }

        public String name() {
            String name = Libtorrent.torrentName(t);
            // can be empy for magnet links, show hash instead
            if (name.isEmpty()) {
                name = hash;
            }
            return name;
        }

        public void start() {
            String s = path.getScheme();
            if (s.startsWith(ContentResolver.SCHEME_FILE)) {
                File f = new File(path.getPath());
                if (!f.exists())
                    f.mkdirs();
            }
            if (!Libtorrent.startTorrent(t))
                throw new RuntimeException(Libtorrent.error());
            StatsTorrent b = Libtorrent.torrentStats(t);
            downloaded.start(b.getDownloaded());
            uploaded.start(b.getUploaded());
        }

        public void update() {
            StatsTorrent b = Libtorrent.torrentStats(t);
            downloaded.step(b.getDownloaded());
            uploaded.step(b.getUploaded());
        }

        public void stop() {
            Libtorrent.stopTorrent(t);
            StatsTorrent b = Libtorrent.torrentStats(t);
            downloaded.end(b.getDownloaded());
            uploaded.end(b.getUploaded());
        }

        // "Left: 5m 30s · ↓ 1.5Mb/s · ↑ 0.6Mb/s"
        public String status() {
            String str = "";

            switch (Libtorrent.torrentStatus(t)) {
                case Libtorrent.StatusQueued:
                case Libtorrent.StatusChecking:
                case Libtorrent.StatusPaused:
                case Libtorrent.StatusSeeding:
                    if (Libtorrent.metaTorrent(t))
                        str += MainApplication.formatSize(context, Libtorrent.torrentBytesLength(t)) + " · ";

                    str += "↓ " + MainApplication.formatSize(context, downloaded.getCurrentSpeed()) + context.getString(R.string.per_second);
                    str += " · ↑ " + MainApplication.formatSize(context, uploaded.getCurrentSpeed()) + context.getString(R.string.per_second);
                    break;
                case Libtorrent.StatusDownloading:
                    long c = 0;
                    if (Libtorrent.metaTorrent(t))
                        c = Libtorrent.torrentPendingBytesLength(t) - Libtorrent.torrentPendingBytesCompleted(t);
                    int a = downloaded.getAverageSpeed();
                    String left = "∞";
                    if (c > 0 && a > 0) {
                        long diff = c * 1000 / a;
                        int diffDays = (int) (diff / (AlarmManager.DAY1));
                        if (diffDays < 30)
                            left = "" + MainApplication.formatDuration(context, diff) + "";
                    }
                    str += left;
                    str += " · ↓ " + MainApplication.formatSize(context, downloaded.getCurrentSpeed()) + context.getString(R.string.per_second);
                    str += " · ↑ " + MainApplication.formatSize(context, uploaded.getCurrentSpeed()) + context.getString(R.string.per_second);
                    break;
            }

            return str.trim();
        }

        public String toString() {
            if (t == -1) // prevent debugger crash
                return "(deleted)";

            String str = name();

            if (Libtorrent.metaTorrent(t))
                str += " · " + MainApplication.formatSize(context, Libtorrent.torrentBytesLength(t));

            str += " · (" + getProgress() + "%)";

            return str;
        }

        public static int getProgress(long t) {
            if (Libtorrent.metaTorrent(t)) {
                long p = Libtorrent.torrentPendingBytesLength(t);
                if (p == 0)
                    return 0;
                return (int) (Libtorrent.torrentPendingBytesCompleted(t) * 100 / p);
            }
            return 0;
        }

        public int getProgress() {
            return getProgress(t);
        }

        public boolean altered(Storage storage) {
            for (int k = 0; k < Libtorrent.torrentFilesCount(t); k++) {
                libtorrent.File f = Libtorrent.torrentFiles(t, k);
                if (f.getBytesCompleted() != 0) {
                    String s = path.getScheme();
                    if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
                        Uri file = storage.child(path, f.getPath());
                        if (!storage.exists(file) || storage.getLength(file) == 0) {
                            return true;
                        }
                    } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
                        File file = new File(path.getPath(), f.getPath());
                        if (!file.exists() || file.length() == 0) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        public boolean readonly() {
            String s = path.getScheme();
            if (s.startsWith(ContentResolver.SCHEME_FILE)) {
                File p = new File(path.getPath());
                if (!p.canWrite())
                    return true;
                for (int k = 0; k < Libtorrent.torrentFilesCount(t); k++) {
                    libtorrent.File f = Libtorrent.torrentFiles(t, k);
                    if (f.getBytesCompleted() != 0) {
                        File file = new File(p, f.getPath());
                        if (file.exists() && !file.canWrite()) { // we can only check parent folder and existing files, skip middle folders
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    // seeds should go to start. !seeds to the end (so start download it).
    // seed ordered by seed time desc. !seed ordered by percent
    public static class LoadTorrents implements Comparator<Torrent> {

        @Override
        public int compare(Torrent lhs, Torrent rhs) {
            Boolean lseed = Libtorrent.pendingCompleted(lhs.t);
            Boolean rseed = Libtorrent.pendingCompleted(rhs.t);

            // booth done
            if (lseed && rseed) {
                Long ltime = Libtorrent.torrentStats(lhs.t).getSeeding();
                Long rtime = Libtorrent.torrentStats(rhs.t).getSeeding();

                // seed time desc
                return rtime.compareTo(ltime);
            }

            // seed to start, download to the end
            if (lseed || rseed) {
                return rseed.compareTo(lseed);
            }

            if (!lseed && !rseed) {
                Integer lp = lhs.getProgress();
                Integer rp = rhs.getProgress();
                // seed time desc
                return lp.compareTo(rp);
            }

            return 0;
        }
    }

    public Storage(Context context) {
        super(context);
        Log.d(TAG, "Storage()");
        handler = new Handler(context.getMainLooper());
    }

    public MainApplication getApp() {
        return (MainApplication) context.getApplicationContext();
    }

    public void update() {
        BytesInfo b = Libtorrent.stats();

        downloaded.step(b.getDownloaded());
        uploaded.step(b.getUploaded());
    }

    public void updateHeader() {
        String header = formatHeader();
        header += "\n";
        for (int i = 0; i < count(); i++) {
            Storage.Torrent t = torrent(i);
            if (Libtorrent.torrentActive(t.t)) {
                if (Libtorrent.torrentStatus(t.t) == Libtorrent.StatusSeeding) {
                    header += "(" + t.getProgress() + ") ";
                } else {
                    header += "(" + t.getProgress() + "%) ";
                }
            }
        }
        TorrentPlayer p = getApp().player;
        String player = "";
        boolean playing = false;
        if (p != null) {
            playing = p.isPlaying();
            player = p.formatHeader();

        }
        TorrentService.updateNotify(context, header, player, playing);
    }

    public void load() {
        Log.d(TAG, "load()");
        ArrayList<Torrent> resume = new ArrayList<>();

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        int count = shared.getInt("torrent_count", -1);
        if (count == -1) // <=2.4.0
            count = shared.getInt("TORRENT_COUNT", 0);
        for (int i = 0; i < count; i++) {
            try {
                JSONObject o = new JSONObject();
                String json = shared.getString("torrent_" + i, "");
                if (json.isEmpty()) { // <=2.4.0
                    String path = shared.getString("TORRENT_" + i + "_PATH", "");

                    if (path.isEmpty())
                        path = getStoragePath().getPath();

                    String state = shared.getString("TORRENT_" + i + "_STATE", "");

                    int status = shared.getInt("TORRENT_" + i + "_STATUS", 0);

                    boolean message = shared.getBoolean("TORRENT_" + i + "_MESSAGE", false);
                    o.put("path", path);
                    o.put("state", state);
                    o.put("status", status);
                    o.put("message", message);
                } else {
                    o = new JSONObject(json);
                }

                byte[] b = Base64.decode(o.getString("state"), Base64.DEFAULT);

                synchronized (hashs) {
                    String path = o.getString("path");
                    long t = Libtorrent.loadTorrent(path, b);
                    if (t == -1) {
                        Log.d(TAG, Libtorrent.error());
                        continue;
                    }
                    Uri u;
                    if (path.startsWith(ContentResolver.SCHEME_CONTENT))
                        u = Uri.parse(path);
                    else if (path.startsWith(ContentResolver.SCHEME_FILE))
                        u = Uri.parse(path);
                    else
                        u = Uri.fromFile(new File(path));
                    Torrent tt = new Torrent(context, t, u, o.getBoolean("message"));
                    torrents.add(tt);
                    hashs.put(tt.hash, tt);

                    tt.done = o.optBoolean("done", false);
                    if (tt.altered(this)) {
                        tt.check = true;
                    }
                    if (tt.readonly()) {
                        tt.readonly = true;
                    }

                    if (o.getInt("status") != Libtorrent.StatusPaused) {
                        resume.add(tt);
                    }
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        Collections.sort(resume, new LoadTorrents());

        for (Torrent t : resume) {
            if (t.check || t.readonly)
                continue;
            start(t);
        }
    }

    public void save() {
        Log.d(TAG, "save()");
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = shared.edit();
        edit.putInt("torrent_count", torrents.size());
        for (int i = 0; i < torrents.size(); i++) {
            save(edit, i);
        }
        edit.commit();
    }

    void save(Torrent t) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = shared.edit();
        save(edit, torrents.indexOf(t));
        edit.commit();
    }

    void save(SharedPreferences.Editor edit, int i) {
        Torrent t = torrents.get(i);
        byte[] b = Libtorrent.saveTorrent(t.t);
        String state = Base64.encodeToString(b, Base64.DEFAULT);
        try {
            JSONObject o = new JSONObject();
            o.put("status", Libtorrent.torrentStatus(t.t));
            o.put("state", state);
            o.put("path", t.path.toString());
            o.put("message", t.message);
            o.put("done", t.done);
            edit.putString("torrent_" + i, o.toString());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public void create() {
        TorrentService.startService(context, formatHeader());

        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            String version = pInfo.versionName;
            Libtorrent.setClientVersion(context.getString(R.string.app_name) + " " + version);
        } catch (PackageManager.NameNotFoundException e) {
        }

        Libtorrent.setBindAddr(":0");

        Libtorrent.torrentStorageSet(this);

        if (!Libtorrent.create()) {
            throw new RuntimeException(Libtorrent.error());
        }

        updateRates();

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        Libtorrent.setDefaultAnnouncesList(shared.getString(MainApplication.PREFERENCE_ANNOUNCE, ""));

        boolean wifi = shared.getBoolean(MainApplication.PREFERENCE_WIFI, true);

        if (!connectionsAllowed(context)) {
            pause();
        }

        downloaded.start(0);
        uploaded.start(0);

        load();

        refresh();

        // start at least. prevent java.util.ConcurrentModificationException on .torrents
        IntentFilter wifiFilter = new IntentFilter();
        wifiFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        wifiFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        wifiReciver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean wifi = shared.getBoolean(MainApplication.PREFERENCE_WIFI, true);
                final String action = intent.getAction();
                if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
                    SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
                    Log.d(TAG, state.toString());
                    if (wifi) { // suplicant only correspond to 'wifi only'
                        if (intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false)) {
                            resume();
                            return;
                        }
                        if (isConnectedWifi(context)) { // maybe 'state' have incorrect state. check system service additionaly.
                            resume();
                            return;
                        }
                        pause();
                        return;
                    }
                }
                if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                    NetworkInfo state = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    Log.d(TAG, state.toString());
                    if (state.isConnected()) {
                        if (wifi) { // wifi only?
                            switch (state.getType()) {
                                case ConnectivityManager.TYPE_WIFI:
                                case ConnectivityManager.TYPE_ETHERNET:
                                    resume();
                                    return;
                            }
                        } else { // resume for any connection type
                            resume();
                            return;
                        }
                    }
                    // if not state.isConnected() maybe it is not correct, check service information
                    if (wifi) {
                        if (isConnectedWifi(context)) {
                            resume();
                            return;
                        }
                    } else {
                        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                        if (activeNetwork != null) { // connected to the internet
                            resume();
                            return;
                        }
                    }
                    pause();
                    return;
                }
            }
        };
        context.registerReceiver(wifiReciver, wifiFilter);

        if (active()) {
            saveDelay();
            if (wifi) {
                if (isConnectedWifi(context)) {
                    resume();
                }
            } else {
                resume();
            }
        }
    }

    void refresh() {
        if (refresh != null)
            handler.removeCallbacks(refresh);
        refresh = new Runnable() {
            @Override
            public void run() {
                updateHeader();
                updateDone();
                handler.postDelayed(refresh, AlarmManager.SEC1);
            }
        };
        refresh.run();
    }

    void updateDone() {
        for (Torrent t : torrents) {
            if (Libtorrent.metaTorrent(t.t)) {
                if (Libtorrent.torrentPendingBytesLength(t.t) == Libtorrent.torrentPendingBytesCompleted(t.t)) {
                    if (!t.done) {
                        TorrentService.notifyDone(context, t, torrents.indexOf(t));
                    }
                    t.done = true;
                } else {
                    t.done = false;
                }
            }
        }
    }

    boolean active() {
        for (Torrent t : torrents) {
            if (Libtorrent.torrentActive(t.t))
                return true;
        }
        return false;
    }

    void saveDelay() {
        handler.removeCallbacks(save);
        handler.postDelayed(save, SAVE_INTERVAL);
    }

    public void close() {
        Log.d(TAG, "close()");

        save();

        torrents.clear();
        hashs.clear();

        Libtorrent.close();

        if (mcastLock != null) {
            mcastLock.release();
            mcastLock = null;
        }

        if (refresh != null) {
            handler.removeCallbacks(refresh);
            refresh = null;
        }

        handler.removeCallbacks(save);

        if (wifiReciver != null) {
            context.unregisterReceiver(wifiReciver);
            wifiReciver = null;
        }

        TorrentService.stopService(context);
    }

    public void add(Torrent t) {
        synchronized (hashs) {
            torrents.add(t);
            hashs.put(t.hash, t);
        }
        save();
    }

    public int count() {
        return torrents.size();
    }

    public Torrent torrent(int i) {
        return torrents.get(i);
    }

    public void remove(Torrent t) {
        torrents.remove(t);
        hashs.remove(t.hash);
        t.close();
        save();
    }

    public Uri path(long t) {
        for (Torrent a : torrents) {
            if (a.t == t) {
                return a.path;
            }
        }
        throw new RuntimeException("unable to find");
    }

    public boolean isLocalStorageEmpty() {
        File[] ff = getLocalStorage().listFiles();
        if (ff == null)
            return true;
        return ff.length == 0;
    }

    public boolean isExternalStoragePermitted() {
        return permitted(context, PERMISSIONS);
    }

    public File fallbackStorage() {
        File internal = getLocalInternal();

        // Starting in KITKAT, no permissions are required to read or write to the returned path;
        // it's always accessible to the calling app.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            if (!permitted(context, PERMISSIONS))
                return internal;
        }

        File external = getLocalExternal();

        if (external == null)
            return internal;

        return external;
    }

    public Uri getStoragePath() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String path = shared.getString(MainApplication.PREFERENCE_STORAGE, "");
        if (Build.VERSION.SDK_INT >= 21 && path.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Uri uri = Uri.parse(path);
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
            try {
                final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                resolver.takePersistableUriPermission(uri, takeFlags);
                Cursor c = resolver.query(doc, null, null, null, null);
                if (c != null) {
                    c.close();
                    return uri;
                }
            } catch (SecurityException e) {
                Log.d(TAG, "open SAF failed", e);
            }
            path = fallbackStorage().getAbsolutePath(); // we need to fallback to local storage internal or exernal
        }
        File f;
        if (path.startsWith(ContentResolver.SCHEME_FILE)) {
            f = new File(Uri.parse(path).getPath());
        } else {
            f = new File(path);
        }
        if (!permitted(context, PERMISSIONS))
            return Uri.fromFile(getLocalStorage());
        else
            return Uri.fromFile(super.getStoragePath(f));
    }

    public void migrateLocalStorage() {
        File l = getLocalStorage();
        Uri t = getStoragePath();

        // if we are local return
        if (l.equals(t))
            return;

        // we are not local

        migrateTorrents();
        migrateFiles();
    }

    void migrateTorrents() {
        File l = getLocalStorage();
        Uri dir = getStoragePath();
        String s = dir.getScheme();

        boolean touch = false;
        // migrate torrents, then migrate download data
        for (int i = 0; i < torrents.size(); i++) {
            Torrent torrent = torrents.get(i);
            String ts = torrent.path.getScheme();
            if (ts.startsWith(ContentResolver.SCHEME_FILE)) { // only migrate files torrents
                String tf = torrent.path.getPath(); // torrent file
                if (tf.startsWith(l.getPath())) { // only migrate torrent from local storage
                    Libtorrent.stopTorrent(torrent.t);
                    String name = Libtorrent.torrentName(torrent.t);
                    if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
                        File f = new File(tf, name);
                        touch = true;
                        if (f.exists()) {
                            String n = getNameNoExt(f);
                            String e = getExt(f);
                            Uri t = getNextFile(torrent.path, n, e);
                            move(f, t);
                            // target name changed update torrent meta or pause it
                            if (!getDocumentName(t).equals(name)) {
                                // TODO replace with rename when it will be impelemented
                                //Libtorrent.TorrentFileRename(torrent.t, 0, tt.getName());
                            }
                        }
                        torrent.path = dir; // new torrent home = current storage
                    } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
                        if (!Storage.permitted(context, PERMISSIONS))
                            return; // no file operations if not permitted
                        File f = new File(tf, name);
                        touch = true;
                        if (f.exists()) {
                            File d = new File(dir.getPath());
                            File t = getNextFile(new File(d, f.getName()));
                            move(f, t);
                            // target name changed update torrent meta or pause it
                            if (!t.getName().equals(name)) {
                                // TODO replace with rename when it will be impelemented
                                //Libtorrent.TorrentFileRename(torrent.t, 0, tt.getName());
                            }
                        }
                        torrent.path = dir; // new torrent home = current storage
                    } else {
                        throw new RuntimeException("unknown uri");
                    }
                }
            }
        }

        if (touch) {
            save();

            for (Torrent torrent : torrents) {
                torrent.close();
            }

            torrents.clear();
            hashs.clear();

            load();
        }
    }

    void migrateFiles() { // migrate rest files and sub dirs
        File l = getLocalStorage();
        Uri dir = getStoragePath();

        File[] ff = l.listFiles();
        if (ff == null)
            return;

        for (File f : ff) {
            String n = f.getName();
            String e = getExt(f);
            Uri t = getNextFile(dir, n, e);
            move(f, t); // move file and sub dirs
        }
    }

    public Uri move(File f, Uri dir) {
        String s = dir.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Log.d(TAG, "migrate: " + f + " --> " + getTargetName(dir));
            if (f.isDirectory()) {
                Uri t = createFolder(dir, f.getName());
                return move(f, t);
            }
            return move(f, dir);
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            Log.d(TAG, "migrate: " + f + " --> " + dir.getPath());
            if (f.isDirectory()) {
                String[] files = f.list();
                if (files != null) {
                    for (String n : files) {
                        File ff = new File(f, n);
                        Uri u = child(dir, n);
                        move(ff, u);
                    }
                }
                FileUtils.deleteQuietly(f);
                return dir;
            }

            File to = new File(dir.getPath());
            File parent = to.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                throw new RuntimeException("No permissions: " + parent);
            }

            return Uri.fromFile(com.github.axet.androidlibrary.app.Storage.move(f, to));
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public void pause() {
        Log.d(TAG, "pause()");

        if (mcastLock != null) {
            mcastLock.release();
            mcastLock = null;
        }

        Libtorrent.pause();
    }

    public void resume() {
        Log.d(TAG, "resume()");

        if (mcastLock == null) {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                mcastLock = wm.createMulticastLock(TAG);
                mcastLock.acquire();
            }
        }

        Libtorrent.resume();

        if (active()) {
            saveDelay();
        }
    }

    public String formatHeader() {
        Uri f = getStoragePath();
        long free = getFree(f);
        return MainApplication.formatFree(context, free, downloaded.getCurrentSpeed(), uploaded.getCurrentSpeed());
    }

    public List<String> splitMagnets(String ff) {
        List<String> ret = new ArrayList<>();

        ff = ff.trim();

        String scheme = "magnet:";
        String[] ss = ff.split(scheme);
        if (ss.length > 1) {
            for (String s : ss) {
                s = s.trim();
                if (s.isEmpty())
                    continue;
                ret.add(scheme + s);
            }
            return ret;
        }

        ss = ff.split("\\W+");

        for (String s : ss) {
            s = s.trim();
            if (s.isEmpty())
                continue;
            int len = HASH_LEN;
            if (s.length() % len == 0) {
                int index = 0;
                // check all are 40 bytes hex strings
                while (index < s.length()) {
                    String mag = s.substring(index, index + len);
                    index += mag.length();
                    try {
                        new BigInteger(mag, 16);
                        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
                        String[] tt = shared.getString(MainApplication.PREFERENCE_ANNOUNCE, "").split("\n");
                        ff = "magnet:?xt=urn:btih:" + mag;
                        for (String t : tt) {
                            try {
                                ff += "&tr=" + URLEncoder.encode(t, "UTF-8");
                            } catch (UnsupportedEncodingException e) {
                            }
                        }
                        ret.add(ff);
                    } catch (NumberFormatException e) {
                    }
                }
            }
        }
        return ret;
    }

    public Torrent addMagnet(String s) {
        Uri p = getStoragePath();
        Torrent tt = prepareTorrentFromMagnet(p, s);
        if (tt == null) {
            throw new RuntimeException(Libtorrent.error());
        }
        add(tt);
        return tt;
    }

    public Torrent addTorrentFromBytes(byte[] buf) {
        Uri s = getStoragePath();
        Torrent tt = prepareTorrentFromBytes(s, buf);
        if (tt == null) {
            throw new RuntimeException(Libtorrent.error());
        }
        add(tt);
        return tt;
    }

    public void addTorrentFromURL(String p) {
        Uri s = getStoragePath();
        long t = Libtorrent.addTorrentFromURL(s.toString(), p);
        if (t == -1) {
            throw new RuntimeException(Libtorrent.error());
        }
        add(new Storage.Torrent(context, t, s, true));
    }

    public static boolean isConnectedWifi(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork != null) { // connected to the internet
            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                return true;
            }
        }
        return false;
    }

    public static boolean connectionsAllowed(Context context) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        Libtorrent.setDefaultAnnouncesList(shared.getString(MainApplication.PREFERENCE_ANNOUNCE, ""));

        boolean wifi = shared.getBoolean(MainApplication.PREFERENCE_WIFI, true);

        if (wifi && !isConnectedWifi(context)) {
            return false;
        }

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork != null) { // connected to the internet
            return true;
        }
        return false;
    }

    public void start(Torrent t) {
        t.start();
        saveDelay();
    }

    public void stop(Torrent t) {
        t.stop();
        saveDelay();
    }

    public Torrent find(long t) {
        for (int i = 0; i < torrents.size(); i++) {
            Torrent tt = torrents.get(i);
            if (tt.t == t)
                return tt;
        }
        return null;
    }

    public Torrent find(String hash) {
        for (int i = 0; i < torrents.size(); i++) {
            Torrent tt = torrents.get(i);
            if (tt.hash.equals(hash))
                return tt;
        }
        return null;
    }

    public int getUnreadCount() {
        int count = 0;
        for (int i = 0; i < torrents.size(); i++) {
            if (torrents.get(i).message)
                count++;
        }
        return count;
    }

    public void clearUnreadCount() {
        for (int i = 0; i < torrents.size(); i++) {
            torrents.get(i).message = false;
        }
    }

    public void updateRates() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (!shared.getBoolean(MainApplication.PREFERENCE_SPEEDLIMIT, false)) {
            Libtorrent.setUploadRate(-1);
            Libtorrent.setDownloadRate(-1);
        } else {
            Libtorrent.setUploadRate(shared.getInt(MainApplication.PREFERENCE_UPLOAD, -1) * 1024);
            Libtorrent.setDownloadRate(shared.getInt(MainApplication.PREFERENCE_DOWNLOAD, -1) * 1024);
        }
    }

    @Override
    public void createZeroLengthFile(String hash, String path) throws Exception {
        Torrent t = hashs.get(hash);
        String s = t.path.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            createFile(t.path, path);
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File ff = new File(t.path.getPath(), path);
            ff.createNewFile();
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    @Override
    public long readFileAt(String hash, String path, Buffer buf, long off) throws Exception {
        Torrent t;
        synchronized (hashs) {
            t = hashs.get(hash);
        }
        String s = t.path.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Uri u = child(t.path, path);
            ParcelFileDescriptor fd = resolver.openFileDescriptor(u, "rw"); // rw to make it file request (r or w can be a pipes)
            FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
            FileChannel c = fis.getChannel();
            c.position(off);
            ByteBuffer bb = ByteBuffer.allocate((int) buf.length());
            c.read(bb);
            long l = c.position() - off;
            c.close();
            bb.flip();
            buf.write(bb.array(), 0, l);
            return l;
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File f = new File(t.path.getPath(), path);
            RandomAccessFile r = new RandomAccessFile(f, "r");
            r.seek(off);
            int l = (int) buf.length();
            long rest = r.length() - off;
            if (rest < l)
                l = (int) rest;
            byte[] b = new byte[l];
            int a = r.read(b);
            if (a != l)
                throw new RuntimeException("unable to read a!=l " + a + "!=" + l);
            r.close();
            long k = buf.write(b, 0, l);
            if (l != k)
                throw new RuntimeException("unable to write l!=k " + l + "!=" + k);
            return l;
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    @Override
    public long writeFileAt(String hash, String path, byte[] buf, long off) throws Exception {
        Torrent t;
        synchronized (hashs) {
            t = hashs.get(hash);
        }
        String s = t.path.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Uri u = createFile(t.path, path);
            ParcelFileDescriptor fd = resolver.openFileDescriptor(u, "rw");
            FileOutputStream fos = new FileOutputStream(fd.getFileDescriptor());
            FileChannel c = fos.getChannel();
            c.position(off);
            ByteBuffer bb = ByteBuffer.wrap(buf);
            c.write(bb);
            long l = c.position() - off;
            c.close();
            return l;
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File f = new File(t.path.getPath(), path);
            File p = f.getParentFile();
            p.mkdirs();
            RandomAccessFile r = new RandomAccessFile(f, "rw");
            r.seek(off);
            r.write(buf);
            r.close();
            for (int i = 0; i < buf.length; i++)
                buf[i] = 0;
            return buf.length;
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public Torrent prepareTorrentFromBuilder(Uri pp) {
        synchronized (hashs) {
            final long t = Libtorrent.createTorrentFromMetaInfo();
            if (t == -1) {
                return null;
            }
            Storage.Torrent tt = new Storage.Torrent(context, t, pp, true);
            hashs.put(tt.hash, tt);
            return tt;
        }
    }

    public void cancelTorrent(String hash) { // cancel adding torrent, remove storage IO interface
        synchronized (hashs) {
            Torrent t = hashs.get(hash);
            t.close();
            hashs.remove(hash);
        }
    }

    public Torrent prepareTorrentFromBytes(Uri pp, byte[] buf) {
        synchronized (hashs) {
            long t = Libtorrent.addTorrentFromBytes(pp.toString(), buf);
            if (t == -1)
                return null;
            Storage.Torrent tt = new Storage.Torrent(context, t, pp, true);
            hashs.put(tt.hash, tt);
            return tt;
        }
    }

    public Torrent prepareTorrentFromMagnet(Uri pp, String m) {
        synchronized (hashs) {
            long t = Libtorrent.addMagnet(pp.toString(), m);
            if (t == -1)
                return null;
            Storage.Torrent tt = new Storage.Torrent(context, t, pp, true);
            hashs.put(tt.hash, tt);
            return tt;
        }
    }

}
