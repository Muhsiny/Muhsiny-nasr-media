package com.nadaye.beheshti;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.File;

public class AdhanService extends Service {
    private static final String CHANNEL="nadaye_adhan";
    private MediaPlayer player;
    private PowerManager.WakeLock wakeLock;

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
            NotificationChannel c=new NotificationChannel(CHANNEL,"ندای بهشتی — اذان",NotificationManager.IMPORTANCE_HIGH);
            c.setSound(null,null); c.enableVibration(false);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        String label=intent!=null?intent.getStringExtra("label"):"نماز";
        String id=intent!=null?intent.getStringExtra("id"):"";
        if(label==null)label="نماز";
        Intent stop=new Intent(this,AlarmReceiver.class).setAction(AlarmReceiver.STOP_ACTION);
        PendingIntent stopPi=PendingIntent.getBroadcast(this,991,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent openPi=PendingIntent.getActivity(this,992,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder b=Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?new android.app.Notification.Builder(this,CHANNEL):new android.app.Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_launcher).setContentTitle("ندای بهشتی — "+label).setContentText("وقت نماز فرا رسیده است").setOngoing(true).setCategory(android.app.Notification.CATEGORY_ALARM).setContentIntent(openPi).addAction(android.R.drawable.ic_media_pause,"توقف",stopPi);
        startForeground(1201,b.build());
        try { PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE); wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Nadaye:Adhan"); wakeLock.acquire(12*60*1000L); } catch(Exception ignored) {}
        play(id);
        return START_NOT_STICKY;
    }

    private void play(String id) {
        releasePlayer();
        SharedPreferences p=getSharedPreferences("nadaye",MODE_PRIVATE);
        boolean fajr=id!=null&&id.endsWith("-fajr");
        String url=fajr?p.getString("fajr_adhan_url",""):"";
        if(url==null||url.isEmpty())url=p.getString("adhan_url","");
        if(url==null||url.isEmpty()){playBundled();return;}
        try {
            String cached=p.getString("offline_adhan_"+Integer.toHexString(url.hashCode()),"");
            player=new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            if(cached!=null&&!cached.isEmpty()&&new File(cached).isFile())player.setDataSource(cached);else player.setDataSource(url);
            player.setOnPreparedListener(MediaPlayer::start);
            player.setOnCompletionListener(mp->stopSelf());
            player.setOnErrorListener((mp,what,extra)->{playBundled();return true;});
            player.prepareAsync();
        } catch(Exception e){playBundled();}
    }

    private void playBundled() {
        releasePlayer();
        try { player=MediaPlayer.create(this,R.raw.adhan_default); if(player!=null){player.setOnCompletionListener(mp->stopSelf());player.start();} else stopSelf(); } catch(Exception e){stopSelf();}
    }

    private void releasePlayer(){try{if(player!=null){player.reset();player.release();}}catch(Exception ignored){}player=null;}
    @Override public void onDestroy(){releasePlayer();try{if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();}catch(Exception ignored){}super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
