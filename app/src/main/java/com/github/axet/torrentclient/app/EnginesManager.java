package com.github.axet.torrentclient.app;

import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EnginesManager {
    public static final String TAG = EnginesManager.class.getSimpleName();

    Context context;
    MainActivity main;
    ArrayList<Search> list = new ArrayList<>();

    public EnginesManager(MainActivity main) {
        this.context = main;
        this.main = main;

        load();
    }

    public int getCount() {
        return list.size();
    }

    public Search get(int i) {
        return list.get(i);
    }

    public void add(File f) {
        try {
            String json = IOUtils.toString(new FileInputStream(f), Search.UTF8);
            add(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void add(String json) {
        SearchEngine engine = new SearchEngine();
        engine.loadJson(json);
        Search search = new Search(main);
        search.setEngine(engine);
        list.add(search);
    }

    public void remove(Search s) {
        list.remove(s);
    }

    public void load() {
        Log.d(TAG, "load()");

        list.clear();

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        int count = shared.getInt("engine_count", 0);
        for (int i = 0; i < count; i++) {
            String data = shared.getString("engine_" + i + "_data", "");
            String state = shared.getString("engine_" + i + "_state", "");
            SearchEngine engine = new SearchEngine();
            engine.loadJson(data);
            Search search = new Search(main);
            search.setEngine(engine);
            search.load(state);
            list.add(search);
        }

        if (count == 0) {
            try {
                String json = IOUtils.toString(context.getResources().openRawResource(R.raw.google), Search.UTF8);
                add(json);
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
            Search search = list.get(i);
            SearchEngine engine = search.getEngine();
            edit.putString("engine_" + i + "_name", engine.getName());
            edit.putString("engine_" + i + "_data", engine.save());
            edit.putString("engine_" + i + "_state", search.save());
        }
        edit.commit();
    }
}
