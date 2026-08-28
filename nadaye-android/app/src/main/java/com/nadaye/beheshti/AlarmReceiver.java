package com.nadaye.beheshti;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class AlarmReceiver extends BroadcastReceiver {
    public static final String STOP_ACTION = "com.nadaye.beheshti.STOP_ADHAN";
    private static final String CHANNEL = "nadaye_alerts";

    @Override public void onReceive(Context context, Intent intent) {
        if (STOP_ACTION.equals(intent.getAction())) {
            context.stopService(new Intent(context, AdhanService.class));
            return;
        }
        String id = intent.getStringExtra("id");
        String label = intent.getStringExtra("label");
        String type = intent.getStringExtra("type");
        boolean vibrate = intent.getBooleanExtra("vibrate", true);
        if (id == null) id = String.valueOf(System.currentTimeMillis());
        if (label == null || label.isEmpty()) label = "یادآور ندای بهشتی";
        NativeScheduler.markFired(context,id);
        if ("prayer".equals(type)) {
            Intent s = new Intent(context, AdhanService.class);
            s.putExtra("id",id); s.putExtra("label",label);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(s); else context.startService(s);
        } else showNotification(context,label,"یادآور ندای بهشتی",id.hashCode(),vibrate);
    }

    public static void showNotification(Context context, String title, String body, int id, boolean vibrate) {
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c=new NotificationChannel(CHANNEL,"ندای بهشتی — اعلان‌ها",NotificationManager.IMPORTANCE_HIGH);
            c.enableVibration(true); nm.createNotificationChannel(c);
        }
        Intent open=new Intent(context,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(context,77,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder b=Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?new android.app.Notification.Builder(context,CHANNEL):new android.app.Notification.Builder(context);
        b.setSmallIcon(R.drawable.ic_launcher).setContentTitle(title).setContentText(body).setAutoCancel(true).setContentIntent(pi).setCategory(android.app.Notification.CATEGORY_REMINDER);
        nm.notify(id,b.build());
        if (vibrate) try { Vibrator v=(Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE); if (Build.VERSION.SDK_INT>=26) v.vibrate(VibrationEffect.createOneShot(700,VibrationEffect.DEFAULT_AMPLITUDE)); else v.vibrate(700); } catch(Exception ignored) {}
    }
}
