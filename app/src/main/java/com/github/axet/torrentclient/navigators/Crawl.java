package com.github.axet.torrentclient.navigators;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.axet.androidlibrary.app.AlarmManager;
import com.github.axet.androidlibrary.net.HttpClient;
import com.github.axet.androidlibrary.widgets.HeaderGridView;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.app.SearchEngine;
import com.github.axet.torrentclient.app.Storage;
import com.github.axet.torrentclient.net.HttpProxyClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;

public class Crawl extends Search {
    public static final String TAG = Crawl.class.getSimpleName();

    public static Locale EN = new Locale("en");

    public static int REFRESH_CRAWL = 8 * AlarmManager.HOUR1; // autorefresh interval - 8 hours
    public static int CRAWL_SHOW = 20; // how many items to load per page
    public static int CRAWL_DELAY = AlarmManager.SEC1; // try to download after error / next page - 1 second
    public static int CRAWL_END = 3; // how many tries (last page reload) to confirm end
    public static int CRAWL_DUPS = 20; // how many dups == end

    FrameLayout progressFrame;
    TextView progressStatus;
    ImageView progressRefresh;
    ProgressBar progressBar;
    TreeMap<String, State> crawls = new TreeMap<>();
    ArrayList<State> crawlsTop = new ArrayList<>();
    int crawlsTopIndex = 0;
    Runnable crawlNext = new Runnable() {
        @Override
        public void run() {
            crawlNextThread();
        }
    };
    Thread crawlThread;
    HttpProxyClient crawlHttp;

    CrawlDbHelper db;

    public static boolean isString(String s) {
        if (s.length() < 2)
            return false;
        char[] chars = s.toCharArray();
        for (char c : chars) {
            if (Character.isLetter(c))
                return true;
            if (Character.isDigit(c) && c != '-')
                return true;
        }
        return true;
    }

    public static String getString(Cursor c, String name) {
        int i = c.getColumnIndex(name);
        if (i == -1)
            return null;
        if (c.isNull(i))
            return null;
        String s = c.getString(i);
        if (s == null || s.isEmpty())
            return null;
        return s;
    }

    public static Long getLong(Cursor c, String name) {
        int i = c.getColumnIndex(name);
        if (i == -1)
            return null;
        if (c.isNull(i))
            return null;
        return c.getLong(i);
    }

    public static String strip(String s, String cc) {
        for (char c : cc.toCharArray()) {
            while (s.startsWith("" + c)) {
                s = s.substring(1, s.length());
            }
            while (s.endsWith("" + c)) {
                s = s.substring(1, s.length());
            }
        }
        return s;
    }

    public static class CrawlSort implements Comparable<State> {

        @Override
        public int compareTo(@NonNull State o) {
            return 0;
        }
    }

    public static class CrawlEntry implements BaseColumns {
        public static final String TABLE_NAME = "crawl";
        public static final String COLUMN_ENGINE = "engine";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_IMAGE = "image";
        public static final String COLUMN_DETAILS = "details";
        public static final String COLUMN_MAGNET = "magnet";
        public static final String COLUMN_TORRENT = "torrent";
        public static final String COLUMN_DATE = "date";
        public static final String COLUMN_LAST = "last"; // last update timestamp
        public static final String COLUMN_DOWNLOADS = "downloads"; // downloads from last update
        public static final String COLUMN_DOWNLOADS_TOTAL = "downloads_total";
        public static final String COLUMN_SEED = "seed"; // seed from last update
        public static final String COLUMN_LEECH = "leech"; // leech from last update
    }

    public static class IndexEntry {
        public static final String TABLE_NAME = "FTS";
        public static final String COL_ENGINE = "ENGINE";
        public static final String COL_WORD = "WORD";
        public static final String COL_CRAWL = "CRAWL_ID";
    }

    public static class CrawlDbHelper extends SQLiteOpenHelper {
        public static final int DATABASE_VERSION = 2;
        public static final String DATABASE_NAME = "crawl.db";

        public static final String FTS_TABLE_CREATE =
                "CREATE VIRTUAL TABLE " + IndexEntry.TABLE_NAME +
                        " USING fts3 (" +
                        IndexEntry.COL_ENGINE + ", " +
                        IndexEntry.COL_WORD + ", " +
                        IndexEntry.COL_CRAWL + ")";

