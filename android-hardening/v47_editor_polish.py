from pathlib import Path
p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/MainActivity.java')
s=p.read_text(encoding='utf-8')

# request code for title-only font
s=s.replace('REQ_SPIRIT_AUDIO=815;','REQ_SPIRIT_AUDIO=815, REQ_TITLE_FONT=816;',1)

# cover height follows the selected image's real aspect ratio
s=s.replace('body.addView(makeHeroCard(),new LinearLayout.LayoutParams(-1,dp(heroHeight())));','body.addView(makeHeroCard(),new LinearLayout.LayoutParams(-1,dp(heroDisplayHeight())));',1)
anchor='    View makeHeroCard(){\n'
helper=r'''    int heroDisplayHeight(){
        String path=prefs.getString("theme.heroCoverPath","");
        if(path.isEmpty()||!new File(path).exists())return heroHeight();
        try{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,o);if(o.outWidth>0&&o.outHeight>0){float density=getResources().getDisplayMetrics().density;float screenDp=getResources().getDisplayMetrics().widthPixels/density;float usable=Math.max(80f,screenDp-2f*homePadding());return Math.max(1,Math.round(usable*((float)o.outHeight/(float)o.outWidth)));}}catch(Exception ignored){}
        return heroHeight();
    }

'''
assert anchor in s
s=s.replace(anchor,helper+anchor,1)

# dedicated title style helpers
insert='    void applyFont(TextView v,boolean bold){v.setTypeface(appTypeface(),bold?Typeface.BOLD:Typeface.NORMAL);}\n'
assert insert in s
extra=r'''    Typeface topTitleTypeface(){try{String p=prefs.getString("title.fontPath","");if(!p.isEmpty()&&new File(p).exists())return Typeface.createFromFile(p);}catch(Exception ignored){}String f=prefs.getString("title.fontFamily","serif");return "sans".equals(f)?Typeface.SANS_SERIF:"mono".equals(f)?Typeface.MONOSPACE:Typeface.SERIF;}
    int safeColor(String key,int fallback){try{return Color.parseColor(prefs.getString(key,String.format(Locale.US,"#%06X",0xFFFFFF&fallback)));}catch(Exception e){return fallback;}}
    void applyTopTitleStyle(TextView v){v.setTextSize(prefs.getInt("title.size",titleSize()));v.setTextColor(safeColor("title.color",green()));v.setTypeface(topTitleTypeface(),prefs.getBoolean("title.bold",true)?Typeface.BOLD:Typeface.NORMAL);if(Build.VERSION.SDK_INT>=21)v.setLetterSpacing(Math.max(-.05f,Math.min(.20f,prefs.getFloat("title.letterSpacing",0f))));}
'''
s=s.replace(insert,insert+extra,1)

# top brand title and ornament are independently styled
old='TextView orn=text("✦  ◇  ✦",11,gold(),true);orn.setGravity(Gravity.CENTER);orn.setPadding(0,0,0,0);\n        TextView title=text(prefs.getString("label.title","ندای بهشتی"),titleSize(),green(),true);title.setGravity(Gravity.CENTER);title.setPadding(0,0,0,0);title.setSingleLine(true);title.setOnClickListener(v->hiddenTap());\n        brand.addView(orn,new LinearLayout.LayoutParams(-1,dp(24)));brand.addView(title,new LinearLayout.LayoutParams(-1,dp(50)));'
new='TextView orn=text(prefs.getString("title.ornament","✦  ◇  ✦"),11,gold(),true);orn.setGravity(Gravity.CENTER);orn.setPadding(0,0,0,0);orn.setVisibility(prefs.getBoolean("title.showOrnament",true)?View.VISIBLE:View.GONE);\n        TextView title=text(prefs.getString("label.title","ندای بهشتی"),titleSize(),green(),true);applyTopTitleStyle(title);title.setGravity(Gravity.CENTER);title.setPadding(0,0,0,0);title.setSingleLine(true);title.setOnClickListener(v->hiddenTap());\n        brand.addView(orn,new LinearLayout.LayoutParams(-1,dp(24)));brand.addView(title,new LinearLayout.LayoutParams(-1,dp(50)));'
assert old in s
s=s.replace(old,new,1)

