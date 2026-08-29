from pathlib import Path
p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/MainActivity.java')
s=p.read_text(encoding='utf-8')

# Replace the tiny five-item dua page with a real Mafatih-backed library.
start=s.index('    void showDuas(){')
end=s.index('\n\n    String shiaAdhanText()', start)
new=r'''    ArrayList<JSONObject> allMafatihArticles(){
        ArrayList<JSONObject> out=new ArrayList<>();JSONArray cs=mafatih();
        for(int i=0;i<cs.length();i++){JSONObject c=cs.optJSONObject(i);JSONArray ss=c==null?null:c.optJSONArray("sections");if(ss==null)continue;
            for(int j=0;j<ss.length();j++){JSONObject se=ss.optJSONObject(j);JSONArray aa=se==null?null:se.optJSONArray("articles");if(aa==null)continue;
                for(int k=0;k<aa.length();k++){JSONObject ar=aa.optJSONObject(k);if(ar!=null)out.add(ar);}}}
        return out;
    }
    JSONObject findMafatihArticle(ArrayList<JSONObject> all,String needle){
        String n=needle==null?"":needle.replace('ي','ی').replace('ك','ک').trim();
        for(JSONObject ar:all){String t=ar.optString("title","").replace('ي','ی').replace('ك','ک');if(t.contains(n))return ar;}return null;
    }
    String mafatihArticleText(JSONObject ar){
        if(ar==null)return "";StringBuilder z=new StringBuilder();z.append(ar.optString("title","")).append("\n\n");JSONArray items=ar.optJSONArray("items");
        if(items!=null)for(int i=0;i<items.length();i++){JSONObject it=items.optJSONObject(i);if(it==null)continue;String content=it.optString("content","");if(!content.trim().isEmpty())z.append(content).append("\n\n");}
        return z.toString().trim();
    }
    void showDuas(){
        LinearLayout root=page("دعاها و نیایش‌ها — مفاتیح");ArrayList<JSONObject> all=allMafatihArticles();
        if(all.isEmpty()){addCard(root,text("کتابخانه کامل مفاتیح بارگذاری نشد.",16,textColor(),false));return;}
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.VERTICAL);top.setPadding(dp(12),0,dp(12),dp(8));
        addCard(top,text("متن کامل دعاها و زیارات از کتابخانه مفاتیح داخل خود برنامه است. عنوان را انتخاب کنید؛ برای دعاهای شاخص، صوت ایرانی نیز از دکمه پخش در دسترس است.",14,textColor(),false));
        String[] featured={"دعای توسل","زیارت عاشورا","دعای کمیل","دعای عهد","دعای ندبه","دعای جوشن کبیر","دعای افتتاح","دعای عرفه","دعای سمات","دعای ابوحمزه ثمالی","مناجات شعبانیه","حدیث کساء","زیارت جامعه کبیره","زیارت امین الله","زیارت آل یاسین"};
        ArrayList<JSONObject> featuredArticles=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();
        for(String f:featured){JSONObject ar=findMafatihArticle(all,f.replace("دعای ","").replace("زیارت ",""));if(ar!=null){featuredArticles.add(ar);labels.add(ar.optString("title",f));}}
        Spinner sp=new Spinner(this);sp.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));top.addView(sp,new LinearLayout.LayoutParams(-1,dp(54)));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);Button play=smallButton("▶ پخش صوت");Button stop=smallButton("■ توقف");Button full=smallButton("همه مفاتیح");row.addView(play,new LinearLayout.LayoutParams(0,dp(50),1));row.addView(stop,new LinearLayout.LayoutParams(0,dp(50),1));row.addView(full,new LinearLayout.LayoutParams(0,dp(50),1));top.addView(row);
        root.addView(top,new LinearLayout.LayoutParams(-1,-2));ScrollView sv=new ScrollView(this);TextView body=text("",20,textColor(),false);body.setTextIsSelectable(true);if(Build.VERSION.SDK_INT>=26)body.setJustificationMode(android.text.Layout.JUSTIFICATION_MODE_INTER_WORD);sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        final JSONObject[] current={featuredArticles.isEmpty()?null:featuredArticles.get(0)};Runnable render=()->{int pos=sp.getSelectedItemPosition();if(pos>=0&&pos<featuredArticles.size())current[0]=featuredArticles.get(pos);body.setText(mafatihArticleText(current[0]));};
        sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?>p){}public void onItemSelected(android.widget.AdapterView<?>p,View v,int pos,long id){render.run();}});
        play.setOnClickListener(v->{JSONObject ar=current[0];if(ar==null)return;String u=spiritualAudioFor(ar);if(u.isEmpty()){toast("برای این عنوان فعلاً صوت آماده وجود ندارد؛ متن کامل داخل برنامه موجود است");return;}playUri(Uri.parse(u));});stop.setOnClickListener(v->stopPlayer());full.setOnClickListener(v->showSpiritualTreasury());render.run();
    }
'''
s=s[:start]+new+s[end:]

# Stronger Quran network playback: don't silently skip on transient errors; retry same ayah twice, then stop clearly.
s=s.replace('int quranPlaySurah=-1,quranPlayAyah=1,quranPlayQari=0; boolean quranQueueActive=false,quranPaused=false; TextView quranNowPlaying; Button quranPlayPauseBtn;',
            'int quranPlaySurah=-1,quranPlayAyah=1,quranPlayQari=0,quranRetry=0; boolean quranQueueActive=false,quranPaused=false; TextView quranNowPlaying; Button quranPlayPauseBtn;')
s=s.replace('quranPlayAyah=Math.max(1,ayah);quranQueueActive=true;quranPaused=false;playQueuedAyah();',
            'quranPlayAyah=Math.max(1,ayah);quranRetry=0;quranQueueActive=true;quranPaused=false;playQueuedAyah();')
s=s.replace('player.setOnPreparedListener(mp->{if(!quranQueueActive)return;mp.start();quranPaused=false;refreshQuranPlayerUi();});player.setOnCompletionListener(mp->{if(!quranQueueActive)return;quranPlayAyah++;playQueuedAyah();});player.setOnErrorListener((mp,w,e)->{if(quranQueueActive){quranPlayAyah++;new Handler(Looper.getMainLooper()).postDelayed(this::playQueuedAyah,450);}return true;});',
            'player.setOnPreparedListener(mp->{if(!quranQueueActive)return;quranRetry=0;mp.start();quranPaused=false;refreshQuranPlayerUi();});player.setOnCompletionListener(mp->{if(!quranQueueActive)return;quranRetry=0;quranPlayAyah++;playQueuedAyah();});player.setOnErrorListener((mp,w,e)->{if(!quranQueueActive)return true;if(quranRetry<2){quranRetry++;new Handler(Looper.getMainLooper()).postDelayed(this::playQueuedAyah,700L*quranRetry);}else{quranQueueActive=false;quranRetry=0;toast("پخش تلاوت متوقف شد؛ اتصال صوتی در دسترس نیست");refreshQuranPlayerUi();}return true;});')

p.write_text(s,encoding='utf-8')
print('v5.3 final completion: full Mafatih dua page + hardened Quran playback')
