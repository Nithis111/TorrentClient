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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SearchEngine {
    public static final String TAG = SearchEngine.class.getSimpleName();

    Map<String, Object> map = new LinkedHashMap<>();

    public static Object toJSON(Object object) throws JSONException {
        if (object instanceof Map) {
            JSONObject json = new JSONObject();
            Map map = (Map) object;
            for (Object key : map.keySet()) {
                json.put(key.toString(), toJSON(map.get(key)));
            }
            return json;
        } else if (object instanceof Iterable) {
            JSONArray json = new JSONArray();
            for (Object value : ((Iterable) object)) {
                json.put(value);
            }
            return json;
        } else {
            return object;
        }
    }

    public static Map<String, Object> toMap(JSONObject object) throws JSONException {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
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
        List<Object> list = new ArrayList<>();
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
            JSONObject obj = new JSONObject(json);
            map = toMap(obj);
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
        try {
            JSONObject json = (JSONObject) toJSON(map);
            return json.toString(2);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public String getName() {
        return getString("name");
    }

    public int getVersion() {
        return ((Number) map.get("version")).intValue();
    }
}
