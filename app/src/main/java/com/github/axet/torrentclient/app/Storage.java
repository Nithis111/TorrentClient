package com.github.axet.torrentclient.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Base64;
import android.util.Log;

import com.github.axet.androidlibrary.app.MainLibrary;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.services.TorrentService;
import com.github.axet.wget.SpeedInfo;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.MulticastSocket;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import libtorrent.BytesInfo;
import libtorrent.Libtorrent;
import libtorrent.StatsTorrent;

public class Storage extends com.github.axet.androidlibrary.app.Storage {
    public static final String TAG = Storage.class.getSimpleName();

    public static final String TORRENTS = "torrents";
    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    public static final int SAVE_INTERVAL = 1 * 60 * 1000;

    SpeedInfo downloaded = new SpeedInfo();
    SpeedInfo uploaded = new SpeedInfo();

    ArrayList<Torrent> torrents = new ArrayList<>();

    Handler handler;

    // refresh title
    Runnable refresh;

    // save state every 5 min
    Runnable save;

    BroadcastReceiver wifiReciver;

    WifiManager.MulticastLock mcastLock;
    private static MulticastSocket socket;

    public static class Torrent {
        Context context;

        public long t;
        public String path;
        public boolean message;
        public boolean check; // force check required, files were altered
        public boolean readonly; // readonly files or target path, show warning

        SpeedInfo downloaded = new SpeedInfo();
        SpeedInfo uploaded = new SpeedInfo();

        public Torrent(Context context, long t, String path, boolean message) {
            this.context = context;
            this.t = t;
            this.path = path;
            this.message = message;
        }

        public String name() {
            String name = Libtorrent.torrentName(t);
            // can be empy for magnet links, show hash instead
            if (name.isEmpty()) {
                name = Libtorrent.torrentHash(t);
            }
            return name;
        }

        public void start() {
            File f = new File(path);
            if (!f.exists())
                f.mkdirs();
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
                        str += MainLibrary.formatSize(context, Libtorrent.torrentBytesLength(t)) + " · ";

                    str += "↓ " + MainLibrary.formatSize(context, downloaded.getCurrentSpeed()) + context.getString(R.string.per_second);
                    str += " · ↑ " + MainLibrary.formatSize(context, uploaded.getCurrentSpeed()) + context.getString(R.string.per_second);
                    break;
                case Libtorrent.StatusDownloading:
                    long c = 0;
                    if (Libtorrent.metaTorrent(t))
                        c = Libtorrent.torrentPendingBytesLength(t) - Libtorrent.torrentPendingBytesCompleted(t);
                    int a = downloaded.getAverageSpeed();
                    String left = "∞";
                    if (c > 0 && a > 0) {
                        long diff = c * 1000 / a;
                        int diffDays = (int) (diff / (24 * 60 * 60 * 1000));
                        if (diffDays < 30)
                            left = "" + MainLibrary.formatDuration(context, diff) + "";
                    }
                    str += left;
                    str += " · ↓ " + MainLibrary.formatSize(context, downloaded.getCurrentSpeed()) + context.getString(R.string.per_second);
                    str += " · ↑ " + MainLibrary.formatSize(context, uploaded.getCurrentSpeed()) + context.getString(R.string.per_second);
                    break;
            }

            return str.trim();
        }

