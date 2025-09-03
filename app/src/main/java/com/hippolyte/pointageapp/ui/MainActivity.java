package com.hippolyte.pointageapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.hippolyte.pointageapp.data.Prefs;
import com.hippolyte.pointageapp.databinding.ActivityMainBinding;
import com.hippolyte.pointageapp.notif.NotificationHelper;
import com.hippolyte.pointageapp.util.PermissionUtils;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_POST_NOTIF = 10;
    private ActivityMainBinding vb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vb = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        vb.btnEditName.setOnClickListener(v -> startActivity(new Intent(this, NameActivity.class)));
        vb.btnStart.setOnClickListener(v -> onStartTour());
        vb.btnStop.setOnClickListener(v -> onStopTour());
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureUserName();

        long startAt = Prefs.getStartAt(this);
        if (startAt > 0L) {
            bindChrono(startAt, true);
            vb.stateText.setText("Tournée en cours…");
            vb.btnStart.setEnabled(false);
            vb.btnStop.setEnabled(true);
        } else {
            bindChrono(System.currentTimeMillis(), false);
            vb.stateText.setText("Aucune tournée en cours");
            vb.btnStart.setEnabled(true);
            vb.btnStop.setEnabled(false);
        }
        vb.userName.setText(Prefs.getUserName(this) == null ? "—" : Prefs.getUserName(this));
    }

    private void ensureUserName() {
        String name = Prefs.getUserName(this);
        if (TextUtils.isEmpty(name)) {
            startActivity(new Intent(this, NameActivity.class));
            Toast.makeText(this, "Veuillez saisir votre nom", Toast.LENGTH_SHORT).show();
        }
    }

    private void bindChrono(long startAt, boolean running) {
        long elapsed = System.currentTimeMillis() - startAt;
        long base = SystemClock.elapsedRealtime() - Math.max(0, elapsed);
        vb.chronometer.setBase(base);
        if (running) vb.chronometer.start(); else vb.chronometer.stop();
    }

    private void onStartTour() {
        if (Prefs.getStartAt(this) > 0L) return;

        if (PermissionUtils.needsPostNotifPermission() && !PermissionUtils.hasPostNotif(this)) {
            PermissionUtils.requestPostNotif(this, REQ_POST_NOTIF);
            return;
        }
        startNow();
    }

    private void startNow() {
        long startAt = System.currentTimeMillis();
        Prefs.setStartAt(this, startAt);
        bindChrono(startAt, true);
        vb.stateText.setText("Tournée en cours…");
        vb.btnStart.setEnabled(false);
        vb.btnStop.setEnabled(true);
        NotificationHelper.notifyTimer(this, startAt);
        Toast.makeText(this, "Tournée démarrée", Toast.LENGTH_SHORT).show();
    }

    private void onStopTour() {
        long startAt = Prefs.getStartAt(this);
        if (startAt <= 0L) return;

        long durationMs = System.currentTimeMillis() - startAt;
        Prefs.clearStart(this);
        NotificationHelper.cancelTimer(this);

        bindChrono(System.currentTimeMillis(), false);
        vb.stateText.setText("Aucune tournée en cours");
        vb.btnStart.setEnabled(true);
        vb.btnStop.setEnabled(false);

        long minutes = durationMs / 60000L;
        Toast.makeText(this, "Tournée terminée (" + minutes + " min)", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_POST_NOTIF) {
            // même si refus: on démarre quand même la tournée (notif absente)
            startNow();
        }
    }
}