        private static final String TEXT_TYPE = " TEXT";
        public static String LONG_TYPE = " INTEGER";
        private static final String COMMA_SEP = ",";
        private static final String SQL_CREATE_ENTRIES =
                "CREATE TABLE " + CrawlEntry.TABLE_NAME + " (" +
                        CrawlEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT" + COMMA_SEP +
                        CrawlEntry.COLUMN_ENGINE + TEXT_TYPE + COMMA_SEP +
                        CrawlEntry.COLUMN_TITLE + TEXT_TYPE + COMMA_SEP +
                        CrawlEntry.COLUMN_IMAGE + TEXT_TYPE + COMMA_SEP +
                        CrawlEntry.COLUMN_DETAILS + TEXT_TYPE + COMMA_SEP +
                        CrawlEntry.COLUMN_MAGNET + TEXT_TYPE + COMMA_SEP +
                        CrawlEntry.COLUMN_TORRENT + TEXT_TYPE + COMMA_SEP +
                        CrawlEntry.COLUMN_DATE + TEXT_TYPE + COMMA_SEP +
                        CrawlEntry.COLUMN_LAST + LONG_TYPE + COMMA_SEP +
                        CrawlEntry.COLUMN_SEED + LONG_TYPE + COMMA_SEP +
                        CrawlEntry.COLUMN_LEECH + LONG_TYPE + COMMA_SEP +
                        CrawlEntry.COLUMN_DOWNLOADS + LONG_TYPE + COMMA_SEP +
                        CrawlEntry.COLUMN_DOWNLOADS_TOTAL + LONG_TYPE +
                        " )";

        private static final String SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS " + CrawlEntry.TABLE_NAME;

        public CrawlDbHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SQL_CREATE_ENTRIES);
            db.execSQL(FTS_TABLE_CREATE);
        }

        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            int cur = oldVersion;
            if (cur == 1) { // 1 --> 2
                db.execSQL("ALTER TABLE " + CrawlEntry.TABLE_NAME + " ADD COLUMN " + CrawlEntry.COLUMN_LAST + " " + LONG_TYPE + " ;");
                db.execSQL("ALTER TABLE " + CrawlEntry.TABLE_NAME + " ADD COLUMN " + CrawlEntry.COLUMN_SEED + " " + LONG_TYPE + " ;");
                db.execSQL("ALTER TABLE " + CrawlEntry.TABLE_NAME + " ADD COLUMN " + CrawlEntry.COLUMN_LEECH + " " + LONG_TYPE + " ;");
                db.execSQL("ALTER TABLE " + CrawlEntry.TABLE_NAME + " ADD COLUMN " + CrawlEntry.COLUMN_DOWNLOADS + " " + LONG_TYPE + " ;");
                db.execSQL("ALTER TABLE " + CrawlEntry.TABLE_NAME + " ADD COLUMN " + CrawlEntry.COLUMN_DOWNLOADS_TOTAL + " " + LONG_TYPE + " ;");
                cur = 2;
            }
