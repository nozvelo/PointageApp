package com.hippolyte.pointageapp.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.hippolyte.pointageapp.data.HistoryManager;
import com.hippolyte.pointageapp.databinding.ActivityHistoryBinding;
import com.hippolyte.pointageapp.model.HistoryEntry;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private ActivityHistoryBinding vb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vb = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        List<HistoryEntry> entries = HistoryManager.load(this);
        vb.recycler.setLayoutManager(new LinearLayoutManager(this));
        vb.recycler.setAdapter(new HistoryAdapter(entries));
    }

    // Adapter interne
    private static class HistoryAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<HistoryViewHolder> {
        private final List<HistoryEntry> list;

        HistoryAdapter(List<HistoryEntry> list){ this.list = list; }

        @Override
        public HistoryViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new HistoryViewHolder(v);
        }

        @Override
        public void onBindViewHolder(HistoryViewHolder h, int pos) {
            HistoryEntry e = list.get(pos);
            String title = e.getDate() + " - " + e.getSlot();
            long mins = e.getDurationMs() / 60000L;
            String subtitle = formatTime(e.getStartAt()) + " → " + formatTime(e.getEndAt()) + " (" + mins + " min)";
            h.bind(title, subtitle);
        }

        @Override
        public int getItemCount() { return list.size(); }

        private String formatTime(long ms){
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms));
        }
    }

    private static class HistoryViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        private final android.widget.TextView t1;
        private final android.widget.TextView t2;

        HistoryViewHolder(android.view.View itemView){
            super(itemView);
            t1 = itemView.findViewById(android.R.id.text1);
            t2 = itemView.findViewById(android.R.id.text2);
        }

        void bind(String title, String subtitle){
            t1.setText(title);
            t2.setText(subtitle);
        }
    }
}
