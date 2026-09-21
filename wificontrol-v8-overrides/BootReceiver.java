package org.sayeh.wificontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Map;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        SharedPreferences p=c.getSharedPreferences("wifi_control_v6",Context.MODE_PRIVATE);
        for(Map.Entry<String,?> e:p.getAll().entrySet()){
            String k=e.getKey(); if(!(e.getValue() instanceof String)) continue; String hh=(String)e.getValue();
            if(k.startsWith("sched_block_")&&ScheduleManager.valid(hh)) ScheduleManager.scheduleDaily(c,k.substring("sched_block_".length()),true,hh);
            else if(k.startsWith("sched_unblock_")&&ScheduleManager.valid(hh)) ScheduleManager.scheduleDaily(c,k.substring("sched_unblock_".length()),false,hh);
        }
    }
}
