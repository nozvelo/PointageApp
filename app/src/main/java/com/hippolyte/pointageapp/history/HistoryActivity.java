package com.hippolyte.pointageapp.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hippolyte.pointageapp.R;
import com.hippolyte.pointageapp.databinding.ActivityHistoryBinding;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private ActivityHistoryBinding vb;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vb = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        setTitle(getString(R.string.history));

        List<HistoryEntry> entries = HistoryManager.load(this);

        vb.recycler.setLayoutManager(new LinearLayoutManager(this));
        vb.recycler.addItemDecoration(new DividerItemDecoration(this, RecyclerView.VERTICAL));
        vb.recycler.setAdapter(new HistoryAdapter(entries));
    }

    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryVH> {
        private final List<HistoryEntry> list;
        HistoryAdapter(List<HistoryEntry> list) { this.list = list; }

        @NonNull @Override
        public HistoryVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new HistoryVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull HistoryVH h, int position) {
            HistoryEntry e = list.get(position);
            DateTimeFormatter d = DateTimeFormatter.ofPattern("EEE dd MMM yyyy", Locale.getDefault());
            DateTimeFormatter t = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());

            String date = e.startLocal().format(d);
            String start = e.startLocal().format(t);
            String end   = e.endLocal().format(t);
            String slot  = (e.slot == null || e.slot.isEmpty()) ? "" : " • " + e.slot;

            h.title.setText(date + slot);
            h.subtitle.setText(start + " → " + end);
        }

        @Override public int getItemCount() { return list == null ? 0 : list.size(); }
    }

    private static class HistoryVH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        HistoryVH(@NonNull View v) {
            super(v);
            title = v.findViewById(android.R.id.text1);
            subtitle = v.findViewById(android.R.id.text2);
        }
    }
}
