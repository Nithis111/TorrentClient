package com.github.axet.torrentclient.app;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import net.lingala.zip4j.core.NativeFile;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class ZipNativeFileSAF extends NativeFile {
    Storage storage;
    Uri u;
    FileChannel c;
    ParcelFileDescriptor fd;
    FileInputStream fis;
    FileOutputStream fos;

    public ZipNativeFileSAF(Storage storage, Uri u, String mode) throws FileNotFoundException {
        this.u = u;
        this.storage = storage;
        ContentResolver resolver = storage.getContext().getContentResolver();
        fd = resolver.openFileDescriptor(u, "rw");
        if (mode.equals("r")) {
            fis = new FileInputStream(fd.getFileDescriptor());
            c = fis.getChannel();
        }
        if (mode.equals("rw")) {
            fos = new FileOutputStream(fd.getFileDescriptor());
            c = fos.getChannel();
        }
    }

    @Override
    public long length() throws IOException {
        return c.size();
    }

    @Override
    public void seek(long s) throws IOException {
        c.position(s);
    }

    @Override
    public void readFully(byte[] buf, int off, int len) throws IOException {
        int r = read(buf, off, len);
        if (r != len)
            throw new IOException("bad read");
    }

    @Override
    public int read(byte[] buf) throws IOException {
        return read(buf, 0, buf.length);
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(buf, off, len);
        int l = c.read(bb);
        return l;
    }

    @Override
    public long getFilePointer() throws IOException {
        return c.position();
    }

    @Override
    public void close() throws IOException {
        if (c != null) {
            c.close();
            c = null;
        }
        if (fis != null) {
            fis.close();
            fis = null;
        }
        if (fos != null) {
            fos.close();
            fos = null;
        }
        if (fd != null) {
            fd.close();
            fd = null;
        }
    }

    @Override
    public void write(byte[] buf) throws IOException {
        write(buf, 0, buf.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(b, off, len);
        c.write(bb);
    }
}
