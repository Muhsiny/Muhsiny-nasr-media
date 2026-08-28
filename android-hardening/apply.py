from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path('android-nadaye')
APP = ROOT / 'app'
JAVA = APP / 'src/main/java/com/nadaye/beheshti'
ANDROID_NS = 'http://schemas.android.com/apk/res/android'
A = '{%s}' % ANDROID_NS
ET.register_namespace('android', ANDROID_NS)

# Final release identity.
gradle = APP / 'build.gradle'
s = gradle.read_text(encoding='utf-8')
s = re.sub(r'versionCode\s+\d+', 'versionCode 7', s)
s = re.sub(r"versionName\s+['\"][^'\"]+['\"]", "versionName '4.2.0'", s)
gradle.write_text(s, encoding='utf-8')

# Manifest privacy + resilient rescheduling.
manifest = APP / 'src/main/AndroidManifest.xml'
tree = ET.parse(manifest)
root = tree.getroot()
perms = {x.get(A+'name') for x in root.findall('uses-permission')}
for p in ['android.permission.WAKE_LOCK', 'android.permission.ACCESS_NETWORK_STATE']:
    if p not in perms:
        el = ET.Element('uses-permission'); el.set(A+'name', p); root.insert(0, el)
application = root.find('application')
if application is None: raise SystemExit('application element missing')
application.set(A+'allowBackup', 'false')
application.set(A+'fullBackupContent', 'false')
application.set(A+'icon', '@mipmap/ic_launcher')
application.set(A+'roundIcon', '@mipmap/ic_launcher_round')

boot = None
for r in application.findall('receiver'):
    if r.get(A+'name') in ('.BootReceiver', 'com.nadaye.beheshti.BootReceiver'):
        boot = r; break
if boot is None:
    boot = ET.SubElement(application, 'receiver'); boot.set(A+'name', '.BootReceiver'); boot.set(A+'exported', 'true')
intent = boot.find('intent-filter')
if intent is None: intent = ET.SubElement(boot, 'intent-filter')
actions = {x.get(A+'name') for x in intent.findall('action')}
for action in ['android.intent.action.BOOT_COMPLETED','android.intent.action.TIME_SET','android.intent.action.TIMEZONE_CHANGED','android.intent.action.DATE_CHANGED','android.intent.action.MY_PACKAGE_REPLACED']:
    if action not in actions:
        x = ET.SubElement(intent, 'action'); x.set(A+'name', action)
manifest.write_text(ET.tostring(root, encoding='unicode'), encoding='utf-8')

