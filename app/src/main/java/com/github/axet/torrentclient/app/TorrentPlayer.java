package com.github.axet.torrentclient.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.support.v7.preference.PreferenceManager;

import com.github.axet.torrentclient.services.TorrentContentProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.innosystec.unrar.Archive;
import de.innosystec.unrar.rarfile.FileHeader;
import libtorrent.Libtorrent;

public class TorrentPlayer {

    public static final String PLAYER_NEXT = TorrentPlayer.class.getCanonicalName() + ".PLAYER_NEXT";
    public static final String PLAYER_PROGRESS = TorrentPlayer.class.getCanonicalName() + ".PLAYER_PROGRESS";
    public static final String PLAYER_STOP = TorrentPlayer.class.getCanonicalName() + ".PLAYER_STOP";
    public static final String PLAYER_PAUSE = TorrentPlayer.class.getCanonicalName() + ".PLAYER_PAUSE";

    public static void save(Context context, TorrentPlayer player) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = shared.edit();
        save(player, edit);
        edit.commit();
    }

    public static void save(TorrentPlayer player, SharedPreferences.Editor edit) {
        if (player != null) {
            Uri uri = player.getUri();
            if (uri != null) {
                edit.putString(MainApplication.PREFERENCE_PLAYER, uri.toString());
                return;
            }
        }
        edit.remove(MainApplication.PREFERENCE_PLAYER);
    }

    public static String formatHeader(Context context, int pos, int dur) {
        String header = MainApplication.formatDuration(context, pos);
        if (dur > 0)
            header += "/" + MainApplication.formatDuration(context, dur);
        return header;
    }

    public static class State {
        public Uri state;
        public Uri uri;
        public String hash;
        public long t;

        public State(String u) {
            this(Uri.parse(u));
        }

        public State(Uri u) {
            state = u;
            String p = u.getPath();
            String[] pp = p.split("/");
            hash = pp[1];
            String v = u.getQueryParameter("t");
            if (v != null && !v.isEmpty())
                t = Integer.parseInt(v);
            Uri.Builder b = u.buildUpon();
            b.clearQuery();
            uri = b.build();
        }
    }


    public static class SortPlayerFiles implements Comparator<PlayerFile> {
        @Override
        public int compare(TorrentPlayer.PlayerFile file, TorrentPlayer.PlayerFile file2) {
            List<String> s1 = MainApplication.splitPath(file.getPath());
            List<String> s2 = MainApplication.splitPath(file2.getPath());

            int c = new Integer(s1.size()).compareTo(s2.size());
            if (c != 0)
                return c;

            for (int i = 0; i < s1.size(); i++) {
                String p1 = s1.get(i);
                String p2 = s2.get(i);
                c = p1.compareTo(p2);
                if (c != 0)
                    return c;
            }

            return 0;
        }
    }

    public static class SortArchiveFiles implements Comparator<ArchiveFile> {
        @Override
        public int compare(ArchiveFile file, ArchiveFile file2) {
            List<String> s1 = MainApplication.splitPath(file.getPath());
            List<String> s2 = MainApplication.splitPath(file2.getPath());

            int c = new Integer(s1.size()).compareTo(s2.size());
            if (c != 0)
                return c;

            for (int i = 0; i < s1.size(); i++) {
                String p1 = s1.get(i);
                String p2 = s2.get(i);
                c = p1.compareTo(p2);
                if (c != 0)
                    return c;
            }

            return 0;
        }
    }

    public static class Receiver extends BroadcastReceiver {
        Context context;

        public Receiver(Context context) {
            this.context = context;
            IntentFilter ff = new IntentFilter();
            ff.addAction(PLAYER_NEXT);
            ff.addAction(PLAYER_PROGRESS);
            ff.addAction(PLAYER_STOP);
            context.registerReceiver(this, ff);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
        }

        public void close() {
            context.unregisterReceiver(this);
        }
    }


    public interface Decoder {
        boolean supported(TorFile f);

        ArrayList<ArchiveFile> list(TorFile f);
    }

    public interface ArchiveFile {
        String getPath();

        InputStream open();

        long getLength();
    }

    public static class TorFile {
        public long index; // libtorrent index
        public libtorrent.File file;

        public TorFile(long i, libtorrent.File f) {
            this.index = i;
            this.file = f;
        }
    }

    public class PlayerFile {
        public int index; // file index in dirrectory / archive
        public int count; // directory count
        public Uri uri;
        public TorFile tor;
        public ArchiveFile file;

        public PlayerFile(TorFile t) {
            this.tor = t;
            uri = TorrentContentProvider.getUriForFile(torrentHash, t.file.getPath());
        }

        public PlayerFile(TorFile t, ArchiveFile f) {
            this.tor = t;
            this.file = f;
            File ff = new File(tor.file.getPath(), file.getPath());
            uri = TorrentContentProvider.getUriForFile(torrentHash, ff.getPath());
        }

        public PlayerFile index(int i, int count) {
            this.index = i;
            this.count = count;
            return this;
        }

        public File getFile() {
            if (file != null) {
                return new File(torrent.path, file.getPath());
            }
            return new File(torrent.path, tor.file.getPath());
        }

        public String getPath() {
            if (file != null) {
                return new File(tor.file.getPath(), file.getPath()).toString();
            }
            return tor.file.getPath();
        }

        public String getName() {
            if (file != null) {
                return new File(file.getPath()).getName();
            }
            return new File(tor.file.getPath()).getName();
        }

        public long getLength() {
            if (file != null)
                return file.getLength();
            return tor.file.getLength();
        }

        public boolean isLoaded() {
            return tor.file.getBytesCompleted() == tor.file.getLength();
        }

        public int getPercent() {
            return (int) (tor.file.getBytesCompleted() * 100 / tor.file.getLength());
        }

        public InputStream open() {
            if (file != null) {
                return file.open();
            }
            try {
                final File local = new File(torrent.path, tor.file.getPath());
                FileInputStream is = new FileInputStream(local);
                return is;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    Context context;
    Storage.Torrent torrent;
    ArrayList<PlayerFile> ff = new ArrayList<>();
    public String torrentHash;
    public String torrentName;
    Storage storage;
    MediaPlayer player;
    int playingIndex = -1;
    Uri playingUri;
    Runnable next;
    Runnable progress = new Runnable() {
        @Override
        public void run() {
            notifyPlayer();
            handler.removeCallbacks(progress);
            handler.postDelayed(progress, 1000);
        }
    };
    Runnable saveDelay = new Runnable() {
        @Override
        public void run() {
            save(context, TorrentPlayer.this);
            saveDelay();
        }
    };
    Handler handler;

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (a.equals(PLAYER_PAUSE)) {
                pause();
            }
        }
    };

    public Decoder RAR = new Decoder() {
        @Override
        public boolean supported(TorFile f) {
            File local = new File(torrent.path, f.file.getPath());
            if (!local.exists())
                return false;
            if (!local.isFile())
                return false;
            return local.getName().toLowerCase().endsWith(".rar");
        }

        @Override
        public ArrayList<ArchiveFile> list(TorFile f) {
            try {
                ArrayList<ArchiveFile> ff = new ArrayList<>();
                File local = new File(torrent.path, f.file.getPath());
                final Archive archive = new Archive(local);
                List<FileHeader> list = archive.getFileHeaders();
                for (FileHeader h : list) {
                    final FileHeader header = h;
                    ArchiveFile a = new ArchiveFile() {

                        @Override
                        public String getPath() {
                            String s = header.getFileNameW();
                            if (s == null || s.isEmpty())
                                s = header.getFileNameString();
                            return s;
                        }

                        @Override
                        public InputStream open() {
                            try {
                                final PipedInputStream is = new PipedInputStream();
                                final PipedOutputStream os = new PipedOutputStream(is);
                                Thread thread = new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            archive.extractFile(header, os);
                                            os.flush();
                                            os.close();
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                }, "Write Archive File");
                                thread.start();
                                return is;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public long getLength() {
                            return header.getFullUnpackSize();
                        }
                    };
                    ff.add(a);
                }
                return ff;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    };

    public Decoder ZIP = new Decoder() {
        @Override
        public boolean supported(TorFile f) {
            File local = new File(torrent.path, f.file.getPath());
            if (!local.exists())
                return false;
            if (!local.isFile())
                return false;
            return local.getName().toLowerCase().endsWith(".zip");
        }

        @Override
        public ArrayList<ArchiveFile> list(TorFile f) {
            try {
                ArrayList<ArchiveFile> ff = new ArrayList<>();
                File local = new File(torrent.path, f.file.getPath());
                final ZipFile zip = new ZipFile(local);
                Enumeration<?> enu = zip.entries();
                while (enu.hasMoreElements()) {
                    final ZipEntry zipEntry = (ZipEntry) enu.nextElement();
                    if (zipEntry.isDirectory())
                        continue;
                    ArchiveFile a = new ArchiveFile() {

                        @Override
                        public String getPath() {
                            return zipEntry.getName();
                        }

                        @Override
                        public InputStream open() {
                            try {
                                return zip.getInputStream(zipEntry);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public long getLength() {
                            return zipEntry.getSize();
                        }
                    };
                    ff.add(a);
                }
                return ff;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    };

    Decoder[] DECODERS = new Decoder[]{RAR, ZIP};

    public TorrentPlayer(Context context, Storage storage, long t) {
        this.handler = new Handler(context.getMainLooper());
        this.context = context;
        this.storage = storage;
        this.torrent = storage.find(t);

        IntentFilter ff = new IntentFilter();
        ff.addAction(PLAYER_PAUSE);
        context.registerReceiver(receiver, ff);

        update();
    }

    public Decoder getDecoder(TorFile f) {
        for (Decoder d : DECODERS) {
            if (d.supported(f))
                return d;
        }
        return null;
    }

    public void update() {
        torrentName = Libtorrent.torrentName(torrent.t);
        torrentHash = Libtorrent.torrentHash(torrent.t);

        long l = Libtorrent.torrentFilesCount(torrent.t);

        ff.clear();
        ArrayList<PlayerFile> ff = new ArrayList<>();
        for (long i = 0; i < l; i++) {
            TorFile f = new TorFile(i, Libtorrent.torrentFiles(torrent.t, i));
            ff.add(new PlayerFile(f).index((int) i, (int) l));
        }
        Collections.sort(ff, new SortPlayerFiles());
        for (PlayerFile f : ff) {
            this.ff.add(f);
            if (f.tor.file.getBytesCompleted() == f.tor.file.getLength()) {
                Decoder d = getDecoder(f.tor);
                if (d != null) {
                    ArrayList<ArchiveFile> list = d.list(f.tor);
                    Collections.sort(list, new SortArchiveFiles());
                    int q = 0;
                    for (ArchiveFile a : list) {
                        this.ff.add(new PlayerFile(f.tor, a).index(q++, list.size()));
                    }
                }
            }
        }
    }

    public int getSize() {
        return ff.size();
    }

    public int getPlaying() {
        return playingIndex;
    }

    public PlayerFile get(int i) {
        return ff.get(i);
    }

    public PlayerFile find(Uri uri) {
        for (PlayerFile f : ff) {
            if (f.uri.equals(uri)) {
                return f;
            }
        }
        return null;
    }

    public boolean open(Uri uri) {
        TorrentPlayer.PlayerFile f = find(uri);
        return open(f);
    }

    public boolean open(PlayerFile f) {
        final int i = ff.indexOf(f);
        if (player != null) {
            player.release();
            player = null;
        }
        playingIndex = i;
        playingUri = f.uri;
        if (f.tor.file.getBytesCompleted() == f.tor.file.getLength())
            player = MediaPlayer.create(context, f.uri);
        if (player == null) {
            Intent intent = new Intent(PLAYER_NEXT);
            intent.putExtra("t", torrent.t);
            context.sendBroadcast(intent);
            next(i + 1);
            return false;
        }
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                next(i + 1);
            }
        });
        return true;
    }

    public void play(final int i) {
        PlayerFile f = get(i);
        if (!open(f))
            return;
        saveDelay();
        player.start();
        progress.run();
    }

    public void next(final int next) {
        handler.removeCallbacks(this.next);
        this.next = new Runnable() {
            @Override
            public void run() {
                TorrentPlayer.this.next = null;
                int n = next;
                if (n >= ff.size()) {
                    stop();
                    return; // n = 0;
                }
                play(n);
            }
        };
        handler.postDelayed(this.next, 1000);
    }

    public boolean isPlaying() { // actual sound
        if (player == null)
            return false;
        return player.isPlaying();
    }

    public boolean isStop() {
        return player == null;
    }

    public void pause() {
        if (getPlaying() != -1) {
            if (player == null) {
                stop(); // clear next
                return;
            }
            if (player.isPlaying()) {
                player.pause();
                progress.run();
                handler.removeCallbacks(progress);
                handler.removeCallbacks(saveDelay);
            } else {
                player.start();
                progress.run();
                saveDelay();
            }
        }
    }

    public void stop() {
        if (player != null) {
            player.stop();
            Intent intent = new Intent(PLAYER_STOP);
            context.sendBroadcast(intent);
        }
        handler.removeCallbacks(progress);
        handler.removeCallbacks(next);
        handler.removeCallbacks(saveDelay);
        next = null;
        playingIndex = -1;
        playingUri = null;
    }

    public void close() {
        stop();
        if (player != null) {
            player.release();
            player = null;
        }
        if (receiver != null) {
            context.unregisterReceiver(receiver);
            receiver = null;
        }
    }

    public long getTorrent() {
        return torrent.t;
    }

    public void notifyPlayer() {
        if (player == null)
            return;
        Intent intent = new Intent(PLAYER_PROGRESS);
        intent.putExtra("t", torrent.t);
        intent.putExtra("pos", player.getCurrentPosition());
        intent.putExtra("dur", player.getDuration());
        intent.putExtra("play", player.isPlaying() || next != null);
        context.sendBroadcast(intent);
    }

    public Uri getUri() {
        if (player == null)
            return null;
        if (playingUri == null)
            return null;
        Uri.Builder b = playingUri.buildUpon();
        b.appendQueryParameter("t", "" + player.getCurrentPosition());
        return b.build();
    }

    public void seek(long i) {
        player.seekTo((int) i);
    }

    public String formatHeader() {
        if (player == null)
            return "";
        return formatHeader(context, player.getCurrentPosition(), player.getDuration());
    }

    public void saveDelay() {
        handler.removeCallbacks(saveDelay);
        handler.postDelayed(saveDelay, 60 * 1000);
    }
}
