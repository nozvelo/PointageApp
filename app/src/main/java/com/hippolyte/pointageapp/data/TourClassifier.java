package com.hippolyte.pointageapp.data;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

public final class TourClassifier {
    private TourClassifier() {}

    public enum Period { MORNING, AFTERNOON, EVENING }

    // Règles strictes :
    // Matin : start ≥ 06:00 ET end ≤ 13:45
    // Soir  : start ≥ 15:30 ET end ≤ 21:45
    // Sinon : Après-midi
    public static Period classify(long startAtMs, long endAtMs) {
        ZoneId zone = ZoneId.systemDefault();
        LocalTime start = Instant.ofEpochMilli(startAtMs).atZone(zone).toLocalTime();
        LocalTime end   = Instant.ofEpochMilli(endAtMs).atZone(zone).toLocalTime();

        LocalTime six     = LocalTime.of(6, 0);
        LocalTime thirt   = LocalTime.of(13, 45);
        LocalTime fifteen = LocalTime.of(15, 30);
        LocalTime twenty1 = LocalTime.of(21, 45);

        boolean morning = (start.equals(six) || start.isAfter(six)) && (end.isBefore(thirt) || end.equals(thirt));
        boolean evening = (start.equals(fifteen) || start.isAfter(fifteen)) && (end.isBefore(twenty1) || end.equals(twenty1));

        if (morning) return Period.MORNING;
        if (evening) return Period.EVENING;
        return Period.AFTERNOON;
    }

    public static String periodLabel(Period p) {
        switch (p) {
            case MORNING: return "Matin";
            case EVENING: return "Soir";
            default: return "Après-midi";
        }
    }
}
