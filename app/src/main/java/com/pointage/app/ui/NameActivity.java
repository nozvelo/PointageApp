package com.pointage.app.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.pointage.app.R;
import com.pointage.app.data.Prefs;
import com.pointage.app.databinding.ActivityNameBinding;

public class NameActivity extends AppCompatActivity {

    private ActivityNameBinding vb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vb = ActivityNameBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        // Pré-remplir si déjà présent
        vb.etName.setText(Prefs.getUserName(this));

        vb.btnSave.setOnClickListener(v -> {
            String name = vb.etName.getText() != null ? vb.etName.getText().toString().trim() : "";
            if (TextUtils.isEmpty(name)) {
                vb.etName.setError(getString(R.string.snack_name_required));
                return;
            }
            Prefs.setUserName(this, name);
            Toast.makeText(this, R.string.name_saved, Toast.LENGTH_SHORT).show();
            finish(); // retour à MainActivity
        });
    }
}
