package com.github.axet.torrentclient.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;

import com.github.axet.torrentclient.services.TorrentContentProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
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

    Context context;
    Storage.Torrent torrent;
    ArrayList<PlayerFile> ff = new ArrayList<>();
    public String torrentHash;
    public String torrentName;
    Storage storage;
    MediaPlayer player;
    int playing = -1;
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
    Handler handler;

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if(a.equals(PLAYER_PAUSE)) {
                pause();
            }
        }
    };

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

    public static String formatHeader(Context context, int pos, int dur) {
        String header = MainApplication.formatDuration(context, pos);
        if (dur > 0)
            header += "/" + MainApplication.formatDuration(context, dur);
        return header;
    }

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
                            return header.getFileNameW();
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

    public TorrentPlayer(Context context, long t) {
        this.handler = new Handler(context.getMainLooper());
        this.context = context;
        this.storage = ((MainApplication) context.getApplicationContext()).getStorage();
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
        for (long i = 0; i < l; i++) {
            TorFile f = new TorFile(i, Libtorrent.torrentFiles(torrent.t, i));
            ff.add(new PlayerFile(f).index((int) i, (int) l));
            Decoder d = getDecoder(f);
            if (d != null) {
                ArrayList<ArchiveFile> list = d.list(f);
                int q = 0;
                for (ArchiveFile a : list) {
                    ff.add(new PlayerFile(f, a).index(q++, list.size()));
                }
            }
        }
    }

    public int getSize() {
        return ff.size();
    }

    public int getPlaying() {
        return playing;
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

    public void open(Uri uri) {
        TorrentPlayer.PlayerFile f = find(uri);
        open(f);
    }

    public void open(PlayerFile f) {
        final int i = ff.indexOf(f);
        if (player != null) {
            player.release();
        }
        playing = i;
        playingUri = f.uri;
        Intent intent = new Intent(PLAYER_NEXT);
        context.sendBroadcast(intent);
        player = MediaPlayer.create(context, f.uri);
        if (player == null) {
            next(i + 1);
            return;
        }
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                next(i + 1);
            }
        });
        notifyPlayer();
    }

    public void play(final int i) {
        PlayerFile f = get(i);
        open(f);
        player.start();
        progress.run();
    }

    public void next(int next) {
        if (next >= ff.size()) {
            next = 0;
        }
        final int n = next;
        handler.removeCallbacks(this.next);
        this.next = new Runnable() {
            @Override
            public void run() {
                play(n);
            }
        };
        handler.postDelayed(this.next, 1000);
    }

    public boolean isPlaying() {
        if (player == null)
            return false;
        return player.isPlaying();
    }

    public void pause() {
        if (player == null)
            return;
        if (player.isPlaying()) {
            player.pause();
            progress.run();
            handler.removeCallbacks(progress);
        } else {
            player.start();
            progress.run();
        }
    }

    public void stop() {
        Intent intent = new Intent(PLAYER_STOP);
        context.sendBroadcast(intent);
        if (player != null) {
            player.stop();
        }
        handler.removeCallbacks(progress);
        handler.removeCallbacks(next);
        playing = -1;
        playingUri = null;
    }

    public void close() {
        stop();
        if (player != null) {
            player.release();
            player = null;
        }
        context.unregisterReceiver(receiver);
    }

    public long getTorrent() {
        return torrent.t;
    }

    public void notifyPlayer() {
        Intent intent = new Intent(PLAYER_PROGRESS);
        intent.putExtra("t", torrent.t);
        intent.putExtra("pos", player.getCurrentPosition());
        intent.putExtra("dur", player.getDuration());
        intent.putExtra("play", player.isPlaying());
        context.sendBroadcast(intent);
    }

    public Uri getUri() {
        Uri.Builder b = playingUri.buildUpon();
        b.appendQueryParameter("t", "" + player.getCurrentPosition());
        return b.build();
    }

    public void seek(int i) {
        player.seekTo(i);
        notifyPlayer();
    }

    public String formatHeader() {
        return formatHeader(context, player.getCurrentPosition(), player.getDuration());
    }
}
