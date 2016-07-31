package com.github.axet.torrentclient.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.DownloadListener;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.app.MainApplication;
import com.github.axet.torrentclient.fragments.DetailsFragment;
import com.github.axet.torrentclient.fragments.FilesFragment;
import com.github.axet.torrentclient.fragments.PeersFragment;
import com.github.axet.torrentclient.fragments.TrackersFragment;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchDialogFragment extends DialogFragment implements MainActivity.TorrentFragmentInterface {
    ViewPager pager;
    View v;
    Handler handler = new Handler();

    public static SearchDialogFragment create(String url) {
        SearchDialogFragment f = new SearchDialogFragment();
        Bundle args = new Bundle();
        args.putString("url", url);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        final Activity activity = getActivity();
        if (activity instanceof DialogInterface.OnDismissListener) {
            ((DialogInterface.OnDismissListener) activity).onDismiss(dialog);
        }
    }

    public void update() {
        // dialog maybe created but onCreateView not yet called
        if (pager == null)
            return;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setNeutralButton(getContext().getString(R.string.close),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.dismiss();
                            }
                        }
                )
                .setView(createView(LayoutInflater.from(getContext()), null, savedInstanceState))
                .create();
    }

    @Nullable
    @Override
    public View getView() {
        return null;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return v;
    }

    MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

    public View createView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.search_details, container);

        final WebView web = (WebView) v.findViewById(R.id.webview);

        final View back = v.findViewById(R.id.search_details_back);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                web.goBack();
            }
        });
        final View forward = v.findViewById(R.id.search_details_forward);
        forward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                web.goForward();
            }
        });

        back.setEnabled(web.canGoBack());
        forward.setEnabled(web.canGoForward());

        String url = getArguments().getString("url");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                back.setEnabled(web.canGoBack());
                forward.setEnabled(web.canGoForward());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                back.setEnabled(web.canGoBack());
                forward.setEnabled(web.canGoForward());
                return super.shouldOverrideUrlLoading(view, url);
            }
        });

        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(final String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                if (url.startsWith("magnet")) {
                    getMainActivity().addMagnet(url, true);
                } else {
                    Thread thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                final byte[] buf = IOUtils.toByteArray(new URL(url));
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        getMainActivity().addTorrentFromBytes(buf, true);
                                    }
                                });
                            } catch (final IOException e) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        getMainActivity().Error(e.getMessage());
                                    }
                                });
                            }
                        }
                    });
                    thread.start();
                }
            }
        });

        web.getSettings().setLoadWithOverviewMode(true);
        web.getSettings().setUseWideViewPort(true);
        web.getSettings().setBuiltInZoomControls(true);
        web.getSettings().setDisplayZoomControls(true);

        web.loadUrl(url);

        return v;
    }

}
