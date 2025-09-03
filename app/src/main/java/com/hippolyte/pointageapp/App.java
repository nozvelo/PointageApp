package com.hippolyte.pointageapp;

import android.app.Application;
import com.hippolyte.pointageapp.notif.NotificationHelper;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannels(this);
    }
}
