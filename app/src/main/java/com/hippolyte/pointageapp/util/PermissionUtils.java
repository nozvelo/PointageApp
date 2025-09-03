package com.hippolyte.pointageapp.util;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

public final class PermissionUtils {
    private PermissionUtils(){}

    public static boolean needsPostNotifPermission(){
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    public static boolean hasPostNotif(Activity a){
        if (!needsPostNotifPermission()) return true;
        return a.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestPostNotif(Activity a, int reqCode){
        if (needsPostNotifPermission()){
            a.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, reqCode);
        }
    }
}
