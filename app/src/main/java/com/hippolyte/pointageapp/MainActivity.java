package com.hippolyte.pointageapp;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;

import com.hippolyte.pointageapp.databinding.ActivityMainBinding;
import com.hippolyte.pointageapp.excel.ExcelHelper;
import com.hippolyte.pointageapp.history.HistoryActivity;

import java.io.File;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "pointage_prefs";
    private static final String KEY_START_AT = "startAt";
    private static final String KEY_USER_NAME = "user_name";
    private static final String NOTIF_CHANNEL_ID = "pointage_chrono";
    private static final int NOTIF_ID = 101;

    private ActivityMainBinding binding;
    private SharedPreferences sp;

    @Nullable private Chronometer chronometer;
    @Nullable private Button btnStart, btnStop, btnExport, btnHistory, btnEditName, btnOpenFile;
    @Nullable private TextView tvStatus;

    private final ActivityResultLauncher<String> requestPostNotifications =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {});

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sp = getSharedPreferences(PREFS, MODE_PRIVATE);

        // Map des vues (IDs connus + secours par texte)
        mapViewsById();
        wireByTextIfMissing((ViewGroup) binding.getRoot());
        findStatusByInitialTextIfMissing((ViewGroup) binding.getRoot());

        if (getIntent() != null && getIntent().getBooleanExtra("restore_notification_only", false)) {
            long startAt = sp.getLong(KEY_START_AT, 0L);
            if (startAt > 0L) {
                ensureNotificationPermissionIfNeeded();
                showOngoingChronoNotification(startAt);
            }
            finish();
            return;
        }

        if (btnStart   != null) btnStart.setOnClickListener(v -> startTour());
        if (btnStop    != null) btnStop.setOnClickListener(v -> stopTour());
        if (btnExport  != null) btnExport.setOnClickListener(v -> shareCurrentExcel());
        if (btnHistory != null) btnHistory.setOnClickListener(v -> openHistory());
        if (btnEditName!= null) btnEditName.setOnClickListener(v -> showEditNameDialog());
        if (btnOpenFile!= null) btnOpenFile.setOnClickListener(v -> openOrShareDialog());

        long startAt = sp.getLong(KEY_START_AT, 0L);
        if (startAt > 0L) {
            ensureNotificationPermissionIfNeeded();
            showOngoingChronoNotification(startAt);
            startChrono(startAt);
            toggleButtons(true);
            updateStatus(true);
        } else {
            toggleButtons(false);
            updateStatus(false);
        }
    }

    // ===== Start/Stop =====
    private void startTour() {
        long startAt = sp.getLong(KEY_START_AT, 0L);
        if (startAt > 0L) {
            Toast.makeText(this, "Une tournée est déjà en cours.", Toast.LENGTH_SHORT).show();
            return;
        }
        long now = System.currentTimeMillis();
        sp.edit().putLong(KEY_START_AT, now).apply();

        ensureNotificationPermissionIfNeeded();
        showOngoingChronoNotification(now);
        startChrono(now);
        toggleButtons(true);
        updateStatus(true);
    }

    private void stopTour() {
        long startAt = sp.getLong(KEY_START_AT, 0L);
        if (startAt <= 0L) {
            Toast.makeText(this, "Aucune tournée en cours.", Toast.LENGTH_SHORT).show();
            return;
        }

        long endAt = System.currentTimeMillis();
        long durationMs = endAt - startAt;

        stopChrono();
        NotificationManagerCompat.from(this).cancel(NOTIF_ID);
        sp.edit().putLong(KEY_START_AT, 0L).apply();
        toggleButtons(false);
        updateStatus(false);

        boolean ok = ExcelHelper.recordTour(this, startAt, endAt);
        String msg = "Tournée terminée. Durée ~ " + (durationMs / 60000) + " min" + (ok ? " (Excel OK)" : " (Excel KO)");
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    // ===== Chrono/UI/Statut =====
    private void startChrono(long startAt) {
        if (chronometer == null) return;
        long base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - startAt);
        chronometer.setBase(base);
        chronometer.start();
    }

    private void stopChrono() {
        if (chronometer == null) return;
        chronometer.stop();
    }

    private void toggleButtons(boolean running) {
        if (btnStart != null) btnStart.setEnabled(!running);
        if (btnStop  != null) btnStop.setEnabled(running);
    }

    private void updateStatus(boolean running) {
        if (tvStatus == null) return;
        tvStatus.setText(running ? "Statut : en cours" : "Statut : à l’arrêt");
    }

    // ===== Notification =====
    private void showOngoingChronoNotification(long startAt) {
        createNotificationChannelIfNeeded();

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPI = PendingIntent.getActivity(
                this, 0, openIntent,
                Build.VERSION.SDK_INT >= 23
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        String userName = sp.getString(KEY_USER_NAME, "Tournée en cours");
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(userName)
                .setContentText("Tournée en cours")
                .setContentIntent(contentPI)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setUsesChronometer(true)
                .setWhen(startAt)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        NotificationManagerCompat.from(this).notify(NOTIF_ID, b.build());
    }

    private void createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Chronomètre de tournée",
                NotificationManager.IMPORTANCE_LOW
        );
        ch.setDescription("Notification persistante pour la durée de la tournée");
        nm.createNotificationChannel(ch);
    }

    private void ensureNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    // ===== Nom utilisateur =====
    private void showEditNameDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setText(sp.getString(KEY_USER_NAME, ""));

        new AlertDialog.Builder(this)
                .setTitle("Modifier le nom")
                .setView(input)
                .setPositiveButton("Enregistrer", (d, which) -> {
                    String name = input.getText() != null ? input.getText().toString().trim() : "";
                    sp.edit().putString(KEY_USER_NAME, name).apply();

                    boolean ok = ExcelHelper.ensureWeekFileAndWriteName(this, name);
                    Toast.makeText(this, ok ? "Nom écrit dans Excel" : "Échec écriture du nom (voir logs)", Toast.LENGTH_LONG).show();

                    long startAt = sp.getLong(KEY_START_AT, 0L);
                    if (startAt > 0L) {
                        showOngoingChronoNotification(startAt);
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    // ===== Fichier de la semaine : Ouvrir ou Partager =====
    private void openOrShareDialog() {
        String[] items = new String[]{"Ouvrir", "Partager"};
        new AlertDialog.Builder(this)
                .setTitle("Fichier de la semaine")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) openCurrentExcel();
                    else shareCurrentExcel();
                })
                .show();
    }

    private File resolveCurrentExcelOrNull() {
        File docsDir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);
        if (docsDir == null) return null;
        return new File(docsDir, "Pointage/Pointage_Semaine.xlsx");
    }

    private void openCurrentExcel() {
        try {
            File excel = resolveCurrentExcelOrNull();
            if (excel == null || !excel.exists()) {
                Toast.makeText(this, "Fichier de la semaine introuvable.", Toast.LENGTH_SHORT).show();
                return;
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", excel);
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(view);
        } catch (Throwable t) {
            Toast.makeText(this, "Impossible d’ouvrir: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareCurrentExcel() {
        try {
            File excel = resolveCurrentExcelOrNull();
            if (excel == null || !excel.exists()) {
                Toast.makeText(this, "Fichier Excel introuvable.", Toast.LENGTH_SHORT).show();
                return;
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", excel);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Partager le pointage"));
        } catch (Throwable t) {
            Toast.makeText(this, "Partage indisponible: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ===== Historique =====
    private void openHistory() {
        try {
            startActivity(new Intent(this, HistoryActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, "Historique indisponible.", Toast.LENGTH_SHORT).show();
        }
    }

    // ===== Mapping souple =====
    private void mapViewsById() {
        chronometer = (Chronometer) findFirstById(new int[]{id("chronometer"), id("chronoView"), id("textChrono"), id("tvChrono")});
        btnStart    = (Button) findFirstById(new int[]{id("btnStart"), id("buttonStart"), id("startButton"), id("btn_commencer")});
        btnStop     = (Button) findFirstById(new int[]{id("btnStop"), id("buttonStop"), id("stopButton"), id("btn_terminer")});
        btnExport   = (Button) findFirstById(new int[]{id("btnExport"), id("buttonExport"), id("exportButton")});
        btnHistory  = (Button) findFirstById(new int[]{id("btnHistory"), id("buttonHistory"), id("historyButton"), id("btn_historique")});
        btnEditName = (Button) findFirstById(new int[]{id("btnEditName"), id("buttonEditName"), id("btn_name"), id("btnModifierNom"), id("btn_user_name"), id("btnChangeName")});
        btnOpenFile = (Button) findFirstById(new int[]{id("btnOpenFile"), id("buttonOpenFile"), id("btnFile"), id("btnSemaine"), id("btnFichierSemaine")});
        tvStatus    = (TextView) findFirstById(new int[]{id("status"), id("tvStatus"), id("statusText"), id("textStatus"), id("tv_statut")});
    }

    private void wireByTextIfMissing(ViewGroup root) { traverseAndWire(root); }
    private void traverseAndWire(View v) {
        if (v instanceof Button) {
            Button b = (Button) v;
            String t = String.valueOf(b.getText()).trim().toLowerCase(Locale.ROOT);
            if (btnHistory == null && t.contains("historique")) { btnHistory = b; btnHistory.setOnClickListener(x -> openHistory()); }
            if (btnEditName == null && (t.contains("modifier le nom") || t.contains("nom") || t.contains("user") || t.contains("name"))) { btnEditName = b; btnEditName.setOnClickListener(x -> showEditNameDialog()); }
            if (btnOpenFile == null && (t.contains("fichier") || t.contains("semaine") || t.contains("excel"))) { btnOpenFile = b; btnOpenFile.setOnClickListener(x -> openOrShareDialog()); }
            if (btnStart == null && (t.contains("commencer") || t.equals("start"))) { btnStart = b; btnStart.setOnClickListener(x -> startTour()); }
            if (btnStop == null && (t.contains("terminer") || t.equals("stop"))) { btnStop = b; btnStop.setOnClickListener(x -> stopTour()); }
            if (btnExport == null && (t.contains("partager") || t.contains("export") || t.equals("share"))) { btnExport = b; btnExport.setOnClickListener(x -> shareCurrentExcel()); }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) traverseAndWire(g.getChildAt(i));
        }
    }

    private void findStatusByInitialTextIfMissing(ViewGroup root) {
        if (tvStatus != null) return;
        TextView candidate = findTextViewWithText(root, "Statut : à l’arrêt");
        if (candidate == null) candidate = findTextViewWithText(root, "Statut : a l'arret");
        if (candidate != null) tvStatus = candidate;
    }

    private TextView findTextViewWithText(View v, String text) {
        if (v instanceof TextView) {
            CharSequence cs = ((TextView) v).getText();
            if (cs != null && cs.toString().trim().equalsIgnoreCase(text)) return (TextView) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView found = findTextViewWithText(g.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private int id(String name) { return getResources().getIdentifier(name, "id", getPackageName()); }
    @Nullable private Object findFirstById(int[] ids) {
        for (int vid : ids) {
            if (vid != 0) {
                Object v = findViewById(vid);
                if (v != null) return v;
            }
        }
        return null;
    }
}
