package com.github.axet.torrentclient.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class WebViewCustom extends WebView {
    public static final String TAG = WebViewCustom.class.getSimpleName();

    public WebViewCustom(Context context) {
        super(context);
        create();
    }

    public WebViewCustom(Context context, AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public WebViewCustom(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public WebViewCustom(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        create();
    }

    public WebViewCustom(Context context, AttributeSet attrs, int defStyleAttr, boolean privateBrowsing) {
        super(context, attrs, defStyleAttr, privateBrowsing);
        create();
    }

    public void create() {
        getSettings().setSupportMultipleWindows(true);
        getSettings().setDomStorageEnabled(true);
        getSettings().setJavaScriptEnabled(true);
        getSettings().setLoadWithOverviewMode(true);
        getSettings().setUseWideViewPort(true);
        getSettings().setBuiltInZoomControls(true);
        getSettings().setDisplayZoomControls(true);

        setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                WebViewCustom.this.onProgressChanged(view, newProgress);
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
                WebViewCustom.this.onConsoleMessage(msg, lineNumber, sourceID);
            }

            @Override
            public boolean onJsAlert(WebView view, String url, final String message, JsResult result) {
                Log.d(TAG, message);
                result.confirm();
                WebViewCustom.this.onJsAlert(view, url, message, result);
                return true;//super.onJsAlert(view, url, message, result);
            }
        });

        setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                WebViewCustom.this.onPageFinished(view, url);
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                WebViewCustom.this.onReceivedHttpError(view, request, errorResponse);
            }

            @TargetApi(Build.VERSION_CODES.M)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                String str = error.getDescription().toString();
                Log.d(TAG, str);
                WebViewCustom.this.onReceivedError(view, str, request.getUrl().toString());
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                // on M will becalled above method
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    Log.d(TAG, description);
                }
                WebViewCustom.this.onReceivedError(view, description, failingUrl);
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                WebViewCustom.this.onPageCommitVisible(view, url);
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
                WebViewCustom.this.onLoadResource(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                WebViewCustom.this.onPageStarted(view, url, favicon);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return WebViewCustom.this.shouldOverrideUrlLoading(view, url);
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
    }

    @Override
    public void loadUrl(String url) {
        super.loadUrl(url);
    }

    @Override
    public void postUrl(String url, byte[] postData) {
        super.postUrl(url, postData);
    }

    public void onProgressChanged(WebView view, int newProgress) {
    }

    public void onConsoleMessage(String msg, int lineNumber, String sourceID) {
    }

    public void onJsAlert(WebView view, String url, final String message, JsResult result) {
    }

    public void onPageFinished(WebView view, String url) {
    }

    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
    }

    public void onPageCommitVisible(WebView view, String url) {
    }

    public void onReceivedError(WebView view, String message, String url) {
    }

    public void onLoadResource(WebView view, String url) {
    }

    public void onPageStarted(WebView view, String url, Bitmap favicon) {
    }

    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return false;
    }

    // not working. use removeAllCookies() then add ones you need.
    public void clearCookies(String url) {
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
