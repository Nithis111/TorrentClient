package com.github.axet.torrentclient.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.DownloadListener;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.URL;

public class SearchDialogFragment extends DialogFragment implements MainActivity.TorrentFragmentInterface, DialogInterface {
    ViewPager pager;
    View v;
    Handler handler = new Handler();
    ImageButton back;
    ImageButton forward;
    WebView web;

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

        if (web != null) {
            web.destroy();
            web = null;
        }

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

        web = (WebView) v.findViewById(R.id.webview);

        back = (ImageButton) v.findViewById(R.id.search_details_back);
        forward = (ImageButton) v.findViewById(R.id.search_details_forward);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                web.goBack();
            }
        });
        forward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                web.goForward();
            }
        });

        updateButtons();

        String url = getArguments().getString("url");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                updateButtons();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);

                updateButtons();
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);

                updateButtons();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);

                updateButtons();
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);

                updateButtons();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("magnet")) {
                    getMainActivity().addMagnet(url, true);
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, url);
            }
        });

        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(final String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
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
                            getMainActivity().post(e);
                        }
                    }
                });
                thread.start();
            }
        });

        web.getSettings().setLoadWithOverviewMode(true);
        web.getSettings().setUseWideViewPort(true);
        web.getSettings().setBuiltInZoomControls(true);
        web.getSettings().setDisplayZoomControls(true);

        web.loadUrl(url);

        return v;
    }

    void updateButtons() {
        if (web.canGoBack()) {
            back.setColorFilter(Color.BLACK);
            back.setEnabled(true);
        } else {
            back.setColorFilter(Color.GRAY);
            back.setEnabled(false);
        }
        if (web.canGoForward()) {
            forward.setColorFilter(Color.BLACK);
            forward.setEnabled(true);
        } else {
            forward.setColorFilter(Color.GRAY);
            forward.setEnabled(false);
        }
    }

    @Override
    public void cancel() {

    }
}
