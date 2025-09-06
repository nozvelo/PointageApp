package com.pointage.app.excel;

import com.pointage.app.data.TourClassifier;
import com.pointage.app.data.TourEntry;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Écrit les heures début/fin dans E/F selon les règles :
 * - Lundi de la semaine = dans le nom du fichier (Pointage_YYYY-MM-DD.xlsx)
 * - Offset = 0..6 (lundi..dimanche)
 * - BaseRows : Matin=10, Aprem=20, Soir=30 -> row = base + offset
 * - Colonnes : E=4, F=5 (0-based)
 * - Ne modifie aucune autre cellule (B/C/G restent intacts).
 * - v0.9 : n'écrase JAMAIS une case déjà remplie → IllegalStateException("SLOT_FILLED")
 * - Compat POI 3.x : Cell#getCellType() retourne int (Cell.CELL_TYPE_*)
 */
public final class ExcelTimeWriter {

    private ExcelTimeWriter() {}

    public static void writeEF(File weeklyXlsx, TourEntry entry, TourClassifier.Period period) throws Exception {
        if (weeklyXlsx == null || !weeklyXlsx.exists() || !weeklyXlsx.isFile()) {
            throw new IllegalArgumentException("weeklyXlsx invalide: " + weeklyXlsx);
        }

        // 1) Lundi depuis le nom de fichier (Pointage_YYYY-MM-DD.xlsx)
        LocalDate monday = parseMondayFromFilename(weeklyXlsx.getName());

        // 2) Offset du jour (0..6)
        ZoneId zone = ZoneId.systemDefault();
        LocalDate day = Instant.ofEpochMilli(entry.startAt).atZone(zone).toLocalDate();
        int offset = (int) java.time.temporal.ChronoUnit.DAYS.between(monday, day);
        if (offset < 0 || offset > 6) {
            offset = Math.max(0, Math.min(6, offset)); // clamp sécurité
        }

        // 3) Ligne selon période
        int base;
        switch (period) {
            case MORNING: base = 10; break;
            case EVENING: base = 30; break;
            default: base = 20; break; // AFTERNOON
        }
        int excelRow1Based = base + offset;   // 10..16 / 20..26 / 30..36
        int rowIdx = excelRow1Based - 1;      // POI: 0-based

        // 4) Dates Excel pour début/fin (même jour)
        LocalTime startLt = Instant.ofEpochMilli(entry.startAt).atZone(zone).toLocalTime();
        LocalTime endLt   = Instant.ofEpochMilli(entry.endAt).atZone(zone).toLocalTime();
        Date startDate = Date.from(LocalDateTime.of(day, startLt).atZone(zone).toInstant());
        Date endDate   = Date.from(LocalDateTime.of(day, endLt).atZone(zone).toInstant());

        File lock = new File(weeklyXlsx.getParentFile(), "pointage.lock");
        File tmp  = new File(weeklyXlsx.getParentFile(), weeklyXlsx.getName() + ".tmp");

        try (FileOutputStream lockOut = new FileOutputStream(lock)) {
            lockOut.write(1);
            lockOut.flush();

            try (FileInputStream fis = new FileInputStream(weeklyXlsx);
                 XSSFWorkbook wb = new XSSFWorkbook(fis)) {

                XSSFSheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : wb.createSheet("Feuille1");

                Row row = sheet.getRow(rowIdx);
                if (row == null) row = sheet.createRow(rowIdx);

                // E = 4, F = 5 (0-based)
                Cell cellE = row.getCell(4);
                Cell cellF = row.getCell(5);

                // v0.9 : si déjà rempli → ne pas écraser
                if (!isEmpty(cellE) || !isEmpty(cellF)) {
                    throw new IllegalStateException("SLOT_FILLED");
                }

                if (cellE == null) cellE = row.createCell(4);
                if (cellF == null) cellF = row.createCell(5);

                // On ne touche pas aux styles (le modèle formate HH:mm)
                cellE.setCellValue(startDate);
                cellF.setCellValue(endDate);

                // Sauvegarde atomique -> .tmp
                try (FileOutputStream fos = new FileOutputStream(tmp)) {
                    wb.write(fos);
                    fos.flush();
                }

                // Remplacement atomique du fichier d’origine
                replaceFile(tmp, weeklyXlsx);
            }
        } finally {
            if (tmp.exists()) tmp.delete();
            if (lock.exists()) lock.delete();
        }
    }

    // Compat POI 3.x : getCellType() → int ; BLANK/STRING via constantes Cell.CELL_TYPE_*
    private static boolean isEmpty(Cell c) {
        if (c == null) return true;
        int t = c.getCellType();
        if (t == Cell.CELL_TYPE_BLANK) return true;
        if (t == Cell.CELL_TYPE_STRING) {
            String s = c.getStringCellValue();
            return s == null || s.trim().isEmpty();
        }
        // NUMERIC, BOOLEAN, FORMULA → considéré "rempli"
        return false;
    }

    private static LocalDate parseMondayFromFilename(String name) {
        // "Pointage_YYYY-MM-DD.xlsx"
        try {
            int us = name.indexOf('_');
            int dot = name.lastIndexOf('.');
            String iso = name.substring(us + 1, dot);
            return LocalDate.parse(iso, DateTimeFormatter.ISO_DATE);
        } catch (Exception e) {
            // fallback : lundi semaine courante
            LocalDate today = LocalDate.now();
            return today.minusDays((today.getDayOfWeek().getValue() + 6) % 7);
        }
    }

    private static void replaceFile(File src, File dst) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            java.nio.file.Files.move(
                    src.toPath(),
                    dst.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
            );
        } else {
            try (FileChannel in = new FileInputStream(src).getChannel();
                 FileChannel out = new FileOutputStream(dst).getChannel()) {
                out.transferFrom(in, 0, in.size());
            }
        }
    }
}
