package com.hippolyte.pointageapp.excel;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.zip.ZipException;

/**
 * Écriture Excel stable, sans modifier la structure du modèle.
 * - Modèle : app/src/main/assets/Fichier_vierge.xlsx (figé)
 * - Fichier semaine : <ExternalFiles>/Documents/Pointage/Pointage_Semaine.xlsx
 * - C4 : Nom & prénom (écrire en C4 uniquement, zone fusionnée dans le modèle)
 * - E/F : Début / Fin (G total se calcule par les formules présentes dans le modèle)
 * - Écriture atomique (.tmp + rename) + petit lock file
 * - Ouverture via OPCPackage pour éviter le ClassCastException sur Android
 */
public final class ExcelHelper {

    private static final String TAG = "ExcelHelper";

    private static final String WEEK_DIR_NAME   = "Pointage";
    private static final String WEEK_FILE_NAME  = "Pointage_Semaine.xlsx";
    private static final String ASSET_TEMPLATE  = "Fichier_vierge.xlsx"; // DOIT être dans app/src/main/assets/
    private static final String LOCK_NAME       = "pointage.lock";

    // Colonnes (0-based): C=2, E=4, F=5
    private static final int COL_NAME_C4 = 2;
    private static final int COL_START_E = 4;
    private static final int COL_END_F   = 5;

    static {
        // Évite des erreurs de sécurité ZIP agressives avec certains modèles.
        try { ZipSecureFile.setMinInflateRatio(0.0d); } catch (Throwable ignore) {}
    }

    private ExcelHelper() {}

    // ------------------- API publique -------------------

    /** Copie le modèle si besoin, puis écrit le nom (C4). Retourne true si OK. */
    public static boolean ensureWeekFileAndWriteName(Context ctx, String userName) {
        File weekFile = ensureWeekFile(ctx);
        return writeUserNameInternal(weekFile, userName);
    }

    /** Écrit le nom (C4) sans (re)copier le modèle. */
    public static boolean writeUserName(Context ctx, String userName) {
        File weekFile = getWeekFile(ctx);
        return writeUserNameInternal(weekFile, userName);
    }

    /** Écrit un tour (début/fin) dans E/F selon les règles. Retourne true si OK. */
    public static boolean recordTour(Context ctx, long startAtMs, long endAtMs) {
        File weekFile = ensureWeekFile(ctx);
        return writeTourInternal(weekFile, startAtMs, endAtMs, ZoneId.systemDefault());
    }

    /** Ouvre le modèle depuis assets et tente un XSSFWorkbook → true si OK. */
    public static boolean selfTestTemplate(Context ctx) {
        try {
            File tmp = new File(ctx.getCacheDir(), "model_self_test.xlsx");
            if (tmp.exists()) //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            if (!copyTemplateFromAssets(ctx, tmp)) return false;
            try (FileInputStream fis = new FileInputStream(tmp);
                 OPCPackage pkg = OPCPackage.open(fis);
                 XSSFWorkbook wb = new XSSFWorkbook(pkg)) {
                Log.d(TAG, "selfTestTemplate: OK — sheets=" + wb.getNumberOfSheets() + " path=" + tmp.getAbsolutePath());
                return wb.getNumberOfSheets() > 0;
            } finally {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } catch (Throwable t) {
            Log.e(TAG, "selfTestTemplate: FAIL " + t.getClass().getSimpleName() + ": " + t.getMessage(), t);
            return false;
        }
    }

    // ------------------- Accès fichiers -------------------

    private static File getWeekDir(Context ctx) {
        File docs = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        File dir = new File(docs, WEEK_DIR_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "getWeekDir: mkdirs failed at " + dir.getAbsolutePath());
        }
        return dir;
    }

    /** Public si besoin dans d’autres écrans. */
    public static File getWeekFile(Context ctx) {
        File f = new File(getWeekDir(ctx), WEEK_FILE_NAME);
        Log.d(TAG, "getWeekFile -> " + f.getAbsolutePath());
        return f;
    }

