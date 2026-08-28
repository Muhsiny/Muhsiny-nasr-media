# admin studio methods list
old='String[] names={"رنگ‌ها و استایل","فونت و اندازه‌ها","تصاویر و پس‌زمینه","متن‌ها و عنوان‌ها","ابعاد و چیدمان دقیق صفحه اصلی","مدیریت موذن‌ها","ویرایش پیشرفته JSON","تغییر رمز پنل","بازنشانی فقط ظاهر"};\n        View.OnClickListener[] acts={v->showColorEditor(),v->showFontEditor(),v->showImageEditor(),v->showLabelEditor(),v->showLayoutEditor(),v->showMuezzinManager(),v->showJsonEditor(),v->changeAdminPin(),v->resetTheme()};'
new='String[] names={"رنگ‌ها و استایل","فونت و اندازه‌ها","تصاویر و قاب همراه معنوی","مدیریت تمام آیکن‌ها","زندگی‌نامه و اندیشه‌های آیت‌الله بهشتی","مدیریت صوت گنج معنوی","متن‌ها و عنوان‌ها","ابعاد و چیدمان دقیق صفحه اصلی","مدیریت موذن‌ها","ویرایش پیشرفته JSON","تغییر رمز پنل","بازنشانی فقط ظاهر"};\n        View.OnClickListener[] acts={v->showColorEditor(),v->showFontEditor(),v->showImageEditor(),v->showIconEditor(),v->showBeheshtiEditor(),v->showSpiritualAudioEditor(),v->showLabelEditor(),v->showLayoutEditor(),v->showMuezzinManager(),v->showJsonEditor(),v->changeAdminPin(),v->resetTheme()};'
assert old in s
s=s.replace(old,new,1)

needle='Button leader=button("تغییر تصویر رهبر");leader.setOnClickListener(v->pickImage(REQ_LEADER));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.setMargins(0,dp(8),0,0);b.addView(leader,lp);'
repl='Button cover=button("تغییر کل قاب همراه معنوی مثل Cover");cover.setOnClickListener(v->pickImage(REQ_HERO));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.setMargins(0,dp(8),0,0);b.addView(cover,lp); EditText ca=field("شفافیت تصویر قاب 0.15 تا 1",String.valueOf(prefs.getFloat("theme.heroCoverAlpha",1f)));ca.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);addCard(b,ca);EditText oa=field("لایه روشن روی قاب 0 تا 0.92",String.valueOf(prefs.getFloat("theme.heroOverlayAlpha",.18f)));oa.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);addCard(b,oa); Button leader=button("تغییر تصویر رهبر");leader.setOnClickListener(v->pickImage(REQ_LEADER));b.addView(leader,lp);'
assert needle in s
s=s.replace(needle,repl,1)
needle2='prefs.edit().putFloat("theme.backgroundAlpha",Math.max(0f,Math.min(1f,a))).putFloat("theme.leaderWidth",w)'
repl2='prefs.edit().putFloat("theme.backgroundAlpha",Math.max(0f,Math.min(1f,a))).putFloat("theme.heroCoverAlpha",Math.max(.15f,Math.min(1f,Float.parseFloat(ca.getText().toString())))).putFloat("theme.heroOverlayAlpha",Math.max(0f,Math.min(.92f,Float.parseFloat(oa.getText().toString())))).putFloat("theme.leaderWidth",w)'
assert needle2 in s
s=s.replace(needle2,repl2,1)
s=s.replace('deletePrefFile("theme.backgroundPath");deletePrefFile("theme.leaderPath");prefs.edit().remove("theme.backgroundPath").remove("theme.leaderPath")', 'deletePrefFile("theme.backgroundPath");deletePrefFile("theme.leaderPath");deletePrefFile("theme.heroCoverPath");prefs.edit().remove("theme.backgroundPath").remove("theme.leaderPath").remove("theme.heroCoverPath").remove("theme.heroCoverAlpha").remove("theme.heroOverlayAlpha")',1)

s=s.replace('protected void onDraw(Canvas c0){super.onDraw(c0);setup();float w=getWidth()', 'protected void onDraw(Canvas c0){super.onDraw(c0);if(drawCustomIcon(c0,"feature."+type,getWidth(),getHeight()))return;setup();float w=getWidth()',1)
s=s.replace('protected void onDraw(Canvas c0){super.onDraw(c0);float cx=getWidth()/2f,cy=getHeight()/2f;p.setColor(green());', 'protected void onDraw(Canvas c0){super.onDraw(c0);if(drawCustomIcon(c0,"top."+type,getWidth(),getHeight()))return;float cx=getWidth()/2f,cy=getHeight()/2f;p.setColor(green());',1)
s=s.replace('protected void onDraw(Canvas c0){super.onDraw(c0);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.26f;p.setColor(sel?gold():green());', 'protected void onDraw(Canvas c0){super.onDraw(c0);if(drawCustomIcon(c0,"bottom."+type,getWidth(),getHeight()))return;float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.26f;p.setColor(sel?gold():green());',1)
s=s.replace('protected void onDraw(Canvas c0){super.onDraw(c0);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.28f;p.setColor(active?gold():Color.WHITE);', 'protected void onDraw(Canvas c0){super.onDraw(c0);if(drawCustomIcon(c0,"prayer."+key,getWidth(),getHeight()))return;float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.28f;p.setColor(active?gold():Color.WHITE);',1)

oldmore='String[] n={"اوقات شرعی","اذان و موذن‌ها","قبله‌نما","ذکرشمار","تنظیمات","جستجو"};View.OnClickListener[] a={v->showPrayerTimes(),v->showAdhan(),v->showQibla(),v->showTasbih(),v->showSettings(),v->showGlobalSearch()};'
newmore='String[] n={"زندگی‌نامه و اندیشه‌های آیت‌الله بهشتی","گنج معنوی کامل","اوقات شرعی","اذان و موذن‌ها","قبله‌نما","ذکرشمار","تنظیمات","جستجو"};View.OnClickListener[] a={v->showBeheshtiLibrary(),v->showSpiritualTreasury(),v->showPrayerTimes(),v->showAdhan(),v->showQibla(),v->showTasbih(),v->showSettings(),v->showGlobalSearch()};'
assert oldmore in s
s=s.replace(oldmore,newmore,1)
s=s.replace('v->showDuas());','v->showSpiritualTreasury());',1)

insert_at=s.index('    void showLabelEditor(){')
methods=r'''
