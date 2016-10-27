package com.github.axet.torrentclient.dialogs;

import android.app.Activity;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.github.axet.androidlibrary.net.HttpClient;
import com.github.axet.torrentclient.activities.MainActivity;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;

public class OpenIntentDialogFragment extends DialogFragment {
    Handler handler = new Handler();
    Thread t;

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        final Activity activity = getActivity();
    }

    @Override
    public void onStart() {
        super.onStart();

        final MainActivity activity = (MainActivity) getActivity();

        t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    openURL(getArguments().getString("url"));
                } catch (final RuntimeException e) {
                    activity.post(e);
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (activity.isFinishing())
                            return;
                        dismiss();
                    }
                });
            }
        });
        t.start();
    }

    public void openURL(final String str) {
        final MainActivity activity = (MainActivity) getActivity();
        if (activity == null) // when app was destoryed
            return;

        if (str.startsWith("magnet")) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (activity.isFinishing())
                        return;
                    activity.addMagnet(str);
                }
            });
            return;
        }

        if (str.startsWith("content")) {
            try {
                Uri uri = Uri.parse(str);
                final byte[] buf = IOUtils.toByteArray(activity.getContentResolver().openInputStream(uri));
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (activity.isFinishing())
                            return;
                        activity.addTorrentFromBytes(buf);
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        if (str.startsWith("http")) {
            try {
                URL url = new URL(str);
                URLConnection conn = url.openConnection();
                conn.setConnectTimeout(HttpClient.CONNECTION_TIMEOUT);
                final byte[] buf = IOUtils.toByteArray(conn);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (activity.isFinishing())
                            return;
                        activity.addTorrentFromBytes(buf);
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        if (str.startsWith("file")) {
            Uri uri = Uri.parse(str);
            try {
                String path = uri.getEncodedPath();
                final String s = URLDecoder.decode(path, "UTF-8");
                final byte[] buf = IOUtils.toByteArray(new FileInputStream(new File(s)));
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (activity.isFinishing())
                            return;
                        activity.addTorrentFromBytes(buf);
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        // .torrent?
        if (new File(str).exists()) {
            try {
                final byte[] buf = IOUtils.toByteArray(new FileInputStream(new File(str)));
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (activity.isFinishing())
                            return;
                        activity.addTorrentFromBytes(buf);
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ProgressBar view = new ProgressBar(inflater.getContext());
        view.setIndeterminate(true);

        // wait until torrent loaded
        setCancelable(false);

        //getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        return view;
    }
}
