package com.github.axet.torrentclient.app;

import com.github.axet.androidlibrary.net.HttpClient;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.methods.CloseableHttpResponse;
import cz.msebera.android.httpclient.client.methods.HttpRequestBase;

public class GoogleProxy extends HttpClient {

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
        setProxy("compress.googlezip.net", 80, "http");
        setProxy("proxy.googlezip.net", 443, "https");
    }

    void authHeader(HttpRequestBase request) {
        String authValue = "ac4500dd3b7579186c1b0620614fdb1f7d61f944";
        String timestamp = Long.toString(System.currentTimeMillis()).substring(0, 10);
        String[] chromeVersion = {"52", "0", "2743", "82"};
        String sid = (timestamp + authValue + timestamp);
        sid = md5(sid);
        String value = "ps=" + timestamp + "-" + Integer.toString((int) (Math.random() * 1000000000))
                + "-" + Integer.toString((int) (Math.random() * 1000000000))
                + "-" + Integer.toString((int) (Math.random() * 1000000000))
                + ", sid=" + sid + ", b=" + chromeVersion[2] + ", p=" + chromeVersion[3] + ", c=win";
        request.addHeader("Chrome-Proxy", value);
    }

    @Override
    public CloseableHttpResponse execute(String base, HttpRequestBase request) throws IOException {
        authHeader(request);
        return super.execute(base, request);
    }

    String google(String url) {
        if (url == null)
            return null;
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    @Override
    public DownloadResponse getResponse(String base, String url) {
        base = google(base);
        url = google(url);
        return super.getResponse(base, url);
    }

    @Override
    public DownloadResponse postResponse(String base, String url, List<NameValuePair> nvps) {
        base = google(base);
        url = google(url);
        return super.postResponse(base, url, nvps);
    }
}
