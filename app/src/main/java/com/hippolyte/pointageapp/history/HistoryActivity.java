package com.hippolyte.pointageapp.history;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.hippolyte.pointageapp.databinding.ActivityHistoryBinding;

public class HistoryActivity extends AppCompatActivity {

    private ActivityHistoryBinding vb;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        vb = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        setTitle("Historique");
        // Écran placeholder : on affichera la vraie liste en v1.0
        vb.tvHistoryInfo.setText("Historique en cours de préparation.\n"
                + "Les tournées seront listées ici dans une prochaine version.");
    }
}
