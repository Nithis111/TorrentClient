package com.github.axet.torrentclient.app;

import com.github.axet.androidlibrary.net.HttpClient;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import cz.msebera.android.httpclient.client.config.RequestConfig;
import cz.msebera.android.httpclient.client.methods.CloseableHttpResponse;
import cz.msebera.android.httpclient.client.methods.HttpRequestBase;

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

    void setProxy() {
        //setProxy("compress.googlezip.net", 80, "http");
        //setProxy("proxy.googlezip.net", 80, "http");
        setProxy("proxy.googlezip.net", 443, "https");
    }

    void authHeader(HttpRequestBase request) {
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
    public CloseableHttpResponse execute(HttpRequestBase request) throws IOException {
        if (enabled) {
            // Google Data Saver plugin does not work for sites on https
            if (request.getURI().getScheme().equals("https")) {
                request.setConfig(null);
                httpClientContext.setRequestConfig(RequestConfig.copy(httpClientContext.getRequestConfig()).setProxy(null).build());
            } else {
                authHeader(request);
            }
        }else {
            request.setConfig(null);
            httpClientContext.setRequestConfig(RequestConfig.copy(httpClientContext.getRequestConfig()).setProxy(null).build());
        }
        return super.execute(request);
    }

}