# Professionalized Ja'fari prayer engine: NOAA solar model + high-latitude correction.
prayer = r'''package com.nadaye.beheshti;

import java.text.SimpleDateFormat;
import java.util.*;

public final class PrayerTimes {
    private PrayerTimes() {}
    public static final class Result {
        public final LinkedHashMap<String, Long> millis = new LinkedHashMap<>();
        public String get(String key){ Long v=millis.get(key); return v==null?"--:--":fmt(v); }
    }
    public static Result calculate(long dayMillis,double latitude,double longitude){
        Calendar c=Calendar.getInstance(); c.setTimeInMillis(dayMillis);
        c.set(Calendar.HOUR_OF_DAY,12);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
        int n=c.get(Calendar.DAY_OF_YEAR);
        double gamma=2.0*Math.PI/365.0*(n-1);
        double eq=229.18*(0.000075+0.001868*Math.cos(gamma)-0.032077*Math.sin(gamma)-0.014615*Math.cos(2*gamma)-0.040849*Math.sin(2*gamma));
        double decl=0.006918-0.399912*Math.cos(gamma)+0.070257*Math.sin(gamma)-0.006758*Math.cos(2*gamma)+0.000907*Math.sin(2*gamma)-0.002697*Math.cos(3*gamma)+0.00148*Math.sin(3*gamma);
        double tz=TimeZone.getDefault().getOffset(c.getTimeInMillis())/3600000.0;
        double noon=720.0-4.0*longitude-eq+tz*60.0;
        double rise=noon-angleMinutes(latitude,decl,-0.833);
        double set=noon+angleMinutes(latitude,decl,-0.833);
        double fajrRaw=noon-angleMinutes(latitude,decl,-16.0);
        double maghribRaw=noon+angleMinutes(latitude,decl,-4.0);
        double ishaRaw=noon+angleMinutes(latitude,decl,-14.0);
        double night=(24*60.0-set)+rise;
        if(!Double.isFinite(rise)||!Double.isFinite(set)||night<1){ rise=noon-360; set=noon+360; night=720; }
        double fajr=adjustBefore(rise,fajrRaw,night,16.0);
        double maghrib=adjustAfter(set,maghribRaw,night,4.0);
        double isha=adjustAfter(set,ishaRaw,night,14.0);
        Result r=new Result();
        r.millis.put("فجر",minuteToMillis(c,fajr));
        r.millis.put("طلوع",minuteToMillis(c,rise));
        r.millis.put("ظهر",minuteToMillis(c,noon));
        r.millis.put("مغرب",minuteToMillis(c,maghrib));
        r.millis.put("عشاء",minuteToMillis(c,isha));
        return r;
    }
    private static double adjustBefore(double sunrise,double raw,double night,double angle){
        double limit=night*angle/60.0;
        if(!Double.isFinite(raw)||sunrise-raw>limit) return sunrise-limit;
        return raw;
    }
    private static double adjustAfter(double sunset,double raw,double night,double angle){
        double limit=night*angle/60.0;
        if(!Double.isFinite(raw)||raw-sunset>limit) return sunset+limit;
        return raw;
    }
    private static double angleMinutes(double latDeg,double declRad,double altitudeDeg){
        double lat=Math.toRadians(Math.max(-89.8,Math.min(89.8,latDeg)));
        double alt=Math.toRadians(altitudeDeg);
        double den=Math.cos(lat)*Math.cos(declRad);
        if(Math.abs(den)<1e-12) return Double.NaN;
        double cosH=(Math.sin(alt)-Math.sin(lat)*Math.sin(declRad))/den;
        if(cosH < -1.0 || cosH > 1.0) return Double.NaN;
        return Math.toDegrees(Math.acos(cosH))*4.0;
    }
    private static long minuteToMillis(Calendar base,double minute){
        Calendar d=(Calendar)base.clone();d.set(Calendar.HOUR_OF_DAY,0);d.set(Calendar.MINUTE,0);d.set(Calendar.SECOND,0);d.set(Calendar.MILLISECOND,0);
        return d.getTimeInMillis()+Math.round(minute*60000.0);
    }
    public static String fmt(long millis){return new SimpleDateFormat("HH:mm",new Locale("fa")).format(new Date(millis));}
    public static String nextPrayer(Result r,long now){
        for(Map.Entry<String,Long> e:r.millis.entrySet()){
            if("طلوع".equals(e.getKey())) continue;
            if(e.getValue()>now) return e.getKey()+"  "+fmt(e.getValue());
        }
        Calendar t=Calendar.getInstance();t.setTimeInMillis(now);t.add(Calendar.DAY_OF_YEAR,1);
        Result tomorrow=calculate(t.getTimeInMillis(),0,0);
        return "فجر فردا";
    }
}
'''
(JAVA/'PrayerTimes.java').write_text(prayer,encoding='utf-8')

# Alarm engine: always schedules today + tomorrow; each alarm self-heals the next horizon.
alarm = r'''package com.nadaye.beheshti;
import android.app.*;import android.content.*;import android.os.Build;import java.util.*;
public class AlarmReceiver extends BroadcastReceiver {
    static final String[] LABELS={"فجر","ظهر","مغرب","عشاء"};
    @Override public void onReceive(Context c,Intent i){
        if(!c.getSharedPreferences("nadaye",Context.MODE_PRIVATE).getBoolean("notifications",true))return;
        String label=i==null?null:i.getStringExtra("label"); if(label==null)label="وقت نماز";
        AdhanService.start(c,label);
        scheduleToday(c);
    }
    public static void scheduleToday(Context c){
        if(!c.getSharedPreferences("nadaye",Context.MODE_PRIVATE).getBoolean("notifications",true))return;
        double lat=Double.longBitsToDouble(c.getSharedPreferences("nadaye",Context.MODE_PRIVATE).getLong("lat",Double.doubleToLongBits(34.5553)));
        double lon=Double.longBitsToDouble(c.getSharedPreferences("nadaye",Context.MODE_PRIVATE).getLong("lon",Double.doubleToLongBits(69.2075)));
        long now=System.currentTimeMillis();
        Calendar day=Calendar.getInstance();
        for(int d=0;d<2;d++){
            Calendar x=(Calendar)day.clone();x.add(Calendar.DAY_OF_YEAR,d);
            PrayerTimes.Result r=PrayerTimes.calculate(x.getTimeInMillis(),lat,lon);
            int y=x.get(Calendar.YEAR), doy=x.get(Calendar.DAY_OF_YEAR);
            for(int n=0;n<LABELS.length;n++){
                Long when=r.millis.get(LABELS[n]); if(when!=null&&when>now) schedule(c,LABELS[n],when,requestCode(y,doy,n));
            }
        }
    }
    static int requestCode(int year,int day,int slot){return Math.abs((year%100)*10000+day*10+slot);}
    static void schedule(Context c,String label,long when,int id){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am==null)return;
        Intent in=new Intent(c,AlarmReceiver.class).putExtra("label",label);
        PendingIntent pi=PendingIntent.getBroadcast(c,id,in,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        try{
            if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
            else if(Build.VERSION.SDK_INT>=23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
            else am.setExact(AlarmManager.RTC_WAKEUP,when,pi);
        }catch(Exception e){ try{if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);else am.set(AlarmManager.RTC_WAKEUP,when,pi);}catch(Exception ignored){} }
    }
    public static void cancelAll(Context c){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am==null)return;
        Calendar day=Calendar.getInstance();
        for(int d=-1;d<=2;d++){Calendar x=(Calendar)day.clone();x.add(Calendar.DAY_OF_YEAR,d);for(int n=0;n<LABELS.length;n++){
            PendingIntent pi=PendingIntent.getBroadcast(c,requestCode(x.get(Calendar.YEAR),x.get(Calendar.DAY_OF_YEAR),n),new Intent(c,AlarmReceiver.class),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);if(pi!=null)am.cancel(pi);
        }}
    }
}
'''
(JAVA/'AlarmReceiver.java').write_text(alarm,encoding='utf-8')

