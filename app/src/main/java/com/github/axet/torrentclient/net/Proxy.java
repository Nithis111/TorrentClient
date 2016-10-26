package com.github.axet.torrentclient.net;

import cz.msebera.android.httpclient.HttpRequest;
import cz.msebera.android.httpclient.protocol.HttpContext;

public interface Proxy {
    void filter(HttpRequest request, HttpContext context);

    void close();
}
