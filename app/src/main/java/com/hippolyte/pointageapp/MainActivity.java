package com.hippolyte.pointageapp;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.hippolyte.pointageapp.data.Prefs;
import com.hippolyte.pointageapp.databinding.ActivityMainBinding;
import com.hippolyte.pointageapp.excel.ExcelHelper;
import com.hippolyte.pointageapp.history.HistoryActivity;
import com.hippolyte.pointageapp.notif.NotificationHelper;
import com.hippolyte.pointageapp.ui.FileActivity;
import com.hippolyte.pointageapp.ui.NameActivity;
import com.hippolyte.pointageapp.util.ChronometerManager;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

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

        vb.btnEditName.setOnClickListener(v -> startActivity(new Intent(this, NameActivity.class)));
        vb.btnStart.setOnClickListener(v -> onStartClicked());
        vb.btnStop.setOnClickListener(v -> onStopClicked());

        vb.btnHistory.setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));

        vb.btnFile.setOnClickListener(v ->
                startActivity(new Intent(this, FileActivity.class)));
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
        if (Prefs.getStartAt(this) > 0) {
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

        boolean ok = ExcelHelper.writeTimes(this, (FileInputStream in, FileOutputStream out) -> {
            Workbook wb = (in != null) ? new XSSFWorkbook(in) : new XSSFWorkbook();
            Sheet sheet = wb.getSheetAt(0);

            // ✅ ÉCRIRE NOM & PRÉNOM EN C4 (fusion C4:G4 déjà dans le modèle ; on ne touche qu’à C4)
            String userName = Prefs.getUser(this);
            if (!TextUtils.isEmpty(userName)) {
                Row rowC4 = sheet.getRow(3); // ligne 4 -> index 3
                if (rowC4 == null) rowC4 = sheet.createRow(3);
                Cell cellC4 = rowC4.getCell(2); // colonne C -> index 2
                if (cellC4 == null) cellC4 = rowC4.createCell(2);
                cellC4.setCellValue(userName);
            }

            // 🔢 Récupérer le lundi de la semaine via C6
            Row rowC6 = sheet.getRow(5); // ligne 6 -> index 5
            if (rowC6 == null) rowC6 = sheet.createRow(5);
            Cell cellC6 = rowC6.getCell(2); // colonne C -> index 2
            if (cellC6 == null) cellC6 = rowC6.createCell(2);

            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            evaluator.evaluateFormulaCell(cellC6);
            Date monday = cellC6.getDateCellValue();

            if (monday == null) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(startAt);
                int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
                int isoDay = (dayOfWeek == Calendar.SUNDAY) ? 7 : dayOfWeek - 1;
                cal.add(Calendar.DAY_OF_MONTH, -(isoDay - 1));
                zeroTime(cal);
                monday = cal.getTime();
            }

            // 📅 Calcul offset 0..6
            Calendar cStart = Calendar.getInstance();
            cStart.setTimeInMillis(startAt);
            zeroTime(cStart);
            Calendar cMonday = Calendar.getInstance();
            cMonday.setTime(monday);
            zeroTime(cMonday);

            long diffDays = TimeUnit.MILLISECONDS.toDays(
                    cStart.getTimeInMillis() - cMonday.getTimeInMillis());
            int offset = (int) Math.max(0, Math.min(6, diffDays));

            // ⏰ Classification
            Calendar startCal = Calendar.getInstance();
            startCal.setTimeInMillis(startAt);
            int startHour = startCal.get(Calendar.HOUR_OF_DAY);
            int startMin = startCal.get(Calendar.MINUTE);

            Calendar endCal = Calendar.getInstance();
            endCal.setTimeInMillis(endAt);
            int endHour = endCal.get(Calendar.HOUR_OF_DAY);
            int endMin = endCal.get(Calendar.MINUTE);

            boolean isMorning = (startHour > 6 || (startHour == 6 && startMin >= 0)) &&
                    (endHour < 13 || (endHour == 13 && endMin <= 45));
            boolean isEvening = (startHour > 15 || (startHour == 15 && startMin >= 30)) &&
                    (endHour < 21 || (endHour == 21 && endMin <= 45));

            if (startHour == 6 && startMin == 0) isMorning = true;
            if (startHour == 15 && startMin == 30) isEvening = true;

            int baseRow;
            if (isMorning) baseRow = 10;
            else if (isEvening) baseRow = 30;
            else baseRow = 20;

            int excelRowNumber = baseRow + offset;
            int poiRowIndex = excelRowNumber - 1;

            Row r = sheet.getRow(poiRowIndex);
            if (r == null) r = sheet.createRow(poiRowIndex);

            // ✍️ Écriture des heures en E/F (indices 4 et 5). On ne touche JAMAIS à G.
            Cell cE = r.getCell(4); if (cE == null) cE = r.createCell(4);
            Cell cF = r.getCell(5); if (cF == null) cF = r.createCell(5);

            cE.setCellValue(new Date(startAt));
            cF.setCellValue(new Date(endAt));

            wb.write(out);
            wb.close();
        });

        // 🔄 Reset état et UI
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

    private static void zeroTime(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private void ensurePostNotifPermissionThenShow(long startAt) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_POST_NOTIF);
                return;
            }
        }
        showOngoingNotification(startAt);
    }

    private void showOngoingNotification(long startAt) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(1, NotificationHelper.buildOngoing(this, startAt));
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
