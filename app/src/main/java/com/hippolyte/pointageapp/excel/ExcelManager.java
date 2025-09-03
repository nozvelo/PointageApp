package com.hippolyte.pointageapp.excel;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import androidx.core.content.FileProvider;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import java.util.Locale;

/**
 * ExcelManager v0.4
 * - Copie assets/Fichier_vierge.xlsx en début de semaine si absent
 * - Écrit Nom & prénom en C4 (une seule cellule) si manquant/différent
 * - Écrit uniquement les heures (texte "HH:mm") dans E/F (formules en G intactes)
 * - Écriture atomique (tmp + rename) + lock simple
 * - Partage via FileProvider (${applicationId}.fileprovider)
 */
public class ExcelManager {

    private static final String ASSET_TEMPLATE_NAME = "Fichier_vierge.xlsx";
    private static final String FOLDER_NAME = "Pointage";
    private static final String FILE_PREFIX = "Pointage_"; // ex: Pointage_2025-09-01_Lundi.xlsx
    private static final String FILE_EXT = ".xlsx";
    private static final String LOCK_NAME = "pointage.lock";

    private final Context app;

    public ExcelManager(Context app) {
        this.app = app.getApplicationContext();
    }

    /** Retourne/crée le fichier hebdomadaire pour la semaine du lundi courant. */
    public File getOrCreateWeeklyFile() throws Exception {
        File dir = new File(app.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), FOLDER_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Impossible de créer le dossier Pointage");
        }
        LocalDate monday = getCurrentMonday(LocalDate.now());
        String fileName = FILE_PREFIX + monday + "_Lundi" + FILE_EXT;
        File target = new File(dir, fileName);

        if (!target.exists()) {
            try (InputStream in = app.getAssets().open(ASSET_TEMPLATE_NAME);
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
        }
        return target;
    }

    /** S'assure que C4 contient le nom et prénom (écrit si vide ou différent). */
    public void ensureUserName(File weeklyFile, String fullName) throws Exception {
        if (fullName == null) fullName = "";
        File lock = new File(weeklyFile.getParentFile(), LOCK_NAME);
        if (lock.exists()) throw new IllegalStateException("Écriture en cours. Réessaie dans un instant.");
        if (!lock.createNewFile()) throw new IllegalStateException("Impossible de créer le lock.");

        File tmp = new File(weeklyFile.getParentFile(), weeklyFile.getName() + ".tmp");
        try (FileInputStream fis = new FileInputStream(weeklyFile);
             XSSFWorkbook wb = new XSSFWorkbook(fis)) {

            XSSFSheet sheet = wb.getSheetAt(0);
            // C4 → row index 3 (0-based), col index 2 (0-based)
            Row r = sheet.getRow(3);
            if (r == null) r = sheet.createRow(3);
            Cell c = r.getCell(2);
            if (c == null) c = r.createCell(2, CellType.STRING);

            String current = c.getStringCellValue() == null ? "" : c.getStringCellValue();
            if (!fullName.equals(current)) {
                c.setCellValue(fullName);
            }

            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                wb.write(fos);
                fos.flush();
            }
        } finally {
            replaceFile(tmp, weeklyFile);
            //noinspection ResultOfMethodCallIgnored
            lock.delete();
        }
    }

    /**
     * Écrit les heures début/fin au bon endroit :
     * - Offset 0..6 via lundi courant
     * - Classe Matin/Aprem/Soir → baseRow: 10/20/30
     * - Col E (index 4) = début, Col F (index 5) = fin
     * - Texte HH:mm (formules en G intactes)
     */
    public void writeShiftTimes(File weeklyFile,
                                LocalDateTime startDateTime,
                                LocalDateTime endDateTime) throws Exception {

        File lock = new File(weeklyFile.getParentFile(), LOCK_NAME);
        if (lock.exists()) throw new IllegalStateException("Écriture en cours. Réessaie dans un instant.");
        if (!lock.createNewFile()) throw new IllegalStateException("Impossible de créer le lock.");

        try {
            LocalDate monday = getCurrentMonday(startDateTime.toLocalDate());
            int offset = (int) (endDateTime.toLocalDate().toEpochDay() - monday.toEpochDay());
            if (offset < 0 || offset > 6) throw new IllegalArgumentException("Offset de jour invalide: " + offset);

            LocalTime startT = startDateTime.toLocalTime();
            LocalTime endT   = endDateTime.toLocalTime();

            ShiftClassifier.Slot slot = ShiftClassifier.classify(startT, endT);
            int base = ShiftClassifier.baseRowFor(slot);
            int rowIndex = base + offset; // 1-based dans le modèle

            File tmp = new File(weeklyFile.getParentFile(), weeklyFile.getName() + ".tmp");

            try (FileInputStream fis = new FileInputStream(weeklyFile);
                 XSSFWorkbook wb = new XSSFWorkbook(fis)) {

                XSSFSheet sheet = wb.getSheetAt(0);
                setCellTime(sheet, rowIndex - 1, 4, startT); // E
                setCellTime(sheet, rowIndex - 1, 5, endT);   // F

                try (FileOutputStream fos = new FileOutputStream(tmp)) {
                    wb.write(fos);
                    fos.flush();
                }
            }

            replaceFile(tmp, weeklyFile);

        } finally {
            //noinspection ResultOfMethodCallIgnored
            lock.delete();
        }
    }

    private static void setCellTime(XSSFSheet sheet, int row0, int col0, LocalTime t) {
        Row r = sheet.getRow(row0);
        if (r == null) r = sheet.createRow(row0);
        Cell c = r.getCell(col0);
        if (c == null) c = r.createCell(col0, CellType.STRING);
        String val = String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
        c.setCellValue(val);
    }

    private static void replaceFile(File src, File dst) throws RuntimeException {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                java.nio.file.Files.move(src.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                try (FileChannel in = new FileInputStream(src).getChannel();
                     FileChannel out = new FileOutputStream(dst).getChannel()) {
                    out.transferFrom(in, 0, in.size());
                }
                //noinspection ResultOfMethodCallIgnored
                src.delete();
            }
        } catch (Exception e) {
            throw new RuntimeException("Remplacement atomique échoué: " + e.getMessage(), e);
        }
    }

    /** Uri de partage via FileProvider (${applicationId}.fileprovider). */
    public Uri getShareUri(File weeklyFile) {
        String authority = app.getPackageName() + ".fileprovider";
        return FileProvider.getUriForFile(app, authority, weeklyFile);
    }

    /** Renvoie le lundi de la semaine de 'any'. */
    public static LocalDate getCurrentMonday(LocalDate any) {
        DayOfWeek dow = any.getDayOfWeek();
        int shift = dow.getValue() - DayOfWeek.MONDAY.getValue(); // 0..6
        return any.minusDays(shift);
    }

    /** Format lisible pour logs/toasts. */
    public static String niceDateTime(LocalDateTime dt) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(java.util.Date.from(dt.atZone(ZoneId.systemDefault()).toInstant()));
    }
}