# add title editor to owner studio
oldnames='String[] names={"انتشار طراحی فعلی برای همه","رنگ‌ها و استایل","فونت و اندازه‌ها","تصاویر و قاب همراه معنوی","مدیریت تمام آیکن‌ها","زندگی‌نامه و اندیشه‌های آیت‌الله بهشتی","مدیریت صوت گنج معنوی","متن‌ها و عنوان‌ها","ابعاد و چیدمان دقیق صفحه اصلی","مدیریت موذن‌ها","ویرایش پیشرفته JSON","تغییر رمز پنل","بازنشانی فقط ظاهر"};\n        View.OnClickListener[] acts={v->showCentralPublisher(),v->showColorEditor(),v->showFontEditor(),v->showImageEditor(),v->showIconEditor(),v->showBeheshtiEditor(),v->showSpiritualAudioEditor(),v->showLabelEditor(),v->showLayoutEditor(),v->showMuezzinManager(),v->showJsonEditor(),v->changeAdminPin(),v->resetTheme()};'
newnames='String[] names={"انتشار طراحی فعلی برای همه","ویرایش مستقل عنوان بالای اپ — ندای بهشتی","رنگ‌ها و استایل","فونت عمومی و استایل کارت‌ها","تصاویر و قاب همراه معنوی","مدیریت تمام آیکن‌ها","زندگی‌نامه و اندیشه‌های آیت‌الله بهشتی","مدیریت صوت گنج معنوی","متن‌ها و عنوان‌ها","ابعاد و چیدمان دقیق صفحه اصلی","مدیریت موذن‌ها","ویرایش پیشرفته JSON","تغییر رمز پنل","بازنشانی فقط ظاهر"};\n        View.OnClickListener[] acts={v->showCentralPublisher(),v->showTopTitleEditor(),v->showColorEditor(),v->showFontEditor(),v->showImageEditor(),v->showIconEditor(),v->showBeheshtiEditor(),v->showSpiritualAudioEditor(),v->showLabelEditor(),v->showLayoutEditor(),v->showMuezzinManager(),v->showJsonEditor(),v->changeAdminPin(),v->resetTheme()};'
assert oldnames in s
s=s.replace(oldnames,newnames,1)

# labelled field helper so values are never anonymous numbers
field_anchor='    void showColorEditor(){\n'
labelhelper=r'''    EditText ownerField(LinearLayout parent,String label,String help,String value,int inputType){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);card.setPadding(dp(14),dp(10),dp(14),dp(10));card.setBackground(cardBg());TextView l=text(label,15,green(),true);l.setPadding(0,0,0,0);card.addView(l,new LinearLayout.LayoutParams(-1,dp(30)));if(help!=null&&!help.isEmpty()){TextView h=text(help,11,textColor(),false);h.setAlpha(.72f);h.setPadding(0,0,0,dp(4));card.addView(h,new LinearLayout.LayoutParams(-1,-2));}EditText e=field("",value);e.setInputType(inputType);e.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);card.addView(e,new LinearLayout.LayoutParams(-1,dp(52)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(7),0,0);parent.addView(card,lp);return e;}
    void ownerSection(LinearLayout b,String title){TextView t=text(title,18,green(),true);t.setPadding(dp(4),dp(15),dp(4),dp(5));b.addView(t,new LinearLayout.LayoutParams(-1,-2));}
    void showTopTitleEditor(){ScrollView s=scrollPage("عنوان بالای اپ — ندای بهشتی");LinearLayout b=body(s);addCard(b,text("این بخش فقط عنوان بزرگ «ندای بهشتی» در بالای صفحه اصلی را تغییر می‌دهد و روی متن‌ها یا فونت سایر قسمت‌های اپ اثری ندارد.",14,textColor(),false));ownerSection(b,"متن و اندازه");EditText tx=ownerField(b,"متن عنوان","همان نوشته بزرگ بالای Cover",prefs.getString("label.title","ندای بهشتی"),InputType.TYPE_CLASS_TEXT);EditText sz=ownerField(b,"اندازه عنوان (sp)","پیشنهاد: 24 تا 40",String.valueOf(prefs.getInt("title.size",titleSize())),InputType.TYPE_CLASS_NUMBER);EditText col=ownerField(b,"رنگ عنوان (HEX)","مثال: #054232",prefs.getString("title.color",String.format(Locale.US,"#%06X",0xFFFFFF&green())),InputType.TYPE_CLASS_TEXT);EditText ls=ownerField(b,"فاصله حروف","از -0.05 تا 0.20؛ مقدار 0 حالت طبیعی است",String.valueOf(prefs.getFloat("title.letterSpacing",0f)),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);ownerSection(b,"مدل فونت");Spinner fam=new Spinner(this);String[] fs={"کلاسیک / Serif","ساده / Sans","Monospace"};fam.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,fs));String cur=prefs.getString("title.fontFamily","serif");fam.setSelection("sans".equals(cur)?1:"mono".equals(cur)?2:0);addCard(b,fam);CheckBox bold=new CheckBox(this);bold.setText("عنوان ضخیم / Bold باشد");bold.setChecked(prefs.getBoolean("title.bold",true));addCard(b,bold);Button fp=button("انتخاب فونت اختصاصی فقط برای عنوان");fp.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_TITLE_FONT);});b.addView(fp,new LinearLayout.LayoutParams(-1,dp(56)));ownerSection(b,"نشان تزئینی بالای عنوان");CheckBox show=new CheckBox(this);show.setText("نشان ✦ ◇ ✦ نمایش داده شود");show.setChecked(prefs.getBoolean("title.showOrnament",true));addCard(b,show);EditText orn=ownerField(b,"متن نشان تزئینی","می‌توانید نماد دلخواه بنویسید",prefs.getString("title.ornament","✦  ◇  ✦"),InputType.TYPE_CLASS_TEXT);Button save=button("اعمال تغییرات عنوان بالای اپ");save.setOnClickListener(v->{try{int size=Math.max(16,Math.min(60,Integer.parseInt(sz.getText().toString())));float letter=Math.max(-.05f,Math.min(.20f,Float.parseFloat(ls.getText().toString())));Color.parseColor(col.getText().toString().trim());prefs.edit().putString("label.title",tx.getText().toString()).putInt("title.size",size).putString("title.color",col.getText().toString().trim()).putFloat("title.letterSpacing",letter).putString("title.fontFamily",fam.getSelectedItemPosition()==1?"sans":fam.getSelectedItemPosition()==2?"mono":"serif").putBoolean("title.bold",bold.isChecked()).putBoolean("title.showOrnament",show.isChecked()).putString("title.ornament",orn.getText().toString()).apply();toast("عنوان بالای اپ ذخیره شد");showHome();}catch(Exception e){toast("رنگ یا اندازه عنوان درست نیست");}});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));lp.setMargins(0,dp(10),0,0);b.addView(save,lp);Button reset=smallButton("بازنشانی فقط عنوان بالای اپ");reset.setOnClickListener(v->{deletePrefFile("title.fontPath");SharedPreferences.Editor ed=prefs.edit();for(String k:new ArrayList<>(prefs.getAll().keySet()))if(k.startsWith("title."))ed.remove(k);ed.apply();toast("عنوان به حالت اصلی برگشت");showTopTitleEditor();});b.addView(reset,new LinearLayout.LayoutParams(-1,dp(52)));}

'''
assert field_anchor in s
s=s.replace(field_anchor,labelhelper+field_anchor,1)

