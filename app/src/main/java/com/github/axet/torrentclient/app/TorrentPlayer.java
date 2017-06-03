package com.github.axet.torrentclient.app;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.github.axet.torrentclient.services.TorrentContentProvider;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import de.innosystec.unrar.Archive;
import de.innosystec.unrar.exception.RarException;
import de.innosystec.unrar.rarfile.FileHeader;
import libtorrent.Libtorrent;

public class TorrentPlayer {

    Context context;
    ArrayList<PlayerFile> ff = new ArrayList<>();
    public String torrentHash;
    public String torrentName;
    Storage.Torrent torrent;
    Storage storage;
    ArrayList<Decoder> decoders = new ArrayList<>();
    MediaPlayer player;

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

    {
        decoders.add(RAR);
        decoders.add(ZIP);
    }

    public interface Decoder {
        public boolean supported(TorFile f);

        public ArrayList<ArchiveFile> list(TorFile f);
    }

    public interface ArchiveFile {
        public int index = 0;

        public String getPath();

        public InputStream open();

        public long getLength();
    }

    public static class TorFile {
        public long index;
        public libtorrent.File file;

        public TorFile(long i, libtorrent.File f) {
            this.file = f;
            this.index = i;
        }
    }

    public class PlayerFile {
        public int index; // file index in dirrectory / archive
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

        public PlayerFile index(int i) {
            this.index = i;
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
        this.context = context;
        this.storage = ((MainApplication) context.getApplicationContext()).getStorage();
        this.torrent = storage.find(t);
        update();
    }

    public Decoder getDecoder(TorFile f) {
        for (Decoder d : decoders) {
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
            ff.add(new PlayerFile(f).index((int) i));
            Decoder d = getDecoder(f);
            if (d != null) {
                ArrayList<ArchiveFile> list = d.list(f);
                int q = 0;
                for (ArchiveFile a : list) {
                    ff.add(new PlayerFile(f, a).index(q++));
                }
            }
        }
    }

    public int getSize() {
        return ff.size();
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

    public void play(int i) {
        PlayerFile f = get(i);
        if (player != null)
            player.release();
        player = MediaPlayer.create(context, f.uri);
        player.start();
    }

    public void close() {
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
