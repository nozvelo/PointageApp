package com.hippolyte.pointageapp.excel;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.hippolyte.pointageapp.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public class ExcelHelper {
    private static final String TAG = "ExcelHelper";
    private static final String DIR_NAME = "Pointage";
    private static final String LOCK_NAME = "pointage.lock";
    private static final String MODEL_ASSET = "Fichier_vierge.xlsx";

    public interface Writer {
        void write(@Nullable FileInputStream in, FileOutputStream out) throws Exception;
    }

    public static File getWeekFile(Context ctx) {
        File docs = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        File dir = new File(docs, DIR_NAME);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "Pointage_Semaine.xlsx");
    }

    private static void copyModelTo(Context ctx, File dest) throws Exception {
        AssetManager am = ctx.getAssets();
        try (InputStream in = am.open(MODEL_ASSET);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            out.flush(); // ✅ flush au lieu de sync
        }
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            out.flush(); // ✅ flush
        }
    }

    public static boolean writeTimes(Context ctx, Writer writer) {
        File target = getWeekFile(ctx);
        File dir = target.getParentFile();
        if (dir == null) {
            Toast.makeText(ctx, "Répertoire invalide", Toast.LENGTH_LONG).show();
            return false;
        }
        if (dir.getUsableSpace() < 1_000_000L) {
            Toast.makeText(ctx, "Espace disque insuffisant", Toast.LENGTH_LONG).show();
            return false;
        }

        File lock = new File(dir, LOCK_NAME);
        File tmp = new File(dir, target.getName() + ".tmp");

        try {
            if (!lock.createNewFile()) {
                Toast.makeText(ctx, "Fichier en cours d’utilisation", Toast.LENGTH_LONG).show();
                return false;
            }

            File base;
            if (target.exists() && target.length() > 0) {
                base = target;
            } else {
                File modelPrep = new File(dir, target.getName() + ".model");
                copyModelTo(ctx, modelPrep);
                base = modelPrep;
            }

            try (FileInputStream in = base.exists() ? new FileInputStream(base) : null;
                 FileOutputStream out = new FileOutputStream(tmp)) {
                if (writer != null) {
                    writer.write(in, out);
                } else if (in != null) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
                }
                out.flush(); // ✅ flush
            }

            if (tmp.length() == 0) {
                tmp.delete();
                Toast.makeText(ctx, ctx.getString(R.string.snack_excel_error), Toast.LENGTH_LONG).show();
                return false;
            }

            if (target.exists() && !target.delete()) {
                copyFile(tmp, target);
                tmp.delete();
            } else if (!tmp.renameTo(target)) {
                copyFile(tmp, target);
                tmp.delete();
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "writeTimes error", e);
            Toast.makeText(ctx, ctx.getString(R.string.snack_excel_error), Toast.LENGTH_LONG).show();
            tmp.delete();
            return false;
        } finally {
            if (lock.exists()) lock.delete();
        }
    }
}