package com.hippolyte.pointageapp.history;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class HistoryEntry {
    public final long startAtMillis;
    public final long endAtMillis;
    public final String slot;

    public HistoryEntry(long startAtMillis, long endAtMillis, String slot) {
        this.startAtMillis = startAtMillis;
        this.endAtMillis = endAtMillis;
        this.slot = slot == null ? "" : slot;
    }

    public LocalDateTime startLocal() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(startAtMillis), ZoneId.systemDefault());
    }

    public LocalDateTime endLocal() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(endAtMillis), ZoneId.systemDefault());
    }
}
