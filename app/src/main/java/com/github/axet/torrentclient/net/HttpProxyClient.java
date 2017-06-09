package com.github.axet.torrentclient.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.github.axet.androidlibrary.net.HttpClient;
import com.github.axet.torrentclient.app.MainApplication;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import cz.msebera.android.httpclient.HttpHost;
import cz.msebera.android.httpclient.HttpRequest;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.ProtocolException;
import cz.msebera.android.httpclient.client.config.RequestConfig;
import cz.msebera.android.httpclient.client.methods.CloseableHttpResponse;
import cz.msebera.android.httpclient.client.methods.HttpRequestBase;
import cz.msebera.android.httpclient.client.methods.HttpUriRequest;
import cz.msebera.android.httpclient.client.protocol.HttpClientContext;
import cz.msebera.android.httpclient.config.Registry;
import cz.msebera.android.httpclient.config.RegistryBuilder;
import cz.msebera.android.httpclient.conn.socket.ConnectionSocketFactory;
import cz.msebera.android.httpclient.conn.socket.PlainConnectionSocketFactory;
import cz.msebera.android.httpclient.conn.ssl.SSLConnectionSocketFactory;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;
import cz.msebera.android.httpclient.impl.client.LaxRedirectStrategy;
import cz.msebera.android.httpclient.impl.conn.PoolingHttpClientConnectionManager;
import cz.msebera.android.httpclient.protocol.HttpContext;

public class HttpProxyClient extends HttpClient {
    boolean enabled;

    String name = "";
    Proxy proxy;
    MySocketFactory http;
    MySocketFactory https;

    public class MySocketFactory implements ConnectionSocketFactory {
        ConnectionSocketFactory base;

        public MySocketFactory(ConnectionSocketFactory b) {
            this.base = b;
        }

        @Override
        public Socket createSocket(HttpContext context) throws IOException {
            return base.createSocket(context);
        }

        @Override
        public Socket connectSocket(int connectTimeout, Socket sock, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException {
            return base.connectSocket(connectTimeout, sock, host, remoteAddress, localAddress, context);
        }
    }

    public HttpProxyClient() {
    }

    public HttpProxyClient(String cookies) {
        super(cookies);
    }

    @Override
    public void create() {
        super.create();
    }

    static RequestConfig filter(RequestConfig config) {
        if (config == null)
            return null;
        config = RequestConfig.copy(config).setProxy(null).build();
        return config;
    }

    public void filter(HttpRequest request, HttpContext context) {
        if (!enabled) {
            clear(request, context);
        } else {
            proxy.filter(request, context);
        }
    }

    public static void clear(HttpRequest request, HttpContext context) {
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
    }

    @Override
    public RequestConfig build(RequestConfig.Builder builder) {
        builder.setCircularRedirectsAllowed(true);
        return super.build(builder);
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
        http = new MySocketFactory(PlainConnectionSocketFactory.getSocketFactory());
        https = new MySocketFactory(SSLConnectionSocketFactory.getSocketFactory());
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", http)
                .register("https", https)
                .build();
        PoolingHttpClientConnectionManager conn = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        builder.setConnectionManager(conn);
        return super.build(builder);
    }

    @Override
    public CloseableHttpResponse execute(HttpRequestBase request) {
        filter(request, httpClientContext);
        return super.execute(request);
    }

    public void update(Context context) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String name = shared.getString(MainApplication.PREFERENCE_PROXY, "");
        enabled = !name.isEmpty();
        if (!this.name.equals(name)) {
            this.name = name;

            clearProxy();

            if (name.equals(GoogleProxy.NAME)) {
                proxy = new GoogleProxy(this);
            }
            if (name.equals(TorProxy.NAME)) {
                proxy = new TorProxy(this);
            }
        }

        if (!enabled) {
            clearProxy();
        }
    }

    @Override
    public void clearProxy() {
        super.clearProxy();
        if (proxy != null) {
            proxy.close();
            proxy = null;
        }
        http.base = PlainConnectionSocketFactory.getSocketFactory();
        https.base = SSLConnectionSocketFactory.getSocketFactory();
    }
}
