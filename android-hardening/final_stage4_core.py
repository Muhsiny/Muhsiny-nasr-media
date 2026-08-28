from pathlib import Path
p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/MainActivity.java')
s=p.read_text(encoding='utf-8')

# Quran continuous-player state
s=s.replace('    MediaPlayer player;\n    JSONArray quran;','    MediaPlayer player;\n    JSONArray quran;\n    int quranPlaySurah=-1,quranPlayAyah=1,quranPlayQari=0; boolean quranQueueActive=false,quranPaused=false; TextView quranNowPlaying; Button quranPlayPauseBtn;',1)

start=s.index('    void showQuran(){')
end=s.index('\n    LinkedHashMap<String,String> duas(){',start)
new=r'''    int quranVerseCount(int surahIndex){loadQuran();try{JSONObject ch=quran.optJSONObject(surahIndex);JSONArray vs=ch==null?null:ch.optJSONArray("verses");return vs==null?0:vs.length();}catch(Exception e){return 0;}}
    void showQuran(){
        LinearLayout root=page("قرآن کریم");LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.VERTICAL);top.setPadding(dp(12),0,dp(12),dp(6));
        Spinner surah=new Spinner(this);surah.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,surahNames));top.addView(surah,new LinearLayout.LayoutParams(-1,dp(52)));
        Spinner qari=new Spinner(this);qari.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,qariNames));qari.setSelection(Math.max(0,Math.min(qariNames.length-1,prefs.getInt("qari.index",0))));top.addView(qari,new LinearLayout.LayoutParams(-1,dp(52)));
        quranNowPlaying=text("یک سوره را انتخاب کنید و ▶ را بزنید؛ تلاوت تا پایان سوره بدون کلیک روی آیه‌ها ادامه می‌یابد.",13,textColor(),false);quranNowPlaying.setGravity(Gravity.CENTER);addCard(top,quranNowPlaying);
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);controls.setOrientation(LinearLayout.HORIZONTAL);
        Button stop=smallButton("■ توقف");quranPlayPauseBtn=button("▶ پخش سوره");Button prev=smallButton("آیه قبل");Button next=smallButton("آیه بعد");
        controls.addView(stop,new LinearLayout.LayoutParams(0,dp(52),1));controls.addView(quranPlayPauseBtn,new LinearLayout.LayoutParams(0,dp(52),2));top.addView(controls);
        LinearLayout seekRow=new LinearLayout(this);seekRow.setOrientation(LinearLayout.HORIZONTAL);seekRow.addView(prev,new LinearLayout.LayoutParams(0,dp(48),1));seekRow.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));top.addView(seekRow);
        root.addView(top,new LinearLayout.LayoutParams(-1,-2));
        ScrollView sc=new ScrollView(this);TextView qtext=text("در حال بارگذاری...",23,textColor(),false);qtext.setTextIsSelectable(true);if(Build.VERSION.SDK_INT>=26)qtext.setJustificationMode(android.text.Layout.JUSTIFICATION_MODE_INTER_WORD);sc.addView(qtext);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        Runnable render=()->{loadQuran();int idx=surah.getSelectedItemPosition();StringBuilder z=new StringBuilder((idx==0||idx==8)?"":"﷽\n\n");try{JSONObject ch=quran.getJSONObject(idx);JSONArray verses=ch.optJSONArray("verses");if(verses!=null)for(int i=0;i<verses.length();i++){JSONObject v=verses.optJSONObject(i);if(v==null)continue;String tx=v.optString("text",v.optString("text_ar",""));z.append(tx).append("  ﴿").append(i+1).append("﴾  ");}}catch(Exception e){z=new StringBuilder("متن سوره بارگذاری نشد");}qtext.setText(z.toString());};
        surah.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?>p,View v,int pos,long id){render.run();}public void onNothingSelected(android.widget.AdapterView<?>p){}});
        qari.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?>p,View v,int pos,long id){prefs.edit().putInt("qari.index",pos).apply();}public void onNothingSelected(android.widget.AdapterView<?>p){}});
        quranPlayPauseBtn.setOnClickListener(v->{if(quranQueueActive&&quranPlaySurah==surah.getSelectedItemPosition()&&quranPlayQari==qari.getSelectedItemPosition()){toggleQuranPause();}else startQuranSurah(surah.getSelectedItemPosition(),qari.getSelectedItemPosition(),1);});
        stop.setOnClickListener(v->stopQuranQueue());
        next.setOnClickListener(v->{if(quranQueueActive){int max=quranVerseCount(quranPlaySurah);if(quranPlayAyah<max){quranPlayAyah++;playQueuedAyah();}}else startQuranSurah(surah.getSelectedItemPosition(),qari.getSelectedItemPosition(),1);});
        prev.setOnClickListener(v->{if(quranQueueActive&&quranPlayAyah>1){quranPlayAyah--;playQueuedAyah();}});
        render.run();refreshQuranPlayerUi();
    }
    void startQuranSurah(int surahIndex,int qariIndex,int ayah){stopPlayer();quranPlaySurah=Math.max(0,Math.min(113,surahIndex));quranPlayQari=Math.max(0,Math.min(qariDirs.length-1,qariIndex));quranPlayAyah=Math.max(1,ayah);quranQueueActive=true;quranPaused=false;playQueuedAyah();}
    void playQueuedAyah(){if(!quranQueueActive)return;int max=quranVerseCount(quranPlaySurah);if(max<=0||quranPlayAyah>max){stopQuranQueue();toast("تلاوت سوره کامل شد");return;}String file=String.format(Locale.US,"%03d%03d.mp3",quranPlaySurah+1,quranPlayAyah),url="https://everyayah.com/data/"+qariDirs[quranPlayQari]+"/"+file;try{if(player!=null){player.reset();player.release();player=null;}player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build());player.setDataSource(url);player.setOnPreparedListener(mp->{if(!quranQueueActive)return;mp.start();quranPaused=false;refreshQuranPlayerUi();});player.setOnCompletionListener(mp->{if(!quranQueueActive)return;quranPlayAyah++;playQueuedAyah();});player.setOnErrorListener((mp,w,e)->{if(quranQueueActive){quranPlayAyah++;new Handler(Looper.getMainLooper()).postDelayed(this::playQueuedAyah,450);}return true;});player.prepareAsync();refreshQuranPlayerUi();}catch(Exception e){quranPlayAyah++;if(quranPlayAyah<=max)playQueuedAyah();else stopQuranQueue();}}
    void toggleQuranPause(){try{if(player==null){playQueuedAyah();return;}if(player.isPlaying()){player.pause();quranPaused=true;}else{player.start();quranPaused=false;}refreshQuranPlayerUi();}catch(Exception e){playQueuedAyah();}}
    void stopQuranQueue(){quranQueueActive=false;quranPaused=false;try{if(player!=null){if(player.isPlaying())player.stop();player.release();player=null;}}catch(Exception ignored){}refreshQuranPlayerUi();}
    void refreshQuranPlayerUi(){if(quranNowPlaying!=null){if(quranQueueActive)quranNowPlaying.setText("سوره "+surahNames[quranPlaySurah]+" — آیه "+PersianDate.fa(quranPlayAyah)+" — "+qariNames[quranPlayQari]);else quranNowPlaying.setText("آماده پخش پیوسته سوره");}if(quranPlayPauseBtn!=null)quranPlayPauseBtn.setText(quranQueueActive?(quranPaused?"▶ ادامه":"⏸ مکث"):"▶ پخش سوره");}
    void playAyah(int surah,int ayah,int qariIndex){startQuranSurah(surah-1,qariIndex,ayah);}

'''
s=s[:start]+new+s[end:]

