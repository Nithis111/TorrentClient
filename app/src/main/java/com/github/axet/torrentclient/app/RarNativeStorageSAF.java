package com.github.axet.torrentclient.app;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileNotFoundException;

import de.innosystec.unrar.NativeStorage;

public class RarNativeStorageSAF extends NativeStorage {
    Storage storage;
    Uri u;
    Uri parent;
    RarNativeStorageSAF parentFolder;

    public RarNativeStorageSAF(Storage storage, Uri parent, Uri u) {
        super((File) null);
        this.storage = storage;
        this.u = u;
        this.parent = parent;
    }

    public RarNativeStorageSAF(Storage storage, RarNativeStorageSAF parent, Uri u) {
        super((File) null);
        this.storage = storage;
        this.u = u;
        this.parentFolder = parent;
        this.parent = parentFolder.u;
    }

    public RarNativeStorageSAF(RarNativeStorageSAF v) {
        super((File) null);
        u = Uri.parse(v.u.toString());
        storage = v.storage;
        parent = v.parent;
    }

    @Override
    public RarNativeFileSAF read() throws FileNotFoundException {
        return new RarNativeFileSAF(storage, u, "r");
    }

    @Override
    public NativeStorage open(String name) {
        return new RarNativeStorageSAF(storage, this, storage.child(parent, name));
    }

    @Override
    public boolean exists() {
        return storage.exists(u);
    }

    @Override
    public NativeStorage getParent() {
        return parentFolder;
    }

    @Override
    public long length() {
        return storage.getLength(u);
    }

    @Override
    public String getPath() {
        return u.toString();
    }

}
