package com.pointage.app.excel;

import java.time.LocalTime;

public final class ShiftClassifier {

    public enum Slot { MATIN, APREM, SOIR }

    private static final LocalTime MORNING_START_MIN = LocalTime.of(6, 0);
    private static final LocalTime MORNING_END_MAX   = LocalTime.of(13, 45);

    private static final LocalTime EVENING_START_MIN = LocalTime.of(15, 30);
    private static final LocalTime EVENING_END_MAX   = LocalTime.of(21, 45);

    private ShiftClassifier() {}

    public static Slot classify(LocalTime start, LocalTime end) {
        if (!start.isBefore(end)) {
            return Slot.APREM; // sécurité
        }
        if (!start.isBefore(MORNING_START_MIN) && !end.isAfter(MORNING_END_MAX)) {
            return Slot.MATIN;
        }
        if (!start.isBefore(EVENING_START_MIN) && !end.isAfter(EVENING_END_MAX)) {
            return Slot.SOIR;
        }
        return Slot.APREM;
    }

    public static int baseRowFor(Slot slot) {
        switch (slot) {
            case MATIN: return 10;
            case SOIR:  return 30;
            default:    return 20; // APREM
        }
    }
}
