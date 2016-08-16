package com.github.axet.torrentclient.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.github.axet.androidlibrary.net.HttpClient;
import com.github.axet.androidlibrary.widgets.WebViewCustom;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.app.GoogleProxy;
import com.github.axet.torrentclient.app.MainApplication;

public class BrowserDialogFragment extends DialogFragment implements MainActivity.TorrentFragmentInterface {
    public static String TAG = BrowserDialogFragment.class.getSimpleName();

    public static String ABOUT_HTML = "about:html";

    ViewPager pager;
    View v;
    Handler handler = new Handler();
    ImageButton back;
    ImageButton forward;
    WebViewCustom web;
    GoogleProxy http;
    Thread thread;
    int load;

    public static boolean logIgnore(String msg) {
        if (msg.contains("insecure content")) // some pages old phones gives: The page at https://www... ran insecure content from inject://0...
            return true;
        if (msg.contains("insecure script")) // Mixed Content: The page at 'https://...' was loaded over HTTPS, but requested an insecure script 'inject://0...'. This content should also be served over HTTPS.
            return true;
        return false;
    }

    public static BrowserDialogFragment create(String head, String url, String cookies, String js, String js_post) {
        BrowserDialogFragment f = new BrowserDialogFragment();
        Bundle args = new Bundle();
        args.putString("head", head);
        args.putString("url", url);
        args.putString("js", js);
        args.putString("js_post", js_post);
        args.putString("cookies", cookies);
        f.setArguments(args);
        return f;
    }

    public static BrowserDialogFragment createHtml(String html_base, String head, String html, String js, String js_post) {
        BrowserDialogFragment f = new BrowserDialogFragment();
        Bundle args = new Bundle();
        args.putString("base", html_base);
        args.putString("head", head);
        args.putString("html", html);
        args.putString("js", js);
        args.putString("js_post", js_post);
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

        final ProgressBar progress = (ProgressBar) v.findViewById(R.id.search_details_process);
        final ImageView stop = (ImageView) v.findViewById(R.id.search_details_stop);
        final ImageView refresh = (ImageView) v.findViewById(R.id.search_details_refresh);
        final TextView status = (TextView) v.findViewById(R.id.status_details_status);

        RelativeLayout r = (RelativeLayout) v.findViewById(R.id.search_details_base);

        final String url = getArguments().getString("url");
        final String head = getArguments().getString("head");
        final String html = getArguments().getString("html");
        final String html_base = getArguments().getString("base", ABOUT_HTML);

        String js = getArguments().getString("js");
        String js_post = getArguments().getString("js_post");
        String result = ";\n\ntorrentclient.result()";
        String script = null;
        String script_post = null;
        if (js != null) {
            script = js;
            if (js_post == null) // only execute result() once
                script += result;
        }
        if (js_post != null) {
            script_post = js_post + result;
        }

        web = new WebViewCustom(getContext()) {
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
            public boolean onConsoleMessage(String msg, int lineNumber, String sourceID) {
                if (logIgnore(msg))
                    return super.onConsoleMessage(msg, lineNumber, sourceID);
                if (sourceID == null || sourceID.isEmpty() || sourceID.startsWith(INJECTS_URL)) {
                    getMainActivity().post(msg + "\nLine:" + lineNumber + "\n" + formatInjectError(sourceID, lineNumber));
                } else if (html != null && sourceID.equals(html_base)) { // we uploaded json, then html errors is our responsability
                    String[] lines = web.getHtml().split("\n");
                    int t = lineNumber - 1;
                    String line = "";
                    if (t > 0 && t < lines.length)
                        line = "\n" + lines[t];
                    getMainActivity().post(msg + "\nLine:" + lineNumber + line);
                }
                return super.onConsoleMessage(msg, lineNumber, sourceID);
            }

            @Override
            public boolean onJsAlert(WebView view, String url, final String message, JsResult result) {
                getMainActivity().post(message);
                return super.onJsAlert(view, url, message, result);
            }

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
            public void onReceivedError(WebView view, String message, String url) {
                super.onReceivedError(view, message, url);
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

            @Override
            public HttpClient.DownloadResponse shouldInterceptRequest(WebView view, String url) {
                if (url.equals(html_base)) {
                    return getBase(html_base, html);
                }
                return super.shouldInterceptRequest(view, url);
            }
        };

        web.setInject(script);
        web.setInjectPost(script_post);
        web.setHead(head);

        status.setText("");

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());

        http = new GoogleProxy();
        http.enabled = shared.getString(MainApplication.PREFERENCE_PROXY, "").equals(GoogleProxy.NAME);
        String cc = getArguments().getString("cookies");
        if (cc != null && !cc.isEmpty())
            http.addCookies(cc);
        web.setHttpClient(http);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.BELOW, R.id.search_details_toolbar);
        params.addRule(RelativeLayout.ABOVE, R.id.status_details_status_group);
        web.setLayoutParams(params);
        r.addView(web);

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

        final String base = url;

        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(final String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                Log.d(TAG, "onDownloadStart " + url);
                thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final byte[] buf = http.getBytes(base, url);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    getMainActivity().addTorrentFromBytes(buf);
                                }
                            });
                        } catch (RuntimeException e) {
                            web.load(WebViewCustom.ABOUT_ERROR, new HttpClient.HttpError(e));
                        }
                    }
                });
                thread.start();
            }
        });

        if (script != null || script_post != null)
            web.addJavascriptInterface(new Inject(), "torrentclient");

        if (url != null)
            web.loadUrl(url);

        if (html != null)
            web.loadHtmlWithBaseURL(html_base, html, html_base);

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
}
