package com.pointage.app.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String FILE = "pointage_prefs";
    public static final String KEY_USER_NAME = "user_name";
    public static final String KEY_START_AT = "start_at";
    public static final String KEY_HISTORY_JSON = "history_json"; // JSONArray string

    private Prefs() {}

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static void setUserName(Context ctx, String name) {
        sp(ctx).edit().putString(KEY_USER_NAME, name).apply();
    }

    public static String getUserName(Context ctx) {
        return sp(ctx).getString(KEY_USER_NAME, "");
    }

    public static void setStartAt(Context ctx, long epochMillis) {
        sp(ctx).edit().putLong(KEY_START_AT, epochMillis).apply();
    }

    public static long getStartAt(Context ctx) {
        return sp(ctx).getLong(KEY_START_AT, 0L);
    }

    public static void clearStartAt(Context ctx) {
        sp(ctx).edit().remove(KEY_START_AT).apply();
    }

    public static void setHistoryJson(Context ctx, String jsonArrayString) {
        sp(ctx).edit().putString(KEY_HISTORY_JSON, jsonArrayString).apply();
    }

    public static String getHistoryJson(Context ctx) {
        return sp(ctx).getString(KEY_HISTORY_JSON, "[]");
    }
}
