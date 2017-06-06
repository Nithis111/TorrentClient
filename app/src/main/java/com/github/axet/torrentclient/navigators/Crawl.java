package com.github.axet.torrentclient.navigators;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.provider.BaseColumns;
import android.support.v4.text.TextUtilsCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.axet.androidlibrary.net.HttpClient;
import com.github.axet.androidlibrary.widgets.HeaderGridView;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.app.SearchEngine;

import org.apache.commons.codec.binary.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.helper.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Crawl extends Search {
    public static final String TAG = Crawl.class.getSimpleName();

    public static Locale EN = new Locale("en");

    public static int REFRESH_CRAWL = 24 * 60 * 60 * 1000; // 1 day

    private static final String TEXT_TYPE = " TEXT";
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
                    CrawlEntry.COLUMN_DATE + TEXT_TYPE +
                    " )";

    private static final String SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS " + CrawlEntry.TABLE_NAME;

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
        return c.getString(i);
    }

    public static Long getLong(Cursor c, String name) {
        int i = c.getColumnIndex(name);
        if (i == -1)
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

    public static class CrawlEntry implements BaseColumns {
        public static final String TABLE_NAME = "crawl";
        public static final String COLUMN_ENGINE = "engine";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_IMAGE = "image";
        public static final String COLUMN_DETAILS = "details";
        public static final String COLUMN_MAGNET = "magnet";
        public static final String COLUMN_TORRENT = "torrent";
        public static final String COLUMN_DATE = "date";
    }

    public static class CrawlDbHelper extends SQLiteOpenHelper {
        // If you change the database schema, you must increment the database version.
        public static final int DATABASE_VERSION = 1;
        public static final String DATABASE_NAME = "crawl.db";

        private SQLiteDatabase mDatabase;

        public CrawlDbHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        public void onCreate(SQLiteDatabase db) {
            this.mDatabase = db;
            db.execSQL(SQL_CREATE_ENTRIES);
        }

        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // This database is only a cache for online data, so its upgrade policy is
            // to simply to discard the data and start over
            db.execSQL(SQL_DELETE_ENTRIES);
            onCreate(db);
        }

        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onUpgrade(db, oldVersion, newVersion);
        }

        public long addCrawl(String engine, String title, String image, String details, String date) {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues initialValues = new ContentValues();
            initialValues.put(CrawlEntry.COLUMN_ENGINE, engine);
            initialValues.put(CrawlEntry.COLUMN_TITLE, title);
            initialValues.put(CrawlEntry.COLUMN_IMAGE, image);
            initialValues.put(CrawlEntry.COLUMN_DETAILS, details);
            initialValues.put(CrawlEntry.COLUMN_MAGNET, "");
            initialValues.put(CrawlEntry.COLUMN_TORRENT, "");
            initialValues.put(CrawlEntry.COLUMN_DATE, date);
            return db.insert(CrawlEntry.TABLE_NAME, null, initialValues);
        }

        public SearchItem get(long id) {
            String selection = CrawlEntry._ID + " = ?";
            String[] selectionArgs = new String[]{"" + id};
            Cursor c = query(selection, selectionArgs, null);
            if (c == null)
                return null;
            SearchItem s = new SearchItem();
            s.title = getString(c, CrawlEntry.COLUMN_TITLE);
            s.image = getString(c, CrawlEntry.COLUMN_IMAGE);
            s.details = getString(c, CrawlEntry.COLUMN_DETAILS);
            s.date = getString(c, CrawlEntry.COLUMN_DATE);
            return s;
        }

        public Cursor exist(String title) {
            String selection = CrawlEntry.COLUMN_TITLE + " = ?";
            String[] selectionArgs = new String[]{title};
            Cursor c = query(selection, selectionArgs, null);
            return c;
        }

        public long count() {
            return DatabaseUtils.queryNumEntries(getReadableDatabase(), CrawlEntry.TABLE_NAME, null, null);
        }

        private Cursor query(String selection, String[] selectionArgs, String[] columns) {
            SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
            builder.setTables(CrawlEntry.TABLE_NAME);

            Cursor cursor = builder.query(getReadableDatabase(),
                    columns, selection, selectionArgs, null, null, null);

            if (cursor == null) {
                return null;
            } else if (!cursor.moveToFirst()) {
                cursor.close();
                return null;
            }
            return cursor;
        }
    }

    public static class CrawlDbIndexHelper extends SQLiteOpenHelper { // https://developer.android.com/training/search/search.html#populate
        public static final int DATABASE_VERSION = 1;
        public static final String FTS_VIRTUAL_TABLE = "FTS";
        public static final String DATABASE_NAME = "crawl-index.db";

        public static final String COL_ENGINE = "ENGINE";
        public static final String COL_WORD = "WORD";
        public static final String COL_CRAWL = "CRAWL_ID";

        private final Context mHelperContext;
        private SQLiteDatabase mDatabase;

        public static final String FTS_TABLE_CREATE =
                "CREATE VIRTUAL TABLE " + FTS_VIRTUAL_TABLE +
                        " USING fts3 (" +
                        COL_ENGINE + ", " +
                        COL_WORD + ", " +
                        COL_CRAWL + ")";

        CrawlDbIndexHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            mHelperContext = context;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            mDatabase = db;
            mDatabase.execSQL(FTS_TABLE_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + FTS_VIRTUAL_TABLE);
            onCreate(db);
        }

        public long addWord(String engine, String word, long definition) {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues initialValues = new ContentValues();
            initialValues.put(COL_ENGINE, engine);
            initialValues.put(COL_WORD, word);
            initialValues.put(COL_CRAWL, definition);
            return db.insert(FTS_VIRTUAL_TABLE, null, initialValues);
        }

        public Cursor getWordMatches(String engine, String query, String[] columns) {
            String selection = COL_ENGINE + " = ? AND " + COL_WORD + " MATCH ?";
            String[] selectionArgs = new String[]{engine, query + "*"};
            return query(selection, selectionArgs, columns);
        }

        private Cursor query(String selection, String[] selectionArgs, String[] columns) {
            SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
            builder.setTables(FTS_VIRTUAL_TABLE);

            Cursor cursor = builder.query(getReadableDatabase(),
                    columns, selection, selectionArgs, null, null, null);

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
        public boolean end; // have end reached? is it full loaded?
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
                end = o.getBoolean("end");
                last = o.getLong("last");
                url = o.getString("url");
                next = o.optString("next", null);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    FrameLayout progressFrame;
    TextView progressStatus;
    ProgressBar progressBar;
    TreeMap<String, State> crawls = new TreeMap<>();
    Runnable crawlDelay = new Runnable() {
        @Override
        public void run() {
            handler.removeCallbacks(crawlNext);
            handler.postDelayed(crawlNext, 5 * 1000);
        }
    };
    Runnable crawlNext = new Runnable() {
        @Override
        public void run() {
            crawlNextThread();
        }
    };
    Thread thread;

    CrawlDbHelper db;
    CrawlDbIndexHelper index;

    public Crawl(MainActivity m) {
        super(m);
        db = new CrawlDbHelper(m);
        index = new CrawlDbIndexHelper(m);
    }

    public void install(final HeaderGridView list) {
        super.install(list);
        LinearLayout toolbar = (LinearLayout) header.findViewById(R.id.search_header_toolbar_parent);
        progressFrame = new FrameLayout(context);
        progressBar = new ProgressBar(context);
        progressBar.setIndeterminate(true);
        int p24 = ThemeUtils.dp2px(context, 24);
        AbsListView.LayoutParams lp = new AbsListView.LayoutParams(p24, p24);
        progressFrame.addView(progressBar, lp);
        progressStatus = new TextView(context);
        progressStatus.setTextSize(6);
        FrameLayout.LayoutParams lp1 = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp1.gravity = Gravity.CENTER;
        progressFrame.addView(progressStatus, lp1);
        toolbar.addView(progressFrame, 0, lp);
        progressFrame.setVisibility(View.GONE);

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

        crawlNextThread();
    }

    @Override
    public void remove(HeaderGridView list) {
        super.remove(list);
        stop();
    }

    void crawlNextThread() {
        long last = Long.MAX_VALUE;
        State s = null;
        long now = System.currentTimeMillis();
        for (String key : crawls.keySet()) {
            State next = crawls.get(key);
            if (next.end) {
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
        if (s == null)
            return;

        if (thread != null) {
            stop();
        }

        progressFrame.setVisibility(View.VISIBLE);
        progressStatus.setText("" + s.page);

        final State ss = s;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    crawlLoad(ss, crawlDelay);
                } catch (RuntimeException e) {
                    post(e);
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        progressStatus.setText("" + ss.page);
                    }
                });
            }
        });
        thread.start();
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

    void crawlLoad(State state, Runnable done) {
        String url = state.next;
        if (url == null || url.isEmpty())
            url = state.url;
        HttpClient.DownloadResponse html = null;
        if (state.s.containsKey("get")) {
            html = http.getResponse(null, url);
            html.download();
        }
        if (html != null) {
            crawlHtml(state, url, html, done);
            return;
        }
    }

    void crawlHtml(State state, String url, final HttpClient.DownloadResponse html, final Runnable done) {
        crawlList(state, url, html.getHtml());
        if (done != null)
            handler.post(done);
    }

    void crawlList(final State state, String url, String html) {
        String select = state.s.get("list");

        Document doc = Jsoup.parse(html);
        Elements list = doc.select(select);
        for (int i = 0; i < list.size(); i++) {
            SearchItem item = searchItem(state.s, url, list.get(i).outerHtml());
            Cursor c = db.exist(item.title);
            if (state.end) {
                if (c != null) {
                    state.next = null;
                    c.close();
                    return;
                }
            } else {
                if (c != null) {
                    dropCrawl(c.getLong(0));
                    c.close();
                }
            }
            long id = db.addCrawl(engine.getName(), item.title, item.image, item.details, item.date);
            String[] ss = item.title.split("\\s+");
            for (String s : ss) {
                s = s.trim();
                s = strip(s, "-,._()");
                s = s.toLowerCase(EN);
                if (isString(s))
                    index.addWord(engine.getName(), s, id);
            }
            Log.d(TAG, "item " + item.title);
        }

        String next = matcher(url, html, state.s.get("next"));
        if (next == null) {
            state.end = true;
        }

        state.last = System.currentTimeMillis();
        state.page++;
        state.next = next;
    }

    public void stop() {
        if (progressFrame != null)
            progressFrame.setVisibility(View.GONE);
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join();
                thread = null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        handler.removeCallbacks(crawlDelay);
        handler.removeCallbacks(crawlNext);
    }

    @Override
    public void close() {
        super.close();
        stop();
        db.close();
        index.close();
    }

    @Override
    public void search(final Map<String, String> s, final String search, final Runnable done) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                searchUI(s, search.toLowerCase(EN), done);
            }
        });
    }

    void searchUI(Map<String, String> s, String search, final Runnable done) {
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
        gridUpdate();

        String[] qq = search.split("\\s+");

        TreeSet<Long> prev = null;
        for (String q : qq) {
            TreeSet<Long> ids = new TreeSet<>();
            Cursor c = index.getWordMatches(engine.getName(), q, null);
            if (c != null) {
                while (c.moveToNext()) {
                    long id = getLong(c, CrawlDbIndexHelper.COL_CRAWL);
                    if (prev != null) {
                        if (prev.contains(id)) {
                            ids.add(id);
                        }
                    } else {
                        ids.add(id);
                    }
                }
                c.close();
            }
            prev = ids;
        }
        for (Long id : prev) {
            SearchItem item = db.get(id);
            if (item != null)
                this.list.add(item);
        }

        next = null;
        nextType = null;
        nextSearch = engine.getMap("crawl");

        notifyDataSetChanged();

        hideKeyboard();

        if (done != null)
            done.run();
    }

    void dropCrawl(long id) {
        db.getWritableDatabase().delete(CrawlEntry.TABLE_NAME, CrawlEntry._ID + " == ?", new String[]{"" + id});
        index.getWritableDatabase().delete(CrawlDbIndexHelper.FTS_VIRTUAL_TABLE, CrawlDbIndexHelper.COL_CRAWL + " == ?", new String[]{"" + id});
    }
}
