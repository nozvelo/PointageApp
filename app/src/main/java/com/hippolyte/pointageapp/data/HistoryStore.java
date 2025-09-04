package com.hippolyte.pointageapp.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public final class HistoryStore {
    private HistoryStore() {}

    public static synchronized void add(Context ctx, TourEntry entry) {
        try {
            JSONArray arr = new JSONArray(Prefs.getHistoryJson(ctx));
            arr.put(entry.toJson());
            Prefs.setHistoryJson(ctx, arr.toString());
        } catch (JSONException e) {
            // En cas de corruption, on réinitialise proprement avec cet unique élément
            try {
                JSONArray arr = new JSONArray();
                arr.put(entry.toJson());
                Prefs.setHistoryJson(ctx, arr.toString());
            } catch (JSONException ignore) {}
        }
    }

    public static synchronized ArrayList<TourEntry> list(Context ctx) {
        ArrayList<TourEntry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(Prefs.getHistoryJson(ctx));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(TourEntry.fromJson(o));
            }
        } catch (JSONException e) {
            // ignore -> retourne liste vide
        }
        return out;
    }

    public static synchronized void clearAll(Context ctx) {
        Prefs.setHistoryJson(ctx, "[]");
    }
}