# Add built-in Iranian-style featured audio mapping + player fallback before Mafatih treasury
anchor='    JSONArray mafatih(){'
assert anchor in s
extra=r'''    String builtinSpiritualAudio(String title){String t=title==null?"":title.replace('ي','ی').replace('ك','ک').trim();if(t.contains("توسل"))return "https://shiavoice.com/stream-cbWPX";if(t.contains("عاشورا"))return "https://shiavoice.com/stream-cbyA4";if(t.contains("کمیل")||t.contains("كميل"))return "https://shiavoice.com/stream-cRsCf";if(t.contains("عهد"))return "https://shiavoice.com/stream-BXSlF";if(t.contains("کساء")||t.contains("كساء"))return "https://shiavoice.com/stream-J501B";if(t.contains("شعبانی")||t.contains("شعبان"))return "https://shiavoice.com/stream-ukyyn";return "";}
    String spiritualAudioFor(JSONObject ar){if(ar==null)return "";String key="spiritual.audio."+ar.optInt("id",-1),custom=prefs.getString(key,"");if(!custom.isEmpty())return custom;return builtinSpiritualAudio(ar.optString("title",""));}
'''
s=s.replace(anchor,extra+anchor,1)

# Replace treasury player behavior to use built-in defaults and clearer continuous media controls
old='play.setOnClickListener(v->{JSONObject ar=current[0];if(ar==null)return;String key="spiritual.audio."+ar.optInt("id",-1);String u=prefs.getString(key,"");if(u.isEmpty()){toast("برای این دعا هنوز صوت رسمی در پنل مالک ثبت نشده");return;}playUri(Uri.parse(u));});stop.setOnClickListener(v->stopPlayer());ch.setSelection(0);'
assert old in s
new2='play.setOnClickListener(v->{JSONObject ar=current[0];if(ar==null)return;String u=spiritualAudioFor(ar);if(u.isEmpty()){toast("متن کامل موجود است؛ برای این عنوان صوت آماده پیدا نشد");return;}playUri(Uri.parse(u));toast("پخش صوت با لحن ایرانی");});stop.setOnClickListener(v->stopPlayer());ch.setSelection(0);'
s=s.replace(old,new2,1)

# Ensure general stopPlayer also clears Quran queue flags, but avoid recursion from stopQuranQueue by direct release there.
oldsp='    void stopPlayer(){try{if(player!=null){if(player.isPlaying())player.stop();player.release();player=null;}}catch(Exception ignored){player=null;}}'
assert oldsp in s
newsp='    void stopPlayer(){quranQueueActive=false;quranPaused=false;try{if(player!=null){if(player.isPlaying())player.stop();player.release();player=null;}}catch(Exception ignored){}refreshQuranPlayerUi();}'
s=s.replace(oldsp,newsp,1)

p.write_text(s,encoding='utf-8')
print('Stage 4: continuous Quran + built-in featured spiritual audio')
