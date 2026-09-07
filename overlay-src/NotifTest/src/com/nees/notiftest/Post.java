package com.nees.notiftest;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Bundle;

public class Post extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("popup", "Pop-up test", NotificationManager.IMPORTANCE_HIGH));
        String title = getIntent().getStringExtra("title");
        String text = getIntent().getStringExtra("text");
        Notification n = new Notification.Builder(this, "popup")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title != null ? title : "Pop-up test")
                .setContentText(text != null ? text : "This is how notification banners look now.")
                .build();
        nm.notify((int) System.currentTimeMillis(), n);
        finish();
    }
}
