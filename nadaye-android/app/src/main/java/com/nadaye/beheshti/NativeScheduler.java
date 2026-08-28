package com.nadaye.beheshti;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

public final class NativeScheduler {
    private static final String PREFS = "nadaye";
    private static final String KEY = "scheduled_alarms";
    private NativeScheduler() {}

    public static synchronized void schedule(Context context, String id, String label, long atMillis, String type, boolean vibrate) {
        if (id == null || id.isEmpty() || atMillis <= System.currentTimeMillis()) return;
        doSchedule(context, id, label, atMillis, type, vibrate);
        try {
            JSONArray old = read(context), next = new JSONArray();
            for (int i=0;i<old.length();i++) {
                JSONObject o = old.optJSONObject(i);
                if (o != null && !id.equals(o.optString("id")) && o.optLong("at") > System.currentTimeMillis()) next.put(o);
            }
            JSONObject n = new JSONObject();
            n.put("id", id); n.put("label", label); n.put("at", atMillis); n.put("type", type); n.put("vibrate", vibrate);
            next.put(n); write(context,next);
        } catch (Exception ignored) {}
    }

    private static void doSchedule(Context context, String id, String label, long atMillis, String type, boolean vibrate) {
        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, AlarmReceiver.class);
        i.setAction("com.nadaye.beheshti.ALARM." + id);
        i.putExtra("id", id); i.putExtra("label", label); i.putExtra("type", type); i.putExtra("vibrate", vibrate);
        PendingIntent pi = PendingIntent.getBroadcast(context, id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi);
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi);
        else am.setExact(AlarmManager.RTC_WAKEUP, atMillis, pi);
    }

    public static synchronized void cancelPrayerAlarms(Context context) {
        try {
            JSONArray old = read(context), keep = new JSONArray();
            for (int i=0;i<old.length();i++) {
                JSONObject o = old.optJSONObject(i); if (o == null) continue;
                if ("prayer".equals(o.optString("type"))) cancel(context,o.optString("id")); else if (o.optLong("at") > System.currentTimeMillis()) keep.put(o);
            }
            write(context,keep);
        } catch (Exception ignored) {}
    }

    public static synchronized void restoreAll(Context context) {
        try {
            JSONArray a = read(context), keep = new JSONArray();
            long now = System.currentTimeMillis();
            for (int i=0;i<a.length();i++) {
                JSONObject o = a.optJSONObject(i); if (o == null || o.optLong("at") <= now) continue;
                doSchedule(context,o.optString("id"),o.optString("label"),o.optLong("at"),o.optString("type","event"),o.optBoolean("vibrate",true));
                keep.put(o);
            }
            write(context,keep);
        } catch (Exception ignored) {}
    }

    public static synchronized void markFired(Context context, String id) {
        try {
            JSONArray a = read(context), keep = new JSONArray();
            for (int i=0;i<a.length();i++) { JSONObject o=a.optJSONObject(i); if (o!=null && !id.equals(o.optString("id")) && o.optLong("at")>System.currentTimeMillis()) keep.put(o); }
            write(context,keep);
        } catch (Exception ignored) {}
    }

    private static void cancel(Context context, String id) {
        Intent i = new Intent(context, AlarmReceiver.class); i.setAction("com.nadaye.beheshti.ALARM." + id);
        PendingIntent pi = PendingIntent.getBroadcast(context,id.hashCode(),i,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) { ((AlarmManager)context.getSystemService(Context.ALARM_SERVICE)).cancel(pi); pi.cancel(); }
    }

    private static JSONArray read(Context context) {
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        try { return new JSONArray(p.getString(KEY,"[]")); } catch (Exception e) { return new JSONArray(); }
    }
    private static void write(Context context, JSONArray a) { context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,a.toString()).apply(); }
}
