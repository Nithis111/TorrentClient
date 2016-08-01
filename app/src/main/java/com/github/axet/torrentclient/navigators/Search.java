package com.github.axet.torrentclient.navigators;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.app.SearchEngine;
import com.github.axet.torrentclient.dialogs.SearchDialogFragment;

import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.CookieStore;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.client.methods.CloseableHttpResponse;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.client.protocol.HttpClientContext;
import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.impl.client.BasicCookieStore;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClients;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import cz.msebera.android.httpclient.util.EntityUtils;

public class Search extends BaseAdapter implements DialogInterface.OnDismissListener {
    public static final String TAG = Search.class.getSimpleName();

    public static final String UTF8 = "UTF8";

    Context context;
    MainActivity main;
    ArrayList<SearchItem> list = new ArrayList<>();
    CloseableHttpClient httpclient = HttpClients.createDefault();
    HttpClientContext httpClientContext = HttpClientContext.create();
    Thread thread;
    WebView web;
    SearchEngine engine;
    Handler handler;

    FrameLayout header;
    View search_header;
    View login_header;

    public static class SearchItem {
        public String title;
        public String details;
        public String html;
        public String magnet;
        public String size;
        public String seed;
        public String leech;
        public String torrent;
        public Map<String, String> search;
    }

    public interface Inject {
        @JavascriptInterface
        void result(String html);
    }

    public Search(MainActivity m) {
        this.main = m;
        this.context = m;
        this.handler = new Handler();
    }

    public void setEngine(SearchEngine engine) {
        this.engine = engine;
    }

    public SearchEngine getEngine() {
        return engine;
    }

