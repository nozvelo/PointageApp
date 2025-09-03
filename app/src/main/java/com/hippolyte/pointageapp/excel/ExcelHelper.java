package com.hippolyte.pointageapp.excel;

import android.content.Context;
import android.os.Environment;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class ExcelHelper {

    private static final String ASSET_NAME = "Fichier_vierge.xlsx";
    private static final String DIR_NAME = "Pointage";
    private static final String LOCK_NAME = "pointage.lock";

    private ExcelHelper() {}

    /** Écrit le nom (C4) si vide + les heures début/fin en E/F pour le bon créneau/ jour. */
    public static File writeTour(Context ctx, String userName, long startAt, long endAt) throws Exception {
        File weeklyFile = ensureWeeklyFile(ctx);

        // Petit lock file (meilleure sécurité si plusieurs actions rapprochées)
        File lock = new File(weeklyFile.getParentFile(), LOCK_NAME);
        if (!lock.createNewFile()) {
            // déjà en écriture : on échoue proprement
            throw new IllegalStateException("Écriture Excel en cours, réessayez.");
        }

        File tmp = new File(weeklyFile.getParentFile(), weeklyFile.getName() + ".tmp");
        try (FileInputStream fis = new FileInputStream(weeklyFile);
             XSSFWorkbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : wb.createSheet("Feuille1");

            // 1) Écrire le nom en C4 si la cellule est vide
            setIfEmpty(sheet, /*rowIdx*/3, /*colIdx C*/2, userName);

            // 2) Calcul créneau + ligne
            SlotAndRow sr = resolveSlotAndRow(startAt, endAt);
            int rowIdx = sr.rowIndexZeroBased;

            // 3) Écrire heures "HH:mm" en E/F (E=4, F=5)
            String startStr = fmt("HH:mm", startAt);
            String endStr   = fmt("HH:mm", endAt);

            setString(sheet, rowIdx, /*E*/4, startStr);
            setString(sheet, rowIdx, /*F*/5, endStr);

            // 4) Sauvegarde atomique
            try (OutputStream fos = new FileOutputStream(tmp)) {
                wb.write(fos);
                fos.flush();
            }
        } finally {
            // Retrait du lock dès que possible
            //noinspection ResultOfMethodCallIgnored
            lock.delete();
        }

        if (!tmp.renameTo(weeklyFile)) {
            copyFile(tmp, weeklyFile);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
        return weeklyFile;
    }

    /** Test v0.4 initial : écrire nom en C4 et renvoyer le fichier (conservé pour debug éventuel). */
    public static File writeUserNameC4(Context ctx, String userName) throws Exception {
        File weeklyFile = ensureWeeklyFile(ctx);
        File tmp = new File(weeklyFile.getParentFile(), weeklyFile.getName() + ".tmp");

        try (FileInputStream fis = new FileInputStream(weeklyFile);
             XSSFWorkbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : wb.createSheet("Feuille1");
            setString(sheet, /*rowIdx*/3, /*C*/2, userName);

            try (OutputStream fos = new FileOutputStream(tmp)) {
                wb.write(fos);
                fos.flush();
            }
        }
        if (!tmp.renameTo(weeklyFile)) {
            copyFile(tmp, weeklyFile);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
        return weeklyFile;
    }

    // ---------- Internes ----------

    private static void setIfEmpty(Sheet sheet, int rowIdx, int colIdx, String value) {
        XSSFRow row = (XSSFRow) sheet.getRow(rowIdx);
        if (row == null) row = (XSSFRow) sheet.createRow(rowIdx);
        XSSFCell cell = (XSSFCell) row.getCell(colIdx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        String cur = cell.getStringCellValue();
        if (cur == null || cur.trim().isEmpty()) {
            cell.setCellValue(value);
        }
    }

    private static void setString(Sheet sheet, int rowIdx, int colIdx, String value) {
        XSSFRow row = (XSSFRow) sheet.getRow(rowIdx);
        if (row == null) row = (XSSFRow) sheet.createRow(rowIdx);
        XSSFCell cell = (XSSFCell) row.getCell(colIdx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        cell.setCellValue(value);
    }

    private static String fmt(String pattern, long epochMs) {
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(epochMs));
    }

    /** Calcule créneau + ligne (0-based) selon les règles horaires et la semaine du startAt. */
    private static SlotAndRow resolveSlotAndRow(long startAt, long endAt) {
        // Créneau
        String slot;
        String s = fmt("HH:mm", startAt);
        String e = fmt("HH:mm", endAt);
        if (s.compareTo("06:00") >= 0 && e.compareTo("13:45") <= 0) {
            slot = "Matin";
        } else if (s.compareTo("15:30") >= 0 && e.compareTo("21:45") <= 0) {
            slot = "Soir";
        } else {
            slot = "Après-midi";
        }

        // Offset jour 0..6 basé sur le lundi de la semaine du startAt
        int offset = dayOffsetFromMonday(startAt);

        // Base rows (Excel est 1-based) -> indices 0-based
        int base = slot.equals("Matin") ? 10 : slot.equals("Soir") ? 30 : 20; // Matin 10, Aprem 20, Soir 30
        int rowExcelOneBased = base + offset;
        int rowIdxZeroBased = rowExcelOneBased - 1;

        return new SlotAndRow(slot, rowIdxZeroBased);
    }

    private static int dayOffsetFromMonday(long timeMs) {
        Calendar cal = Calendar.getInstance(Locale.getDefault());
        cal.setTimeInMillis(timeMs);
        // trouve le lundi de cette semaine
        int dow = cal.get(Calendar.DAY_OF_WEEK); // Dim=1…Sam=7
        int offsetToMonday = ((dow + 5) % 7);    // Lun=0…Dim=6
        // offset du jour courant par rapport à ce lundi
        return offsetToMonday;
    }

    private static File ensureWeeklyFile(Context ctx) throws Exception {
        File base = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) throw new IllegalStateException("External files dir unavailable");
        File dir = new File(base, DIR_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Cannot create directory: " + dir.getAbsolutePath());
        }

        String monday = currentWeekMonday();
        String fileName = "Pointage_" + monday + ".xlsx";
        File weekly = new File(dir, fileName);

        if (!weekly.exists()) {
            try (InputStream in = ctx.getAssets().open(ASSET_NAME);
                 OutputStream out = new FileOutputStream(weekly)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.flush();
            }
        }
        return weekly;
    }

    private static String currentWeekMonday() {
        Calendar cal = Calendar.getInstance(Locale.getDefault());
        int dow = cal.get(Calendar.DAY_OF_WEEK); // Dim=1 … Sam=7
        int offsetToMonday = ((dow + 5) % 7);    // Lun=0 … Dim=6
        cal.add(Calendar.DAY_OF_MONTH, -offsetToMonday);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
    }

    private static void copyFile(File from, File to) throws Exception {
        try (FileInputStream fis = new FileInputStream(from);
             FileOutputStream fos = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) > 0) fos.write(buf, 0, n);
            fos.flush();
        }
    }

    private static final class SlotAndRow {
        final String slot;
        final int rowIndexZeroBased;

        SlotAndRow(String slot, int rowIndexZeroBased) {
            this.slot = slot;
            this.rowIndexZeroBased = rowIndexZeroBased;
        }
    }
}
