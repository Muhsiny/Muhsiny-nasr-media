package org.sayeh.wificontrol;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

public final class ScheduleManager {
    private ScheduleManager(){}

    public static boolean valid(String hhmm){
        if(hhmm==null||!hhmm.matches("^(?:[01]\\d|2[0-3]):[0-5]\\d$")) return false;
        return true;
    }

    public static void scheduleDaily(Context c,String mac,boolean block,String hhmm){
        if(!valid(hhmm)) return;
        String[] x=hhmm.split(":"); Calendar cal=Calendar.getInstance(); cal.set(Calendar.HOUR_OF_DAY,Integer.parseInt(x[0])); cal.set(Calendar.MINUTE,Integer.parseInt(x[1])); cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0);
        if(cal.getTimeInMillis()<=System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR,1);
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE); PendingIntent pi=pending(c,mac,block);
        if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,cal.getTimeInMillis(),pi);
        else if(Build.VERSION.SDK_INT>=23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,cal.getTimeInMillis(),pi);
        else am.setExact(AlarmManager.RTC_WAKEUP,cal.getTimeInMillis(),pi);
    }

    public static void cancel(Context c,String mac,boolean block){ AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE); am.cancel(pending(c,mac,block)); }

    private static PendingIntent pending(Context c,String mac,boolean block){
        Intent i=new Intent(c,ScheduleReceiver.class); i.setAction((block?"BLOCK:":"UNBLOCK:")+mac); i.putExtra("mac",mac); i.putExtra("block",block);
        int rc=(mac+(block?"B":"U")).hashCode()&0x7fffffff;
        return PendingIntent.getBroadcast(c,rc,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
}
