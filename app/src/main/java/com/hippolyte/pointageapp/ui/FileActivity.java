package com.hippolyte.pointageapp.ui;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.hippolyte.pointageapp.BuildConfig;
import com.hippolyte.pointageapp.R;
import com.hippolyte.pointageapp.databinding.ActivityFileBinding;
import com.hippolyte.pointageapp.excel.ExcelHelper;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class FileActivity extends AppCompatActivity {

    private ActivityFileBinding vb;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        vb = ActivityFileBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        setTitle(getString(R.string.file_title));

        final File f = ExcelHelper.getWeekFile(this);
        vb.tvPath.setText(f.getAbsolutePath());
        refreshInfo(f);

        vb.btnOpen.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openFile(f);
            }
        });

        vb.btnShare.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                shareFile(f);
            }
        });
    }

    private void refreshInfo(File f) {
        boolean exists = f.exists();
        StringBuilder sb = new StringBuilder();

        if (exists) {
            String size = Formatter.formatFileSize(this, f.length());
            String mod = DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()
            ).format(new Date(f.lastModified()));
            long free = f.getParentFile().getUsableSpace();
            String freeHuman = Formatter.formatFileSize(this, free);

            boolean notifGranted = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifGranted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
            }

            sb.append("Taille : ").append(size)
                    .append("\nDernière écriture : ").append(mod)
                    .append("\nEspace libre : ").append(freeHuman)
                    .append("\nVersion app : ").append(BuildConfig.VERSION_NAME)
                    .append("\nPermission notifications : ").append(notifGranted ? "OK" : "Refusée");

            vb.btnOpen.setEnabled(true);
            vb.btnShare.setEnabled(true);
        } else {
            sb.append(getString(R.string.file_missing));
            vb.btnOpen.setEnabled(false);
            vb.btnShare.setEnabled(false);
        }
        vb.tvInfo.setText(sb.toString());
    }

    private void openFile(File f) {
        if (!f.exists()) {
            Toast.makeText(this, R.string.file_missing, Toast.LENGTH_LONG).show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(
                this,
                BuildConfig.APPLICATION_ID + ".provider",
                f
        );

        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_viewer, Toast.LENGTH_LONG).show();
        }
    }

    private void shareFile(File f) {
        if (!f.exists()) {
            Toast.makeText(this, R.string.file_missing, Toast.LENGTH_LONG).show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(
                this,
                BuildConfig.APPLICATION_ID + ".provider",
                f
        );

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // Ajout d’un ClipData pour les clients qui l’exigent (Gmail, etc.)
        share.setClipData(ClipData.newRawUri("xlsx", uri));

        startActivity(Intent.createChooser(share, getString(R.string.btn_share)));
    }
}
