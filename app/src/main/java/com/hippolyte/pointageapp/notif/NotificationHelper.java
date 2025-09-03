package com.hippolyte.pointageapp.notif;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.hippolyte.pointageapp.App;
import com.hippolyte.pointageapp.R;
import com.hippolyte.pointageapp.ui.MainActivity;

public class NotificationHelper {

    public static Notification buildOngoing(Context ctx, long startAt) {
        Intent i = new Intent(ctx, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                ctx, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(ctx, App.CHANNEL_ID)
                .setContentTitle(ctx.getString(R.string.notif_ongoing_title))
                .setContentText(ctx.getString(R.string.notif_ongoing_text))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true)
                .setWhen(startAt)
                .setUsesChronometer(true)
                .build();
    }
}
