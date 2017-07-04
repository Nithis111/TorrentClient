package com.github.axet.torrentclient.app;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import com.github.axet.androidlibrary.app.AlarmManager;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.PlayerActivity;
import com.github.axet.torrentclient.services.TorrentContentProvider;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import net.lingala.zip4j.core.NativeStorage;
import net.lingala.zip4j.core.ZipFile;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.innosystec.unrar.Archive;
import de.innosystec.unrar.exception.RarException;
import de.innosystec.unrar.rarfile.FileHeader;
import libtorrent.Libtorrent;

public class TorrentPlayer {
    public static String TAG = TorrentPlayer.class.getSimpleName();

    public static final String PLAYER_NEXT = TorrentPlayer.class.getCanonicalName() + ".PLAYER_NEXT";
    public static final String PLAYER_PROGRESS = TorrentPlayer.class.getCanonicalName() + ".PLAYER_PROGRESS";
    public static final String PLAYER_STOP = TorrentPlayer.class.getCanonicalName() + ".PLAYER_STOP";
    public static final String PLAYER_PAUSE = TorrentPlayer.class.getCanonicalName() + ".PLAYER_PAUSE";

    Context context;
    Storage.Torrent torrent;
    ArrayList<PlayerFile> files = new ArrayList<>();
    public String torrentHash;
    public String torrentName;
    Storage storage;
    BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
    TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
    TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
    SimpleExoPlayer player;
    int playingIndex = -1;
    Uri playingUri;
    PlayerFile playingFile;
    Runnable next;
    Runnable progress = new Runnable() {
        @Override
        public void run() {
            notifyProgress();
            handler.removeCallbacks(progress);
            handler.postDelayed(progress, AlarmManager.SEC1);
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
    int video = -1; // index

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
            Uri u = storage.child(torrent.path, f.file.getPath());
            String s = u.getScheme();
            if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
                return u.getPath().endsWith(".rar");
            } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
                File local = new File(u.getPath());
                if (!local.exists())
                    return false;
                if (!local.isFile())
                    return false;
                return local.getName().toLowerCase().endsWith(".rar");
            } else {
                throw new RuntimeException("unknown uri");
            }
        }

        @Override
        public ArrayList<ArchiveFile> list(TorFile f) {
            try {
                ArrayList<ArchiveFile> ff = new ArrayList<>();
                Uri u = storage.child(torrent.path, f.file.getPath());
                String s = u.getScheme();
                final Archive archive;
                if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
                    RarNativeStorageSAF local = new RarNativeStorageSAF(storage, torrent.path, u);
                    archive = new Archive(new RarNativeStorageSAF(local));
                } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
                    File local = new File(u.getPath());
                    archive = new Archive(new de.innosystec.unrar.NativeStorage(local));
                } else {
                    throw new RuntimeException("unknown uri");
                }
                List<FileHeader> list = archive.getFileHeaders();
                for (FileHeader h : list) {
                    if (h.isDirectory())
                        continue;
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
                        public void write(OutputStream os) {
                            try {
                                archive.extractFile(header, os);
                                os.flush();
                                os.close();
                            } catch (IOException | RarException e) {
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
            Uri u = storage.child(torrent.path, f.file.getPath());
            String s = u.getScheme();
            if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
                return u.getPath().endsWith(".zip");
            } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
                File local = new File(u.getPath());
                if (!local.exists())
                    return false;
                if (!local.isFile())
                    return false;
                return local.getName().toLowerCase().endsWith(".zip");
            } else {
                throw new RuntimeException("unknown uri");
            }
        }

        @Override
        public ArrayList<ArchiveFile> list(TorFile f) {
            try {
                ArrayList<ArchiveFile> ff = new ArrayList<>();
                Uri u = storage.child(torrent.path, f.file.getPath());
                String s = u.getScheme();
                final ZipFile zip;
                if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
                    zip = new ZipFile(new ZipNativeStorageSAF(storage, torrent.path, u));
                } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
                    File local = new File(u.getPath());
                    zip = new ZipFile(new NativeStorage(local));
                } else {
                    throw new RuntimeException("unknown uri");
                }
                List list = zip.getFileHeaders();
                for (Object o : list) {
                    final net.lingala.zip4j.model.FileHeader zipEntry = (net.lingala.zip4j.model.FileHeader) o;
                    if (zipEntry.isDirectory())
                        continue;
                    ArchiveFile a = new ArchiveFile() {

                        @Override
                        public String getPath() {
                            return zipEntry.getFileName();
                        }

                        @Override
                        public InputStream open() {
                            try {
                                return zip.getInputStream(zipEntry);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }

                        public void write(OutputStream os) {
                            try {
                                InputStream is = zip.getInputStream(zipEntry);
                                IOUtils.copy(is, os);
                                os.flush();
                                os.close();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public long getLength() {
                            return zipEntry.getUncompressedSize();
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

    public static boolean skipType(PlayerFile f) { // MediaPlayer will open jpg and wait forever
        String type = TorrentContentProvider.getType(f.getName());
        if (type == null || type.isEmpty())
            return false;
        String[] skip = new String[]{"image"};
        for (String s : skip) {
            if (type.startsWith(s))
                return true;
        }
        return false;
    }

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

    public static String formatHeader(Context context, long pos, long dur) {
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

        void write(OutputStream os);

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

        public Uri getFile() {
            if (file != null) { // archive file
                return storage.child(torrent.path, file.getPath());
            } else { // local file
                return storage.child(torrent.path, tor.file.getPath());
            }
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
    }

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
        torrentName = torrent.name();
        torrentHash = torrent.hash;

        long l = Libtorrent.torrentFilesCount(torrent.t);

        files.clear();
        ArrayList<PlayerFile> ff = new ArrayList<>();
        for (long i = 0; i < l; i++) {
            TorFile f = new TorFile(i, Libtorrent.torrentFiles(torrent.t, i));
            if (f.file.getCheck())
                ff.add(new PlayerFile(f).index((int) i, (int) l));
        }
        Collections.sort(ff, new SortPlayerFiles());

        for (PlayerFile f : ff) {
            files.add(f);
            if (f.tor.file.getBytesCompleted() == f.tor.file.getLength()) {
                Decoder d = getDecoder(f.tor);
                if (d != null) {
                    try {
                        ArrayList<ArchiveFile> list = d.list(f.tor);
                        Collections.sort(list, new SortArchiveFiles());
                        int q = 0;
                        for (ArchiveFile a : list) {
                            files.add(new PlayerFile(f.tor, a).index(q++, list.size()));
                        }
                    } catch (RuntimeException e) {
                        Log.d(TAG, "Unable to unpack zip", e);
                    }
                }
            }
        }
    }

    public int getSize() {
        return files.size();
    }

    public int getPlaying() {
        return playingIndex;
    }

    public PlayerFile get(int i) {
        return files.get(i);
    }

    public PlayerFile find(Uri uri) {
        for (PlayerFile f : files) {
            if (f.uri.equals(uri)) {
                return f;
            }
        }
        return null;
    }

    public boolean open(Uri uri) {
        TorrentPlayer.PlayerFile f = find(uri);
        if (f == null)
            return false;
        return open(f);
    }

    public boolean open(PlayerFile f) {
        final int i = files.indexOf(f);
        if (player != null) {
            player.release();
            player = null;
            video = -1;
        }
        playingIndex = i;
        playingUri = f.uri;
        playingFile = f;
        if (f.tor.file.getBytesCompleted() == f.tor.file.getLength()) {
            if (!skipType(f)) {
                prepare(f.uri, i);
            }
        }
        if (player == null) {
            next(i + 1);
            return false;
        }
        return true;
    }

    public void play(final int i) {
        handler.removeCallbacks(next);
        PlayerFile f = get(i);
        if (!open(f))
            return;
        play();
    }

    public void play() {
        if (video != getPlaying()) { // already playing video? just call start()
            String type = TorrentContentProvider.getType(playingFile.getName());
            if (type != null && type.startsWith("video")) {
                PlayerActivity.startActivity(context);
                return;
            } else {
                if (video >= 0) {
                    PlayerActivity.closeActivity(context);
                    video = -1;
                }
            }
        }
        resume();
    }

    public void close(SimpleExoPlayerView view) {
        if (isPlaying())
            pause();
        video = -1; // do not close player, keep seek progress
    }

    public void hide(SimpleExoPlayerView view) {
        video = -1; // do not close player, keep seek progress
    }

    void prepare(Uri u, final int i) {
        player = ExoPlayerFactory.newSimpleInstance(context, trackSelector);
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(context, Util.getUserAgent(context, context.getString(R.string.app_name)));
        ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
        MediaSource source = new ExtractorMediaSource(u, dataSourceFactory, extractorsFactory, null, null);
        player.prepare(source);
        player.addListener(new ExoPlayer.EventListener() {
            @Override
            public void onTimelineChanged(Timeline timeline, Object manifest) {
            }

            @Override
            public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
            }

            @Override
            public void onLoadingChanged(boolean isLoading) {
            }

            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (playbackState == ExoPlayer.STATE_READY) {
                    getDuration();
                }
                if (playbackState == ExoPlayer.STATE_ENDED)
                    next(i + 1);
            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                next(i + 1);
            }

            @Override
            public void onPositionDiscontinuity() {
            }

            @Override
            public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            }

        });
    }

    public void play(SimpleExoPlayerView view) {
        video = playingIndex;
        Long seek = null;
        if (player != null) {
            seek = player.getCurrentPosition();
            player.release();
        }
        prepare(playingUri, playingIndex);
        view.setPlayer(player);
        if (seek != null)
            player.seekTo(seek);
        resume();
    }

    public void resume() {
        player.setPlayWhenReady(true);
        progress.run();
        saveDelay();
    }

    public void next(final int next) {
        handler.removeCallbacks(this.progress);
        handler.removeCallbacks(this.next);
        this.next = new Runnable() {
            @Override
            public void run() {
                TorrentPlayer.this.next = null;
                if (next >= files.size()) {
                    stop();
                    notifyStop();
                    return; // n = 0;
                }
                PlayerFile f = get(next);
                boolean b = open(f);
                notifyNext();
                if (b)
                    play();
            }
        };
        handler.postDelayed(this.next, AlarmManager.SEC1);
    }

    public boolean isPlaying() { // actual sound
        if (player == null)
            return false;
        return player.getPlayWhenReady();
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
            if (isPlaying()) {
                player.setPlayWhenReady(false);
                notifyProgress();
                handler.removeCallbacks(progress);
                handler.removeCallbacks(saveDelay);
            } else {
                play();
            }
        }
    }

    public void stop() {
        if (player != null) {
            player.setPlayWhenReady(false);
        }
        handler.removeCallbacks(progress);
        handler.removeCallbacks(next);
        handler.removeCallbacks(saveDelay);
        next = null;
        playingIndex = -1;
        playingUri = null;
        playingFile = null;
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

    public long getDuration() {
        long d = player.getDuration();
        if (d == C.TIME_UNSET)
            return 0;
        return d;
    }

    Intent notify(String a) {
        Intent intent = new Intent(a);
        intent.putExtra("t", torrent.t);
        if (player != null) {
            intent.putExtra("pos", player.getCurrentPosition());
            intent.putExtra("dur", getDuration());
        }
        return intent;
    }

    public void notifyNext() {
        Intent intent = notify(PLAYER_NEXT);
        intent.putExtra("play", player != null);
        context.sendBroadcast(intent);
    }

    Intent notifyProgressIntent() {
        Intent intent = notify(PLAYER_PROGRESS);
        if (player != null)
            intent.putExtra("play", isPlaying() || next != null);
        else
            intent.putExtra("play", false);
        return intent;
    }

    public void notifyProgress() {
        Intent intent = notifyProgressIntent();
        context.sendBroadcast(intent);
    }

    public void notifyProgress(Receiver receiver) {
        Intent intent = notifyProgressIntent();
        receiver.onReceive(context, intent);
    }

    public void notifyStop() {
        Intent intent = new Intent(PLAYER_STOP);
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
        return formatHeader(context, player.getCurrentPosition(), getDuration());
    }

    public void saveDelay() {
        handler.removeCallbacks(saveDelay);
        handler.postDelayed(saveDelay, AlarmManager.MIN1);
    }
}
