package com.github.axet.torrentclient.navigators;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.app.SearchEngine;
import com.github.axet.torrentclient.dialogs.SearchDialogFragment;

import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.client.methods.CloseableHttpResponse;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClients;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import cz.msebera.android.httpclient.util.EntityUtils;

public class Search extends BaseAdapter implements DialogInterface.OnDismissListener {
    public static final String TAG = Search.class.getSimpleName();

    Context context;
    MainActivity main;
    ArrayList<SearchItem> list = new ArrayList<>();
    CloseableHttpClient httpclient = HttpClients.createDefault();
    Thread thread;
    WebView web;
    SearchEngine engine;
    View header;
    Handler handler;

    public static class SearchItem {
        public String title;
        public String details;
        public String html;
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

    public void install(ListView list) {
        list.setAdapter(null);

        LayoutInflater inflater = LayoutInflater.from(context);

        header = inflater.inflate(R.layout.search_header, list, false);

        final TextView t = (TextView) header.findViewById(R.id.search_header_text);

        View v = header.findViewById(R.id.search_header_search);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                request(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Looper.prepare();
                            engine = new SearchEngine();
                            String json = IOUtils.toString(context.getResources().openRawResource(R.raw.google), "UTF8");
                            engine.loadJson(json);
                            search(t.getText().toString(), new Runnable() {
                                @Override
                                public void run() {
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Log.d(TAG, "Destory Web");
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
                    e.printStackTrace();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            main.Error(e.getMessage());
                        }
                    });
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

    public void inject(String html, String js, Inject inject) {
        final String script = js + "\n\nbrowser.result(document.documentElement.outerHTML)";
        web = new WebView(context);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(final ConsoleMessage consoleMessage) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        main.Error(consoleMessage.message() + " " + consoleMessage.lineNumber());
                    }
                });
                return super.onConsoleMessage(consoleMessage);
            }

            @Override
            public boolean onJsAlert(WebView view, String url, final String message, JsResult result) {
                Log.d(TAG, message);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        main.Error(message);
                    }
                });
                return super.onJsAlert(view, url, message, result);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                web.loadUrl("javascript:" + script);
                super.onPageFinished(view, url);
            }
        });
        web.addJavascriptInterface(inject, "browser");
        web.loadData(html, "text/html", null);
    }

    public void login(Map<String, String> login) throws IOException {
        HttpPost httpPost = new HttpPost("http://targethost/login");
        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("username", "vip"));
        nvps.add(new BasicNameValuePair("password", "secret"));
        httpPost.setEntity(new UrlEncodedFormEntity(nvps));
        CloseableHttpResponse response2 = httpclient.execute(httpPost);

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
                                searchList(s, html);
                                if (done != null)
                                    done.run();
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
        Document doc1 = Jsoup.parse(html);
        Elements list1 = doc1.select(q);
        if (list1.size() > 0) {
            String a = list1.get(0).outerHtml();
            if (r != null) {
                Pattern p1 = Pattern.compile(r);
                Matcher m1 = p1.matcher(a);
                if (m1.matches()) {
                    a = m1.group(1);
                }
            }
            return a;
        }
        return "";
    }

    String get(String url) throws IOException {
        HttpGet httpGet = new HttpGet(url);
        CloseableHttpResponse response1 = httpclient.execute(httpGet);
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
