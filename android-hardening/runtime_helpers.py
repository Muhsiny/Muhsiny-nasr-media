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

# Make the hidden owner entry usable: seven taps on the main title within five seconds.
old='long now=System.currentTimeMillis();if(now-tapWindow>1800){hiddenTapCount=0;tapWindow=now;}hiddenTapCount++;if(hiddenTapCount>=7){hiddenTapCount=0;showAdminGate();}'
new='long now=System.currentTimeMillis();if(hiddenTapCount==0||now-tapWindow>5000){hiddenTapCount=0;tapWindow=now;}hiddenTapCount++;if(hiddenTapCount>=7){hiddenTapCount=0;tapWindow=0;showAdminGate();}'
if old in s:s=s.replace(old,new,1)

# Add persistent owner-controlled size/position parameters to the leader image card.
old='ImageView leader=new ImageView(this);leader.setScaleType(ImageView.ScaleType.CENTER_CROP);String leaderPath=prefs.getString("theme.leaderPath","");'
new='''ImageView leader=new ImageView(this);String leaderFit=prefs.getString("theme.leaderFit","crop");leader.setScaleType("fit".equals(leaderFit)?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP);float leaderWidth=Math.max(.15f,Math.min(.80f,prefs.getFloat("theme.leaderWidth",.45f)));int leaderHeight=Math.max(64,Math.min(Math.max(64,heroHeight()-14),prefs.getInt("theme.leaderHeight",Math.max(64,heroHeight()-14))));float leaderScale=Math.max(.60f,Math.min(1.40f,prefs.getFloat("theme.leaderScale",1f)));int leaderX=Math.max(-80,Math.min(80,prefs.getInt("theme.leaderX",0))),leaderY=Math.max(-80,Math.min(80,prefs.getInt("theme.leaderY",0)));leader.setScaleX(leaderScale);leader.setScaleY(leaderScale);leader.setTranslationX(dp(leaderX));leader.setTranslationY(dp(leaderY));String leaderPath=prefs.getString("theme.leaderPath","");'''
if old in s:s=s.replace(old,new,1)
old='hero.addView(leader,new LinearLayout.LayoutParams(0,-1,.45f));'
new='LinearLayout.LayoutParams leaderLp=new LinearLayout.LayoutParams(0,dp(leaderHeight),leaderWidth);leaderLp.gravity=Gravity.CENTER_VERTICAL;hero.addView(leader,leaderLp);'
if old in s:s=s.replace(old,new,1)
old='hero.addView(tx,new LinearLayout.LayoutParams(0,-1,.55f));return hero;'
new='hero.addView(tx,new LinearLayout.LayoutParams(0,-1,Math.max(.20f,1f-leaderWidth)));return hero;'
if old in s:s=s.replace(old,new,1)

# Extend image editor with real dimensions, scale, position and fit mode.
needle='EditText alpha=field("شفافیت پس‌زمینه 0 تا 1",String.valueOf(prefs.getFloat("theme.backgroundAlpha",1f)));alpha.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);addCard(b,alpha);'
insert='''EditText lw=field("عرض تصویر رهبر 15 تا 80 درصد",String.valueOf(Math.round(prefs.getFloat("theme.leaderWidth",.45f)*100f)));lw.setInputType(InputType.TYPE_CLASS_NUMBER);addCard(b,lw);\n        EditText lh=field("ارتفاع تصویر رهبر (dp)",String.valueOf(prefs.getInt("theme.leaderHeight",Math.max(64,heroHeight()-14))));lh.setInputType(InputType.TYPE_CLASS_NUMBER);addCard(b,lh);\n        EditText ls=field("مقیاس تصویر 0.60 تا 1.40",String.valueOf(prefs.getFloat("theme.leaderScale",1f)));ls.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);addCard(b,ls);\n        EditText lx=field("جابه‌جایی افقی X از -80 تا 80",String.valueOf(prefs.getInt("theme.leaderX",0)));lx.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);addCard(b,lx);\n        EditText ly=field("جابه‌جایی عمودی Y از -80 تا 80",String.valueOf(prefs.getInt("theme.leaderY",0)));ly.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);addCard(b,ly);\n        Spinner fit=new Spinner(this);String[] fitNames={"برش و پرکردن قاب","نمایش کامل تصویر"};fit.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,fitNames));fit.setSelection("fit".equals(prefs.getString("theme.leaderFit","crop"))?1:0);addCard(b,fit);\n        EditText alpha=field("شفافیت پس‌زمینه 0 تا 1",String.valueOf(prefs.getFloat("theme.backgroundAlpha",1f)));alpha.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);addCard(b,alpha);'''
if needle in s:s=s.replace(needle,insert,1)
old='Button save=button("ذخیره شفافیت");save.setOnClickListener(v->{try{float x=Float.parseFloat(alpha.getText().toString());prefs.edit().putFloat("theme.backgroundAlpha",Math.max(0f,Math.min(1f,x))).apply();toast("ذخیره شد");}catch(Exception e){toast("عدد نادرست");}});b.addView(save,lp);'
new='''Button save=button("ذخیره اندازه و جایگاه تصویر");save.setOnClickListener(v->{try{float a=Float.parseFloat(alpha.getText().toString()),w=Math.max(15f,Math.min(80f,Float.parseFloat(lw.getText().toString())))/100f,sc=Math.max(.60f,Math.min(1.40f,Float.parseFloat(ls.getText().toString())));int h=Math.max(64,Math.min(Math.max(64,heroHeight()-14),Integer.parseInt(lh.getText().toString()))),xx=Math.max(-80,Math.min(80,Integer.parseInt(lx.getText().toString()))),yy=Math.max(-80,Math.min(80,Integer.parseInt(ly.getText().toString())));prefs.edit().putFloat("theme.backgroundAlpha",Math.max(0f,Math.min(1f,a))).putFloat("theme.leaderWidth",w).putInt("theme.leaderHeight",h).putFloat("theme.leaderScale",sc).putInt("theme.leaderX",xx).putInt("theme.leaderY",yy).putString("theme.leaderFit",fit.getSelectedItemPosition()==1?"fit":"crop").apply();toast("اندازه و جایگاه تصویر ذخیره شد");}catch(Exception e){toast("مقادیر تصویر نادرست است");}});b.addView(save,lp);'''
if old in s:s=s.replace(old,new,1)
old='prefs.edit().remove("theme.backgroundPath").remove("theme.leaderPath").apply();toast("بازنشانی شد");'
new='prefs.edit().remove("theme.backgroundPath").remove("theme.leaderPath").remove("theme.leaderWidth").remove("theme.leaderHeight").remove("theme.leaderScale").remove("theme.leaderX").remove("theme.leaderY").remove("theme.leaderFit").apply();toast("تصاویر و اندازه‌ها بازنشانی شد");'
if old in s:s=s.replace(old,new,1)

p.write_text(s,encoding='utf-8')
assert 'now-tapWindow>5000' in s
assert 'theme.leaderWidth' in s and 'theme.leaderHeight' in s and 'theme.leaderScale' in s
assert 'ذخیره اندازه و جایگاه تصویر' in s
assert 'void ensureExactAlarmAccess()' in s
assert 'void refreshLocationFresh(boolean showToast)' in s
print('Runtime helpers, hidden admin entry and image controls applied')
