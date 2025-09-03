package com.hippolyte.pointageapp.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String FILE = "prefs_pointage";
    private static final String KEY_USER = "user_name";
    private static final String KEY_START_AT = "startAt";

    private Prefs() {}

    public static void setUserName(Context ctx, String name) {
        prefs(ctx).edit().putString(KEY_USER, name).apply();
    }

    public static String getUserName(Context ctx) {
        return prefs(ctx).getString(KEY_USER, "");
    }

    public static void setStartAt(Context ctx, long epochMillis) {
        prefs(ctx).edit().putLong(KEY_START_AT, epochMillis).apply();
    }

    public static long getStartAt(Context ctx) {
        return prefs(ctx).getLong(KEY_START_AT, 0L);
    }

    public static void clearStartAt(Context ctx) {
        prefs(ctx).edit().remove(KEY_START_AT).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
