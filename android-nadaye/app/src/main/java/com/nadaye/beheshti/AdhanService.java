package com.nadaye.beheshti;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;

public class AdhanService extends Service {
    static final String CHANNEL="nadaye_adhan"; static final String STOP="com.nadaye.beheshti.STOP_ADHAN"; MediaPlayer player;
    public static void start(Context c,String label){Intent i=new Intent(c,AdhanService.class);i.putExtra("label",label);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}
    @Override public void onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel(CHANNEL,"اذان ندای بهشتی",NotificationManager.IMPORTANCE_HIGH);ch.setDescription("اعلان و پخش اذان در وقت نماز");ch.enableVibration(true);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);}}
    @Override public int onStartCommand(Intent i,int flags,int id){if(i!=null&&STOP.equals(i.getAction())){stopNow();return START_NOT_STICKY;}String label=i==null?"وقت نماز":i.getStringExtra("label");if(label==null)label="وقت نماز";Intent si=new Intent(this,AdhanService.class);si.setAction(STOP);PendingIntent stop=PendingIntent.getService(this,991,si,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);b.setSmallIcon(com.nadaye.beheshti.R.drawable.ic_launcher).setContentTitle("ندای بهشتی — "+label).setContentText("وقت نماز فرا رسیده است").setOngoing(true).addAction(android.R.drawable.ic_media_pause,"توقف",stop);startForeground(2001,b.build());play();new Handler(Looper.getMainLooper()).postDelayed(this::stopNow,5*60*1000L);return START_NOT_STICKY;}
    void play(){try{if(player!=null){player.release();player=null;}String saved=getSharedPreferences("nadaye",MODE_PRIVATE).getString("adhan_uri","");Uri u=saved.isEmpty()?RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM):Uri.parse(saved);player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());player.setDataSource(this,u);player.setOnPreparedListener(MediaPlayer::start);player.setOnCompletionListener(mp->stopNow());player.prepareAsync();}catch(Exception e){stopForeground(false);}}
    void stopNow(){try{if(player!=null){if(player.isPlaying())player.stop();player.release();player=null;}}catch(Exception ignored){}stopForeground(true);stopSelf();}
    @Override public void onDestroy(){stopNow();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