# replace global font editor with clear labelled controls
start=s.index('    void showFontEditor(){')
end=s.index('\n    void showImageEditor(){',start)
newfont=r'''    void showFontEditor(){
        ScrollView s=scrollPage("فونت عمومی و استایل کارت‌ها");LinearLayout b=body(s);addCard(b,text("این تنظیمات روی متن‌های عمومی اپ و ظاهر کارت‌ها اعمال می‌شود. عنوان بزرگ «ندای بهشتی» تنظیم مستقل خودش را دارد.",14,textColor(),false));
        ownerSection(b,"فونت عمومی اپ");Spinner fam=new Spinner(this);String[] fs={"کلاسیک / Serif","ساده / Sans","Monospace"};fam.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,fs));String cur=prefs.getString("theme.fontFamily","sans");fam.setSelection("sans".equals(cur)?1:"mono".equals(cur)?2:0);addCard(b,fam);
        EditText scale=ownerField(b,"مقیاس همه متن‌های عمومی","1.00 = اندازه اصلی؛ بازه 0.65 تا 1.80",String.valueOf(textScale()),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Button pick=button("انتخاب فونت TTF/OTF برای متن‌های عمومی");pick.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_FONT);});b.addView(pick,new LinearLayout.LayoutParams(-1,dp(56)));
        ownerSection(b,"ظاهر کارت‌ها");EditText rad=ownerField(b,"گردی گوشه کارت‌ها (dp)","0 = چهارگوش، 24 تا 36 = نرم و گرد",String.valueOf(radius()),InputType.TYPE_CLASS_NUMBER);EditText st=ownerField(b,"ضخامت خط دور کارت‌ها (dp)","0 = بدون خط؛ 1 یا 2 پیشنهاد می‌شود",String.valueOf(stroke()),InputType.TYPE_CLASS_NUMBER);EditText sp=ownerField(b,"فاصله‌گذاری عمومی (dp)","فاصله داخلی عناصر؛ 2 تا 30",String.valueOf(spacing()),InputType.TYPE_CLASS_NUMBER);
        Button sv=button("ذخیره فونت عمومی و استایل کارت‌ها");sv.setOnClickListener(v->{try{float sc=Float.parseFloat(scale.getText().toString());int rr=Integer.parseInt(rad.getText().toString()),ss=Integer.parseInt(st.getText().toString()),spa=Integer.parseInt(sp.getText().toString());prefs.edit().putString("theme.fontFamily",fam.getSelectedItemPosition()==1?"sans":fam.getSelectedItemPosition()==2?"mono":"serif").putFloat("theme.textScale",Math.max(.65f,Math.min(1.8f,sc))).putInt("theme.radius",Math.max(0,Math.min(60,rr))).putInt("theme.stroke",Math.max(0,Math.min(8,ss))).putInt("theme.spacing",Math.max(2,Math.min(30,spa))).apply();toast("تنظیمات عمومی ذخیره شد");showAdminStudio();}catch(Exception e){toast("یکی از مقادیر درست نیست");}});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));lp.setMargins(0,dp(10),0,0);b.addView(sv,lp);
    }
'''
s=s[:start]+newfont+s[end:]

