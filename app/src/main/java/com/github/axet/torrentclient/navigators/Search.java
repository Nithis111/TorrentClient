package com.github.axet.torrentclient.navigators;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.androidlibrary.widgets.WebViewCustom;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.app.GoogleProxy;
import com.github.axet.torrentclient.app.MainApplication;
import com.github.axet.torrentclient.app.SearchEngine;
import com.github.axet.torrentclient.dialogs.BrowserDialogFragment;
import com.github.axet.torrentclient.dialogs.LoginDialogFragment;
import com.github.axet.torrentclient.widgets.UnreadCountDrawable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.client.CookieStore;
import cz.msebera.android.httpclient.client.methods.AbstractExecutionAwareRequest;
import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.impl.client.BasicCookieStore;

public class Search extends BaseAdapter implements DialogInterface.OnDismissListener,
        UnreadCountDrawable.UnreadCount, MainActivity.NavigatorInterface,
        SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String TAG = Search.class.getSimpleName();

    Context context;
    MainActivity main;
    ArrayList<SearchItem> list = new ArrayList<>();

    BrowserDialogFragment dialog;

    Thread thread;
    Looper threadLooper;

    GoogleProxy http;
    WebViewCustom web;
    SearchEngine engine;
    Handler handler;

    String lastSearch; // last search request
    String lastLogin;// last login user name

    ArrayList<String> message = new ArrayList<>();

    // search header
    View header;
    ViewGroup message_panel;
    View message_close;
    ProgressBar header_progress; // progressbar / button
    View header_stop; // stop image
    View header_search; // search button
    TextView searchText;

    // footer data
    View footer;
    View footer_next; // load next button
    ProgressBar footer_progress; // progress bar / button
    View footer_stop; // stop image

    // load next data
    String next;
    ArrayList<String> nextLast = new ArrayList<>();

    public static class SearchItem {
        public String title;
        public String details;
        public String details_html;
        public String html;
        public String magnet;
        public String size;
        public String seed;
        public String leech;
        public String torrent;
        public Map<String, String> search;
        public String base;
    }

    public class Inject {
        // do not make it public, old phones conflict with method name
        String json;
        String html;

        public Inject() {
        }

        public Inject(String json) {
            this.json = json;
        }

        @JavascriptInterface
        public void result(String html) {
            Log.d(TAG, "result()");
            this.html = html;
        }

        @JavascriptInterface
        public String json() {
            Log.d(TAG, "json()");
            return json;
        }
    }

    public static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public Search(MainActivity m) {
        this.main = m;
        this.context = m;
        this.handler = new Handler();

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

        http = new GoogleProxy();
        http.enabled = shared.getString(MainApplication.PREFERENCE_PROXY, "").equals(GoogleProxy.NAME);

        shared.registerOnSharedPreferenceChangeListener(this);
    }

    public void setEngine(SearchEngine engine) {
        this.engine = engine;
    }

    public SearchEngine getEngine() {
        return engine;
    }

    public void load(String state) {
        CookieStore cookieStore = http.getCookieStore();
        if (cookieStore == null) {
            cookieStore = new BasicCookieStore();
            http.setCookieStore(cookieStore);
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
        CookieStore cookieStore = http.getCookieStore();
        // do not save cookies between restarts for non login
        if (cookieStore != null && engine.getMap("login") != null) {
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

    public void install(final ListView list) {
        list.setAdapter(null); // old phones crash to addHeader

        LayoutInflater inflater = LayoutInflater.from(context);

        header = inflater.inflate(R.layout.search_header, null, false);
        footer = inflater.inflate(R.layout.search_footer, null, false);

        footer_progress = (ProgressBar) footer.findViewById(R.id.search_footer_progress);
        footer_stop = footer.findViewById(R.id.search_footer_stop);
        footer_next = footer.findViewById(R.id.search_footer_next);
        footer_next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                footer_progress.setVisibility(View.VISIBLE);
                footer_stop.setVisibility(View.VISIBLE);
                footer_next.setVisibility(View.GONE);

                request(new Runnable() {
                    @Override
                    public void run() {
                        Map<String, String> s = engine.getMap("search");

                        String url = next;
                        String html = http.get(null, url);

                        searchHtml(s, url, html, new Runnable() {
                            @Override
                            public void run() {
                                // destory looper thread
                                requestCancel();
                            }
                        });
                    }
                }, new Runnable() {
                    @Override
                    public void run() {
                        // thread will be cleared by request()
                        updateLoadNext();
                    }
                });
            }
        });
        footer_progress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestCancel();
            }
        });
        footer_next.setVisibility(View.GONE);
        if (thread == null) {
            footer_progress.setVisibility(View.GONE);
            footer_stop.setVisibility(View.GONE);
        } else {
            footer_progress.setVisibility(View.VISIBLE);
            footer_stop.setVisibility(View.VISIBLE);
        }

        message_panel = (ViewGroup) header.findViewById(R.id.search_header_message_panel);

        if (message.size() == 0) {
            message_panel.setVisibility(View.GONE);
        } else {
            message_panel.setVisibility(View.VISIBLE);
            message_panel.removeAllViews();
            for (int i = 0; i < message.size(); i++) {
                final String msg = message.get(i);

                final View v = inflater.inflate(R.layout.search_message, null);
                message_panel.addView(v);
                TextView text = (TextView) v.findViewById(R.id.search_header_message_text);
                text.setText(msg);

                message_close = v.findViewById(R.id.search_header_message_close);
                message_close.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View vv) {
                        message.remove(msg);
                        message_panel.removeView(v);
                        main.updateUnread();
                        notifyDataSetChanged();

                        if (message.size() == 0) {
                            message_panel.setVisibility(View.GONE);
                        }
                    }
                });
            }
        }

        searchText = (TextView) header.findViewById(R.id.search_header_text);
        header_search = header.findViewById(R.id.search_header_search);
        header_progress = (ProgressBar) header.findViewById(R.id.search_header_progress);
        header_stop = header.findViewById(R.id.search_header_stop);

        searchText.setText(lastSearch);

        header_progress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestCancel();
            }
        });

        updateHeaderButtons();

        header_search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Search.this.list.clear();
                Search.this.nextLast.clear();
                footer_next.setVisibility(View.GONE);
                notifyDataSetChanged();

                request(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            search(searchText.getText().toString(), new Runnable() {
                                @Override
                                public void run() {
                                    // destory looper thread
                                    requestCancel();
                                }
                            });
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }, null);
            }
        });

        View home = header.findViewById(R.id.search_header_home);
        home.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dialog != null)
                    return;

                Map<String, String> home = Search.this.engine.getMap("home");

                String url = home.get("get");

                BrowserDialogFragment d = BrowserDialogFragment.create(url, http.getCookies(), null, null);
                dialog = d;
                d.show(main.getSupportFragmentManager(), "");
            }
        });

        View login = header.findViewById(R.id.search_header_login);
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dialog != null)
                    return;

                Map<String, String> login = Search.this.engine.getMap("login");

                String url = login.get("details");

                String l = null;
                String p = null;

                if (login.get("post") != null) {
                    l = login.get("post_login");
                    p = login.get("post_password");
                }

                // TODO get

                if (l == null && p == null) {
                    LoginDialogFragment d = LoginDialogFragment.create(url, http.getCookies());
                    dialog = d;
                    d.show(main.getSupportFragmentManager(), "");
                } else {
                    LoginDialogFragment d = LoginDialogFragment.create(url, http.getCookies(), lastLogin);
                    dialog = d;
                    d.show(main.getSupportFragmentManager(), "");
                }
            }
        });

        if (engine.getMap("login") == null) {
            login.setVisibility(View.GONE);
            Map<String, String> h = Search.this.engine.getMap("home");
            if (h != null)
                home.setVisibility(View.VISIBLE);
            else
                home.setVisibility(View.GONE);
        } else {
            login.setVisibility(View.VISIBLE);
            home.setVisibility(View.GONE);
        }

        list.addHeaderView(header);
        list.addFooterView(footer);

        list.setAdapter(this);

        handler.post(new Runnable() {
            @Override
            public void run() {
                // hide keyboard on search completed
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInputFromInputMethod(searchText.getWindowToken(), 0);
            }
        });
    }

    public void remove(ListView list) {
        lastSearch = searchText.getText().toString();
        list.removeHeaderView(header);
        list.removeFooterView(footer);
    }

    void updateHeaderButtons() {
        if (thread == null) {
            header_progress.setVisibility(View.GONE);
            header_stop.setVisibility(View.GONE);
            header_search.setVisibility(View.VISIBLE);
        } else {
            header_progress.setVisibility(View.VISIBLE);
            header_stop.setVisibility(View.VISIBLE);
            header_search.setVisibility(View.GONE);
        }
    }

    void updateLoadNext() {
        if (Search.this.next != null) {
            footer_next.setVisibility(View.VISIBLE);
        } else {
            footer_next.setVisibility(View.GONE);
        }
        footer_progress.setVisibility(View.GONE);
        footer_stop.setVisibility(View.GONE);
    }

    void requestCancel() {
        boolean i = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
            i = true;
        }
        if (threadLooper != null) {
            threadLooper.quit();
            threadLooper = null;
            i = true;
        }
        final AbstractExecutionAwareRequest r = http.getRequest();
        if (r != null) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    r.abort();
                }
            });
            thread.start();
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (i)
            Log.d(TAG, "interrupt");
    }

    void request(final Runnable run, final Runnable done) {
        requestCancel();

        header_progress.setVisibility(View.VISIBLE);
        header_stop.setVisibility(View.VISIBLE);
        header_search.setVisibility(View.GONE);

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Looper.prepare();
                    threadLooper = Looper.myLooper();
                    run.run();
                    Looper.loop();
                } catch (final RuntimeException e) {
                    if (thread != null) // ignore errors on abort()
                        error(e);
                } finally {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "Destory Web");

                            if (web != null) {
                                web.destroy();
                                web = null;
                            }
                            // we are this thread, clear it
                            thread = null;
                            threadLooper = null;

                            updateHeaderButtons();

                            if (done != null)
                                done.run();
                        }
                    });
                    Log.d(TAG, "Thread Exit");
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
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        shared.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        this.dialog = null;

        if (dialog instanceof LoginDialogFragment.Result) {
            final LoginDialogFragment.Result l = (LoginDialogFragment.Result) dialog;
            if (l.browser) {
                if (l.clear) {
                    http.clearCookies();
                }
                if (l.cookies != null && !l.cookies.isEmpty())
                    http.addCookies(l.cookies);
            } else if (l.ok) {
                request(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            lastLogin = l.login;
                            login(l.login, l.pass, new Runnable() {
                                @Override
                                public void run() {
                                    requestCancel();
                                }
                            });
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }, null);
            }
        }
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

        TextView size = (TextView) convertView.findViewById(R.id.search_item_size);
        if (item.size == null || item.size.isEmpty()) {
            size.setVisibility(View.GONE);
        } else {
            size.setVisibility(View.VISIBLE);
            size.setText(item.size);
        }

        TextView seed = (TextView) convertView.findViewById(R.id.search_item_seed);
        if (item.seed == null || item.seed.isEmpty()) {
            seed.setVisibility(View.GONE);
        } else {
            seed.setVisibility(View.VISIBLE);
            seed.setText(context.getString(R.string.seed_tab) + " " + item.seed);
        }

        TextView leech = (TextView) convertView.findViewById(R.id.search_item_leech);
        if (item.leech == null || item.leech.isEmpty()) {
            leech.setVisibility(View.GONE);
        } else {
            leech.setVisibility(View.VISIBLE);
            leech.setText(context.getString(R.string.leech_tab) + " " + item.leech);
        }

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
                    main.addMagnet(item.magnet);
                }
            });
            magnet.setColorFilter(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent));
        }

        ImageView torrent = (ImageView) convertView.findViewById(R.id.search_item_torrent);
        torrent.setEnabled(false);
        torrent.setColorFilter(Color.GRAY);
        if (item.torrent != null) {
            torrent.setEnabled(true);
            torrent.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final byte[] buf = http.getBytes(item.search.get(item.base), item.torrent);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    main.addTorrentFromBytes(buf);
                                }
                            });
                        }
                    });
                    thread.start();
                }
            });
            torrent.setColorFilter(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent));
        }

        if (item.details != null) {
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (dialog != null)
                        return;

                    String url = item.details;

                    final Map<String, String> s = engine.getMap("search");
                    String js = s.get("details_js");
                    String js_post = s.get("details_js_post");

                    BrowserDialogFragment d = BrowserDialogFragment.create(url, http.getCookies(), js, js_post);
                    dialog = d;
                    d.show(main.getSupportFragmentManager(), "");
                }
            });
        }

        if (item.details_html != null) {
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (dialog != null)
                        return;

                    final Map<String, String> s = engine.getMap("search");
                    String js = s.get("details_js");
                    String js_post = s.get("details_js_post");

                    String html = "<html>";
                    html += "<meta name=\"viewport\" content=\"initial-scale=1.0,user-scalable=no,maximum-scale=1,width=device-width\">";
                    html += "<body>";
                    html += item.details_html;
                    html += "</body></html>";

                    BrowserDialogFragment d = BrowserDialogFragment.create(html, js, js_post);
                    dialog = d;
                    d.show(main.getSupportFragmentManager(), "");
                }
            });
        }

        return convertView;
    }

    public void inject(final String url, final String html, String js, String js_post, final Inject exec) {
        Log.d(TAG, "inject()");

        String result = ";\n\ntorrentclient.result(document.documentElement.outerHTML);";

        String script = null;
        if (js != null) {
            script = js;
            if (js_post == null) // add exec result() only once
                script += result;
        }
        String script_post = null;
        if (js_post != null) {
            script_post = js_post + result;
        }

        if (web != null) {
            web.destroy();
        }

        web = new WebViewCustom(context) {
            @Override
            public boolean onConsoleMessage(String msg, int lineNumber, String sourceID) {
                if (sourceID == null || sourceID.isEmpty() || sourceID.startsWith(INJECTS_URL)) {
                    error(msg + "\nLine:" + lineNumber + "\n" + formatInjectError(sourceID, lineNumber));
                } else if (exec.json != null) { // we uploaded json, then html errors is our responsability
                    String[] lines = web.getHtml().split("\n");
                    int t = lineNumber - 1;
                    String line = "";
                    if (t > 0 && t < lines.length)
                        line = "\n" + lines[t];
                    error(msg + "\nLine:" + lineNumber + line);
                }
                return super.onConsoleMessage(msg, lineNumber, sourceID);
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                error(message);
                return super.onJsAlert(view, url, message, result);
            }
        };
        web.setHttpClient(http);
        web.setInject(script);
        web.setInjectPost(script_post);
        web.addJavascriptInterface(exec, "torrentclient");
        // Uncaught SecurityError: Failed to read the 'cookie' property from 'Document': Cookies are disabled inside 'data:' URLs.
        // called when page loaded with loadData()
        web.loadHtmlWithBaseURL(url, html, url);
    }

    public void login(String login, String pass, final Runnable done) throws IOException {
        final Map<String, String> s = engine.getMap("login");

        final String post = s.get("post");
        if (post != null) {
            String l = s.get("post_login");
            String p = s.get("post_password");
            String pp = s.get("post_params");
            HashMap<String, String> map = new HashMap<>();
            if (l != null)
                map.put(l, login);
            if (p != null)
                map.put(p, pass);
            String[] params = pp.split(";");
            for (String param : params) {
                String[] m = param.split("=");
                map.put(URLDecoder.decode(m[0].trim(), MainApplication.UTF8), URLDecoder.decode(m[1].trim(), MainApplication.UTF8));
            }
            final String html = http.post(null, post, map);

            final String js = s.get("js");
            final String js_post = s.get("js_post");
            if (js != null || js_post != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        inject(post, html, js, js_post, new Inject() {
                            @JavascriptInterface
                            public void result(String html) {
                                super.result(html);
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (done != null)
                                            done.run();
                                    }
                                });
                            }

                            @JavascriptInterface
                            public String json() {
                                return super.json();
                            }
                        });
                    }
                });
                return;
            }
        }

        // TODO get

        if (done != null)
            done.run();
    }

    public void search(String search, final Runnable done) throws IOException {
        Map<String, String> s = engine.getMap("search");

        String url = null;
        String html = "";
        String json = null;

        String post = s.get("post");
        if (post != null) {
            String t = s.get("post_search");
            url = post;
            html = http.post(null, url, new String[][]{{t, search}});
        }

        String get = s.get("get");
        if (get != null) {
            String query = URLEncoder.encode(search, MainApplication.UTF8);
            url = String.format(get, query);
            html = http.get(null, url);
        }

        String json_get = s.get("json_get");
        if (json_get != null) {
            String query = URLEncoder.encode(search, MainApplication.UTF8);
            url = String.format(json_get, query);
            json = http.get(null, url).trim();
        }

        if (html != null) {
            searchHtml(s, url, html, done);
            return;
        }
        if (json == null) {
            searchJson(s, url, json, done);
            return;
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (done != null)
                    done.run();
            }
        });
    }

    public void searchJson(final Map<String, String> s, final String url, final String json, final Runnable done) {
        this.nextLast.add(url);

        final String js = s.get("js");
        final String js_post = s.get("js_post");
        handler.post(new Runnable() {
            @Override
            public void run() {
                inject(url, "", js, js_post, new Inject(json) {
                    @JavascriptInterface
                    public void result(final String html) {
                        super.result(html);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    searchList(s, url, html);
                                } catch (final RuntimeException e) {
                                    error(e);
                                } finally {
                                    if (done != null)
                                        done.run();
                                }
                            }
                        });
                    }

                    @JavascriptInterface
                    public String json() {
                        return super.json();
                    }
                });
            }
        });
    }

    public void searchHtml(final Map<String, String> s, final String url, final String html, final Runnable done) {
        this.nextLast.add(url);

        final String js = s.get("js");
        final String js_post = s.get("js_post");
        if (js != null || js_post != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    inject(url, html, js, js_post, new Inject() {
                        @JavascriptInterface
                        public void result(final String html) {
                            super.result(html);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        searchList(s, url, html);
                                    } catch (final RuntimeException e) {
                                        error(e);
                                    } finally {
                                        if (done != null)
                                            done.run();
                                    }
                                }
                            });
                        }

                        @JavascriptInterface
                        public String json() {
                            return super.json();
                        }
                    });
                }
            });
            return;
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    searchList(s, url, html);
                } catch (final RuntimeException e) {
                    error(e);
                } finally {
                    if (done != null)
                        done.run();
                }
            }
        });
    }

    void searchList(Map<String, String> s, String url, String html) {
        Document doc = Jsoup.parse(html);
        Elements list = doc.select(s.get("list"));
        for (int i = 0; i < list.size(); i++) {
            SearchItem item = new SearchItem();
            item.html = list.get(i).outerHtml();
            item.title = matcher(item.html, s.get("title"));
            item.magnet = matcher(item.html, s.get("magnet"));
            item.torrent = matcher(url, item.html, s.get("torrent"));
            item.size = matcher(item.html, s.get("size"));
            item.seed = matcher(item.html, s.get("seed"));
            item.leech = matcher(item.html, s.get("leech"));
            item.details = matcher(url, item.html, s.get("details"));
            item.details_html = matcherHtml(item.html, s.get("details_html"));
            item.search = s;
            item.base = url;

            // do not empty items
            if (isEmpty(item.title) && isEmpty(item.magnet) && isEmpty(item.torrent) && isEmpty(item.details))
                continue;

            this.list.add(item);
        }

        String next = matcher(url, html, s.get("next"));
        if (next != null) {
            for (String last : nextLast) {
                if (next.equals(last)) {
                    next = null;
                    break;
                }
            }
        }
        this.next = next;

        updateLoadNext();

        if (list.size() > 0) {
            // hide keyboard on search sucecful completed
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(searchText.getWindowToken(), 0);
        }

        notifyDataSetChanged();
    }

    String matcher(String url, String html, String q) {
        String m = matcher(html, q);

        if (m != null) {
            if (m.isEmpty()) {
                return null;
            } else {
                try {
                    URL u = new URL(url);
                    u = new URL(u, m);
                    m = u.toString();
                } catch (MalformedURLException e) {
                }
            }
        }

        return m;
    }

    String matcherHtml(String html, String q) {
        if (q == null)
            return null;

        String all = "(.*)";
        String regex = "regex\\((.*)\\)";
        String child = "nth-child\\((.*)\\)";
        String last = "last";

        Boolean l = false;
        Integer e = null;
        String r = null;
        Pattern p = Pattern.compile(all + ":" + last + ":" + regex, Pattern.DOTALL);
        Matcher m = p.matcher(q);
        if (m.matches()) { // first we look for q:last:regex
            q = m.group(1);
            l = true;
            r = m.group(2);
        } else { // then we look for q:nth-child:regex
            p = Pattern.compile(all + ":" + child + ":" + regex, Pattern.DOTALL);
            m = p.matcher(q);
            if (m.matches()) {
                q = m.group(1);
                e = Integer.parseInt(m.group(2));
                r = m.group(3);
            } else { // then we look for q:regex
                p = Pattern.compile(all + ":" + regex, Pattern.DOTALL);
                m = p.matcher(q);
                if (m.matches()) {
                    q = m.group(1);
                    r = m.group(2);
                } else { // then for regex only
                    p = Pattern.compile(regex, Pattern.DOTALL);
                    m = p.matcher(q);
                    if (m.matches()) {
                        q = null;
                        r = m.group(1);
                    }
                }
            }
        }

        String a = "";

        if (q == null || q.isEmpty()) {
            a = html;
        } else {
            Document doc1 = Jsoup.parse(html, "", Parser.xmlParser());
            Elements list1 = doc1.select(q);
            if (list1.size() > 0) {
                if (l)
                    a = list1.get(list1.size() - 1).outerHtml();
                else if (e != null) {
                    int i = e - 1;
                    if (i < list1.size()) { // ignore offset
                        a = list1.get(i).outerHtml();
                    }
                } else
                    a = list1.get(0).outerHtml();
            }
        }

        if (r != null) {
            Pattern p1 = Pattern.compile(r, Pattern.DOTALL);
            Matcher m1 = p1.matcher(a);
            if (m1.matches()) {
                a = m1.group(1);
            } else {
                a = ""; // tell we did not find any regex match
            }
        }
        return a;
    }

    String matcher(String html, String q) {
        String a = matcherHtml(html, q);
        if (a == null)
            return null;
        return Html.fromHtml(a).toString();
    }

    public void error(final Throwable e) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (main.active(Search.this)) {
                    main.post(e);
                } else {
                    message.add(e.getMessage());
                    main.updateUnread();
                }
            }
        });
    }

    public void error(String msg) {
        if (main.active(this)) {
            main.post(msg);
        } else {
            message.add(msg);
            main.updateUnread();
        }
    }

    @Override
    public int getUnreadCount() {
        return message.size();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        http.enabled = sharedPreferences.getString(MainApplication.PREFERENCE_PROXY, "").equals(GoogleProxy.NAME);
    }
}
