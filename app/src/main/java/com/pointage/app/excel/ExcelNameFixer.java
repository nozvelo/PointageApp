package com.pointage.app.excel;

import android.content.Context;

import com.pointage.app.data.Prefs;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

/**
 * Patch v0.7.1 : écrit le Nom & prénom en C4 (fusionné C4:G4 dans le modèle).
 * - Ne modifie AUCUNE autre cellule, formule, style ou fusion.
 * - Écriture atomique : sauvegarde vers .tmp puis rename -> fichier final.
 */
public final class ExcelNameFixer {

    private ExcelNameFixer() {}

    /**
     * Écrit user_name (Prefs) dans C4 de la première feuille du fichier weeklyXlsx.
     */
    public static void writeUserNameToC4(Context context, File weeklyXlsx) throws Exception {
        String name = Prefs.getUserName(context);
        if (name == null) name = "";
        writeToC4(weeklyXlsx, name);
    }

    /**
     * Écrit 'name' dans C4 du fichier weeklyXlsx (XLSX).
     */
    public static void writeToC4(File weeklyXlsx, String name) throws Exception {
        if (weeklyXlsx == null || !weeklyXlsx.exists() || !weeklyXlsx.isFile()) {
            throw new IllegalArgumentException("weeklyXlsx invalide : " + weeklyXlsx);
        }

        File lock = new File(weeklyXlsx.getParentFile(), "pointage.lock");
        File tmp  = new File(weeklyXlsx.getParentFile(), weeklyXlsx.getName() + ".tmp");

        // Petit verrou basique
        try (FileOutputStream lockOut = new FileOutputStream(lock)) {
            lockOut.write(1);
            lockOut.flush();

            try (FileInputStream fis = new FileInputStream(weeklyXlsx);
                 XSSFWorkbook wb = new XSSFWorkbook(fis)) {

                XSSFSheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : wb.createSheet("Feuille1");

                // C4 -> (row index 3, col index 2) en zéro-based
                Row row = sheet.getRow(3);
                if (row == null) row = sheet.createRow(3);
                Cell cell = row.getCell(2);
                if (cell == null) cell = row.createCell(2);

                cell.setCellValue(name); // On ne touche PAS au style ; C4 est déjà fusionnée avec G4 dans le modèle

                // Sauvegarde atomique -> .tmp puis rename
                try (FileOutputStream fos = new FileOutputStream(tmp)) {
                    wb.write(fos);
                    fos.flush();
                }

                // Remplacement du fichier d’origine
                replaceFile(tmp, weeklyXlsx);
            }
        } finally {
            // Nettoyage du lock & tmp
            if (tmp.exists()) tmp.delete();
            if (lock.exists()) lock.delete();
        }
    }

    private static void replaceFile(File src, File dst) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            java.nio.file.Files.move(src.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } else {
            // Fallback simple si API < 26
            try (FileChannel in = new FileInputStream(src).getChannel();
                 FileChannel out = new FileOutputStream(dst).getChannel()) {
                out.transferFrom(in, 0, in.size());
            }
            // Supprime le tmp une fois copié
            // (le finally du dessus s'en charge aussi)
        }
    }
}
