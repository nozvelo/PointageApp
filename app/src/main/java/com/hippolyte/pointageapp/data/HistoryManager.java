package com.hippolyte.pointageapp.data;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hippolyte.pointageapp.model.HistoryEntry;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class HistoryManager {
    private static final String FILE_NAME = "history.json";
    private static final Gson gson = new Gson();

    private HistoryManager(){}

    private static File getFile(Context ctx){
        return new File(ctx.getFilesDir(), FILE_NAME);
    }

    public static List<HistoryEntry> load(Context ctx){
        try (FileReader fr = new FileReader(getFile(ctx))) {
            Type listType = new TypeToken<List<HistoryEntry>>(){}.getType();
            return gson.fromJson(fr, listType);
        } catch (Exception e){
            return new ArrayList<>();
        }
    }

    public static void save(Context ctx, List<HistoryEntry> list){
        try (FileWriter fw = new FileWriter(getFile(ctx), false)) {
            gson.toJson(list, fw);
        } catch (Exception ignored){}
    }

    public static void addEntry(Context ctx, long startAt, long endAt){
        long duration = endAt - startAt;
        String slot = classify(startAt, endAt);
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(startAt));

        HistoryEntry entry = new HistoryEntry(startAt, endAt, duration, slot, date);
        List<HistoryEntry> list = load(ctx);
        list.add(entry);
        save(ctx, list);
    }

    private static String classify(long startAt, long endAt){
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String s = fmt.format(new Date(startAt));
        String e = fmt.format(new Date(endAt));

        if (s.compareTo("06:00") >= 0 && e.compareTo("13:45") <= 0) return "Matin";
        if (s.compareTo("15:30") >= 0 && e.compareTo("21:45") <= 0) return "Soir";
        return "Après-midi";
    }
}
