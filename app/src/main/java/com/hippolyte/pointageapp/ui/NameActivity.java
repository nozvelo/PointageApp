package com.hippolyte.pointageapp.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hippolyte.pointageapp.data.Prefs;
import com.hippolyte.pointageapp.databinding.ActivityNameBinding;

public class NameActivity extends AppCompatActivity {

    private ActivityNameBinding vb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vb = ActivityNameBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        String existing = Prefs.getUserName(this);
        if (!TextUtils.isEmpty(existing)) {
            vb.editName.setText(existing);
        }

        vb.btnSave.setOnClickListener(v -> {
            String name = vb.editName.getText() == null ? "" : vb.editName.getText().toString().trim();
            if (name.isEmpty()) {
                vb.editName.setError("Nom obligatoire");
                return;
            }
            Prefs.setUserName(this, name);
            Toast.makeText(this, "Nom sauvegardé", Toast.LENGTH_SHORT).show();
            finish();
        });

        vb.btnCancel.setOnClickListener((View v) -> finish());
    }
}
