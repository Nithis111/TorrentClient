package com.github.axet.torrentclient.app;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.CookieStore;
import cz.msebera.android.httpclient.client.config.RequestConfig;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.client.methods.AbstractExecutionAwareRequest;
import cz.msebera.android.httpclient.client.methods.CloseableHttpResponse;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.client.protocol.HttpClientContext;
import cz.msebera.android.httpclient.entity.ContentType;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;
import cz.msebera.android.httpclient.impl.client.LaxRedirectStrategy;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import cz.msebera.android.httpclient.util.EntityUtils;

public class ApacheHttp {
    CloseableHttpClient httpclient;
    HttpClientContext httpClientContext = HttpClientContext.create();
    AbstractExecutionAwareRequest request;

    public ApacheHttp() {
        RequestConfig.Builder requestBuilder = RequestConfig.custom();
        requestBuilder = requestBuilder.setConnectTimeout(MainApplication.CONNECTION_TIMEOUT);
        requestBuilder = requestBuilder.setConnectionRequestTimeout(MainApplication.CONNECTION_TIMEOUT);

        this.httpclient = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestBuilder.build())
                .setRedirectStrategy(new LaxRedirectStrategy())
                .build();
    }

    public CookieStore getCookieStore() {
        return httpClientContext.getCookieStore();
    }

    public void setCookieStore(CookieStore store) {
        httpClientContext.setCookieStore(store);
    }

    public AbstractExecutionAwareRequest getRequest() {
        return request;
    }

    public void abort() {
        request.abort();
        request = null;
    }

    public String get(String url) throws IOException {
        try {
            HttpGet httpGet = new HttpGet(url);
            request = httpGet;
            CloseableHttpResponse response = httpclient.execute(httpGet, httpClientContext);
            HttpEntity entity = response.getEntity();
            ContentType contentType = ContentType.getOrDefault(entity);
            String html = IOUtils.toString(entity.getContent(), contentType.getCharset());
            EntityUtils.consume(entity);
            response.close();
            return html;
        } finally {
            request = null;
        }
    }

    public String post(String url, String[][] map) throws IOException {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < map.length; i++) {
            m.put(map[i][0], map[i][1]);
        }
        return post(url, m);
    }

    public String post(String url, Map<String, String> map) throws IOException {
        try {
            HttpPost httpPost = new HttpPost(url);
            request = httpPost;
            List<NameValuePair> nvps = new ArrayList<>();
            for (String key : map.keySet()) {
                String value = map.get(key);
                nvps.add(new BasicNameValuePair(key, value));
            }
            httpPost.setEntity(new UrlEncodedFormEntity(nvps));
            CloseableHttpResponse response = httpclient.execute(httpPost, httpClientContext);
            HttpEntity entity = response.getEntity();
            ContentType contentType = ContentType.getOrDefault(entity);
            String html = IOUtils.toString(entity.getContent(), contentType.getCharset());
            EntityUtils.consume(entity);
            response.close();
            return html;
        } finally {
            request = null;
        }
    }
}
