package com.pointage.app.util;

import android.os.SystemClock;
import android.widget.Chronometer;

public class ChronometerManager {
    private final Chronometer chrono;

    public ChronometerManager(Chronometer chrono) {
        this.chrono = chrono;
    }

    public void startFromEpochMillis(long startAtEpochMillis) {
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0L, now - startAtEpochMillis);
        long base = SystemClock.elapsedRealtime() - elapsed;
        chrono.setBase(base);
        chrono.start();
    }

    public void stopAndReset() {
        chrono.stop();
        chrono.setBase(SystemClock.elapsedRealtime());
    }
}
