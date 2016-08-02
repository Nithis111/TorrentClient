package com.github.axet.torrentclient.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.navigators.Search;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.utils.URLEncodedUtils;
import cz.msebera.android.httpclient.impl.cookie.BasicClientCookie;
import cz.msebera.android.httpclient.message.BasicNameValuePair;

public class EnginesManager {
    public static final String TAG = EnginesManager.class.getSimpleName();

    Context context;
    MainActivity main;
    ArrayList<Item> list = new ArrayList<>();
    Thread thread;
    Handler handler = new Handler();

    public static class Item {
        Search search;
        // download url
        String url;
        // update time
        long time;

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

        load();
    }

    public int getCount() {
        return list.size();
    }

    public Search get(int i) {
        return list.get(i).search;
    }

    public boolean addManget(String magnet) {
        Uri uri = Uri.parse(magnet);
        List<NameValuePair> list = URLEncodedUtils.parse(uri.getQuery(), Charset.forName("UTF-8"));
        for (int i = 0; i < list.size(); i++) {
            NameValuePair nn = list.get(i);
            if (nn.getName().equals("as")) {
                final String url = nn.getValue();
                thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final SearchEngine engine = new SearchEngine();
                            engine.loadUrl(url);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Search search = add(url, engine);
                                    save();
                                    main.updateManager();
                                    main.openDrawer(search);
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
        }
        return false;
    }

    public void add(File f) {
        try {
            String json = IOUtils.toString(new FileInputStream(f), MainApplication.UTF8);
            add(f.toURI().toString(), json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void add(String url, String json) {
        SearchEngine engine = new SearchEngine();
        engine.loadJson(json);
        add(url, engine);
    }

    Search add(String url, SearchEngine engine) {
        Search search = new Search(main);
        search.setEngine(engine);

        Item item = new Item();
        item.search = search;
        item.url = url;
        item.time = System.currentTimeMillis();

        for (int i = 0; i < list.size(); i++) {
            Item m = list.get(i);
            if (m.url.equals(url)) {
                list.set(i, item);
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
            String data = shared.getString("engine_" + i + "_data", "");
            String state = shared.getString("engine_" + i + "_state", "");
            String url = shared.getString("engine_" + i + "_url", "");
            long time = shared.getLong("engine_" + i + "_time", 0);

            SearchEngine engine = new SearchEngine();
            engine.loadJson(data);

            Search search = new Search(main);
            search.setEngine(engine);
            search.load(state);

            list.add(new Item(search, url, time));
        }

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
            Item item = list.get(i);
            Search search = item.search;
            SearchEngine engine = search.getEngine();
            edit.putString("engine_" + i + "_data", engine.save());
            edit.putString("engine_" + i + "_state", search.save());
            edit.putString("engine_" + i + "_url", item.url);
            edit.putLong("engine_" + i + "_time", item.time);
        }
        edit.commit();
    }
}
