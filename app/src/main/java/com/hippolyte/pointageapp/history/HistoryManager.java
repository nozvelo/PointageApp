package com.hippolyte.pointageapp.history;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class HistoryManager {
    public static List<HistoryEntry> load(Context ctx) {
        File f = new File(ctx.getFilesDir(), "history.json");
        List<HistoryEntry> out = new ArrayList<>();
        if (!f.exists()) return out;

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        } catch (Exception e) {
            return out;
        }

        try {
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;

                long start = o.optLong("startAt", 0);
                long end   = o.optLong("endAt", 0);
                String slot = o.optString("slot", "");

                if (start > 0 && end > 0) {
                    out.add(new HistoryEntry(start, end, slot));
                }
            }
        } catch (Exception ignore) {}

        return out;
    }
}
