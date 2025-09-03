package com.hippolyte.pointageapp.data;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static final String FILE = "pointage_prefs";
    private static final String KEY_USER = "user_name";
    private static final String KEY_START_AT = "startAt";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String getUser(Context c) {
        return sp(c).getString(KEY_USER, "");
    }

    public static void setUser(Context c, String name) {
        sp(c).edit().putString(KEY_USER, name).apply();
    }

    public static long getStartAt(Context c) {
        return sp(c).getLong(KEY_START_AT, 0L);
    }

    public static void setStartAt(Context c, long when) {
        sp(c).edit().putLong(KEY_START_AT, when).apply();
    }

    public static void clearStart(Context c) {
        sp(c).edit().remove(KEY_START_AT).apply();
    }
}