bootjava = r'''package com.nadaye.beheshti;import android.content.*;public class BootReceiver extends BroadcastReceiver{ @Override public void onReceive(Context c,Intent i){ AlarmReceiver.scheduleToday(c); }}'''
(JAVA/'BootReceiver.java').write_text(bootjava,encoding='utf-8')

# Adhan service: selected URI/stream when configured, guaranteed bundled offline adhan otherwise.
adhan = r'''package com.nadaye.beheshti;
import android.app.*;import android.content.*;import android.media.*;import android.net.Uri;import android.os.*;
public class AdhanService extends Service{
    static final String CHANNEL="nadaye_adhan",STOP="com.nadaye.beheshti.STOP_ADHAN";MediaPlayer player;
    public static void start(Context c,String label){Intent i=new Intent(c,AdhanService.class).putExtra("label",label);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}
    @Override public void onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel(CHANNEL,"اذان ندای بهشتی",NotificationManager.IMPORTANCE_HIGH);ch.setDescription("اعلان و پخش اذان در وقت نماز");ch.enableVibration(true);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);}}
    @Override public int onStartCommand(Intent i,int flags,int id){if(i!=null&&STOP.equals(i.getAction())){stopNow();return START_NOT_STICKY;}String label=i==null?"وقت نماز":i.getStringExtra("label");if(label==null)label="وقت نماز";Intent si=new Intent(this,AdhanService.class).setAction(STOP);PendingIntent stop=PendingIntent.getService(this,991,si,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);b.setSmallIcon(R.drawable.ic_launcher).setContentTitle("ندای بهشتی — "+label).setContentText("وقت نماز فرا رسیده است").setOngoing(true).addAction(android.R.drawable.ic_media_pause,"توقف",stop);startForeground(2001,b.build());play();new Handler(Looper.getMainLooper()).postDelayed(this::stopNow,6*60*1000L);return START_NOT_STICKY;}
    void play(){try{if(player!=null){player.release();player=null;}String saved=getSharedPreferences("nadaye",MODE_PRIVATE).getString("adhan_uri","");Uri u=saved==null||saved.trim().isEmpty()?Uri.parse("android.resource://"+getPackageName()+"/"+R.raw.default_adhan):Uri.parse(saved);player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());player.setDataSource(this,u);player.setOnPreparedListener(MediaPlayer::start);player.setOnCompletionListener(mp->stopNow());player.setOnErrorListener((mp,w,e)->{stopNow();return true;});player.prepareAsync();}catch(Exception e){try{player=MediaPlayer.create(this,R.raw.default_adhan);if(player!=null){player.setOnCompletionListener(mp->stopNow());player.start();}else stopNow();}catch(Exception ignored){stopNow();}}}
    void stopNow(){try{if(player!=null){if(player.isPlaying())player.stop();player.release();player=null;}}catch(Exception ignored){}stopForeground(true);stopSelf();}
    @Override public void onDestroy(){try{if(player!=null){player.release();player=null;}}catch(Exception ignored){}super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
'''
(JAVA/'AdhanService.java').write_text(adhan,encoding='utf-8')

