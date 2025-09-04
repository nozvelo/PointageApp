package com.hippolyte.pointageapp.history;

import android.os.Bundle;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.hippolyte.pointageapp.R;
import com.hippolyte.pointageapp.data.HistoryStore;
import com.hippolyte.pointageapp.data.TourEntry;
import com.hippolyte.pointageapp.databinding.ActivityHistoryBinding;

import java.util.ArrayList;

public class HistoryActivity extends AppCompatActivity {

    private ActivityHistoryBinding vb;
    private ArrayAdapter<TourEntry> adapter;
    private ArrayList<TourEntry> data;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vb = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        data = HistoryStore.list(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, data);
        vb.list.setAdapter(adapter);

        vb.btnClear.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.history_clear_title)
                    .setMessage(R.string.history_clear_msg)
                    .setPositiveButton(R.string.history_clear_yes, (d, w) -> {
                        HistoryStore.clearAll(this);
                        data.clear();
                        adapter.notifyDataSetChanged();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });

        vb.btnClose.setOnClickListener(v -> finish());
    }
}
