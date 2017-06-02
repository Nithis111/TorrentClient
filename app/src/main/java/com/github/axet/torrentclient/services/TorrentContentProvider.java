package com.github.axet.torrentclient.services;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.support.annotation.Nullable;
import android.webkit.MimeTypeMap;

import com.github.axet.androidlibrary.services.FileProvider;
import com.github.axet.torrentclient.app.MainApplication;
import com.github.axet.torrentclient.app.TorrentPlayer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// <application>
//   <provider
//     android:name="com.github.axet.androidlibrary.services.FileProvider"
//     android:authorities="com.github.axet.android-library"
//     android:exported="false"
//     android:grantUriPermissions="true">
//   </provider>
// </application>
//
// url example:
// content://com.github.axet.torrentclient/778811221de5b06a33807f4c80832ad93b58016e/image.rar/123.mp3

public class TorrentContentProvider extends ContentProvider {
    protected static ProviderInfo info;

    public static String getType(String file) {
        String type = MimeTypeMap.getFileExtensionFromUrl(file);
        type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(type);
        return type;
    }

    public static Uri getUriForFile(String hash, String file) {
        File f = new File(hash, file);
        String name = f.toString();
        Uri u = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(info.authority).path(name).build();
        return u;
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        TorrentContentProvider.info = info;
        // Sanity check our security
        if (info.exported) {
            throw new SecurityException("Provider must not be exported");
        }
        if (!info.grantUriPermissions) {
            throw new SecurityException("Provider must grant uri permissions");
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder, CancellationSignal cancellationSignal) {
        if (projection == null) {
            projection = FileProvider.COLUMNS;
        }

        MainApplication app = ((MainApplication) getContext().getApplicationContext());

        if (app.player == null)
            return null;

        TorrentPlayer.PlayerFile f = app.player.find(uri);
        if (f == null)
            return null;

        String[] cols = new String[projection.length];
        Object[] values = new Object[projection.length];
        int i = 0;
        for (String col : projection) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                cols[i] = OpenableColumns.DISPLAY_NAME;
                values[i++] = f.getName();
            } else if (OpenableColumns.SIZE.equals(col)) {
                cols[i] = OpenableColumns.SIZE;
                values[i++] = f.getLength();
            }
        }

        cols = FileProvider.copyOf(cols, i);
        values = FileProvider.copyOf(values, i);

        final MatrixCursor cursor = new MatrixCursor(cols, 1);
        cursor.addRow(values);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        MainApplication app = ((MainApplication) getContext().getApplicationContext());

        if (app.player == null)
            return null;

        TorrentPlayer.PlayerFile f = app.player.find(uri);
        if (f == null)
            return null;

        return getType(f.getName());
    }

    @Nullable
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        MainApplication app = ((MainApplication) getContext().getApplicationContext());

        if (app.player == null)
            return null;

        TorrentPlayer.PlayerFile f = app.player.find(uri);
        if (f == null)
            return null;

        final int fileMode = FileProvider.modeToMode(mode);

        try {
            ParcelFileDescriptor[] pp = ParcelFileDescriptor.createPipe();
            ParcelFileDescriptor r = pp[0];
            ParcelFileDescriptor w = pp[1];
            f.open(w);
            return r;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
