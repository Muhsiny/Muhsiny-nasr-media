from pathlib import Path

p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/MainActivity.java')
s=p.read_text(encoding='utf-8')

# 1) Correct startup ordering: SharedPreferences must exist before any helper can use it.
old='''    @Override public void onCreate(Bundle b){\n        super.onCreate(b);\n        ensureExactAlarmAccess();\n        try { AlarmReceiver.scheduleToday(this); } catch (Exception ignored) { }\n        refreshLocationFresh(false);\n        prefs=getSharedPreferences(PREF,MODE_PRIVATE);\n        sensorManager=(SensorManager)getSystemService(SENSOR_SERVICE);'''
new='''    @Override public void onCreate(Bundle b){\n        super.onCreate(b);\n        prefs=getSharedPreferences(PREF,MODE_PRIVATE);\n        ensureExactAlarmAccess();\n        try { AlarmReceiver.scheduleToday(this); } catch (Exception ignored) { }\n        refreshLocationFresh(false);\n        sensorManager=(SensorManager)getSystemService(SENSOR_SERVICE);'''
assert old in s
s=s.replace(old,new,1)

# 2) Quran persistent cache: every played ayah is cached; a whole surah can be downloaded once and then works without internet.
anchor='    int quranVerseCount(int surahIndex)'
assert anchor in s
helpers=r'''    File quranAudioFile(int surahIndex,int ayah,int qariIndex){File d=new File(getFilesDir(),"quran_audio/q"+qariIndex+"/s"+(surahIndex+1));if(!d.exists())d.mkdirs();return new File(d,String.format(Locale.US,"%03d.mp3",ayah));}
    String quranAudioUrl(int surahIndex,int ayah,int qariIndex){return "https://everyayah.com/data/"+qariDirs[qariIndex]+"/"+String.format(Locale.US,"%03d%03d.mp3",surahIndex+1,ayah);}
    boolean downloadToFile(String url,File out){File tmp=new File(out.getAbsolutePath()+".part");try{URLConnection c=new URL(url).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(45000);c.setRequestProperty("User-Agent","NadayeBeheshti/5.2");InputStream in=c.getInputStream();FileOutputStream fo=new FileOutputStream(tmp);byte[] b=new byte[32768];int n;long total=0;while((n=in.read(b))>0){fo.write(b,0,n);total+=n;}fo.flush();fo.close();in.close();if(total<2048){tmp.delete();return false;}if(out.exists())out.delete();return tmp.renameTo(out);}catch(Exception e){tmp.delete();return false;}}
    void cacheSurahOffline(int surahIndex,int qariIndex){int count=quranVerseCount(surahIndex);if(count<=0){toast("تعداد آیات پیدا نشد");return;}toast("دانلود سوره برای آفلاین آغاز شد");new Thread(()->{int ok=0;for(int a=1;a<=count;a++){File f=quranAudioFile(surahIndex,a,qariIndex);if(f.exists()&&f.length()>2048){ok++;continue;}if(downloadToFile(quranAudioUrl(surahIndex,a,qariIndex),f))ok++;final int done=a,good=ok;runOnUiThread(()->{if(quranNowPlaying!=null)quranNowPlaying.setText("ذخیره آفلاین: "+PersianDate.fa(done)+" / "+PersianDate.fa(count)+" — موفق "+PersianDate.fa(good));});}final int good=ok;runOnUiThread(()->toast(good==count?"سوره کامل برای آفلاین ذخیره شد":"ذخیره آفلاین ناقص بود؛ دوباره بزنید تا فقط فایل‌های باقی‌مانده دانلود شود"));}).start();}
    boolean surahFullyCached(int surahIndex,int qariIndex){int c=quranVerseCount(surahIndex);if(c<=0)return false;for(int a=1;a<=c;a++){File f=quranAudioFile(surahIndex,a,qariIndex);if(!f.exists()||f.length()<2048)return false;}return true;}
'''
s=s.replace(anchor,helpers+anchor,1)

# Add a real offline-download button to Quran UI.
old='''        LinearLayout seekRow=new LinearLayout(this);seekRow.setOrientation(LinearLayout.HORIZONTAL);seekRow.addView(prev,new LinearLayout.LayoutParams(0,dp(48),1));seekRow.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));top.addView(seekRow);'''
new='''        LinearLayout seekRow=new LinearLayout(this);seekRow.setOrientation(LinearLayout.HORIZONTAL);seekRow.addView(prev,new LinearLayout.LayoutParams(0,dp(48),1));seekRow.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));top.addView(seekRow);\n        Button offlineQuran=button("⬇ ذخیره کامل این سوره برای پخش آفلاین");offlineQuran.setOnClickListener(v->cacheSurahOffline(surah.getSelectedItemPosition(),qari.getSelectedItemPosition()));top.addView(offlineQuran,new LinearLayout.LayoutParams(-1,dp(54)));'''
assert old in s
s=s.replace(old,new,1)

