package com.pointage.app.data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Store JSON local des tournées pour l’écran Historique.
 * Hotfix : expose la méthode EXACTE attendue par MainActivity via réflexion :
 *   add(Context, long, long, String)
 * et fournit load(Context) utilisé par HistoryActivity.
 *
 * Format JSON simple (liste d’objets { startAt, endAt, slotLabel }).
 */
public final class HistoryStore {
    private static final String TAG = "HistoryStore";
    private static final String PRIMARY_FILE = "history.json";
    private static final String[] LEGACY_FILES = new String[] { "history_store.json" };

    private static final Gson gson = new Gson();

    private HistoryStore() {}

    // ==== API lue par HistoryActivity ====
    public static List<TourEntry> load(Context ctx) {
        List<RawEntry> raw = loadAllRaw(ctx);
        List<TourEntry> out = new ArrayList<>(raw.size());
        for (RawEntry r : raw) {
            TourEntry te = toTourEntry(r.startAt, r.endAt, r.slotLabel);
            if (te != null) out.add(te);
        }
        return out;
    }

    // ==== API EXACTE attendue par MainActivity (réflexion) ====
    // MainActivity cherche "add(Context,long,long,String)" → on la fournit.
    public static void add(Context ctx, long startAt, long endAt, String slotLabel) {
        try {
            List<RawEntry> raw = loadAllRaw(ctx);
            raw.add(new RawEntry(startAt, endAt, slotLabel));
            writeRaw(ctx, raw);
            Log.d(TAG, "add(String): OK (" + slotLabel + ")");
        } catch (Throwable t) {
            Log.e(TAG, "add(String) failed: " + t.getMessage(), t);
        }
    }

    // ==== Conversion Raw -> TourEntry (tolérante) ====
    @Nullable
    private static TourEntry toTourEntry(long startAt, long endAt, @Nullable String slotLabel) {
        // 1) Essayer TourEntry.from(long,long, Slot)
        try {
            Class<?> slotClass = Class.forName("com.pointage.app.data.Slot");
            Method valueOf = slotClass.getMethod("valueOf", String.class);
            String norm = toEnumName(slotLabel); // MORNING/AFTERNOON/EVENING
            Object slot = valueOf.invoke(null, norm);
            Method from = TourEntry.class.getMethod("from", long.class, long.class, slotClass);
            Object obj = from.invoke(null, startAt, endAt, slot);
            if (obj instanceof TourEntry) return (TourEntry) obj;
        } catch (Throwable ignored) {
            // 2) Reclasser via TourClassifier si dispo, avec from(long,long, X)
            try {
                Method classify = TourClassifier.class.getMethod("classify", long.class, long.class);
                Object slot = classify.invoke(null, startAt, endAt);
                for (Method m : TourEntry.class.getMethods()) {
                    if (!m.getName().equals("from")) continue;
                    Class<?>[] ps = m.getParameterTypes();
                    if (ps.length == 3 && ps[0] == long.class && ps[1] == long.class && ps[2].isInstance(slot)) {
                        Object obj = m.invoke(null, startAt, endAt, slot);
                        if (obj instanceof TourEntry) return (TourEntry) obj;
                    }
                }
            } catch (Throwable ignored2) {
                // 3) Dernier recours : constructeur (long,long,String) si présent
                try {
                    return TourEntry.class
                            .getConstructor(long.class, long.class, String.class)
                            .newInstance(startAt, endAt, safeLabel(slotLabel));
                } catch (Throwable ignored3) {
                    Log.w(TAG, "toTourEntry: fallback échoué, impossible de construire TourEntry.");
                }
            }
        }
        return null;
    }

    // ==== Lecture/Écriture RAW (JSON) ====
    private static List<RawEntry> loadAllRaw(Context ctx) {
        LinkedHashMap<String, RawEntry> merged = new LinkedHashMap<>();
        putAll(merged, readRaw(ctx, PRIMARY_FILE));
        for (String name : LEGACY_FILES) putAll(merged, readRaw(ctx, name));
        return new ArrayList<>(merged.values());
    }

    private static void putAll(LinkedHashMap<String, RawEntry> dst, List<RawEntry> src) {
        if (src == null) return;
        for (RawEntry r : src) dst.put(keyOf(r), r);
    }

    private static String keyOf(RawEntry r) { return r.startAt + "_" + r.endAt; }

    private static List<RawEntry> readRaw(Context ctx, String fileName) {
        File f = new File(ctx.getFilesDir(), fileName);
        if (!f.exists()) return new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            Type t = new TypeToken<List<RawEntry>>(){}.getType();
            List<RawEntry> list = gson.fromJson(br, t);
            return (list != null) ? list : new ArrayList<>();
        } catch (Throwable e) {
            Log.e(TAG, "readRaw(" + fileName + ") failed: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private static void writeRaw(Context ctx, List<RawEntry> list) {
        File f = new File(ctx.getFilesDir(), PRIMARY_FILE);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f, false))) {
            gson.toJson(list, bw);
        } catch (Throwable e) {
            Log.e(TAG, "writeRaw failed: " + e.getMessage(), e);
        }
    }

    // ==== Utils ====
    private static String toEnumName(@Nullable String label) {
        String s = safeLabel(label).toLowerCase(Locale.ROOT).trim();
        if (s.startsWith("matin")) return "MORNING";
        if (s.startsWith("ap") || s.contains("midi")) return "AFTERNOON";
        if (s.startsWith("soir")) return "EVENING";
        return "AFTERNOON";
    }

    private static String safeLabel(@Nullable String s) {
        return (s == null) ? "" : s.trim();
    }

    // ==== DTO JSON interne ====
    private static final class RawEntry {
        long startAt;
        long endAt;
        String slotLabel;

        RawEntry(long startAt, long endAt, String slotLabel) {
            this.startAt = startAt;
            this.endAt = endAt;
            this.slotLabel = (slotLabel == null) ? "" : slotLabel;
        }
    }
}
