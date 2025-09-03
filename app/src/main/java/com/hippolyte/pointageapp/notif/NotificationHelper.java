package com.hippolyte.pointageapp.notif;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.hippolyte.pointageapp.R;

public final class NotificationHelper {
    public static final String CHANNEL_ID_TIMER = "pointage_timer";
    public static final int NOTIF_ID_TIMER = 1001;

    private NotificationHelper(){}

    public static void createChannels(Context ctx){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID_TIMER,
                    "Chronomètre de tournée",
                    NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("Affiche la durée de la tournée en cours");
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(ch);
        }
    }

    public static Notification buildRunningNotification(Context ctx, long startAt){
        return new NotificationCompat.Builder(ctx, CHANNEL_ID_TIMER)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle("Tournée en cours")
                .setContentText("Chronomètre actif")
                .setWhen(startAt)
                .setOngoing(true)
                .setUsesChronometer(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    public static void notifyTimer(Context ctx, long startAt){
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID_TIMER, buildRunningNotification(ctx, startAt));
    }

    public static void cancelTimer(Context ctx){
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(NOTIF_ID_TIMER);
    }
}
