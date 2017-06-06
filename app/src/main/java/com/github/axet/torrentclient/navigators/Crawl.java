package com.github.axet.torrentclient.navigators;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;
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

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.util.Map;
import java.util.TreeMap;

public class Crawl extends Search {
    public static final String TAG = Crawl.class.getSimpleName();

    private static final String TEXT_TYPE = " TEXT";
    private static final String COMMA_SEP = ",";
    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + FeedEntry.TABLE_NAME + " (" +
                    FeedEntry._ID + " INTEGER PRIMARY KEY," +
                    FeedEntry.COLUMN_ENGINE + TEXT_TYPE + COMMA_SEP +
                    FeedEntry.COLUMN_TITLE + TEXT_TYPE + COMMA_SEP +
                    FeedEntry.COLUMN_DETAILS + TEXT_TYPE + COMMA_SEP +
                    FeedEntry.COLUMN_MAGNET + TEXT_TYPE + COMMA_SEP +
                    FeedEntry.COLUMN_TORRENT + TEXT_TYPE + COMMA_SEP +
                    FeedEntry.COLUMN_DATE + TEXT_TYPE +
                    " )";

    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + FeedEntry.TABLE_NAME;

    public static class FeedEntry implements BaseColumns {
        public static final String TABLE_NAME = "crawl";
        public static final String COLUMN_ENGINE = "engine";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_DETAILS = "details";
        public static final String COLUMN_MAGNET = "magnet";
        public static final String COLUMN_TORRENT = "torrent";
        public static final String COLUMN_DATE = "date";
    }

    public static class CrawlDbHelper extends SQLiteOpenHelper {
        // If you change the database schema, you must increment the database version.
        public static final int DATABASE_VERSION = 1;
        public static final String DATABASE_NAME = "crawl.db";

        public CrawlDbHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        public void onCreate(SQLiteDatabase db) {
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
    }

    public static class State {
        public String name; // json 'crawls' name
        public int page;
        public boolean end; // have end reached? is it full loaded?
        public long last; // last time page were loaded
        public String url;
        public Map<String, String> s; // json 'crawl'
        public String next;
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

    public Crawl(MainActivity m) {
        super(m);
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
        progressFrame.addView(progressStatus);
        toolbar.addView(progressFrame, 0, lp);

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

        crawlNextThread();
    }

    @Override
    public void remove(HeaderGridView list) {
        super.remove(list);
        stop();
    }

    void crawlNextThread() {
        if (thread != null) {
            stop();
        }
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                crawlNext();
                thread = null;
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
            return json.toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void load(String state) {
        try {
            JSONObject json = new JSONObject(state);
            String s = json.getString("state");
            super.load(s);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    void crawlLoad(State state, Runnable done) {
        String url = state.next;
        if (url == null)
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

    void crawlList(State state, String url, String html) {
        String select = state.s.get("list");

        Document doc = Jsoup.parse(html);
        Elements list = doc.select(select);
        for (int i = 0; i < list.size(); i++) {
            SearchItem item = searchItem(state.s, url, list.get(i).outerHtml());
            Log.d(TAG, "item " + item.title);
        }

        String next = matcher(url, html, state.s.get("next"));

        state.page++;
        state.next = next;
    }

    public void crawlNext() {
        long last = 0;
        State s = null;
        for (String key : crawls.keySet()) {
            State next = crawls.get(key);
            if (next.last <= last) {
                s = next;
                last = next.last;
            }
        }
        crawlLoad(s, crawlDelay);
    }

    public void stop() {
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
        handler.removeCallbacks(crawlDelay);
        handler.removeCallbacks(crawlNext);
    }

    @Override
    public void close() {
        super.close();
        stop();
    }
}