    public void load(String state) {
        CookieStore cookieStore = httpClientContext.getCookieStore();
        if (cookieStore == null) {
            cookieStore = new BasicCookieStore();
            httpClientContext.setCookieStore(cookieStore);
        }
        cookieStore.clear();

        if (state.isEmpty())
            return;

        try {
            byte[] buf = Base64.decode(state, Base64.DEFAULT);
            ByteArrayInputStream bos = new ByteArrayInputStream(buf);
            ObjectInputStream oos = new ObjectInputStream(bos);
            int count = oos.readInt();
            for (int i = 0; i < count; i++) {
                Cookie c = (Cookie) oos.readObject();
                cookieStore.addCookie(c);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String save() {
        CookieStore cookieStore = httpClientContext.getCookieStore();
        if (cookieStore != null) {
            List<Cookie> cookies = cookieStore.getCookies();
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeInt(cookies.size());
                for (Cookie c : cookies) {
                    oos.writeObject(c);
                }
                oos.flush();
                byte[] buf = bos.toByteArray();
                return Base64.encodeToString(buf, Base64.DEFAULT);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return "";
    }

    public void install(ListView list) {
        list.setAdapter(null);

        LayoutInflater inflater = LayoutInflater.from(context);

        header = new FrameLayout(context);

        login_header = inflater.inflate(R.layout.search_login, header, false);

        search_header = inflater.inflate(R.layout.search_header, header, false);

        final TextView t = (TextView) search_header.findViewById(R.id.search_header_text);
        View search = search_header.findViewById(R.id.search_header_search);
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                request(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Looper.prepare();
                            search(t.getText().toString(), new Runnable() {
                                @Override
                                public void run() {
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Log.d(TAG, "Destory Web");

                                            // hide keyboard on search completed
                                            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                                            imm.hideSoftInputFromWindow(t.getWindowToken(), 0);

                                            if (web != null) {
                                                web.destroy();
                                                web = null;
                                            }

                                            if (thread != null) {
                                                thread.interrupt();
                                                thread = null;
                                            }
                                        }
                                    });
                                }
                            });
                            Looper.loop();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        } finally {
                            Log.d(TAG, "Thread Exit");
                        }
                    }
                });
            }
        });

        header.removeAllViews();
        if (engine.getMap("login") != null) {
            header.addView(login_header);
        } else {
            header.addView(search_header);
        }

        list.addHeaderView(header);

        list.setAdapter(this);
    }

    public void remove(ListView list) {
        list.removeHeaderView(header);
    }

    void request(final Runnable run) {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    run.run();
                } catch (final RuntimeException e) {
                    main.post(e);
                }
            }
        });
        thread.start();
    }

    public Context getContext() {
        return context;
    }

    public void update() {
        notifyDataSetChanged();
    }

    public void close() {
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public SearchItem getItem(int i) {
        return list.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(getContext());

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.search_item, parent, false);
        }

        final SearchItem item = getItem(position);

        TextView text = (TextView) convertView.findViewById(R.id.search_item_name);
        text.setText(item.title);

        ImageView magnet = (ImageView) convertView.findViewById(R.id.search_item_magnet);
        magnet.setEnabled(false);
        magnet.setColorFilter(Color.GRAY);
        if (item.magnet != null) {
            magnet.setEnabled(true);
            magnet.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    main.addMagnet(item.magnet, true);
                }
            });
            magnet.setColorFilter(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent));
        }

        ImageView torrent = (ImageView) convertView.findViewById(R.id.search_item_torrent);
        torrent.setEnabled(false);
        torrent.setColorFilter(Color.GRAY);

        if (item.details != null) {
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SearchDialogFragment d = SearchDialogFragment.create(item.details);
                    d.show(main.getSupportFragmentManager(), "");
                }
            });
        }

        return convertView;
    }

    public void inject(String html, String js, final Inject inject) {
        final String script = js + "\n\nbrowser.result(document.documentElement.outerHTML)";

        final String localhost = "http://localhost";

        web = new WebView(context);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setJavaScriptEnabled(true);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(final ConsoleMessage consoleMessage) {
                String msg = consoleMessage.message() + " " + consoleMessage.lineNumber();
                Log.d(TAG, msg);
                main.post(msg);
                return super.onConsoleMessage(consoleMessage);
            }

            @Override
            public boolean onJsAlert(WebView view, String url, final String message, JsResult result) {
                main.post(message);
                return super.onJsAlert(view, url, message, result);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return super.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
            }

            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request.getUrl().toString().startsWith(localhost))
                    return new WebResourceResponse("", "", null);
                if (request.getUrl().toString().startsWith("http"))
                    return new WebResourceResponse("", "", null);
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (url.startsWith(localhost))
                    return new WebResourceResponse("", "", null);
                if (url.toString().startsWith("http"))
                    return new WebResourceResponse("", "", null);
                return super.shouldInterceptRequest(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                web.loadUrl("javascript:" + script);
                super.onPageFinished(view, url);
            }
        });
        web.addJavascriptInterface(inject, "browser");
        // Uncaught SecurityError: Failed to read the 'cookie' property from 'Document': Cookies are disabled inside 'data:' URLs.
        // called when page loaded with loadData()
        web.loadDataWithBaseURL(localhost, html, "text/html", null, null);
    }

    public void login(Map<String, String> login) throws IOException {
        HttpPost httpPost = new HttpPost("http://targethost/login");
        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("username", "vip"));
        nvps.add(new BasicNameValuePair("password", "secret"));
        httpPost.setEntity(new UrlEncodedFormEntity(nvps));
        CloseableHttpResponse response2 = httpclient.execute(httpPost, httpClientContext);

        try {
            System.out.println(response2.getStatusLine());
            HttpEntity entity2 = response2.getEntity();
            // do something useful with the response body
            // and ensure it is fully consumed
            EntityUtils.consume(entity2);
        } finally {
            response2.close();
        }
    }

    public void search(String search, final Runnable done) throws IOException {
        final Map<String, String> s = engine.getMap("search");

        String post = s.get("post");
        if (post != null) {
        }

        String get = s.get("get");
        if (get != null) {
            String query = URLEncoder.encode(search, "UTF8");
            String url = String.format(get, query);
            final String html = get(url);
            final String js = s.get("js");
            if (js != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        inject(html, js, new Inject() {
                            @JavascriptInterface
                            public void result(String html) {
                                try {
                                    searchList(s, html);
                                    if (done != null)
                                        done.run();
                                } catch (final RuntimeException e) {
                                    main.post(e);
                                }
                            }
                        });
                    }
                });
                return;
            }
            searchList(s, html);
            if (done != null)
                done.run();
        }
    }

    void searchList(Map<String, String> s, String html) {
        Document doc = Jsoup.parse(html);
        Elements list = doc.select(s.get("list"));
        for (int i = 0; i < list.size(); i++) {
            SearchItem item = new SearchItem();
            item.html = list.get(i).outerHtml();
            item.title = matcher(item.html, s.get("title"));
            String magnet = matcher(item.html, s.get("magnet"));
            if (magnet != null)
                item.magnet = Html.fromHtml(magnet).toString(); // always HTML encoded
            item.torrent = matcher(item.html, s.get("torrent"));
            item.size = matcher(item.html, s.get("size"));
            item.seed = matcher(item.html, s.get("seed"));
            item.leech = matcher(item.html, s.get("leech"));
            item.details = matcher(item.html, s.get("details"));
            this.list.add(item);
        }
    }

    String matcher(String html, String q) {
        if (q == null)
            return null;

        String r = null;
        Pattern p = Pattern.compile("(.*):regex\\((.*)\\)", Pattern.DOTALL);
        Matcher m = p.matcher(q);
        if (m.matches()) {
            q = m.group(1);
            r = m.group(2);
        }

        // check for regex only
        p = Pattern.compile("regex\\((.*)\\)", Pattern.DOTALL);
        m = p.matcher(q);
        if (m.matches()) {
            q = null;
            r = m.group(1);
        }

        String a = "";

        if (q == null || q.isEmpty()) {
            a = html;
        } else {
            Document doc1 = Jsoup.parse(html);
            Elements list1 = doc1.select(q);
            if (list1.size() > 0) {
                a = list1.get(0).outerHtml();
            }
        }

        if (r != null) {
            Pattern p1 = Pattern.compile(r);
            Matcher m1 = p1.matcher(a);
            if (m1.matches()) {
                return m1.group(1);
            }
            return ""; // tell we did not find any regex match
        }

        return a;
    }

    String get(String url) throws IOException {
        HttpGet httpGet = new HttpGet(url);
        CloseableHttpResponse response1 = httpclient.execute(httpGet, httpClientContext);
        System.out.println(response1.getStatusLine());
        HttpEntity entity1 = response1.getEntity();
        String html = IOUtils.toString(entity1.getContent(), "UTF8");
        EntityUtils.consume(entity1);
        response1.close();
        return html;
    }

    public void download() {
    }

}
