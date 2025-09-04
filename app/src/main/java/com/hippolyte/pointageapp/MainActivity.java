package com.hippolyte.pointageapp;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.hippolyte.pointageapp.data.Prefs;
import com.hippolyte.pointageapp.data.HistoryStore;
import com.hippolyte.pointageapp.data.TourClassifier;
import com.hippolyte.pointageapp.data.TourEntry;
import com.hippolyte.pointageapp.databinding.ActivityMainBinding;
import com.hippolyte.pointageapp.excel.ExcelNameFixer;
import com.hippolyte.pointageapp.excel.ExcelTimeWriter;
import com.hippolyte.pointageapp.excel.WeeklyFileManager;
import com.hippolyte.pointageapp.history.HistoryActivity;
import com.hippolyte.pointageapp.ui.NameActivity;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding vb;

    private static final String CHANNEL_ID = "pointage_running_channel";
    private static final int NOTIF_ID = 1001;

    private final ActivityResultLauncher<String> notifPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this, R.string.perm_notif_denied, Toast.LENGTH_SHORT).show();
                } else if (isRunning()) {
                    showRunningNotification(Prefs.getStartAt(this));
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vb = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        ensureChannel();

        String name = Prefs.getUserName(this);
        if (name == null || name.trim().isEmpty()) {
            startActivity(new Intent(this, NameActivity.class));
        }
        updateWelcome();
        updateUiFromState();

        vb.btnStart.setOnClickListener(v -> onStartTour());
        vb.btnStop.setOnClickListener(v -> onStopTour());
        if (vb.btnChangeName != null) vb.btnChangeName.setOnClickListener(v -> startActivity(new Intent(this, NameActivity.class)));
        if (vb.btnHistory != null) vb.btnHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        if (vb.btnFile != null) vb.btnFile.setOnClickListener(v -> onFileClick());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateWelcome();
        updateUiFromState();
    }

    private void updateWelcome() {
        String n = Prefs.getUserName(this);
        if (vb.tvWelcome != null) {
            if (n == null || n.trim().isEmpty()) {
                vb.tvWelcome.setText(getString(R.string.welcome_no_name));
            } else {
                vb.tvWelcome.setText(getString(R.string.welcome_name, n));
            }
        }
    }

    private boolean isRunning() {
        return Prefs.getStartAt(this) > 0L;
    }

    private void updateUiFromState() {
        long startAt = Prefs.getStartAt(this);
        if (startAt > 0L) {
            vb.btnStart.setEnabled(false);
            vb.btnStop.setEnabled(true);
            if (vb.status != null) vb.status.setText(R.string.status_running);
            vb.chronometer.setBase(SystemClock.elapsedRealtime() - (System.currentTimeMillis() - startAt));
            vb.chronometer.start();
        } else {
            vb.btnStart.setEnabled(true);
            vb.btnStop.setEnabled(false);
            if (vb.status != null) vb.status.setText(R.string.status_idle);
            vb.chronometer.stop();
            vb.chronometer.setBase(SystemClock.elapsedRealtime());
        }
    }

    private void onStartTour() {
        if (isRunning()) return;
        long startAt = System.currentTimeMillis();
        Prefs.setStartAt(this, startAt);
        updateUiFromState();

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // v0.9 : rationale simple avant la demande
            if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.notif_rationale_title)
                        .setMessage(R.string.notif_rationale_msg)
                        .setPositiveButton(R.string.allow, (d, w) ->
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS))
                        .setNegativeButton(R.string.later, null)
                        .show();
            } else {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            showRunningNotification(startAt);
        }
        Toast.makeText(this, R.string.tour_started, Toast.LENGTH_SHORT).show();
    }

    private void onStopTour() {
        if (!isRunning()) return;

        long startAt = Prefs.getStartAt(this);
        long endAt = System.currentTimeMillis();

        // 1) Classification + historique
        TourClassifier.Period p = TourClassifier.classify(startAt, endAt);
        TourEntry entry = TourEntry.from(startAt, endAt, p);
        HistoryStore.add(this, entry);

        // 2) Écriture Excel (assure existence + C4 + E/F, sans écraser)
        try {
            File weekly = WeeklyFileManager.getWeeklyFile(this);
            WeeklyFileManager.ensureExists(this, weekly);
            ExcelNameFixer.writeUserNameToC4(this, weekly);
            ExcelTimeWriter.writeEF(weekly, entry, p);
        } catch (IllegalStateException filled) {
            if ("SLOT_FILLED".equals(filled.getMessage())) {
                Toast.makeText(this, R.string.excel_slot_filled, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.snack_excel_error, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.snack_excel_error, Toast.LENGTH_LONG).show();
        }

        // 3) Arrêt chrono + état + notif
        Prefs.clearStartAt(this);
        updateUiFromState();
        NotificationManagerCompat.from(this).cancel(NOTIF_ID);

        Toast.makeText(this, getString(R.string.tour_stopped, entry.durationMin), Toast.LENGTH_LONG).show();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription(getString(R.string.channel_desc));
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private void showRunningNotification(long startAt) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent contentPi = PendingIntent.getActivity(
                this, 0, open,
                Build.VERSION.SDK_INT >= 31
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setContentIntent(contentPi)
                .setOngoing(true)
                .setWhen(startAt)
                .setUsesChronometer(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        NotificationManagerCompat.from(this).notify(NOTIF_ID, b.build());
    }

    /* =========================
       Export / Ouvrir / Partager
       ========================= */
    private void onFileClick() {
        try {
            File weekly = WeeklyFileManager.getWeeklyFile(this);
            WeeklyFileManager.ensureExists(this, weekly);
            ExcelNameFixer.writeUserNameToC4(this, weekly);

            new AlertDialog.Builder(this)
                    .setTitle(R.string.file_title)
                    .setItems(new CharSequence[]{
                            getString(R.string.btn_open),
                            getString(R.string.btn_share)
                    }, (d, which) -> {
                        if (which == 0) openWeeklyFile(weekly);
                        else shareWeeklyFile(weekly);
                    })
                    .show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.snack_excel_error, Toast.LENGTH_LONG).show();
        }
    }

    private void openWeeklyFile(File weekly) {
        Intent i = WeeklyFileManager.buildOpenIntent(this, weekly);
        try {
            startActivity(i);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, R.string.no_viewer, Toast.LENGTH_LONG).show();
        }
    }

    private void shareWeeklyFile(File weekly) {
        Intent i = WeeklyFileManager.buildShareIntent(this, weekly);
        startActivity(Intent.createChooser(i, getString(R.string.btn_share)));
    }
}
