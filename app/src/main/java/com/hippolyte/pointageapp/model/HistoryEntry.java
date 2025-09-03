package com.hippolyte.pointageapp.model;

public class HistoryEntry {
    private long startAt;
    private long endAt;
    private long durationMs;
    private String slot; // Matin, Après-midi, Soir
    private String date; // format yyyy-MM-dd

    public HistoryEntry(long startAt, long endAt, long durationMs, String slot, String date) {
        this.startAt = startAt;
        this.endAt = endAt;
        this.durationMs = durationMs;
        this.slot = slot;
        this.date = date;
    }

    public long getStartAt() { return startAt; }
    public long getEndAt() { return endAt; }
    public long getDurationMs() { return durationMs; }
    public String getSlot() { return slot; }
    public String getDate() { return date; }
}
