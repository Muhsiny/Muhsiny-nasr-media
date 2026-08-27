package com.nadaye.beheshti;
import android.content.*;
public class BootReceiver extends BroadcastReceiver { @Override public void onReceive(Context c, Intent i){ AlarmReceiver.scheduleToday(c); } }
