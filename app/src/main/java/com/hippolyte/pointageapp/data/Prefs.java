package com.hippolyte.pointageapp.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String PNAME = "pointage_prefs";
    private static final String KEY_USERNAME = "user_name";
    private static final String KEY_START_AT = "startAt";

    private Prefs(){}

    private static SharedPreferences sp(Context ctx){
        return ctx.getSharedPreferences(PNAME, Context.MODE_PRIVATE);
    }

    public static void setUserName(Context ctx, String value){
        sp(ctx).edit().putString(KEY_USERNAME, value).apply();
    }

    public static String getUserName(Context ctx){
        return sp(ctx).getString(KEY_USERNAME, null);
    }

    public static void setStartAt(Context ctx, long epochMillis){
        sp(ctx).edit().putLong(KEY_START_AT, epochMillis).apply();
    }

    public static long getStartAt(Context ctx){
        return sp(ctx).getLong(KEY_START_AT, 0L);
    }

    public static void clearStart(Context ctx){
        sp(ctx).edit().remove(KEY_START_AT).apply();
    }
}