# MainActivity hardening: exact alarm access + fresh GPS method replacement.
main=JAVA/'MainActivity.java';m=main.read_text(encoding='utf-8')
if 'void ensureExactAlarmAccess()' not in m:
    helper='''\n    void ensureExactAlarmAccess(){ if(Build.VERSION.SDK_INT<31)return; try{ AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE); if(am!=null&&!am.canScheduleExactAlarms()) startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()))); }catch(Exception ignored){} }\n\n'''
    marker='    void immersive(boolean yes)';m=m.replace(marker,helper+marker,1) if marker in m else m
prefix=m.split('void ensureExactAlarmAccess()',1)[0]
if 'ensureExactAlarmAccess();' not in prefix:
    m,n=re.subn(r'(super\.onCreate\s*\([^;]+;)',r'\1\n        ensureExactAlarmAccess();\n        try { AlarmReceiver.scheduleToday(this); } catch (Exception ignored) { }',m,count=1)
    if n==0: raise SystemExit('could not locate super.onCreate')

def replace_method(src, signature, replacement):
    p=src.find(signature)
    if p<0:return src,False
    b=src.find('{',p); depth=0
    for i in range(b,len(src)):
        if src[i]=='{':depth+=1
        elif src[i]=='}':
            depth-=1
            if depth==0:return src[:p]+replacement+src[i+1:],True
    return src,False
fresh=r'''void refreshLocation(boolean toast) {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
        try{
            LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
            LocationListener once=new LocationListener(){
                @Override public void onLocationChanged(Location l){ if(l!=null){prefs.edit().putLong("lat",Double.doubleToLongBits(l.getLatitude())).putLong("lon",Double.doubleToLongBits(l.getLongitude())).apply();AlarmReceiver.cancelAll(MainActivity.this);AlarmReceiver.scheduleToday(MainActivity.this);if(toast)toast("موقعیت تازه شد");try{lm.removeUpdates(this);}catch(Exception ignored){}} }
                @Override public void onProviderEnabled(String p){} @Override public void onProviderDisabled(String p){} @Override public void onStatusChanged(String p,int s,Bundle e){}
            };
            String provider=lm.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER;
            lm.requestSingleUpdate(provider,once,Looper.getMainLooper());
            Location best=null;for(String p:lm.getProviders(true)){Location l=lm.getLastKnownLocation(p);if(l!=null&&(best==null||l.getTime()>best.getTime()))best=l;}
            if(best!=null&&System.currentTimeMillis()-best.getTime()<6*60*60*1000L){prefs.edit().putLong("lat",Double.doubleToLongBits(best.getLatitude())).putLong("lon",Double.doubleToLongBits(best.getLongitude())).apply();}
            else if(toast)toast("در حال دریافت موقعیت تازه…");
        }catch(Exception e){if(toast)toast("دسترسی موقعیت ممکن نشد");}
    }'''
m,_=replace_method(m,'void refreshLocation(boolean toast)',fresh)
m=(m.replace('نسخه ۴٫۱','نسخه ۴٫۲').replace('نسخه ۴.۱','نسخه ۴.۲').replace('نسخه ۴ •','نسخه ۴.۲ •'))
main.write_text(m,encoding='utf-8')

# Adaptive icon.
values=APP/'src/main/res/values';values.mkdir(parents=True,exist_ok=True)
(values/'launcher_colors.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources><color name="launcher_bg">#FAF6EE</color></resources>\n',encoding='utf-8')
legacy=APP/'src/main/res/mipmap-anydpi';legacy.mkdir(parents=True,exist_ok=True)
for name in ['ic_launcher','ic_launcher_round']:(legacy/f'{name}.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n<selector xmlns:android="http://schemas.android.com/apk/res/android"><item android:drawable="@drawable/ic_launcher"/></selector>\n',encoding='utf-8')
modern=APP/'src/main/res/mipmap-anydpi-v26';modern.mkdir(parents=True,exist_ok=True)
for name in ['ic_launcher','ic_launcher_round']:(modern/f'{name}.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/launcher_bg"/><foreground android:drawable="@drawable/ic_launcher"/></adaptive-icon>\n',encoding='utf-8')

# Assertions.
assert 'versionCode 7' in gradle.read_text(encoding='utf-8')
assert "versionName '4.2.0'" in gradle.read_text(encoding='utf-8')
assert 'requestSingleUpdate' in main.read_text(encoding='utf-8')
assert 'R.raw.default_adhan' in (JAVA/'AdhanService.java').read_text(encoding='utf-8')
assert 'for(int d=0;d<2;d++)' in (JAVA/'AlarmReceiver.java').read_text(encoding='utf-8')
assert 'allowBackup="false"' in manifest.read_text(encoding='utf-8')
print('Nadaye Beheshti 4.2 final hardening applied')