# Replace online-only ayah player: cache first, then stream; a successful stream is copied to cache in background.
start=s.index('    void playQueuedAyah(){')
end=s.index('\n    void toggleQuranPause()',start)
newplay=r'''    void playQueuedAyah(){
        if(!quranQueueActive)return;int max=quranVerseCount(quranPlaySurah);if(max<=0||quranPlayAyah>max){stopQuranQueue();toast("تلاوت سوره کامل شد");return;}
        File cached=quranAudioFile(quranPlaySurah,quranPlayAyah,quranPlayQari);String url=quranAudioUrl(quranPlaySurah,quranPlayAyah,quranPlayQari);boolean fromCache=cached.exists()&&cached.length()>2048;
        try{if(player!=null){try{player.reset();player.release();}catch(Exception ignored){}player=null;}player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build());if(fromCache)player.setDataSource(cached.getAbsolutePath());else player.setDataSource(url);
            player.setOnPreparedListener(mp->{if(!quranQueueActive)return;quranRetry=0;mp.start();quranPaused=false;refreshQuranPlayerUi();if(!fromCache)new Thread(()->{if(!cached.exists())downloadToFile(url,cached);}).start();});
            player.setOnCompletionListener(mp->{if(!quranQueueActive)return;quranRetry=0;quranPlayAyah++;playQueuedAyah();});
            player.setOnErrorListener((mp,w,e)->{if(!quranQueueActive)return true;if(fromCache){cached.delete();new Handler(Looper.getMainLooper()).postDelayed(this::playQueuedAyah,250);}else if(quranRetry<2){quranRetry++;new Handler(Looper.getMainLooper()).postDelayed(this::playQueuedAyah,700L*quranRetry);}else{quranQueueActive=false;quranRetry=0;toast("این آیه هنوز آفلاین ذخیره نشده و اینترنت صوتی در دسترس نیست");refreshQuranPlayerUi();}return true;});player.prepareAsync();refreshQuranPlayerUi();
        }catch(Exception e){if(fromCache){cached.delete();playQueuedAyah();}else{quranQueueActive=false;toast("پخش صوت ممکن نشد");refreshQuranPlayerUi();}}
    }
'''
s=s[:start]+newplay+s[end:]

# 3) Dua audio persistent cache. Featured Iranian/Shia streams can be saved once and then remain offline.
anchor='    String spiritualAudioFor(JSONObject ar)'
assert anchor in s
extra=r'''    File spiritualAudioCache(JSONObject ar){File d=new File(getFilesDir(),"spiritual_audio");if(!d.exists())d.mkdirs();int id=ar==null?-1:ar.optInt("id",-1);return new File(d,"article_"+id+".media");}
    void cacheSpiritualAudio(JSONObject ar){if(ar==null)return;String u=builtinSpiritualAudio(ar.optString("title",""));if(u.isEmpty()){u=prefs.getString("spiritual.audio."+ar.optInt("id",-1),"");}if(u.isEmpty()){toast("برای این عنوان منبع صوتی تعریف نشده");return;}final String src=u;File out=spiritualAudioCache(ar);toast("ذخیره صوت برای آفلاین آغاز شد");new Thread(()->{boolean ok=downloadToFile(src,out);runOnUiThread(()->toast(ok?"صوت برای آفلاین ذخیره شد":"دانلود صوت کامل نشد؛ دوباره تلاش کنید"));}).start();}
'''
s=s.replace(anchor,extra+anchor,1)
old='''    String spiritualAudioFor(JSONObject ar){if(ar==null)return "";String key="spiritual.audio."+ar.optInt("id",-1),custom=prefs.getString(key,"");if(!custom.isEmpty())return custom;return builtinSpiritualAudio(ar.optString("title",""));}'''
new='''    String spiritualAudioFor(JSONObject ar){if(ar==null)return "";File cached=spiritualAudioCache(ar);if(cached.exists()&&cached.length()>2048)return Uri.fromFile(cached).toString();String key="spiritual.audio."+ar.optInt("id",-1),custom=prefs.getString(key,"");if(!custom.isEmpty())return custom;return builtinSpiritualAudio(ar.optString("title",""));}'''
assert old in s
s=s.replace(old,new,1)