# replace layout editor: every number has visible label + explanation
start=s.index('    void showLayoutEditor(){')
end=s.index('\n    void migrateHomeDefaults(){',start)
newlayout=r'''    void showLayoutEditor(){
        ScrollView s=scrollPage("ابعاد و چیدمان دقیق");LinearLayout b=body(s);addCard(b,text("هر عدد اکنون نام و کاربرد مشخص دارد. واحد اندازه‌های صفحه dp است و اندازه متن sp. تغییرات فقط پس از زدن «اعمال چیدمان» ذخیره می‌شوند.",14,textColor(),false));
        String[] k={"padding","gap","heroHeight","prayerHeight","featureHeight","iconSize","navHeight","titleSize","heroTitleSize","quoteSize"};
        String[] n={"حاشیه چپ و راست کل صفحه (dp)","فاصله بین بخش‌های صفحه (dp)","ارتفاع قاب همراه معنوی در حالت بدون Cover (dp)","ارتفاع کارت اوقات شرعی (dp)","ارتفاع هر کارت قابلیت مثل قرآن/دعا/اذان (dp)","اندازه آیکن‌های کارت‌های قابلیت (dp)","ارتفاع نوار پایین صفحه (dp)","اندازه پیش‌فرض عنوان بالای اپ (sp)","اندازه عنوان داخل قاب قدیمی همراه معنوی (sp)","اندازه متن نقل‌قول (sp)"};
        String[] help={"فضای خالی دو طرف صفحه؛ مقدار فعلی فقط عرض محتوا را کنترل می‌کند.","فاصله عمودی میان Cover، اوقات شرعی و کارت‌ها.","وقتی Cover سفارشی انتخاب شده باشد این عدد نادیده گرفته می‌شود و ارتفاع دقیقاً از نسبت خود عکس محاسبه می‌شود.","ارتفاع کل بخش سبز اوقات شرعی.","ارتفاع کارت‌های سفید قابلیت‌ها در صفحه اصلی.","فقط اندازه نماد داخل کارت‌های قابلیت؛ خود کارت جداگانه تنظیم می‌شود.","ارتفاع نوار خانه/مقالات/رسانه/من/بیشتر.","مقدار پایه است؛ برای کنترل کامل عنوان از «ویرایش مستقل عنوان بالای اپ» استفاده کنید.","فقط برای قاب قدیمی؛ روی Cover تصویری نوشته‌ای اضافه نمی‌شود.","اندازه نوشته کارت نقل‌قول پایین صفحه."};
        int[] d={14,12,218,220,132,54,76,29,31,16};int[] mn={6,4,150,170,105,34,62,20,22,12};int[] mx={30,30,340,320,190,84,100,42,46,26};EditText[] es=new EditText[k.length];for(int i=0;i<k.length;i++)es[i]=ownerField(b,n[i],help[i],String.valueOf(homeInt(k[i],d[i],mn[i],mx[i])),InputType.TYPE_CLASS_NUMBER);
        Button sv=button("اعمال چیدمان و بازگشت به صفحه اصلی");sv.setOnClickListener(v->{try{SharedPreferences.Editor ed=prefs.edit();for(int i=0;i<k.length;i++){int val=Integer.parseInt(es[i].getText().toString());val=Math.max(mn[i],Math.min(mx[i],val));ed.putInt("home."+k[i],val);}ed.apply();toast("چیدمان ذخیره شد");showHome();}catch(Exception e){toast("یکی از عددها درست نیست");}});LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(58));slp.setMargins(0,dp(10),0,0);b.addView(sv,slp);
        Button reset=smallButton("بازنشانی فقط ابعاد صفحه اصلی");reset.setOnClickListener(v->{SharedPreferences.Editor ed=prefs.edit();for(String k0:k)ed.remove("home."+k0);ed.apply();migrateHomeDefaults();toast("ابعاد بازنشانی شد");showLayoutEditor();});b.addView(reset,new LinearLayout.LayoutParams(-1,dp(52)));
    }
'''
s=s[:start]+newlayout+s[end:]

