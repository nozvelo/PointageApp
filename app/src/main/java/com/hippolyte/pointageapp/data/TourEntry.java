package com.hippolyte.pointageapp.data;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TourEntry {
    public long startAt;     // epoch ms
    public long endAt;       // epoch ms
    public long durationMin; // minutes
    public String date;      // yyyy-MM-dd (local)
    public String weekday;   // Lundi, Mardi...
    public String startStr;  // HH:mm
    public String endStr;    // HH:mm
    public String period;    // "Matin" | "Après-midi" | "Soir"

    public static TourEntry from(long startAt, long endAt, TourClassifier.Period p) {
        TourEntry e = new TourEntry();
        e.startAt = startAt;
        e.endAt = endAt;
        e.durationMin = Math.max(0L, (endAt - startAt) / 60000L);

        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime zStart = Instant.ofEpochMilli(startAt).atZone(zone);

        DateTimeFormatter dfDate = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter dfTime = DateTimeFormatter.ofPattern("HH:mm");
        DateTimeFormatter dfWeek = DateTimeFormatter.ofPattern("EEEE"); // jour complet local

        e.date = dfDate.format(zStart);
        e.weekday = capitalizeFirst(dfWeek.format(zStart));
        e.startStr = dfTime.format(zStart);
        e.endStr = dfTime.format(Instant.ofEpochMilli(endAt).atZone(zone));
        e.period = TourClassifier.periodLabel(p);
        return e;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("startAt", startAt);
        o.put("endAt", endAt);
        o.put("durationMin", durationMin);
        o.put("date", date);
        o.put("weekday", weekday);
        o.put("startStr", startStr);
        o.put("endStr", endStr);
        o.put("period", period);
        return o;
    }

    public static TourEntry fromJson(JSONObject o) throws JSONException {
        TourEntry e = new TourEntry();
        e.startAt = o.getLong("startAt");
        e.endAt = o.getLong("endAt");
        e.durationMin = o.getLong("durationMin");
        e.date = o.getString("date");
        e.weekday = o.getString("weekday");
        e.startStr = o.getString("startStr");
        e.endStr = o.getString("endStr");
        e.period = o.getString("period");
        return e;
    }

    @Override public String toString() {
        long h = durationMin / 60;
        long m = durationMin % 60;
        String hm = h + "h" + (m < 10 ? ("0" + m) : String.valueOf(m));
        return date + " (" + weekday + ") • " + period + " • " + startStr + "–" + endStr + " • " + hm;
    }

    private static String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }
}
