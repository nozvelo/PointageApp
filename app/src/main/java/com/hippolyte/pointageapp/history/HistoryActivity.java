package com.hippolyte.pointageapp.history;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.hippolyte.pointageapp.data.TourEntry;
import com.hippolyte.pointageapp.databinding.ActivityHistoryBinding;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private ActivityHistoryBinding binding;
    private ArrayAdapter<String> adapter;
    private final List<String> items = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                items
        );
        binding.listHistory.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAndRender();
    }

    private void loadAndRender() {
        List<TourEntry> entries = tryLoadHistory(this);

        items.clear();
        if (entries != null && !entries.isEmpty()) {
            for (TourEntry e : entries) {
                items.add(e.toString()); // s’appuie sur toString() existant
            }
            binding.listHistory.setVisibility(View.VISIBLE);
            binding.tvEmpty.setVisibility(View.GONE);
        } else {
            binding.listHistory.setVisibility(View.GONE);
            binding.tvEmpty.setVisibility(View.VISIBLE);
        }
        adapter.notifyDataSetChanged();
    }

    @SuppressWarnings("unchecked")
    private List<TourEntry> tryLoadHistory(Context ctx) {
        try {
            Class<?> store = Class.forName("com.hippolyte.pointageapp.data.HistoryStore");

            // 1) Essaye load(Context)
            try {
                Method m = store.getMethod("load", Context.class);
                Object result = m.invoke(null, ctx);
                if (result instanceof List) return (List<TourEntry>) result;
            } catch (NoSuchMethodException ignore) {}

            // 2) Essaye getAll(Context)
            try {
                Method m = store.getMethod("getAll", Context.class);
                Object result = m.invoke(null, ctx);
                if (result instanceof List) return (List<TourEntry>) result;
            } catch (NoSuchMethodException ignore) {}

        } catch (Throwable t) {
            // no-op : on tombera sur la liste vide
        }
        return Collections.emptyList();
    }
}
