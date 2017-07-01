package com.github.axet.torrentclient.app;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

import libtorrent.Buffer;

public class MetainfoBuilder implements libtorrent.MetainfoBuilder {

    Uri parent;
    Uri u;
    Storage storage;
    ContentResolver resolver;
    ArrayList<Info> list = new ArrayList<>();

    public static class Info {
        public String name;
        public long l;
    }

    public MetainfoBuilder(Uri parent, Storage storage, Uri u) {
        this.parent = parent;
        this.storage = storage;
        this.resolver = storage.getContext().getContentResolver();
        this.u = u;
    }

    @Override
    public long filesCount() throws Exception {
        String s = u.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            return 0;
        } else if (s.equals(ContentResolver.SCHEME_FILE)) {
            File f = new File(u.getPath());
            if (f.isFile()) {
                Info info = new Info();
                info.name = f.getName();
                info.l = f.length();
                list.add(info);
            } else if (f.isDirectory()) {
                list(f.getPath(), f);
            }
            return list.size();
        } else {
            throw new RuntimeException("unknown");
        }
    }

    void list(String r, File f) {
        File[] ff = f.listFiles();
        if (ff == null)
            return;
        for (File file : ff) {
            if (file.isFile()) {
                Info info = new Info();
                info.name = file.getPath().substring(r.length() + 1);
                info.l = file.length();
                list.add(info);
            } else if (file.isDirectory()) {
                list(r, file);
            }
        }
    }

    @Override
    public long filesLength(long l) {
        return list.get((int) l).l;
    }

    @Override
    public String filesName(long l) {
        return list.get((int) l).name;
    }

    @Override
    public String name() {
        return Storage.getDocumentName(u);
    }

    @Override
    public long readFileAt(String path, Buffer buf, long off) throws Exception {
        String s = u.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Uri u = storage.child(this.u, path);
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
            File f = new File(parent.getPath(), path);
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
    public String root() {
        return parent.toString();
    }
}
