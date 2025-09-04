package com.hippolyte.pointageapp.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.app.NotificationManagerCompat;

public class BootReceiver extends BroadcastReceiver {

    private static final String PREFS = "pointage_prefs";
    private static final String KEY_START_AT = "startAt";
    private static final String KEY_NOTIF_CHANNEL_READY = "notif_channel_ready"; // si tu l’utilises

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long startAt = sp.getLong(KEY_START_AT, 0L);
        if (startAt <= 0L) return; // pas de tournée en cours → rien à faire

        // Vérifier les notifications (Android 13+)
        NotificationManagerCompat nmc = NotificationManagerCompat.from(context);
        if (!nmc.areNotificationsEnabled()) {
            // L’utilisateur a désactivé les notifs → on ne spam pas,
            // la tournée reste en cours et l’app réaffichera le chrono en premier-plan.
            return;
        }

        // Relancer la notification persistante avec chronomètre
        // On délègue à une petite Activity utilitaire ou un helper statique si déjà dans le projet.
        // Ici, on envoie un intent explicite vers ta MainActivity pour qu’elle restaure la notif.
        try {
            Class<?> main = Class.forName("com.hippolyte.pointageapp.ui.MainActivity");
            Intent reopen = new Intent(context, main);
            reopen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("restore_notification_only", true);
            context.startActivity(reopen);
        } catch (ClassNotFoundException ignored) {
            // Si le nom du package/chemin diffère, adapte la classe cible.
        }
    }
}