    private static File ensureWeekFile(Context ctx) {
        File f = getWeekFile(ctx);
        if (!f.exists() || f.length() == 0) {
            Log.d(TAG, "ensureWeekFile: copying template → " + f.getAbsolutePath());
            if (!copyTemplateFromAssets(ctx, f)) {
                Log.e(TAG, "ensureWeekFile: copyTemplateFromAssets FAILED");
            }
        }
        return f;
    }

    /** Copie le modèle assets → dest. Retourne true si OK. */
    private static boolean copyTemplateFromAssets(Context ctx, File dest) {
        Log.d(TAG, "copyTemplateFromAssets: assets/" + ASSET_TEMPLATE + " → " + dest.getAbsolutePath());
        try (InputStream in = ctx.getAssets().open(ASSET_TEMPLATE)) {
            File tmp = new File(dest.getParentFile(), WEEK_FILE_NAME + ".tmp");
            try (OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            if (dest.exists() && !dest.delete()) Log.w(TAG, "copyTemplateFromAssets: unable to delete old file");
            if (!tmp.renameTo(dest)) {
                Log.w(TAG, "copyTemplateFromAssets: renameTo failed, fallback stream copy");
                try (FileInputStream fis = new FileInputStream(tmp);
                     FileOutputStream fos = new FileOutputStream(dest)) {
                    byte[] b = new byte[8192];
                    int n;
                    while ((n = fis.read(b)) > 0) fos.write(b, 0, n);
                }
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
            return true;
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Template NOT FOUND in assets/. Place exact file '" + ASSET_TEMPLATE + "' in app/src/main/assets/", e);
            return false;
        } catch (IOException e) {
            Log.e(TAG, "copyTemplateFromAssets IO error: " + e.getMessage(), e);
            return false;
        }
    }

    // ------------------- Écritures -------------------

    private static boolean writeUserNameInternal(File weekFile, String userName) {
        if (weekFile == null) { Log.e(TAG, "writeUserNameInternal: weekFile null"); return false; }
        File lock = new File(weekFile.getParentFile(), LOCK_NAME);
        try {
            //noinspection ResultOfMethodCallIgnored
            lock.createNewFile();

            // ⚠️ IMPORTANT : ouverture via OPCPackage + FileInputStream
            try (FileInputStream fis = new FileInputStream(weekFile);
                 OPCPackage pkg = OPCPackage.open(fis);
                 XSSFWorkbook wb = new XSSFWorkbook(pkg)) {

                Sheet sh = wb.getSheetAt(0);
                Row r4 = getOrCreateRow(sh, 3);              // C4 → row 3 (0-based)
                Cell c4 = getOrCreateCell(r4, COL_NAME_C4);  // col 2
                c4.setCellValue(userName != null ? userName : "");

                writeWorkbookAtomically(wb, weekFile);
                Log.d(TAG, "writeUserNameInternal: OK");
                return true;
            }
        } catch (ZipException | OLE2NotOfficeXmlFileException ze) {
            Log.e(TAG, "writeUserNameInternal: invalid XLSX (zip): " + ze.getMessage(), ze);
        } catch (Throwable t) {
            Log.e(TAG, "writeUserNameInternal: " + t.getClass().getSimpleName() + ": " + t.getMessage(), t);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            lock.delete();
        }
        return false;
    }

    private static boolean writeTourInternal(File weekFile, long startAtMs, long endAtMs, ZoneId zone) {
        if (weekFile == null) { Log.e(TAG, "writeTourInternal: weekFile null"); return false; }
        File lock = new File(weekFile.getParentFile(), LOCK_NAME);
        try {
            //noinspection ResultOfMethodCallIgnored
            lock.createNewFile();

            // ⚠️ IMPORTANT : ouverture via OPCPackage + FileInputStream
            try (FileInputStream fis = new FileInputStream(weekFile);
                 OPCPackage pkg = OPCPackage.open(fis);
                 XSSFWorkbook wb = new XSSFWorkbook(pkg)) {

                Sheet sh = wb.getSheetAt(0);

                LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startAtMs), zone);
                LocalDateTime end   = LocalDateTime.ofInstant(Instant.ofEpochMilli(endAtMs),   zone);

                Slot slot = classify(start.toLocalTime(), end.toLocalTime());
                int offset = dayOffset(start.toLocalDate());

                // BaseRows (Excel 1-based)
                int base = (slot == Slot.MORNING) ? 10 : (slot == Slot.EVENING) ? 30 : 20;
                int rowIndex = (base - 1) + offset; // -> 0-based
                Row row = getOrCreateRow(sh, rowIndex);

                DataFormat df = wb.createDataFormat();
                CellStyle timeStyle = wb.createCellStyle();
                timeStyle.setDataFormat(df.getFormat("HH:mm"));

                Date startDate = toExcelDate(start);
                Date endDate   = toExcelDate(end);

                Cell cStart = getOrCreateCell(row, COL_START_E);
                Cell cEnd   = getOrCreateCell(row, COL_END_F);

                cStart.setCellValue(startDate);
                cStart.setCellStyle(timeStyle);

                cEnd.setCellValue(endDate);
                cEnd.setCellStyle(timeStyle);

                // recalcul des formules (G auto)
                XSSFFormulaEvaluator.evaluateAllFormulaCells(wb);

                writeWorkbookAtomically(wb, weekFile);
                Log.d(TAG, "writeTourInternal: OK row=" + (rowIndex + 1) + " slot=" + slot);
                return true;
            }
        } catch (ZipException | OLE2NotOfficeXmlFileException ze) {
            Log.e(TAG, "writeTourInternal: invalid XLSX (zip): " + ze.getMessage(), ze);
        } catch (Throwable t) {
            Log.e(TAG, "writeTourInternal: " + t.getClass().getSimpleName() + ": " + t.getMessage(), t);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            lock.delete();
        }
        return false;
    }

