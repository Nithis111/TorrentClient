package com.github.axet.torrentclient.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.net.UrlQuerySanitizer;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.navigators.Crawl;
import com.github.axet.torrentclient.navigators.Search;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class EnginesManager {
    public static final String TAG = EnginesManager.class.getSimpleName();

    Context context;
    MainActivity main;
    ArrayList<Item> list = new ArrayList<>();
    Thread thread;
    Handler handler = new Handler();
    long time; // last update time

    public static class Item {
        Search search;
        // download url
        String url;
        // update time
        long time;
        // update available?
        boolean update;

        public Item() {
        }

        public Item(Search search, String url, long time) {
            this.url = url;
            this.search = search;
            this.time = time;
        }
    }

    public EnginesManager(MainActivity main) {
        this.context = main;
        this.main = main;
    }

    public int getCount() {
        return list.size();
    }

    public Search get(int i) {
        return list.get(i).search;
    }

    public String getURL(int i) {
        return list.get(i).url;
    }

    public boolean getUpdate(int i) {
        return list.get(i).update;
    }

    public boolean addManget(String magnet) {
        UrlQuerySanitizer sanitizer = new UrlQuerySanitizer(magnet);
        String type = sanitizer.getValue("x.t");
        if (type == null)
            return false;
        if (type.equals("search")) {
            final String as = sanitizer.getValue("as");
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        final SearchEngine engine = new SearchEngine();
                        engine.loadUrl(context, as);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Search search = add(as, engine);
                                save();
                                main.getDrawer().updateManager();
                                main.getDrawer().openDrawer(search);
                            }
                        });
                    } catch (RuntimeException e) {
                        main.post(e);
                    }
                }
            }, "DownloadJson");
            thread.start();
            return true;
        }
        return false;
    }

    public Search add(File f) {
        try {
            String json = IOUtils.toString(new FileInputStream(f), MainApplication.UTF8);
            return add(f.toURI().toString(), json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Search add(Uri f) {
        try {
            String s = f.getScheme();
            if (s.startsWith(ContentResolver.SCHEME_FILE)) {
                return add(new File(f.getPath()));
            }
            ContentResolver res = context.getContentResolver();
            String json = IOUtils.toString(res.openInputStream(f), MainApplication.UTF8);
            return add(f.toString(), json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Search add(String url, String json) {
        SearchEngine engine = new SearchEngine();
        engine.loadJson(json);
        return add(url, engine);
    }

    public Search add(String url, SearchEngine engine) {
        return add(System.currentTimeMillis(), url, engine);
    }

    public Search add(long time, String url, SearchEngine engine) {
        Search search;
        if (engine.getMap("crawl") != null) {
            search = new Crawl(main);
        } else {
            search = new Search(main);
        }
        search.setEngine(engine);

        Item item = new Item();
        item.search = search;
        item.url = url;
        item.time = time;

        for (int i = 0; i < list.size(); i++) {
            Item m = list.get(i);
            if (m.url.equals(url)) {
                Item old = list.set(i, item);
                old.search.close();
                return search;
            }
        }
        list.add(item);
        return search;
    }

    public void remove(Search s) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).search == s) {
                list.remove(i);
                s.delete();
                s.close();
                return;
            }
        }
    }

    public Uri res(int resId) {
        Resources resources = context.getResources();
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(resources.getResourcePackageName(resId))
                .appendPath(resources.getResourceTypeName(resId))
                .appendPath(resources.getResourceEntryName(resId))
                .build();
    }

    public void load() {
        Log.d(TAG, "load()");

        list.clear();

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        int count = shared.getInt("engine_count", 0);
        for (int i = 0; i < count; i++) {
            try {
                JSONObject o = new JSONObject();
                String json = shared.getString("engine_" + i, "");
                if (json.isEmpty()) { // <=2.4.0
                    String data = shared.getString("engine_" + i + "_data", "");
                    String state = shared.getString("engine_" + i + "_state", "");
                    String url = shared.getString("engine_" + i + "_url", "");
                    long time = shared.getLong("engine_" + i + "_time", 0);
                    o.put("data", data);
                    o.put("state", state);
                    o.put("url", url);
                    o.put("time", time);
                } else {
                    o = new JSONObject(json);
                }

                SearchEngine engine = new SearchEngine();
                engine.loadJson(o.getString("data"));

                Search search = add(o.getString("url"), engine);
                search.load(o.getString("state"));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        this.time = shared.getLong("time", 0);

        if (count == 0) {
            try {
                Uri uri = res(R.raw.google);
                InputStream is = context.getContentResolver().openInputStream(uri);
                String json = IOUtils.toString(is, MainApplication.UTF8);
                add(uri.toString(), json);
            } catch (IOException e) {
                Log.d(TAG, "Unable set default engine", e);
            }
        }
    }

    public void save() {
        Log.d(TAG, "save()");
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = shared.edit();
        edit.putInt("engine_count", list.size());
        for (int i = 0; i < list.size(); i++) {
            try {
                Item item = list.get(i);
                Search search = item.search;
                SearchEngine engine = search.getEngine();
                JSONObject o = new JSONObject();
                o.put("data", engine.save());
                o.put("state", search.save());
                o.put("url", item.url);
                o.put("time", item.time);
                edit.putString("engine_" + i, o.toString());
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        edit.putLong("time", time);
        edit.commit();
    }

    public void refresh() {
        time = System.currentTimeMillis();
        for (int i = 0; i < list.size(); i++) {
            if (Thread.currentThread().isInterrupted())
                return;
            Item item = list.get(i);
            try {
                final SearchEngine engine = new SearchEngine();
                engine.loadUrl(context, item.url);
                if (item.search.getEngine().getVersion() != engine.getVersion()) {
                    item.update = true;
                }
            } catch (RuntimeException e) {
                throw new RuntimeException(item.search.getEngine().getName(), e);
            }
        }
    }

    public void update(int i) {
        Item item = list.get(i);
        final SearchEngine engine = new SearchEngine();
        engine.loadUrl(context, item.url);
        item.search.setEngine(engine);
        item.update = false;
        save();
    }

    public long getTime() {
        return time;
    }

    public boolean updates() {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).update)
                return true;
        }
        return false;
    }

    public void close() {
        for (int i = 0; i < list.size(); i++) {
            list.get(i).search.close();
        }
        list.clear();
    }
}
