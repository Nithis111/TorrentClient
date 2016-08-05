package com.github.axet.torrentclient.dialogs;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class BrowserDialogFragment extends DialogFragment implements MainActivity.TorrentFragmentInterface {
    public static String TAG = BrowserDialogFragment.class.getSimpleName();

    ViewPager pager;
    View v;
    Handler handler = new Handler();
    ImageButton back;
    ImageButton forward;
    WebView web;
    Thread thread;
    int load;

    public static BrowserDialogFragment create(String url, String js) {
        BrowserDialogFragment f = new BrowserDialogFragment();
        Bundle args = new Bundle();
        args.putString("url", url);
        args.putString("js", js);
        f.setArguments(args);
        return f;
    }

    public class Inject {
        @JavascriptInterface
        public void result() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                }
            });
        }
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

        RelativeLayout r = (RelativeLayout) v.findViewById(R.id.search_details_base);

        web = new WebView(getContext()) {
            @Override
            public void postUrl(String url, byte[] postData) {
                super.postUrl(url, postData);
                Log.d(TAG, "post() " + url);
            }
        };

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.BELOW, R.id.search_details_toolbar);
        params.addRule(RelativeLayout.ABOVE, R.id.status_details_status_group);
        web.setLayoutParams(params);
        r.addView(web);

        final ProgressBar progress = (ProgressBar) v.findViewById(R.id.search_details_process);
        final ImageView stop = (ImageView) v.findViewById(R.id.search_details_stop);
        final ImageView refresh = (ImageView) v.findViewById(R.id.search_details_refresh);
        final TextView status = (TextView) v.findViewById(R.id.status_details_status);

        progress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (load < 100) {
                    web.stopLoading();
                    load = 100;
                    return;
                }
                web.reload();
            }
        });

        web.getSettings().setSupportMultipleWindows(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setLoadWithOverviewMode(true);
        web.getSettings().setUseWideViewPort(true);
        web.getSettings().setBuiltInZoomControls(true);
        web.getSettings().setDisplayZoomControls(true);

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

        String js = getArguments().getString("js");

        String result = ";\n\ntorrentclient.result()";

        String script = null;
        if (js != null) {
            script = js + result;
        }

        final String inject = script;

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                load = newProgress;
                if (newProgress < 100) {
                    stop.setVisibility(View.VISIBLE);
                    refresh.setVisibility(View.GONE);
                    progress.setProgress(newProgress);
                } else {
                    stop.setVisibility(View.GONE);
                    refresh.setVisibility(View.VISIBLE);
                    progress.setProgress(0);
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                return true;
            }

            @Override
            public boolean onConsoleMessage(final ConsoleMessage consoleMessage) {
                onConsoleMessage(consoleMessage.message(), consoleMessage.lineNumber(), consoleMessage.sourceId());
                return true;//super.onConsoleMessage(consoleMessage);
            }

            @Override
            public void onConsoleMessage(String msg, int lineNumber, String sourceID) {
                Log.d(TAG, msg);

                if (sourceID == null || sourceID.isEmpty())
                    getMainActivity().post(msg);
            }

            @Override
            public boolean onJsAlert(WebView view, String url, final String message, JsResult result) {
                getMainActivity().post(message);
                result.confirm();
                return true;//super.onJsAlert(view, url, message, result);
            }

        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                updateButtons();
                if (inject != null) {
                    web.loadUrl("javascript:" + inject);
                }
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
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
                status.setText(url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                updateButtons();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("magnet")) {
                    getMainActivity().addMagnet(url);
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, url);
            }

            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return super.shouldInterceptRequest(view, url);
            }
        });

        final String cookieURL = url;

        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(final String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                Log.d(TAG, "onDownloadStart " + url);
                final String cookies = CookieManager.getInstance().getCookie(cookieURL);
                thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            URL u = new URL(url);
                            URLConnection conn = u.openConnection();
                            conn.setRequestProperty("Cookie", cookies);
                            final byte[] buf = IOUtils.toByteArray(conn);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    getMainActivity().addTorrentFromBytes(buf);
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

        if (inject != null)
            web.addJavascriptInterface(new Inject(), "torrentclient");

        web.loadUrl(url);

        return v;
    }

    void updateButtons() {
        if (web == null) // called from on onReceivedHttpError
            return;

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

    // not working. use removeAllCookies() then add ones you need.
    public void clearCookies() {
        String url = getArguments().getString("url");

        CookieManager inst = CookieManager.getInstance();
        // longer url better, domain only can return null
        String cookies = inst.getCookie(url);

        Uri uri = Uri.parse(url);
        String domain = uri.getAuthority();

        if (cookies != null) {
            // we need to set expires, otherwise WebView will keep deleted cookies forever ("name=")
            String expires = "expires=Thu, 01 Jan 1970 03:00:00 GMT"; // # date -r 0 +%a,\ %d\ %b\ %Y\ %H:%M:%S\ GMT

            SimpleDateFormat rfc1123 = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            rfc1123.setTimeZone(TimeZone.getTimeZone("GMT"));
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, 1);
            expires = "expires=" + rfc1123.format(cal.getTime());

            if (Build.VERSION.SDK_INT < 21) {
                CookieSyncManager.createInstance(getContext());
                CookieSyncManager.getInstance().startSync();
            }
            String[] cc = cookies.split(";");
            for (String c : cc) {
                String[] vv = c.split("=");
                for (File f = new File(uri.getPath()); f != null; f = f.getParentFile()) {
                    String p;
                    String path;
                    if (f.equals(new File(File.separator))) {
                        p = "";
                        path = "";
                    } else {
                        p = f.getPath();
                        path = "; path=" + p;
                    }
                    String cookie = vv[0].trim() + "=" + "; domain=" + uri.getAuthority() + path + "; " + expires;
                    String u = new Uri.Builder().scheme("http").authority(domain).path(p).build().toString();
                    inst.setCookie(u, cookie);
                }
            }
            if (Build.VERSION.SDK_INT < 21) {
                CookieSyncManager.getInstance().stopSync();
                CookieSyncManager.getInstance().sync();
                inst.removeSessionCookie();
                inst.removeExpiredCookie();
            } else {
                inst.flush();
                inst.removeSessionCookies(null);
            }
        }
    }
}
