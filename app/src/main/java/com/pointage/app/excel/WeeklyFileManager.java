package com.pointage.app.excel;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;

import com.pointage.app.BuildConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class WeeklyFileManager {

    private static final String DIR_NAME = "Pointage";
    private static final String ASSET_MODEL = "Fichier_vierge.xlsx"; // ton modèle en assets/
    private static final String MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private WeeklyFileManager() {}

    /** Retourne le fichier XLSX de la semaine (nom basé sur lundi de la semaine). */
    public static File getWeeklyFile(Context ctx) {
        File dir = new File(ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), DIR_NAME);
        if (!dir.exists()) dir.mkdirs();

        LocalDate today = LocalDate.now();
        LocalDate monday = today.minusDays((today.getDayOfWeek().getValue() + 6) % 7); // lundi = 1
        String name = "Pointage_" + monday.format(DateTimeFormatter.ISO_DATE) + ".xlsx";
        return new File(dir, name);
    }

    /** Crée le fichier depuis assets si absent (copie byte-for-byte). */
    public static void ensureExists(Context ctx, File weeklyXlsx) throws Exception {
        if (weeklyXlsx.exists()) return;
        try (InputStream in = ctx.getAssets().open(ASSET_MODEL);
             FileOutputStream out = new FileOutputStream(weeklyXlsx)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            out.flush();
        }
    }

    /** Uri FileProvider pour partager/ouvrir. */
    public static Uri getUriForFile(Context ctx, File f) {
        return FileProvider.getUriForFile(ctx, BuildConfig.APPLICATION_ID + ".fileprovider", f);
    }

    /** Intent pour OUVRIR le fichier dans une app Excel. */
    public static Intent buildOpenIntent(Context ctx, File f) {
        Uri uri = getUriForFile(ctx, f);
        Intent i = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, MIME_XLSX)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (Build.VERSION.SDK_INT <= 28) {
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        }
        return i;
    }

    /** Intent pour PARTAGER le fichier. */
    public static Intent buildShareIntent(Context ctx, File f) {
        Uri uri = getUriForFile(ctx, f);
        return new Intent(Intent.ACTION_SEND)
                .setType(MIME_XLSX)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
}
