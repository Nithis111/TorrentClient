package com.github.axet.torrentclient.app;

import android.content.Context;
import android.net.Uri;

import com.github.axet.androidlibrary.net.HttpClient;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SearchEngine {
    public static final String TAG = SearchEngine.class.getSimpleName();

    Map<String, Object> map = new HashMap<>();

    // http://stackoverflow.com/questions/21720759/convert-a-json-string-to-a-hashmap

    public static Map<String, Object> jsonToMap(JSONObject json) throws JSONException {
        Map<String, Object> retMap = new HashMap<String, Object>();
        if (json != JSONObject.NULL) {
            retMap = toMap(json);
        }
        return retMap;
    }

    public static Map<String, Object> toMap(JSONObject object) throws JSONException {
        Map<String, Object> map = new HashMap<String, Object>();
        Iterator<String> keysItr = object.keys();
        while (keysItr.hasNext()) {
            String key = keysItr.next();
            Object value = object.get(key);

            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            map.put(key, value);
        }
        return map;
    }

    public static List<Object> toList(JSONArray array) throws JSONException {
        List<Object> list = new ArrayList<Object>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            list.add(value);
        }
        return list;
    }

    public void loadJson(String json) {
        try {
            JSONObject j = new JSONObject(json);
            map = jsonToMap(j);
        } catch (JSONException e) {
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
        JSONObject o = new JSONObject(map);
        return o.toString();
    }

    public String getName() {
        return getString("name");
    }

    public Integer getVersion() {
        return (Integer) map.get("version");
    }
}
