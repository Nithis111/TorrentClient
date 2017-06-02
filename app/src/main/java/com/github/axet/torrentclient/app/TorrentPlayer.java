package com.github.axet.torrentclient.app;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.List;

import de.innosystec.unrar.Archive;
import de.innosystec.unrar.exception.RarException;
import de.innosystec.unrar.rarfile.FileHeader;
import libtorrent.Libtorrent;

public class TorrentPlayer {

    Context context;
    ArrayList<PlayerFile> ff = new ArrayList<>();
    String torrentName;
    Storage.Torrent torrent;
    Storage storage;
    ArrayList<Decoder> decoders = new ArrayList<>();

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
                                final PipedOutputStream os = new PipedOutputStream();
                                Thread thread = new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            archive.extractFile(header, os);
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                });
                                thread.start();
                                PipedInputStream in = new PipedInputStream(os);
                                return in;
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

    {
        decoders.add(RAR);
    }

    public interface Decoder {
        public boolean supported(TorFile f);

        public ArrayList<ArchiveFile> list(TorFile f);
    }

    public interface ArchiveFile {
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
        public TorFile tor;
        public ArchiveFile file;

        public PlayerFile(TorFile t) {
            this.tor = t;
        }

        public PlayerFile(TorFile t, ArchiveFile f) {
            this.tor = t;
            this.file = f;
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

        public long getLength() {
            if (file != null)
                return file.getLength();
            return tor.file.getLength();
        }

        public boolean isLoaded() {
            return getPercent() == 100;
        }

        public int getPercent() {
            return (int) (tor.file.getBytesCompleted() * 100 / tor.file.getLength());
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

        long l = Libtorrent.torrentFilesCount(torrent.t);

        ff.clear();
        for (long i = 0; i < l; i++) {
            TorFile f = new TorFile(i, Libtorrent.torrentFiles(torrent.t, i));
            ff.add(new PlayerFile(f));
            File local = new File(torrent.path, f.file.getPath());
            Decoder d = getDecoder(f);
            if (d != null) {
                ArrayList<ArchiveFile> list = d.list(f);
                for (ArchiveFile a : list) {
                    ff.add(new PlayerFile(f, a));
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
}
