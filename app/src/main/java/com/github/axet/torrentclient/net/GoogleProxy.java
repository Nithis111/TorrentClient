package com.github.axet.torrentclient.net;

import com.github.axet.androidlibrary.crypto.MD5;

import cz.msebera.android.httpclient.HttpRequest;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.ProtocolException;
import cz.msebera.android.httpclient.client.config.RequestConfig;
import cz.msebera.android.httpclient.client.methods.HttpRequestBase;
import cz.msebera.android.httpclient.client.methods.HttpUriRequest;
import cz.msebera.android.httpclient.client.protocol.HttpClientContext;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;
import cz.msebera.android.httpclient.impl.client.LaxRedirectStrategy;
import cz.msebera.android.httpclient.protocol.HttpContext;

public class GoogleProxy implements Proxy {
    public static final String NAME = "google";

    public GoogleProxy(HttpProxyClient client) {
        //setProxy("compress.googlezip.net", 80, "http");
        client.setProxy("compress.googlezip.net", 443, "https");
        //setProxy("proxy.googlezip.net", 80, "http");
        //setProxy("proxy.googlezip.net", 443, "https");
    }

    public void filter(HttpRequest request, HttpContext context) {
        if (request instanceof HttpUriRequest) {
            HttpUriRequest uri = (HttpUriRequest) request;
            if (uri.getURI().getScheme().equals("https")) {
                // Google Data Saver plugin does not work for sites on https
            } else {
                authHeader(request);
            }
        }
    }

    void authHeader(HttpRequest request) {
        String authValue = "ac4500dd3b7579186c1b0620614fdb1f7d61f944";
        String timestamp = Long.toString(System.currentTimeMillis()).substring(0, 10);
        String sid = (timestamp + authValue + timestamp);
        sid = MD5.digest(sid);
        String value = "ps=" + timestamp + "-" + "0" + "-" + "0" + "-" + "0" + ", sid=" + sid + ", b=2214" + ", p=115" + ", c=win";
        request.addHeader("Chrome-Proxy", value);
        request.addHeader("Upgrade-Insecure-Requests", "1");
        request.addHeader("Save-Data", "on");
    }

}