# Add download button to featured dua page.
old='''        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);Button play=smallButton("▶ پخش صوت");Button stop=smallButton("■ توقف");Button full=smallButton("همه مفاتیح");row.addView(play,new LinearLayout.LayoutParams(0,dp(50),1));row.addView(stop,new LinearLayout.LayoutParams(0,dp(50),1));row.addView(full,new LinearLayout.LayoutParams(0,dp(50),1));top.addView(row);'''
new='''        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);Button play=smallButton("▶ پخش صوت");Button stop=smallButton("■ توقف");Button full=smallButton("همه مفاتیح");row.addView(play,new LinearLayout.LayoutParams(0,dp(50),1));row.addView(stop,new LinearLayout.LayoutParams(0,dp(50),1));row.addView(full,new LinearLayout.LayoutParams(0,dp(50),1));top.addView(row);\n        Button saveAudio=button("⬇ ذخیره صوت این دعا برای آفلاین");top.addView(saveAudio,new LinearLayout.LayoutParams(-1,dp(52)));'''
assert old in s
s=s.replace(old,new,1)
old='''        play.setOnClickListener(v->{JSONObject ar=current[0];if(ar==null)return;String u=spiritualAudioFor(ar);if(u.isEmpty()){toast("برای این عنوان فعلاً صوت آماده وجود ندارد؛ متن کامل داخل برنامه موجود است");return;}playUri(Uri.parse(u));});stop.setOnClickListener(v->stopPlayer());full.setOnClickListener(v->showSpiritualTreasury());render.run();'''
new='''        play.setOnClickListener(v->{JSONObject ar=current[0];if(ar==null)return;String u=spiritualAudioFor(ar);if(u.isEmpty()){toast("برای این عنوان صوت آماده وجود ندارد؛ متن کامل داخل برنامه است");return;}playUri(Uri.parse(u));});stop.setOnClickListener(v->stopPlayer());full.setOnClickListener(v->showSpiritualTreasury());saveAudio.setOnClickListener(v->cacheSpiritualAudio(current[0]));render.run();'''
assert old in s
s=s.replace(old,new,1)

# Make generic media player explicit for http/file/content URIs.
old='''    void playUri(Uri u){try{stopPlayer();player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build());player.setDataSource(this,u);player.setOnPreparedListener(MediaPlayer::start);player.setOnCompletionListener(x->stopPlayer());player.prepareAsync();}catch(Exception e){toast("پخش صوت ممکن نشد");}}'''
new='''    void playUri(Uri u){try{stopPlayer();player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build());String sc=u==null?"":u.getScheme();if("http".equalsIgnoreCase(sc)||"https".equalsIgnoreCase(sc))player.setDataSource(u.toString());else if("file".equalsIgnoreCase(sc))player.setDataSource(u.getPath());else player.setDataSource(this,u);player.setOnPreparedListener(MediaPlayer::start);player.setOnCompletionListener(x->stopPlayer());player.setOnErrorListener((mp,w,e)->{toast("منبع صوتی در دسترس نیست");stopPlayer();return true;});player.prepareAsync();}catch(Exception e){toast("پخش صوت ممکن نشد");}}'''
assert old in s
s=s.replace(old,new,1)

# 4) Remote Shia adhan profiles: download and retain locally when user asks for offline.
old='''void cacheSelectedBuiltInAdhan(int index){toast(index==0?"اذان حسین صبحدل داخل برنامه و کاملاً آفلاین است":(index>0&&index<builtInAdhanNames.length?"این انتخاب از رسانه صوت‌الشیعه آنلاین پخش می‌شود و در قطع اینترنت به صبحدل برمی‌گردد":"فایل موذن شخصی روی دستگاه شما ذخیره می‌شود"));}'''
new=r'''void cacheSelectedBuiltInAdhan(int index){if(index==0){toast("اذان حسین صبحدل از قبل داخل برنامه و آفلاین است");return;}if(index>0&&index<builtInAdhanNames.length){String u=builtInAdhanUrls[index];File d=new File(getFilesDir(),"adhan_media");if(!d.exists())d.mkdirs();File out=new File(d,"remote_"+index+".media");toast("دانلود موذن برای آفلاین آغاز شد");new Thread(()->{boolean ok=downloadToFile(u,out);runOnUiThread(()->toast(ok?"این موذن هم برای آفلاین ذخیره شد":"دانلود موذن کامل نشد؛ دوباره تلاش کنید"));}).start();return;}toast("فایل موذن شخصی روی دستگاه ذخیره می‌شود");}'''
assert old in s
s=s.replace(old,new,1)
s=s.replace('Button offline=button("وضعیت آفلاین اذان");','Button offline=button("⬇ ذخیره موذن انتخابی برای آفلاین");',1)
s=s.replace('اذان حسین صبحدل همراه APK نصب می‌شود و انتخاب پیش‌فرض و fallback آفلاین است. مؤذن‌زاده، اباذر الحلواجي و سعید طوسی از صوت‌الشیعه پخش می‌شوند. صدای اذان از کانال Alarm پخش می‌شود.','اذان حسین صبحدل همراه APK و آفلاین است. مؤذن‌زاده، اباذر الحلواجي و سعید طوسی را نیز می‌توانید با یک لمس داخل برنامه ذخیره کنید تا بعداً بدون اینترنت پخش شوند. صدای اذان از کانال Alarm پخش می‌شود.',1)