        public String toString() {
            if (t == -1) // prevent debugger crash
                return "(deleted)";

            String str = name();

            if (Libtorrent.metaTorrent(t))
                str += " · " + MainLibrary.formatSize(context, Libtorrent.torrentBytesLength(t));

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

        public boolean altered() {
            for (int k = 0; k < Libtorrent.torrentFilesCount(t); k++) {
                libtorrent.File f = Libtorrent.torrentFiles(t, k);
                if (f.getBytesCompleted() != 0) {
                    File file = new File(path, f.getPath());
                    if (!file.exists() || file.length() == 0) {
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean readonly() {
            File path = new File(this.path);
            if (!path.canWrite())
                return true;
            for (int k = 0; k < Libtorrent.torrentFilesCount(t); k++) {
                libtorrent.File f = Libtorrent.torrentFiles(t, k);
                if (f.getBytesCompleted() != 0) {
                    File file = new File(path, f.getPath());
                    if (file.exists() && !file.canWrite()) { // we can only check parent folder and existing files, skip middle folders
                        return true;
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
        TorrentService.updateNotify(context, header);
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

                long t = Libtorrent.loadTorrent(o.getString("path"), b);
                if (t == -1) {
                    Log.d(TAG, Libtorrent.error());
                    continue;
                }
                Torrent tt = new Torrent(context, t, o.getString("path"), o.getBoolean("message"));
                torrents.add(tt);

                if (tt.altered()) {
                    tt.check = true;
                }
                if (tt.readonly()) {
                    tt.readonly = true;
                }

                if (o.getInt("status") != Libtorrent.StatusPaused) {
                    resume.add(tt);
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
            o.put("path", t.path);
            o.put("message", t.message);
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

        if (!Libtorrent.create()) {
            throw new RuntimeException(Libtorrent.error());
        }

        updateRates();

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        Libtorrent.setDefaultAnnouncesList(shared.getString(MainApplication.PREFERENCE_ANNOUNCE, ""));

        boolean wifi = shared.getBoolean(MainApplication.PREFERENCE_WIFI, true);

        if (wifi && !isConnectedWifi()) {
            pause();
        }

        downloaded.start(0);
        uploaded.start(0);

        load();

        refresh();

        if (active()) {
            saveUpdate();

            if (wifi) {
                if (isConnectedWifi()) {
                    resume();
                }
            } else {
                resume();
            }
        }

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
                        if (isConnectedWifi()) { // maybe 'state' have incorrect state. check system service additionaly.
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
                        if (isConnectedWifi()) {
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
    }

    void refresh() {
        if (refresh != null)
            handler.removeCallbacks(refresh);

        refresh = new Runnable() {
            @Override
            public void run() {
                updateHeader();
                handler.postDelayed(refresh, 1000);
            }
        };
        refresh.run();
    }

    boolean active() {
        for (Torrent t : torrents) {
            if (Libtorrent.torrentActive(t.t))
                return true;
        }
        return false;
    }

    void saveUpdate() {
        if (save != null)
            handler.removeCallbacks(save);

        save = new Runnable() {
            @Override
            public void run() {
                save();

                if (!active())
                    return;

                saveUpdate();
            }
        };
        handler.postDelayed(save, SAVE_INTERVAL);
    }

    public void close() {
        Log.d(TAG, "close()");

        save();

        torrents.clear();

        Libtorrent.close();

        if (mcastLock != null) {
            mcastLock.release();
            mcastLock = null;
        }

        if (refresh != null) {
            handler.removeCallbacks(refresh);
            refresh = null;
        }

        if (save != null) {
            handler.removeCallbacks(save);
            save = null;
        }

        if (wifiReciver != null) {
            context.unregisterReceiver(wifiReciver);
            wifiReciver = null;
        }

        TorrentService.stopService(context);
    }

    public void add(Torrent t) {
        torrents.add(t);

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

        long d = t.t; // prevent debugger to crash
        t.t = -1;
        Libtorrent.removeTorrent(d);

        save();
    }

    public String path(long t) {
        for (Torrent a : torrents) {
            if (a.t == t) {
                return a.path;
            }
        }
        throw new RuntimeException("unable to find");
    }

    public boolean permitted(String[] ss) {
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(context, s) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public boolean isLocalStorageEmpty() {
        return getLocalStorage().listFiles().length == 0;
    }

    public boolean isExternalStoragePermitted() {
        return permitted(PERMISSIONS);
    }

    public File getStoragePath() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String path = shared.getString(MainApplication.PREFERENCE_STORAGE, "");
        File f = new File(path);
        if (!permitted(PERMISSIONS))
            return getLocalStorage();
        else
            return super.getStoragePath(f);
    }

    public void migrateLocalStorage() {
        File l = getLocalStorage();
        File t = getStoragePath();

        // if we are local return
        if (l.equals(t))
            return;

        // we are not local

        migrateTorrents();
        migrateFiles();
    }

    void migrateTorrents() {
        File l = getLocalStorage();
        File t = getStoragePath();

        boolean touch = false;
        // migrate torrents, then migrate download data
        for (int i = 0; i < torrents.size(); i++) {
            Torrent torrent = torrents.get(i);
            if (torrent.path.startsWith(l.getPath())) {
                Libtorrent.stopTorrent(torrent.t);
                String name = Libtorrent.torrentName(torrent.t);
                File f = new File(torrent.path, name);
                File tt = getNextFile(t, f);
                touch = true;
                if (f.exists()) {
                    move(f, tt);
                    // target name changed update torrent meta or pause it
                    if (!tt.getName().equals(name)) {
                        // TODO replace with rename when it will be impelemented
                        //Libtorrent.TorrentFileRename(torrent.t, 0, tt.getName());
                    }
                }
                torrent.path = t.getPath();
            }
        }

        if (touch) {
            save();

            for (Torrent torrent : torrents) {
                Libtorrent.removeTorrent(torrent.t);
            }

            torrents.clear();

            load();
        }
    }

    void migrateFiles() {
        File l = getLocalStorage();
        File t = getStoragePath();

        File[] ff = l.listFiles();

        if (ff != null) {
            for (File f : ff) {
                File tt = getNextFile(t, f);
                move(f, tt);
            }
        }
    }

    public FileOutputStream open(File f) {
        File tmp = f;
        File parent = tmp.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new RuntimeException("unable to create: " + parent);
        }
        if (!parent.isDirectory())
            throw new RuntimeException("target is not a dir: " + parent);
        try {
            return new FileOutputStream(tmp, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void move(File f, File to) {
        Log.d(TAG, "migrate: " + f + " --> " + to);
        if (f.isDirectory()) {
            String[] files = f.list();
            if (files != null) {
                for (String n : files) {
                    File ff = new File(f, n);
                    move(ff, new File(to, n));
                }
            }
            FileUtils.deleteQuietly(f);
            return;
        }

        File parent = to.getParentFile();
        parent.mkdirs();
        if (!parent.exists()) {
            throw new RuntimeException("No permissions: " + parent);
        }

        com.github.axet.androidlibrary.app.Storage.move(f, to);
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
            saveUpdate();
        }
    }

    public String formatHeader() {
        File f = getStoragePath();
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
            int len = 40;
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
        String p = getStoragePath().getPath();
        long t = Libtorrent.addMagnet(p, s);
        if (t == -1) {
            throw new RuntimeException(Libtorrent.error());
        }
        Torrent tt = new Storage.Torrent(context, t, p, true);
        add(tt);
        return tt;
    }

    public Torrent addTorrentFromBytes(byte[] buf) {
        String s = getStoragePath().getPath();
        long t = Libtorrent.addTorrentFromBytes(s, buf);
        if (t == -1) {
            throw new RuntimeException(Libtorrent.error());
        }
        Torrent tt = new Storage.Torrent(context, t, s, true);
        add(tt);
        return tt;
    }

    public void addTorrentFromURL(String p) {
        String s = getStoragePath().getPath();
        long t = Libtorrent.addTorrentFromURL(s, p);
        if (t == -1) {
            throw new RuntimeException(Libtorrent.error());
        }
        add(new Storage.Torrent(context, t, s, true));
    }

    public boolean isConnectedWifi() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork != null) { // connected to the internet
            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                return true;
            }
        }
        return false;
    }

    public void start(Torrent t) {
        t.start();
        saveUpdate();
    }

    public void stop(Torrent t) {
        t.stop();
        saveUpdate();
    }

    public Torrent find(long t) {
        for (int i = 0; i < torrents.size(); i++) {
            Torrent tt = torrents.get(i);
            if (tt.t == t)
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
}
