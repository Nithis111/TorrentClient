package com.github.axet.torrentclient.net;

//import com.subgraph.orchid.TorClient;
//import com.subgraph.orchid.sockets.OrchidSocketFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import cz.msebera.android.httpclient.HttpHost;
import cz.msebera.android.httpclient.HttpRequest;
import cz.msebera.android.httpclient.conn.socket.ConnectionSocketFactory;
import cz.msebera.android.httpclient.protocol.HttpContext;

public class TorProxy implements Proxy {

    public static final String NAME = "tor";

    static int count;
//    static TorClient client;
//    OrchidSocketFactory socketFactory;

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
        if (count == 0) {
//            client = new TorClient();
//            client.start();
        }
        c.http.base = new Orichid();
        c.https.base = new Orichid();
//        socketFactory = new OrchidSocketFactory(client);
    }

    @Override
    public void close() {
        count--;
        if (count == 0) {
            ;
        }
    }

    @Override
    public void filter(HttpRequest request, HttpContext context) {
    }
}
