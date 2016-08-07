package com.github.axet.torrentclient.app;

import android.content.Context;
import android.net.Uri;

import com.github.axet.androidlibrary.net.HttpClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

public class SearchEngine {
    public static final String TAG = SearchEngine.class.getSimpleName();

    HashMap<String, Object> map = new HashMap<>();

    public void loadJson(String json) {
        Gson gson = new Gson();
        try {
            map = gson.fromJson(json, map.getClass());
        } catch (JsonSyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadUrl(Context context, String url) {
        try {
            InputStream is;

            try {
                URL u = new URL(url);
                URLConnection conn = u.openConnection();
                conn.setConnectTimeout(HttpClient.CONNECTION_TIMEOUT);
                conn.setReadTimeout(HttpClient.CONNECTION_TIMEOUT);
                is = conn.getInputStream();
            } catch (MalformedURLException e) {
                Uri uri = Uri.parse(url);
                is = context.getContentResolver().openInputStream(uri);
            }

            String json = IOUtils.toString(is, MainApplication.UTF8);
            loadJson(json);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, String> getMap(String key) {
        return (Map<String, String>) map.get(key);
    }

    public String getString(String key) {
        return (String) map.get(key);
    }

    public String save() {
        Gson gson = new GsonBuilder().create();
        return gson.toJson(map);
    }

    public String getName() {
        return getString("name");
    }

    public Integer getVersion() {
        return ((Double) map.get("version")).intValue();
    }
}
