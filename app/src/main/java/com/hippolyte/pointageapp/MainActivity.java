package com.hippolyte.pointageapp.ui;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.hippolyte.pointageapp.R;
import com.hippolyte.pointageapp.util.ChronometerManager;
import com.hippolyte.pointageapp.notif.NotificationHelper;
import com.hippolyte.pointageapp.data.Prefs;
import com.hippolyte.pointageapp.databinding.ActivityMainBinding;
import com.hippolyte.pointageapp.excel.ExcelHelper;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_POST_NOTIF = 1001;
    private ActivityMainBinding vb;
    private ChronometerManager chronoMgr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        vb = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        chronoMgr = new ChronometerManager(vb.chronometer);

        vb.tvWelcome.setText(getString(R.string.app_welcome));

        vb.btnEditName.setOnClickListener(v -> {
            startActivity(new Intent(this, NameActivity.class));
        });

        vb.btnStart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                onStartClicked();
            }
        });

        vb.btnStop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                onStopClicked();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncUiState();
    }

    private void syncUiState() {
        String user = Prefs.getUser(this);
        boolean hasName = !TextUtils.isEmpty(user);
        long startAt = Prefs.getStartAt(this);
        boolean running = startAt > 0;

        vb.btnStart.setEnabled(hasName && !running);
        vb.btnStop.setEnabled(running);

        if (running) {
            chronoMgr.startFromEpochMillis(startAt);
        } else {
            chronoMgr.stopAndReset();
        }
    }

    private void onStartClicked() {
        String user = Prefs.getUser(this);
        if (TextUtils.isEmpty(user)) {
            Toast.makeText(this, R.string.snack_name_required, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, NameActivity.class));
            return;
        }
        long running = Prefs.getStartAt(this);
        if (running > 0) {
            Toast.makeText(this, R.string.snack_already_running, Toast.LENGTH_SHORT).show();
            return;
        }

        long now = System.currentTimeMillis();
        Prefs.setStartAt(this, now);
        chronoMgr.startFromEpochMillis(now);

        ensurePostNotifPermissionThenShow(now);

        Toast.makeText(this, R.string.snack_started, Toast.LENGTH_SHORT).show();
        syncUiState();
    }

    private void onStopClicked() {
        long startAt = Prefs.getStartAt(this);
        if (startAt <= 0) {
            Toast.makeText(this, R.string.snack_need_start, Toast.LENGTH_SHORT).show();
            return;
        }
        long endAt = System.currentTimeMillis();

        // Écriture Excel robuste (utiliser ta logique v0.4 dans writer)
        boolean ok = ExcelHelper.writeTimes(this, (in, out) -> {
            // >>> place ici exactement TA logique POI existante de v0.4 <<
            // - Lire le modèle si in == null
            // - Calcul de l’offset du jour via C6 (déjà présent dans le modèle)
            // - Classification Matin/Aprem/Soir selon règles strictes
            // - Écriture E/F (début/fin) dans la ligne adéquate (G auto)
            // - Sauvegarder le workbook vers 'out'
        });

        // Nettoyage état / notif quoi qu’il arrive
        Prefs.clearStart(this);
        stopOngoingNotification();
        chronoMgr.stopAndReset();
        syncUiState();

        if (ok) {
            Toast.makeText(this, R.string.snack_stopped, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.snack_excel_error, Toast.LENGTH_LONG).show();
        }
    }

    private void ensurePostNotifPermissionThenShow(long startAt) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIF);
                // Affichera la notif dans onRequestPermissionsResult si accordé
                return;
            }
        }
        showOngoingNotification(startAt);
    }

    private void showOngoingNotification(long startAt) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(1, NotificationHelper.buildOngoing(this, startAt));
        }
    }

    private void stopOngoingNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(requestCode, perms, res);
        if (requestCode == REQ_POST_NOTIF) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
                long startAt = Prefs.getStartAt(this);
                if (startAt > 0) showOngoingNotification(startAt);
            } else {
                Toast.makeText(this, "Permission notifications refusée (chronomètre toujours actif)", Toast.LENGTH_LONG).show();
            }
        }
    }
}
