package com.github.axet.torrentclient.app;

import com.github.axet.androidlibrary.net.HttpClient;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import cz.msebera.android.httpclient.HttpRequest;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.ProtocolException;
import cz.msebera.android.httpclient.client.config.RequestConfig;
import cz.msebera.android.httpclient.client.methods.CloseableHttpResponse;
import cz.msebera.android.httpclient.client.methods.HttpRequestBase;
import cz.msebera.android.httpclient.client.methods.HttpUriRequest;
import cz.msebera.android.httpclient.client.protocol.HttpClientContext;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;
import cz.msebera.android.httpclient.impl.client.LaxRedirectStrategy;
import cz.msebera.android.httpclient.protocol.HttpContext;

public class GoogleProxy extends HttpClient {

    // TODO temporary. will be removed when multi proxy option available.
    public boolean enabled;

    public static final String NAME = "google";

    public static final String md5(final String s) {
        final String MD5 = "MD5";
        try {
            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest
                    .getInstance(MD5);
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            // Create Hex String
            StringBuilder hexString = new StringBuilder();
            for (byte aMessageDigest : messageDigest) {
                String h = Integer.toHexString(0xFF & aMessageDigest);
                while (h.length() < 2)
                    h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public GoogleProxy() {
    }

    public GoogleProxy(String cookies) {
        super(cookies);
    }

    @Override
    public void create() {
        super.create();
        setProxy();
    }

    @Override
    protected CloseableHttpClient build(HttpClientBuilder builder) {
        builder.setRedirectStrategy(new LaxRedirectStrategy() {
            @Override
            public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
                HttpUriRequest r = super.getRedirect(request, response, context);
                filter(r, context);
                return r;
            }
        });
        return super.build(builder);
    }

    RequestConfig filter(RequestConfig config) {
        if (config == null)
            return null;
        config = RequestConfig.copy(config).setProxy(null).build();
        return config;
    }

    void filter(HttpRequest request, HttpContext context) {
        if (request instanceof HttpUriRequest) {
            HttpUriRequest uri = (HttpUriRequest) request;
            // Google Data Saver plugin does not work for sites on https
            if (!enabled || uri.getURI().getScheme().equals("https")) {
                if (request instanceof HttpRequestBase) {
                    HttpRequestBase m = (HttpRequestBase) request;
                    RequestConfig config = filter(m.getConfig());
                    if (config != null) {
                        m.setConfig(config);
                    }
                }
                RequestConfig config = filter((RequestConfig) context.getAttribute(HttpClientContext.REQUEST_CONFIG));
                if (config != null) {
                    context.setAttribute(HttpClientContext.REQUEST_CONFIG, config);
                }
            } else {
                authHeader(request);
            }
        }
    }

    void setProxy() {
        //setProxy("compress.googlezip.net", 80, "http");
        setProxy("compress.googlezip.net", 443, "https");
        //setProxy("proxy.googlezip.net", 80, "http");
        //setProxy("proxy.googlezip.net", 443, "https");
    }

    void authHeader(HttpRequest request) {
        String authValue = "ac4500dd3b7579186c1b0620614fdb1f7d61f944";
        String timestamp = Long.toString(System.currentTimeMillis()).substring(0, 10);
        String sid = (timestamp + authValue + timestamp);
        sid = md5(sid);
        String value = "ps=" + timestamp + "-" + "0" + "-" + "0" + "-" + "0" + ", sid=" + sid + ", b=2214" + ", p=115" + ", c=win";
        request.addHeader("Chrome-Proxy", value);
        request.addHeader("Upgrade-Insecure-Requests", "1");
        request.addHeader("Save-Data", "on");
    }

    @Override
    public CloseableHttpResponse execute(HttpRequestBase request) {
        filter(request, httpClientContext);
        return super.execute(request);
    }
}
