package com.nadaye.beheshti;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;

import java.io.File;

public class AdhanService extends Service {
    static final String CHANNEL="nadaye_adhan";
    static final String STOP="com.nadaye.beheshti.STOP_ADHAN";
    static final String[] BUILTIN_URLS={
            "https://shiavoice.com/stream-0wonG",
            "https://shiavoice.com/stream-j7q3b",
            "https://shiavoice.com/stream-7Lntd",
            "https://shiavoice.com/stream-EPfRi"
    };

    MediaPlayer player;
    AudioManager audio;
    int oldAlarmVolume=-1;
    boolean fallbackTried=false;

    public static void start(Context c,String label){
        Intent i=new Intent(c,AdhanService.class);i.putExtra("label",label);
        if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);
    }

    @Override public void onCreate(){
        super.onCreate();
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel(CHANNEL,"اذان ندای بهشتی",NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("اعلان و پخش اذان در وقت نماز");
            ch.enableVibration(true);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i!=null&&STOP.equals(i.getAction())){stopNow();return START_NOT_STICKY;}
        String label=i==null?"وقت نماز":i.getStringExtra("label");if(label==null)label="وقت نماز";
        Intent si=new Intent(this,AdhanService.class);si.setAction(STOP);
        PendingIntent stop=PendingIntent.getService(this,991,si,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setSmallIcon(com.nadaye.beheshti.R.drawable.ic_launcher)
                .setContentTitle("ندای بهشتی — "+label)
                .setContentText("اذان در حال پخش است")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause,"توقف",stop);
        startForeground(2001,b.build());
        boostAlarmVolume();
        playSelected();
        new Handler(Looper.getMainLooper()).postDelayed(this::stopNow,7*60*1000L);
        return START_NOT_STICKY;
    }

    void boostAlarmVolume(){
        try{
            if(audio==null)return;
            oldAlarmVolume=audio.getStreamVolume(AudioManager.STREAM_ALARM);
            int max=audio.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            audio.setStreamVolume(AudioManager.STREAM_ALARM,max,0);
            audio.requestAudioFocus(null,AudioManager.STREAM_ALARM,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }catch(Exception ignored){}
    }

    void restoreAlarmVolume(){
        try{
            if(audio!=null){
                if(oldAlarmVolume>=0)audio.setStreamVolume(AudioManager.STREAM_ALARM,oldAlarmVolume,0);
                audio.abandonAudioFocus(null);
            }
        }catch(Exception ignored){}
        oldAlarmVolume=-1;
    }

    Uri selectedUri(){
        SharedPreferences p=getSharedPreferences("nadaye",MODE_PRIVATE);
        int selected=p.getInt("adhan.selected",1);
        if(selected>=0&&selected<BUILTIN_URLS.length){
            File cached=new File(getFilesDir(),"adhan_builtin_"+selected+".audio");
            if(cached.exists()&&cached.length()>100000)return Uri.fromFile(cached);
            return Uri.parse(BUILTIN_URLS[selected]);
        }
        int slot=selected-BUILTIN_URLS.length+1;
        if(slot>=1&&slot<=8){
            String custom=p.getString("adhan.slot."+slot+".uri","");
            if(!custom.isEmpty())return Uri.parse(custom);
        }
        return null;
    }

    void playSelected(){
        fallbackTried=false;
        Uri u=selectedUri();
        if(u==null){playFallback();return;}
        startUri(u,false);
    }

    void startUri(Uri u,boolean fallback){
        try{
            if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}
            player=new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setVolume(1f,1f);
            player.setDataSource(this,u);
            player.setOnPreparedListener(MediaPlayer::start);
            player.setOnCompletionListener(mp->stopNow());
            player.setOnErrorListener((mp,what,extra)->{
                if(!fallback&&!fallbackTried){fallbackTried=true;new Handler(Looper.getMainLooper()).post(this::playFallback);}else stopNow();
                return true;
            });
            player.prepareAsync();
        }catch(Exception e){
            if(!fallback&&!fallbackTried){fallbackTried=true;playFallback();}else stopNow();
        }
    }

    void playFallback(){
        try{
            Uri u=Uri.parse("android.resource://"+getPackageName()+"/"+com.nadaye.beheshti.R.raw.default_adhan);
            startUri(u,true);
        }catch(Exception e){stopNow();}
    }

    void stopNow(){
        try{if(player!=null){if(player.isPlaying())player.stop();player.release();player=null;}}catch(Exception ignored){}
        restoreAlarmVolume();
        try{stopForeground(true);}catch(Exception ignored){}
        stopSelf();
    }

    @Override public void onDestroy(){
        try{if(player!=null){if(player.isPlaying())player.stop();player.release();player=null;}}catch(Exception ignored){}
        restoreAlarmVolume();
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent i){return null;}
}
