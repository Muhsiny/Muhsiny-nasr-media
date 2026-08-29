from pathlib import Path

p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/MainActivity.java')
s=p.read_text(encoding='utf-8')

# ---- Exact alarm access: visible, actionable, and re-schedule after returning from settings ----
anchor='    void showPrayerTimes(){\n'
assert anchor in s
exact=r'''    boolean hasExactAlarmAccess(){
        if(Build.VERSION.SDK_INT<31)return true;
        try{AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);return am!=null&&am.canScheduleExactAlarms();}catch(Exception e){return false;}
    }
    void requestExactAlarmAccess(){
        if(Build.VERSION.SDK_INT<31){AlarmReceiver.scheduleToday(this);toast("اذان دقیق فعال است");return;}
        if(hasExactAlarmAccess()){AlarmReceiver.scheduleToday(this);toast("اذان دقیق فعال است");return;}
        try{Intent i=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()));startActivity(i);}catch(Exception e){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Exception ignored){toast("از تنظیمات سیستم، اجازه Alarm دقیق را فعال کنید");}}
    }
    @Override protected void onResume(){super.onResume();if(prefs!=null&&prefs.getBoolean("notifications",true)&&hasExactAlarmAccess())AlarmReceiver.scheduleToday(this);}

'''
s=s.replace(anchor,exact+anchor,1)

old='Button sch=button("فعال‌سازی اذان برای اوقات امروز");sch.setOnClickListener(v->{AlarmReceiver.scheduleToday(this);toast("اوقات اذان تنظیم شد");});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.setMargins(0,dp(10),0,0);b.addView(sch,lp);'
assert old in s
new='Button sch=button(hasExactAlarmAccess()?"✓ اذان دقیق فعال — تنظیم دوباره":"فعال‌سازی اجازه اذان دقیق");sch.setOnClickListener(v->{if(!hasExactAlarmAccess())requestExactAlarmAccess();else{AlarmReceiver.scheduleToday(this);toast("اوقات امروز و فردا دوباره تنظیم شد");}});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.setMargins(0,dp(10),0,0);b.addView(sch,lp);'
s=s.replace(old,new,1)

# ---- Quran offline cache: download selected surah once, then all subsequent playback is local ----
# Add helper methods before startQuranSurah.
anchor='    void startQuranSurah(int surahIndex,int qariIndex,int ayah){'
assert anchor in s
helpers=r'''    File quranAudioFile(int surahIndex,int qariIndex,int ayah){File d=new File(getFilesDir(),"quran_audio/"+qariDirs[qariIndex]+"/"+String.format(Locale.US,"%03d",surahIndex+1));if(!d.exists())d.mkdirs();return new File(d,String.format(Locale.US,"%03d%03d.mp3",surahIndex+1,ayah));}
    boolean isSurahOffline(int surahIndex,int qariIndex){int max=quranVerseCount(surahIndex);if(max<=0)return false;for(int a=1;a<=max;a++){File f=quranAudioFile(surahIndex,qariIndex,a);if(!f.exists()||f.length()<1024)return false;}return true;}
    void downloadCurrentSurahOffline(int surahIndex,int qariIndex){
        int max=quranVerseCount(surahIndex);if(max<=0){toast("آیات سوره پیدا نشد");return;}toast("دانلود آفلاین سوره شروع شد");
        new Thread(()->{int ok=0;for(int a=1;a<=max;a++){File dest=quranAudioFile(surahIndex,qariIndex,a);if(dest.exists()&&dest.length()>1024){ok++;continue;}String file=String.format(Locale.US,"%03d%03d.mp3",surahIndex+1,a),url="https://everyayah.com/data/"+qariDirs[qariIndex]+"/"+file;File tmp=new File(dest.getAbsolutePath()+".part");try{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(45000);c.setInstanceFollowRedirects(true);try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(tmp)){byte[] b=new byte[16384];int n;while((n=in.read(b))>0)out.write(b,0,n);}if(tmp.length()>1024&&tmp.renameTo(dest))ok++;else tmp.delete();}catch(Exception e){tmp.delete();}}
            final int done=ok;runOnUiThread(()->toast(done==max?"سوره کامل برای آفلاین ذخیره شد":"دانلود "+PersianDate.fa(done)+" از "+PersianDate.fa(max)+" آیه انجام شد؛ دوباره بزنید تا کامل شود"));}).start();
    }
'''
s=s.replace(anchor,helpers+anchor,1)

# Insert download button in Quran controls under prev/next row.
old='LinearLayout seekRow=new LinearLayout(this);seekRow.setOrientation(LinearLayout.HORIZONTAL);seekRow.addView(prev,new LinearLayout.LayoutParams(0,dp(48),1));seekRow.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));top.addView(seekRow);'
assert old in s
new='LinearLayout seekRow=new LinearLayout(this);seekRow.setOrientation(LinearLayout.HORIZONTAL);seekRow.addView(prev,new LinearLayout.LayoutParams(0,dp(48),1));seekRow.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));top.addView(seekRow);Button offlineQuran=button("⇩ دانلود این سوره برای پخش بدون اینترنت");offlineQuran.setOnClickListener(v->downloadCurrentSurahOffline(surah.getSelectedItemPosition(),qari.getSelectedItemPosition()));top.addView(offlineQuran,new LinearLayout.LayoutParams(-1,dp(52)));'
s=s.replace(old,new,1)