# improve icon editor with visible groups/current status
start=s.index('    void showIconEditor(){')
end=s.index('\n    File beheshtiFile(){',start)
newicons=r'''    void showIconEditor(){
        ScrollView sc=scrollPage("مدیریت آیکن‌ها");LinearLayout b=body(sc);addCard(b,text("هر ردیف دقیقاً نام آیکنی را که تغییر می‌دهید نشان می‌دهد. اگر کنار آن «سفارشی» نوشته باشد، فایل انتخابی شما فعال است.",14,textColor(),false));
        String[][] groups={{"#کارت‌های صفحه اصلی",""},{"feature.adhan","اذان"},{"feature.duas","دعاها"},{"feature.quran","قرآن"},{"feature.settings","تنظیمات"},{"feature.tasbih","ذکرشمار"},{"feature.qibla","قبله"},{"#نوار پایین",""},{"bottom.home","خانه"},{"bottom.article","مقالات"},{"bottom.media","رسانه"},{"bottom.person","من"},{"bottom.more","بیشتر"},{"#بالای صفحه",""},{"top.search","جستجو"},{"top.bell","اعلان"},{"#اوقات شرعی",""},{"prayer.فجر","فجر"},{"prayer.طلوع","طلوع"},{"prayer.ظهر","ظهر"},{"prayer.مغرب","مغرب"},{"prayer.عشاء","عشاء"}};
        for(String[] g:groups){if(g[0].startsWith("#")){ownerSection(b,g[0].substring(1));continue;}String path=prefs.getString("icon."+g[0],"");String state=!path.isEmpty()&&new File(path).exists()?" — سفارشی ✓":" — آیکن اصلی";Button x=button("آیکن «"+g[1]+"»"+state);x.setOnClickListener(v->{pendingIconKey=g[0];pickImage(REQ_ICON);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.setMargins(0,dp(6),0,0);b.addView(x,lp);}
        EditText size=ownerField(b,"اندازه آیکن‌های کارت‌های صفحه اصلی (dp)","همان آیکن‌های قرآن، دعاها، اذان و...؛ بازه 34 تا 84",String.valueOf(iconSize()),InputType.TYPE_CLASS_NUMBER);Button apply=smallButton("اعمال اندازه آیکن‌ها");apply.setOnClickListener(v->{try{int z=Math.max(34,Math.min(84,Integer.parseInt(size.getText().toString())));prefs.edit().putInt("home.iconSize",z).apply();toast("اندازه آیکن‌ها ذخیره شد");showIconEditor();}catch(Exception e){toast("اندازه درست نیست");}});b.addView(apply,new LinearLayout.LayoutParams(-1,dp(52)));
        Button reset=smallButton("بازنشانی همه آیکن‌ها به حالت اصلی");reset.setOnClickListener(v->{SharedPreferences.Editor ed=prefs.edit();for(String k:new ArrayList<>(prefs.getAll().keySet()))if(k.startsWith("icon.")){deletePrefFile(k);ed.remove(k);}ed.apply();toast("آیکن‌ها بازنشانی شد");showIconEditor();});b.addView(reset,new LinearLayout.LayoutParams(-1,dp(52)));
    }
'''
s=s[:start]+newicons+s[end:]

# title font picker result
needle='if(req==REQ_FONT){String p=copyUriToFile(u,"custom_font");Typeface.createFromFile(p);prefs.edit().putString("theme.fontPath",p).apply();toast("فونت سفارشی فعال شد");showFontEditor();}'
assert needle in s
s=s.replace(needle,needle+'\n            if(req==REQ_TITLE_FONT){String p=copyUriToFile(u,"title_font");Typeface.createFromFile(p);prefs.edit().putString("title.fontPath",p).apply();toast("فونت اختصاصی عنوان فعال شد");showTopTitleEditor();}',1)

# central publishing includes dedicated title settings
s=s.replace('return k.startsWith("theme.")||k.startsWith("home.")||k.startsWith("label.")||k.startsWith("icon.")', 'return k.startsWith("theme.")||k.startsWith("home.")||k.startsWith("title.")||k.startsWith("label.")||k.startsWith("icon.")',1)

p.write_text(s,encoding='utf-8')
print('v4.7 editor polish applied')
