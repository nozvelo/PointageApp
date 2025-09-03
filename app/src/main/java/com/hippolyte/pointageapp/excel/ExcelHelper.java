package com.hippolyte.pointageapp.excel;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.hippolyte.pointageapp.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

// NOTE: l'implémentation Apache POI existante en v0.4 est conservée.
// Ici on montre uniquement l'enveloppe IO robuste (tmp + lock). Injecte tes appels POI dans writeTimes().
public class ExcelHelper {

    private static final String TAG = "ExcelHelper";
    private static final String DIR_NAME = "Pointage";
    private static final String LOCK_NAME = "pointage.lock";

    public interface Writer {
        void write(FileInputStream in, FileOutputStream out) throws Exception;
    }

    public static File getWeekFile(Context ctx) {
        File docs = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        File dir = new File(docs, DIR_NAME);
        if (!dir.exists()) dir.mkdirs();
        // Nom de fichier basé sur lundi de la semaine (déjà en v0.4 chez toi)
        String fileName = "Pointage_Semaine.xlsx";
        return new File(dir, fileName);
    }

    public static boolean writeTimes(Context ctx, Writer writer) {
        File target = getWeekFile(ctx);
        File lock = new File(target.getParentFile(), LOCK_NAME);
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");

        if (!hasFreeSpace(target.getParentFile(), 1_000_000L)) {
            Toast.makeText(ctx, "Espace disque insuffisant", Toast.LENGTH_LONG).show();
            return false;
        }

        try {
            if (!lock.createNewFile()) {
                Toast.makeText(ctx, "Fichier en cours d'utilisation", Toast.LENGTH_LONG).show();
                return false;
            }

            try (FileInputStream in = target.exists() ? new FileInputStream(target) : null;
                 FileOutputStream out = new FileOutputStream(tmp)) {

                writer.write(in, out); // ← ici tes appels POI (copie modèle si in == null)
            }

            if (!tmp.renameTo(target)) {
                // Fallback copy
                try (FileInputStream fin = new FileInputStream(tmp);
                     FileOutputStream fout = new FileOutputStream(target)) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = fin.read(buf)) != -1) fout.write(buf, 0, r);
                }
                // supprimer tmp après copie
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "writeTimes error", e);
            Toast.makeText(ctx, ctx.getString(R.string.snack_excel_error), Toast.LENGTH_LONG).show();
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        } finally {
            if (lock.exists()) //noinspection ResultOfMethodCallIgnored
                lock.delete();
        }
    }

    private static boolean hasFreeSpace(File dir, long minBytes) {
        return dir.getUsableSpace() > minBytes;
    }
}
