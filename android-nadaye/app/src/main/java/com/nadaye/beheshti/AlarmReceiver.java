package com.nadaye.beheshti;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.util.*;

public class AlarmReceiver extends BroadcastReceiver {
    static final String[] LABELS={"فجر","ظهر","مغرب","عشاء"};
    static final int[] IDS={1101,1102,1103,1104};

    @Override public void onReceive(Context c, Intent i){
        if(!c.getSharedPreferences("nadaye",Context.MODE_PRIVATE).getBoolean("notifications",true))return;
        String label=i.getStringExtra("label"); if(label==null)label="وقت نماز";
        AdhanService.start(c,label);
        scheduleTomorrowOne(c,label);
    }

    public static void scheduleToday(Context c){
        if(!c.getSharedPreferences("nadaye",Context.MODE_PRIVATE).getBoolean("notifications",true))return;
        double lat=Double.longBitsToDouble(c.getSharedPreferences("nadaye",Context.MODE_PRIVATE).getLong("lat",Double.doubleToLongBits(34.5553)));
        double lon=Double.longBitsToDouble(c.getSharedPreferences("nadaye",Context.MODE_PRIVATE).getLong("lon",Double.doubleToLongBits(69.2075)));
        PrayerTimes.Result r=PrayerTimes.calculate(System.currentTimeMillis(),lat,lon); long now=System.currentTimeMillis();
        for(int n=0;n<LABELS.length;n++){Long when=r.millis.get(LABELS[n]);if(when!=null&&when>now)schedule(c,LABELS[n],when,IDS[n]);else scheduleTomorrowOne(c,LABELS[n]);}
    }

    static void scheduleTomorrowOne(Context c,String label){
        Calendar cal=Calendar.getInstance();cal.add(Calendar.DAY_OF_YEAR,1);
        double lat=Double.longBitsToDouble(c.getSharedPreferences("nadaye",Context.MODE_PRIVATE).getLong("lat",Double.doubleToLongBits(34.5553)));
        double lon=Double.longBitsToDouble(c.getSharedPreferences("nadaye",Context.MODE_PRIVATE).getLong("lon",Double.doubleToLongBits(69.2075)));
        PrayerTimes.Result r=PrayerTimes.calculate(cal.getTimeInMillis(),lat,lon);Long when=r.millis.get(label);if(when!=null)schedule(c,label,when,idFor(label));
    }

    static int idFor(String label){for(int i=0;i<LABELS.length;i++)if(LABELS[i].equals(label))return IDS[i];return 1199;}
    static void schedule(Context c,String label,long when,int id){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);Intent in=new Intent(c,AlarmReceiver.class);in.putExtra("label",label);PendingIntent pi=PendingIntent.getBroadcast(c,id,in,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        try{if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);else am.setExact(AlarmManager.RTC_WAKEUP,when,pi);}catch(SecurityException e){if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);else am.set(AlarmManager.RTC_WAKEUP,when,pi);}
    }
    public static void cancelAll(Context c){AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);for(int id:IDS){PendingIntent pi=PendingIntent.getBroadcast(c,id,new Intent(c,AlarmReceiver.class),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);if(pi!=null)am.cancel(pi);}}
}