# 5) Remove the last public empty-state dependency on hidden owner panel by shipping a built-in starter library.
old='''JSONArray beheshtiEntries(){try{File f=beheshtiFile();if(!f.exists())return new JSONArray();InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] z=new byte[8192];int n;while((n=in.read(z))>0)out.write(z,0,n);in.close();return new JSONArray(out.toString("UTF-8"));}catch(Exception e){return new JSONArray();}}'''
new='''JSONArray beheshtiEntries(){try{File f=beheshtiFile();InputStream in=f.exists()?new FileInputStream(f):getAssets().open("beheshti_library.json");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] z=new byte[8192];int n;while((n=in.read(z))>0)out.write(z,0,n);in.close();return new JSONArray(out.toString("UTF-8"));}catch(Exception e){return new JSONArray();}}'''
assert old in s
s=s.replace(old,new,1)
s=s.replace('if(a.length()==0){addCard(b,text("هنوز مطلبی از پنل مالک افزوده نشده است.",15,textColor(),false));return;}','if(a.length()==0){addCard(b,text("کتابخانه زندگی و اندیشه در دسترس نیست.",15,textColor(),false));return;}',1)

p.write_text(s,encoding='utf-8')

# AdhanService: prefer cached copies of remote Shia profiles, then remote, then bundled Iranian fallback.
a=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/AdhanService.java')
t=a.read_text(encoding='utf-8')
old='''        if(selected==0)return Uri.parse("android.resource://"+getPackageName()+"/"+R.raw.default_adhan);\n        if(selected>=1&&selected<=REMOTE_SHIA.length)return Uri.parse(REMOTE_SHIA[selected-1]);'''
new='''        if(selected==0)return Uri.parse("android.resource://"+getPackageName()+"/"+R.raw.default_adhan);\n        if(selected>=1&&selected<=REMOTE_SHIA.length){File cached=new File(getFilesDir(),"adhan_media/remote_"+selected+".media");if(cached.exists()&&cached.length()>2048)return Uri.fromFile(cached);return Uri.parse(REMOTE_SHIA[selected-1]);}'''
assert old in t
t=t.replace(old,new,1)
# add java.io.File import if needed
if 'import java.io.File;' not in t:t=t.replace('import android.os.*;','import android.os.*;\nimport java.io.File;')
a.write_text(t,encoding='utf-8')

# Alarm robustness: exact permission check plus reschedule on reboot/timezone/time changes/package replacement.
ar=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/AlarmReceiver.java')
r=ar.read_text(encoding='utf-8')
old='''        try{if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);else am.setExact(AlarmManager.RTC_WAKEUP,when,pi);}catch(SecurityException e){if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);else am.set(AlarmManager.RTC_WAKEUP,when,pi);}'''
new='''        try{boolean exact=true;if(Build.VERSION.SDK_INT>=31)exact=am.canScheduleExactAlarms();if(exact){if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);else am.setExact(AlarmManager.RTC_WAKEUP,when,pi);}else{if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);else am.set(AlarmManager.RTC_WAKEUP,when,pi);}}catch(SecurityException e){if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);else am.set(AlarmManager.RTC_WAKEUP,when,pi);}'''
assert old in r
r=r.replace(old,new,1)
ar.write_text(r,encoding='utf-8')

mf=Path('android-nadaye/app/src/main/AndroidManifest.xml')
m=mf.read_text(encoding='utf-8')
if 'android.permission.WAKE_LOCK' not in m:m=m.replace('<uses-permission android:name="android.permission.INTERNET" />','<uses-permission android:name="android.permission.INTERNET" />\n    <uses-permission android:name="android.permission.WAKE_LOCK" />')
old='<receiver android:name=".BootReceiver" android:exported="true">\n            <intent-filter><action android:name="android.intent.action.BOOT_COMPLETED" /></intent-filter>\n        </receiver>'
new='''<receiver android:name=".BootReceiver" android:exported="true">\n            <intent-filter>\n                <action android:name="android.intent.action.BOOT_COMPLETED" />\n                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />\n                <action android:name="android.intent.action.TIME_SET" />\n                <action android:name="android.intent.action.TIMEZONE_CHANGED" />\n            </intent-filter>\n        </receiver>'''
assert old in m
m=m.replace(old,new,1)
mf.write_text(m,encoding='utf-8')

print('v5.5 offline hardening + alarm lifecycle + public content completion applied')