    private static void writeWorkbookAtomically(XSSFWorkbook wb, File weekFile) throws IOException {
        File tmp = new File(weekFile.getParentFile(), WEEK_FILE_NAME + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            wb.write(fos);
        }
        if (weekFile.exists() && !weekFile.delete()) {
            Log.w(TAG, "writeWorkbookAtomically: old file delete failed");
        }
        if (!tmp.renameTo(weekFile)) {
            Log.w(TAG, "writeWorkbookAtomically: renameTo failed, fallback copy");
            try (FileInputStream fis = new FileInputStream(tmp);
                 FileOutputStream fos = new FileOutputStream(weekFile)) {
                byte[] b = new byte[8192];
                int n;
                while ((n = fis.read(b)) > 0) fos.write(b, 0, n);
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
        Log.d(TAG, "writeWorkbookAtomically: wrote " + weekFile.getAbsolutePath());
    }

    // ------------------- Règles & util -------------------

    private enum Slot { MORNING, AFTERNOON, EVENING }

    // Matin : start ≥ 06:00 ET end ≤ 13:45
    // Soir  : start ≥ 15:30 ET end ≤ 21:45
    // Sinon : Après-midi
    private static Slot classify(LocalTime start, LocalTime end) {
        LocalTime MORNING_START = LocalTime.of(6, 0);
        LocalTime MORNING_END   = LocalTime.of(13, 45);
        LocalTime EVENING_START = LocalTime.of(15, 30);
        LocalTime EVENING_END   = LocalTime.of(21, 45);

        boolean isMorning = !start.isBefore(MORNING_START) && !end.isAfter(MORNING_END);
        boolean isEvening = !start.isBefore(EVENING_START) && !end.isAfter(EVENING_END);

        if (isMorning) return Slot.MORNING;
        if (isEvening) return Slot.EVENING;
        return Slot.AFTERNOON;
    }

    /** Lundi=0 .. Dimanche=6 */
    private static int dayOffset(LocalDate date) {
        int dow = date.getDayOfWeek().getValue(); // 1=lundi .. 7=dimanche
        return dow - 1;
    }

    private static Row getOrCreateRow(Sheet sheet, int rowIndex) {
        Row r = sheet.getRow(rowIndex);
        return (r != null) ? r : sheet.createRow(rowIndex);
    }

    private static Cell getOrCreateCell(Row row, int colIndex) {
        Cell c = row.getCell(colIndex);
        return (c != null) ? c : row.createCell(colIndex);
    }

    private static Date toExcelDate(LocalDateTime ldt) {
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }
}
