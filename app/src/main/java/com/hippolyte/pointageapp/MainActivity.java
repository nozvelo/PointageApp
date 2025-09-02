package com.hippolyte.pointageapp;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.hippolyte.pointageapp.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding; // binding auto-généré

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialisation du binding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Exemple d’utilisation : changer le texte du TextView
        binding.titleText.setText("Bienvenue dans PointageApp !");
    }
}