# Prefer local file in queued playback; network only when cache is absent.
old='String file=String.format(Locale.US,"%03d%03d.mp3",quranPlaySurah+1,quranPlayAyah),url="https://everyayah.com/data/"+qariDirs[quranPlayQari]+"/"+file;try{if(player!=null){player.reset();player.release();player=null;}player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build());player.setDataSource(url);'
assert old in s
new='String file=String.format(Locale.US,"%03d%03d.mp3",quranPlaySurah+1,quranPlayAyah),url="https://everyayah.com/data/"+qariDirs[quranPlayQari]+"/"+file;File local=quranAudioFile(quranPlaySurah,quranPlayQari,quranPlayAyah);try{if(player!=null){player.reset();player.release();player=null;}player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build());if(local.exists()&&local.length()>1024)player.setDataSource(local.getAbsolutePath());else player.setDataSource(url);'
s=s.replace(old,new,1)

# ---- Dua audio cache: featured Iranian audio can be saved by the user for reliable offline playback ----
anchor='    String spiritualAudioFor(JSONObject ar){'
assert anchor in s
extra=r'''    File duaAudioFile(JSONObject ar){File d=new File(getFilesDir(),"dua_audio");if(!d.exists())d.mkdirs();return new File(d,"dua_"+(ar==null?-1:ar.optInt("id",-1))+".mp3");}
    String spiritualAudioResolved(JSONObject ar){File f=duaAudioFile(ar);if(f.exists()&&f.length()>1024)return Uri.fromFile(f).toString();return spiritualAudioFor(ar);}
    void downloadDuaAudio(JSONObject ar){if(ar==null)return;String u=spiritualAudioFor(ar);if(u==null||u.isEmpty()||(!u.startsWith("http://")&&!u.startsWith("https://"))){toast("برای این دعا فایل صوتی آنلاین آماده نیست");return;}File dest=duaAudioFile(ar),tmp=new File(dest.getAbsolutePath()+".part");toast("دانلود صوت شروع شد");new Thread(()->{boolean ok=false;try{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(60000);c.setInstanceFollowRedirects(true);try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(tmp)){byte[] b=new byte[16384];int n;while((n=in.read(b))>0)out.write(b,0,n);}ok=tmp.length()>1024&&tmp.renameTo(dest);}catch(Exception e){tmp.delete();}final boolean done=ok;runOnUiThread(()->toast(done?"صوت برای پخش آفلاین ذخیره شد":"دانلود صوت کامل نشد"));}).start();}
'''
s=s.replace(anchor,extra+anchor,1)

# Dua page: add download button and play resolved local/remote media.
old='LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);Button play=smallButton("▶ پخش صوت");Button stop=smallButton("■ توقف");Button full=smallButton("همه مفاتیح");row.addView(play,new LinearLayout.LayoutParams(0,dp(50),1));row.addView(stop,new LinearLayout.LayoutParams(0,dp(50),1));row.addView(full,new LinearLayout.LayoutParams(0,dp(50),1));top.addView(row);'
assert old in s
new='LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);Button play=smallButton("▶ پخش صوت");Button stop=smallButton("■ توقف");Button full=smallButton("همه مفاتیح");row.addView(play,new LinearLayout.LayoutParams(0,dp(50),1));row.addView(stop,new LinearLayout.LayoutParams(0,dp(50),1));row.addView(full,new LinearLayout.LayoutParams(0,dp(50),1));top.addView(row);Button saveDua=button("⇩ ذخیره صوت این دعا برای آفلاین");top.addView(saveDua,new LinearLayout.LayoutParams(-1,dp(50)));'
s=s.replace(old,new,1)
old='play.setOnClickListener(v->{JSONObject ar=current[0];if(ar==null)return;String u=spiritualAudioFor(ar);if(u.isEmpty()){toast("برای این عنوان فعلاً صوت آماده وجود ندارد؛ متن کامل داخل برنامه موجود است");return;}playUri(Uri.parse(u));});stop.setOnClickListener(v->stopPlayer());full.setOnClickListener(v->showSpiritualTreasury());render.run();'
assert old in s
new='play.setOnClickListener(v->{JSONObject ar=current[0];if(ar==null)return;String u=spiritualAudioResolved(ar);if(u.isEmpty()){toast("برای این عنوان فعلاً صوت آماده وجود ندارد؛ متن کامل داخل برنامه موجود است");return;}playUri(Uri.parse(u));});saveDua.setOnClickListener(v->downloadDuaAudio(current[0]));stop.setOnClickListener(v->stopPlayer());full.setOnClickListener(v->showSpiritualTreasury());render.run();'
s=s.replace(old,new,1)

p.write_text(s,encoding='utf-8')
print('v5.5 release hardening: exact alarms + offline Quran/dua caches')
