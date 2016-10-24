package com.github.axet.torrentclient.net;

//import com.subgraph.orchid.TorClient;
//import com.subgraph.orchid.sockets.OrchidSocketFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import cz.msebera.android.httpclient.HttpHost;
import cz.msebera.android.httpclient.HttpRequest;
import cz.msebera.android.httpclient.config.Registry;
import cz.msebera.android.httpclient.config.RegistryBuilder;
import cz.msebera.android.httpclient.conn.socket.ConnectionSocketFactory;
import cz.msebera.android.httpclient.conn.socket.PlainConnectionSocketFactory;
import cz.msebera.android.httpclient.conn.ssl.SSLConnectionSocketFactory;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;
import cz.msebera.android.httpclient.impl.conn.PoolingHttpClientConnectionManager;
import cz.msebera.android.httpclient.protocol.HttpContext;

/*
Android 23 drop SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA cipher suite.

  - https://developer.android.com/reference/javax/net/ssl/SSLEngine.html
  - https://github.com/subgraph/Orchid/issues/16
*/
public class TorProxy implements Proxy {

    public static final String NAME = "tor";

//    static TorClient client;
//    OrchidSocketFactory socketFactory;

    static {
//        client = new TorClient();
//        client.start();
    }

    public class Orichid implements ConnectionSocketFactory {
        @Override
        public Socket createSocket(HttpContext context) throws IOException {
            return new Socket();
        }

        @Override
        public Socket connectSocket(int connectTimeout, Socket sock, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException {
            return sock;//socketFactory.createSocket(remoteAddress.getAddress(), remoteAddress.getPort());
        }
    }

    public TorProxy(HttpProxyClient c) {
        c.http.base = new Orichid();
        c.https.base = new Orichid();
//        socketFactory = new OrchidSocketFactory(client);
    }

    @Override
    public void filter(HttpRequest request, HttpContext context) {
    }
}