//            db.execSQL(SQL_DELETE_ENTRIES);
//            db.execSQL("DROP TABLE IF EXISTS " + IndexEntry.TABLE_NAME);
//            onCreate(db);
        }

        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onUpgrade(db, oldVersion, newVersion);
        }

        void dropDup(long id) {
            SQLiteDatabase db = getWritableDatabase();
            db.delete(CrawlEntry.TABLE_NAME, CrawlEntry._ID + " == ?", new String[]{"" + id});
        }

        void dropCrawl(long id) {
            SQLiteDatabase db = getWritableDatabase();
            db.delete(CrawlEntry.TABLE_NAME, CrawlEntry._ID + " == ?", new String[]{"" + id});
            db.delete(IndexEntry.TABLE_NAME, IndexEntry.COL_CRAWL + " == ?", new String[]{"" + id});
        }

        public long addCrawl(String engine, SearchItem item) {
            long now = System.currentTimeMillis();
            SQLiteDatabase db = getWritableDatabase();
            ContentValues initialValues = new ContentValues();
            initialValues.put(CrawlEntry.COLUMN_ENGINE, engine);
            initialValues.put(CrawlEntry.COLUMN_TITLE, item.title);
            initialValues.put(CrawlEntry.COLUMN_IMAGE, item.image);
            initialValues.put(CrawlEntry.COLUMN_DETAILS, item.details);
            initialValues.put(CrawlEntry.COLUMN_MAGNET, item.magnet);
            initialValues.put(CrawlEntry.COLUMN_TORRENT, item.torrent);
            initialValues.put(CrawlEntry.COLUMN_DATE, item.date);
            initialValues.put(CrawlEntry.COLUMN_LAST, now);
            initialValues.put(CrawlEntry.COLUMN_SEED, item.seed);
            initialValues.put(CrawlEntry.COLUMN_LEECH, item.leech);
            initialValues.put(CrawlEntry.COLUMN_DOWNLOADS, item.downloads);
            initialValues.put(CrawlEntry.COLUMN_DOWNLOADS_TOTAL, item.downloads_total);
            long id = db.insert(CrawlEntry.TABLE_NAME, null, initialValues);
            item.id = id;
            return id;
        }

        public long updateCrawl(long id, SearchItem item) {
            long now = System.currentTimeMillis();
            SQLiteDatabase db = getWritableDatabase();
            ContentValues initialValues = new ContentValues();
            if (item.image != null)
                initialValues.put(CrawlEntry.COLUMN_IMAGE, item.image);
            if (item.details != null)
                initialValues.put(CrawlEntry.COLUMN_DETAILS, item.details);
            if (item.magnet != null)
                initialValues.put(CrawlEntry.COLUMN_MAGNET, item.magnet);
            if (item.torrent != null)
                initialValues.put(CrawlEntry.COLUMN_TORRENT, item.torrent);
            if (item.date != null)
                initialValues.put(CrawlEntry.COLUMN_DATE, item.date);
            initialValues.put(CrawlEntry.COLUMN_LAST, now);
            if (item.seed != null)
                initialValues.put(CrawlEntry.COLUMN_SEED, item.seed);
            if (item.leech != null)
                initialValues.put(CrawlEntry.COLUMN_LEECH, item.leech);
            if (item.downloads != null)
                initialValues.put(CrawlEntry.COLUMN_DOWNLOADS, item.downloads);
            if (item.downloads_total != null)
                initialValues.put(CrawlEntry.COLUMN_DOWNLOADS_TOTAL, item.downloads_total);
            String where = CrawlEntry._ID + " = ?";
            String[] args = new String[]{"" + id};
            return db.update(CrawlEntry.TABLE_NAME, initialValues, where, args);
        }

        public SearchItem getSearchItem(Map<String, String> ss, Cursor c) {
            SearchItem s = new SearchItem();
            s.search = ss;
            s.id = getLong(c, CrawlEntry._ID);
            s.title = getString(c, CrawlEntry.COLUMN_TITLE);
            s.image = getString(c, CrawlEntry.COLUMN_IMAGE);
            s.details = getString(c, CrawlEntry.COLUMN_DETAILS);
            s.magnet = getString(c, CrawlEntry.COLUMN_MAGNET);
            s.torrent = getString(c, CrawlEntry.COLUMN_TORRENT);
            s.date = getString(c, CrawlEntry.COLUMN_DATE);
            s.last = getLong(c, CrawlEntry.COLUMN_LAST);
            s.seed = getLong(c, CrawlEntry.COLUMN_SEED);
            s.leech = getLong(c, CrawlEntry.COLUMN_LEECH);
            s.downloads = getLong(c, CrawlEntry.COLUMN_DOWNLOADS);
            s.downloads_total = getLong(c, CrawlEntry.COLUMN_DOWNLOADS_TOTAL);
            return s;
        }

        public Cursor search(String engine, String order, int offset, int limit) {
            Cursor c = query(CrawlEntry.COLUMN_ENGINE + " = ?", new String[]{engine}, null, order, offset + ", " + limit);
            return c;
        }

        public Cursor exist(String engine, SearchItem item) {
            String selection;
            String[] selectionArgs;
            if (item.details == null || item.details.isEmpty()) {
                selection = CrawlEntry.COLUMN_ENGINE + " = ? AND " + CrawlEntry.COLUMN_TITLE + " = ?";
                selectionArgs = new String[]{engine, item.title};
            } else {
                selection = CrawlEntry.COLUMN_ENGINE + " = ? AND " + CrawlEntry.COLUMN_DETAILS + " = ?";
                selectionArgs = new String[]{engine, item.details};
            }
            return query(selection, selectionArgs, null, null, null);
        }

        public long count(String engine) {
            return DatabaseUtils.queryNumEntries(getReadableDatabase(), CrawlEntry.TABLE_NAME, CrawlEntry.COLUMN_ENGINE + " = ?", new String[]{engine});
        }

        private Cursor query(String selection, String[] selectionArgs, String[] columns, String order, String limit) {
            SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
            builder.setTables(CrawlEntry.TABLE_NAME);

            Cursor cursor = builder.query(getReadableDatabase(),
                    columns, selection, selectionArgs, null, null, order, limit);

            if (cursor == null) {
                return null;
            } else if (!cursor.moveToFirst()) {
                cursor.close();
                return null;
            }
            return cursor;
        }


        public long addWord(String engine, String words, long definition) {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues initialValues = new ContentValues();
            initialValues.put(IndexEntry.COL_ENGINE, engine);
            initialValues.put(IndexEntry.COL_WORD, words);
            initialValues.put(IndexEntry.COL_CRAWL, definition);
            return db.insert(IndexEntry.TABLE_NAME, null, initialValues);
        }

        public Cursor getWordMatches(String engine, String query, String[] columns, String order, int offset, int limit) {
            String[] qq = query.split("\\s+");
            String s = "";

            for (String q : qq) {
                s += q + "*" + " ";
            }

            String selection = IndexEntry.TABLE_NAME + "." + IndexEntry.COL_ENGINE + " = ? AND " + IndexEntry.COL_WORD + " MATCH ?";
            String[] selectionArgs = new String[]{engine, s};
            return queryIndex(selection, selectionArgs, columns, order, offset + ", " + limit);
        }

        private Cursor queryIndex(String selection, String[] selectionArgs, String[] columns, String order, String limit) {
            SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
            builder.setTables(IndexEntry.TABLE_NAME + " INNER JOIN " + CrawlEntry.TABLE_NAME + " ON " + IndexEntry.TABLE_NAME + "." + IndexEntry.COL_CRAWL + "=" + CrawlEntry.TABLE_NAME + "." + CrawlEntry._ID);

            Cursor cursor = builder.query(getReadableDatabase(),
                    columns, selection, selectionArgs, null, null, order, limit);

            if (cursor == null) {
                return null;
            } else if (!cursor.moveToFirst()) {
                cursor.close();
                return null;
            }
            return cursor;
        }
    }

    public static class State {
        public String name; // json 'crawls' name
        public int page;
        public int end; // have end reached? is it full loaded? try end several times
        public int endPage; // page where end happens. to clear 'end'.
        public long last; // last time page were loaded
        public String url;
        public Map<String, String> s; // json 'crawl'
        public String next;

        public JSONObject save() {
            try {
                JSONObject o = new JSONObject();
                o.put("name", name);
                o.put("page", page);
                o.put("end", end);
                o.put("endpage", endPage);
                o.put("last", last);
                o.put("url", url);
                o.put("next", next);
                return o;
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        public void load(String state) {
            try {
                JSONObject o = new JSONObject(state);
                name = o.getString("name");
                page = o.getInt("page");
                end = o.optInt("end", 0);
                endPage = o.optInt("endpage", 0);
                last = o.getLong("last");
                url = o.getString("url");
                next = o.optString("next", null);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Crawl(MainActivity m) {
        super(m);
        db = new CrawlDbHelper(m);
        crawlHttp = new HttpProxyClient() {
            @Override
            protected CloseableHttpClient build(HttpClientBuilder builder) {
                builder.setUserAgent(Search.USER_AGENT); // search requests shold go from desktop browser
                return super.build(builder);
            }
        };
        crawlHttp.update(context);

    }

    public void install(final HeaderGridView list) {
        super.install(list);
        LinearLayout toolbar = (LinearLayout) header.findViewById(R.id.search_header_toolbar_parent);
        progressFrame = new FrameLayout(context);
        progressRefresh = new ImageView(context);
        progressRefresh.setImageResource(R.drawable.ic_refresh_black_24dp);
        int color2 = ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent);
        PorterDuffColorFilter filter2 = new PorterDuffColorFilter(color2, PorterDuff.Mode.SRC_IN);
        progressRefresh.setColorFilter(filter2);
        progressFrame.addView(progressRefresh);
        progressBar = new ProgressBar(context);
        progressBar.setIndeterminate(true);
        progressFrame.addView(progressBar);
        progressStatus = new TextView(context);
        progressStatus.setTextSize(6);
        FrameLayout.LayoutParams lp1 = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp1.gravity = Gravity.CENTER;
        progressStatus.setGravity(Gravity.CENTER);
        progressFrame.addView(progressStatus, lp1);
        int p24 = ThemeUtils.dp2px(context, 24);
        AbsListView.LayoutParams lp = new AbsListView.LayoutParams(p24, p24);
        toolbar.addView(progressFrame, 0, lp);
        progressBar.setVisibility(View.GONE);
        progressRefresh.setVisibility(View.VISIBLE);
        progressRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (crawlThread == null) {
                    for (String key : crawls.keySet()) {
                        State next = crawls.get(key);
                        next.last = 0;
                    }
                    crawlsTopIndex = 0;
                    crawlNextThread();
                }
            }
        });

        Map<String, String> crawls = engine.getMap("crawls");
        for (String key : crawls.keySet()) {
            State s = this.crawls.get(key);
            if (s == null)
                s = new State();
            s.name = key;
            s.url = crawls.get(key);
            s.s = engine.getMap("crawl");
            this.crawls.put(key, s);
        }
        for (String key : new TreeSet<>(this.crawls.keySet())) { // drop old engines
            if (!crawls.containsKey(key))
                this.crawls.remove(key);
        }
        // load 'crawl_top's
        for (String key : engine.keySet()) {
            if (key.startsWith("crawl_")) {
                Map<String, String> crawl = engine.getMap(key);
                String get = crawl.get("get");
                Map<String, String> tops = engine.getMap(get);
                for (String k : tops.keySet()) {
                    String url = tops.get(k);
                    State s = new State();
                    s.url = url;
                    s.s = crawl;
                    this.crawlsTop.add(s);
                }
            }
        }

        progressUpdate();

        crawlNextThread();
    }

    @Override
    public void remove(HeaderGridView list) {
        super.remove(list);
        crawlStop();
    }

    State getNextState() {
        State s = null;
        long last = Long.MAX_VALUE;
        long now = System.currentTimeMillis();
        for (String key : crawls.keySet()) {
            State next = crawls.get(key);
            if (next.end > CRAWL_END) {
                if (next.last + REFRESH_CRAWL > now) {
                    continue;
                } else {
                    if (next.next == null) // if we reach the end, and here is no crawl in progress reset page counter
                        next.page = 0;
                }
            }
            if (next.last < last) {
                s = next;
                last = next.last;
            }
        }
        return s;
    }

    State getLast() {
        State s = null;
        long last = Long.MIN_VALUE;
        for (String key : crawls.keySet()) {
            State next = crawls.get(key);
            if (next.last > last) {
                s = next;
                last = next.last;
            }
        }
        return s;
    }

    void crawlNextThread() {
        final State s = getNextState();

        if (crawlThread != null) {
            crawlStop();
        }

        if (!Storage.connectionsAllowed(context) || error != null || message.size() >= 3) {
            crawlDelay();
            return;
        }

        if (s != null) {
            crawlsTopIndex = 0;
            crawlThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        crawlLoad(s);
                    } catch (RuntimeException e) {
                        post(e);
                    }
                    if (Thread.currentThread().isInterrupted())
                        return;
                    crawlDelay();
                }
            }, "Crawl Thread");
        } else {
            if (crawlsTopIndex >= crawlsTop.size()) {
                return;
            }
            final State t = crawlsTop.get(crawlsTopIndex);
            crawlThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        crawlTopsLoad(t);
                    } catch (RuntimeException e) {
                        post(e);
                    }
                    if (Thread.currentThread().isInterrupted())
                        return;
                    crawlsTopIndex++;
                    crawlDelay();
                }
            }, "Crawl Update Thread");
        }

        progressBar.setVisibility(View.VISIBLE);
        progressRefresh.setVisibility(View.GONE);
        progressUpdate();

        crawlThread.start();
    }

    @Override
    public String save() {
        try {
            JSONObject json = new JSONObject();
            String state = super.save();
            json.put("state", state);
            Map<String, String> map = new TreeMap<>();
            for (String k : crawls.keySet()) {
                State s = crawls.get(k);
                map.put(k, s.save().toString());
            }
            json.put("crawls", SearchEngine.toJSON(map));
            return json.toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void load(String state) {
        try {
            JSONObject json = new JSONObject(state);
            JSONObject cc = json.optJSONObject("crawls");
            if (cc != null) {
                Map<String, Object> c = SearchEngine.toMap(cc);
                for (String k : c.keySet()) {
                    String ss = (String) c.get(k);
                    State s = crawls.get(k);
                    if (s == null)
                        s = new State();
                    s.load(ss);
                    crawls.put(k, s);
                }
            }
            String s = json.getString("state");
            super.load(s);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    void crawlLoad(State state) {
        String url = state.next;
        if (url == null || url.isEmpty())
            url = state.url;
        HttpClient.DownloadResponse html = null;
        if (state.s.containsKey("get")) {
            html = crawlHttp.getResponse(null, url);
            html.download();
        }
        if (html != null) {
            crawlHtml(state, url, html);
            return;
        }
    }

    void crawlHtml(State state, String url, final HttpClient.DownloadResponse html) {
        crawlList(state, url, html.getHtml());
    }

    void crawlList(final State state, String url, String html) {
        String select = state.s.get("list");

        Document doc = Jsoup.parse(html);
        Elements list = doc.select(select);
        int endDups = 0;
        for (int i = 0; i < list.size(); i++) {
            long id;
            SearchItem item = new SearchItem(state.s, url, list.get(i).outerHtml());
            Cursor c = db.exist(engine.getName(), item);
            if (c != null) { // exists?
                id = getLong(c, CrawlEntry._ID);
                while (c.moveToNext()) { // remove dups
                    long dup = getLong(c, CrawlEntry._ID);
                    db.dropDup(dup);
                }
                c.close();
                if (state.end > CRAWL_END) { // updating?
                    endDups++; // inc dup index
                }
                db.updateCrawl(id, item);
            } else {
                id = db.addCrawl(engine.getName(), item);
                String s = item.title.toLowerCase(EN);
                db.addWord(engine.getName(), s, id);
            }
            if (endDups >= list.size() || endDups > CRAWL_DUPS) { // all items are dups, stop. or dups more 20 for single refresh page
                state.next = null;
                state.last = System.currentTimeMillis();
                return;
            }
            Log.d(TAG, "item " + item.title);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    progressUpdate();
                }
            });
            try { // make thread low priority
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        String next = matcherUrl(url, html, state.s.get("next"), null);
        if (next == null) {
            state.end++;
            state.endPage = state.page;
        } else {
            state.next = next;
            state.page++;
            if (state.page > state.endPage) {
                state.end = 0;
                state.endPage = 0;
            }
        }

        state.last = System.currentTimeMillis();
    }

    void crawlTopsLoad(State s) {
        HttpClient.DownloadResponse html = null;
        if (s.s.containsKey("get")) {
            html = crawlHttp.getResponse(null, s.url);
            html.download();
        }
        if (html != null) {
            crawlTopsHtml(s.s, s.url, html);
            return;
        }
    }

    void crawlTopsHtml(Map<String, String> s, String url, final HttpClient.DownloadResponse html) {
        crawlTops(s, url, html.getHtml());
    }

    void crawlTops(final Map<String, String> s, String url, String html) {
        String select = s.get("list");

        String max = s.get("max");
        Integer m = null;
        if (max != null && !max.isEmpty())
            m = Integer.valueOf(max);

        Document doc = Jsoup.parse(html);
        Elements list = doc.select(select);
        for (int i = 0; i < list.size(); i++) {
            SearchItem item = new SearchItem(s, url, list.get(i).outerHtml());
            Cursor c = db.exist(engine.getName(), item);
            if (c != null) {
                long id = getLong(c, CrawlEntry._ID);
                while (c.moveToNext()) { // remove dups
                    long dup = getLong(c, CrawlEntry._ID);
                    db.dropDup(dup);
                }
                c.close();
                db.updateCrawl(id, item);
            }
            if (m != null && i >= m)
                break;
            Log.d(TAG, "update " + item.title);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    progressUpdate();
                }
            });
            try { // make thread low priority
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public void crawlStop() {
        if (crawlThread != null) {
            crawlThread.interrupt();
            crawlThread = null;
        }
        requestCancel(crawlHttp.getRequest());
        if (progressFrame != null) { // stop after remove();
            progressBar.setVisibility(View.GONE);
            progressRefresh.setVisibility(View.VISIBLE);
        }
        handler.removeCallbacks(crawlNext);
    }

    @Override
    public void close() {
        super.close();
        crawlStop();
        db.close();
    }

    @Override
    public void search(final Map<String, String> s, final String search, final Runnable done) {
        String select = null;
        String l = s.get("list");
        if (l != null) {
            select = l;
            gridView = null;
        }
        String g = s.get("grid");
        if (g != null) {
            select = g;
            LayoutInflater inflater = LayoutInflater.from(getContext());
            gridView = inflater.inflate(R.layout.search_item_grid, grid, false);
        }

        if (select.equals("crawl")) {
            final String url = s.get("get");
            handler.post(new Runnable() {
                @Override
                public void run() {
                    gridUpdate();
                    searchCrawl(s, search.toLowerCase(EN), url, done);
                }
            });
        } else {
            super.search(s, search, done);
        }
    }

    @Override
    public void search(final Map<String, String> s, final String type, final String url, final String search, final Runnable done) {
        String select = null;
        String l = s.get("list");
        if (l != null) {
            select = l;
            gridView = null;
        }
        String g = s.get("grid");
        if (g != null) {
            select = g;
            LayoutInflater inflater = LayoutInflater.from(getContext());
            gridView = inflater.inflate(R.layout.search_item_grid, grid, false);
        }

        if (select.equals("crawl")) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    gridUpdate();
                    searchCrawl(s, search, url, done);
                }
            });
        } else {
            super.search(s, type, url, search, done);
        }
        return;
    }

    void searchCrawl(Map<String, String> s, String search, String order, final Runnable done) {
        String next = null;
        String nextText = null;

        int count = 0;

        if (search != null) {
            search = search.toLowerCase(EN);
            Cursor c = db.getWordMatches(engine.getName(), search, null, order, this.list.size(), CRAWL_SHOW + 1);
            while (c != null) {
                count++;
                if (count > CRAWL_SHOW) {
                    next = order;
                    nextText = search;
                    break;
                }
                SearchItem item = db.getSearchItem(s, c);
                if (item != null)
                    this.list.add(item);
                if (!c.moveToNext())
                    break;
            }
        } else {
            Cursor c = db.search(engine.getName(), order, this.list.size(), CRAWL_SHOW + 1);
            while (c != null) {
                count++;
                if (count > CRAWL_SHOW) {
                    next = order;
                    nextText = null;
                    break;
                }
                SearchItem item = db.getSearchItem(s, c);
                if (item != null)
                    this.list.add(item);
                if (!c.moveToNext())
                    break;
            }
        }

        this.next = next;
        this.nextText = nextText;
        this.nextSearch = s;

        notifyDataSetChanged();

        if (count > 0)
            hideKeyboard();

        if (done != null)
            done.run();
    }

    @Override
    public void delete() {
        super.delete();
        db.getWritableDatabase().delete(CrawlEntry.TABLE_NAME, CrawlEntry.COLUMN_ENGINE + " == ?", new String[]{engine.getName()});
        db.getWritableDatabase().delete(IndexEntry.TABLE_NAME, IndexEntry.COL_ENGINE + " == ?", new String[]{engine.getName()});
    }

    void progressUpdate() {
        State s = getLast();
        progressStatus.setText("" + s.page + "\n" + db.count(engine.getName()));
    }

    void crawlDelay() {
        handler.removeCallbacks(crawlNext);
        handler.postDelayed(crawlNext, CRAWL_DELAY);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);
        crawlHttp.update(context);
    }

    @Override
    void detailsList(SearchItem item, Map<String, String> s, String url, String html) {
        super.detailsList(item, s, url, html);
        db.updateCrawl(item.id, item);
    }
}
