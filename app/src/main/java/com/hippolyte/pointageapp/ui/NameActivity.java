package com.hippolyte.pointageapp.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.hippolyte.pointageapp.databinding.ActivityNameBinding;
import com.hippolyte.pointageapp.data.Prefs;

public class NameActivity extends AppCompatActivity {

    private ActivityNameBinding vb;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        vb = ActivityNameBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        vb.etName.setText(Prefs.getUser(this));

        vb.btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String name = vb.etName.getText().toString().trim();
                if (TextUtils.isEmpty(name)) {
                    Toast.makeText(NameActivity.this, "Nom obligatoire", Toast.LENGTH_SHORT).show();
                    return;
                }
                Prefs.setUser(NameActivity.this, name);
                Toast.makeText(NameActivity.this, "Nom enregistré", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
}
