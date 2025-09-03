package com.hippolyte.pointageapp;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.hippolyte.pointageapp.databinding.ActivityMainBinding;
import com.hippolyte.pointageapp.excel.ExcelManager;
import com.hippolyte.pointageapp.util.Prefs;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding b;
    private static final String CHANNEL_ID = "pointage_channel";

    private final ActivityResultLauncher<String> notifPerm =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {});

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        ensureUserName();
        setupUi();
        createNotifChannel();
        askNotifPermissionIfNeeded();
        syncUiFromState();
    }

    private void setupUi() {
        String user = Prefs.getUserName(this);
        b.tvUser.setText(getString(R.string.hello_user, user.isEmpty() ? "—" : user));

        b.btnStart.setOnClickListener(v -> onStartClicked());
        b.btnStop.setOnClickListener(v -> onStopClicked());
        b.btnShare.setOnClickListener(v -> onShareClicked());
        b.btnHistory.setOnClickListener(v -> {
            startActivity(new Intent(this, com.hippolyte.pointageapp.history.HistoryActivity.class));
        });
    }

    private void ensureUserName() {
        if (Prefs.getUserName(this).isEmpty()) {
            EditText input = new EditText(this);
            input.setHint("Nom et prénom");
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            new AlertDialog.Builder(this)
                    .setTitle("Utilisateur")
                    .setMessage("Entrez votre nom et prénom (obligatoire)")
                    .setView(input)
                    .setCancelable(false)
                    .setPositiveButton("OK", (d, w) -> {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty()) {
                            Toast.makeText(this, "Nom requis", Toast.LENGTH_SHORT).show();
                            ensureUserName();
                            return;
                        }
                        Prefs.setUserName(this, name);
                        setupUi();
                    })
                    .show();
        }
    }

    private void onStartClicked() {
        long running = Prefs.getStartAt(this);
        if (running > 0) {
            Toast.makeText(this, "Une tournée est déjà en cours.", Toast.LENGTH_SHORT).show();
            return;
        }
        long now = System.currentTimeMillis();
        Prefs.setStartAt(this, now);

        b.chronometer.setBase(SystemClock.elapsedRealtime());
        b.chronometer.start();

        postOngoingChronoNotification(now);
        syncUiFromState();
    }

    private void onStopClicked() {
        long startAt = Prefs.getStartAt(this);
        if (startAt <= 0) {
            Toast.makeText(this, "Aucune tournée en cours.", Toast.LENGTH_SHORT).show();
            return;
        }
        long endAt = System.currentTimeMillis();

        b.chronometer.stop();

        try {
            ExcelManager excel = new ExcelManager(getApplicationContext());
            File weekly = excel.getOrCreateWeeklyFile();

            // 🔹 Assurer Nom & prénom en C4
            excel.ensureUserName(weekly, Prefs.getUserName(this));

            LocalDateTime startDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(startAt), ZoneId.systemDefault());
            LocalDateTime endDt   = LocalDateTime.ofInstant(Instant.ofEpochMilli(endAt), ZoneId.systemDefault());

            excel.writeShiftTimes(weekly, startDt, endDt);

            String msg = "Écrit: " + ExcelManager.niceDateTime(startDt) + " → " + ExcelManager.niceDateTime(endDt)
                    + "\n" + weekly.getAbsolutePath();
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Erreur Excel: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        Prefs.clearStartAt(this);
        NotificationManagerCompat.from(this).cancel(1);
        syncUiFromState();
    }

    private void onShareClicked() {
        try {
            ExcelManager excel = new ExcelManager(getApplicationContext());
            File weekly = excel.getOrCreateWeeklyFile();

            // 🔹 Assurer Nom & prénom en C4 avant partage
            excel.ensureUserName(weekly, Prefs.getUserName(this));

            Uri uri = excel.getShareUri(weekly);

            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(share, getString(R.string.pointage_label_share_excel)));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Partage impossible: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void syncUiFromState() {
        long startAt = Prefs.getStartAt(this);
        boolean running = startAt > 0;
        b.btnStart.setEnabled(!running);
        b.btnStop.setEnabled(running);

        if (running) {
            long elapsed = System.currentTimeMillis() - startAt;
            b.chronometer.setBase(SystemClock.elapsedRealtime() - elapsed);
            b.chronometer.start();
        } else {
            b.chronometer.setBase(SystemClock.elapsedRealtime());
            b.chronometer.stop();
        }
    }

    private void createNotifChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription(getString(R.string.notif_channel_desc));
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private void postOngoingChronoNotification(long whenEpochMillis) {
        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_running))
                .setContentText(Prefs.getUserName(this))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setUsesChronometer(true)
                .setWhen(whenEpochMillis)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        NotificationManagerCompat.from(this).notify(1, nb.build());
    }

    private void askNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    Toast.makeText(this, getString(R.string.permission_post_notifications_rationale), Toast.LENGTH_LONG).show();
                }
                notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }
}
