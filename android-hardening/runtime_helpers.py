from pathlib import Path
p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/MainActivity.java')
s=p.read_text(encoding='utf-8')
helpers='''
    void ensureExactAlarmAccess(){
        if(android.os.Build.VERSION.SDK_INT<31)return;
        try{android.app.AlarmManager am=(android.app.AlarmManager)getSystemService(ALARM_SERVICE);if(am!=null&&!am.canScheduleExactAlarms())startActivity(new android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:"+getPackageName())));}catch(Exception ignored){}
    }
    void refreshLocationFresh(boolean showToast){
        if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        try{
            final android.location.LocationManager lm=(android.location.LocationManager)getSystemService(LOCATION_SERVICE);
            android.location.LocationListener once=new android.location.LocationListener(){
                @Override public void onLocationChanged(android.location.Location l){if(l!=null){prefs.edit().putLong("lat",Double.doubleToLongBits(l.getLatitude())).putLong("lon",Double.doubleToLongBits(l.getLongitude())).apply();AlarmReceiver.cancelAll(MainActivity.this);AlarmReceiver.scheduleToday(MainActivity.this);if(showToast)toast("موقعیت تازه شد");try{lm.removeUpdates(this);}catch(Exception ignored){}}}
                @Override public void onProviderEnabled(String p){}
                @Override public void onProviderDisabled(String p){}
                @Override public void onStatusChanged(String p,int st,android.os.Bundle e){}
            };
            String provider=lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)?android.location.LocationManager.GPS_PROVIDER:android.location.LocationManager.NETWORK_PROVIDER;
            lm.requestSingleUpdate(provider,once,android.os.Looper.getMainLooper());
            android.location.Location best=null;for(String pr:lm.getProviders(true)){android.location.Location l=lm.getLastKnownLocation(pr);if(l!=null&&(best==null||l.getTime()>best.getTime()))best=l;}
            if(best!=null&&System.currentTimeMillis()-best.getTime()<21600000L)prefs.edit().putLong("lat",Double.doubleToLongBits(best.getLatitude())).putLong("lon",Double.doubleToLongBits(best.getLongitude())).apply();else if(showToast)toast("در حال دریافت موقعیت تازه…");
        }catch(Exception e){if(showToast)toast("دسترسی موقعیت ممکن نشد");}
    }
'''
if 'void ensureExactAlarmAccess()' not in s or 'void refreshLocationFresh(boolean showToast)' not in s:
    add=''
    if 'void ensureExactAlarmAccess()' not in s:add+=helpers.split('    void refreshLocationFresh',1)[0]
    if 'void refreshLocationFresh(boolean showToast)' not in s:add+='    void refreshLocationFresh'+helpers.split('    void refreshLocationFresh',1)[1]
    pos=s.rfind('}')
    if pos<0:raise SystemExit('MainActivity final brace missing')
    s=s[:pos]+'\n'+add+'\n'+s[pos:]
    p.write_text(s,encoding='utf-8')
assert 'void ensureExactAlarmAccess()' in s
assert 'void refreshLocationFresh(boolean showToast)' in s
print('Runtime helpers present')
