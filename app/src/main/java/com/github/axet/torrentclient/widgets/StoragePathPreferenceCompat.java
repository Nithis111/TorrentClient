package com.github.axet.torrentclient.widgets;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.app.Storage;

public class StoragePathPreferenceCompat extends com.github.axet.androidlibrary.widgets.StoragePathPreferenceCompat {
    boolean call = false;
    AlertDialog dialog;

    public StoragePathPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public StoragePathPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StoragePathPreferenceCompat(Context context) {
        super(context);
    }

    @Override
    public void onClick() {
        if (call) {
            super.onClick();
            return;
        }

        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View v = inflater.inflate(R.layout.storage, null);
        View s = v.findViewById(R.id.storage_saf);
        s.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                call = true;
                setPermissionsDialog((Activity) null, null, 0);
                setPermissionsDialog((Fragment) null, null, 0);
                StoragePathPreferenceCompat.this.onClick();
                dismiss();
            }
        });
        View n = v.findViewById(R.id.storage_native);
        n.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                call = true;
                setStorage(new Storage(getContext()) {
                    @Override
                    public Uri getStoragePath(String path) { // prevent reutning SAF
                        Uri u = super.getStoragePath(path);
                        String s = u.getScheme();
                        if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
                            return Uri.fromFile(getStoragePath(fallbackStorage()));
                        }
                        return u;
                    }
                });
                setStorageAccessFramework((Activity) null, 0);
                setStorageAccessFramework((Fragment) null, 0);
                StoragePathPreferenceCompat.this.onClick();
                dismiss();
            }
        });
        b.setView(v);
        b.setTitle("Storage Access Framework (BETA)");
        b.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        dialog = b.create();
        dialog.show();
    }

    void dismiss() {
        dialog.dismiss();
    }
}
