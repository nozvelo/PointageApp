package com.hippolyte.pointageapp.history;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.hippolyte.pointageapp.R;

public class HistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        setTitle("Historique");

        // Pour l’instant écran placeholder (texte simple)
        // TODO v1.2 : brancher la vraie liste JSON des tournées avec RecyclerView
    }
}
