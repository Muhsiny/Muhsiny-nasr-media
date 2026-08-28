from pathlib import Path
p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/MainActivity.java')
s=p.read_text(encoding='utf-8')
start=s.index('    int heroDisplayHeight(){')
end=s.index('\n    View makePrayerPanel(){', start)
locked=r'''    int heroDisplayHeight(){
        try{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeResource(getResources(),R.drawable.nadaye_locked_cover,o);if(o.outWidth>0&&o.outHeight>0){float density=getResources().getDisplayMetrics().density;float screenDp=getResources().getDisplayMetrics().widthPixels/density;float usable=Math.max(80f,screenDp-2f*homePadding());return Math.max(1,Math.round(usable*((float)o.outHeight/(float)o.outWidth)));}}catch(Exception ignored){}
        return Math.max(1,Math.round((getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density-2f*homePadding())*(640f/1536f)));
    }

    View makeHeroCard(){
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Color.TRANSPARENT);frame.setClipChildren(false);frame.setClipToPadding(false);
        ImageView cover=new ImageView(this);cover.setImageResource(R.drawable.nadaye_locked_cover);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);cover.setAdjustViewBounds(false);cover.setAlpha(1f);cover.setBackgroundColor(Color.TRANSPARENT);cover.setOnLongClickListener(v->{showAdminGate();return true;});
        frame.addView(cover,new FrameLayout.LayoutParams(-1,-1));return frame;
    }
'''
s=s[:start]+locked+s[end:]
# The locked cover is part of the APK, not a user-editable field.
cover_line='''        Button cover=button("تغییر کل قاب همراه معنوی مثل Cover");cover.setOnClickListener(v->pickImage(REQ_HERO));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.setMargins(0,dp(8),0,0);b.addView(cover,lp); Button leader=button("تغییر تصویر رهبر");leader.setOnClickListener(v->pickImage(REQ_LEADER));b.addView(leader,lp);'''
if cover_line in s:
    s=s.replace(cover_line,'''        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.setMargins(0,dp(8),0,0);addCard(b,text("کاور اصلی صفحه قفل است و از داخل اپ تغییر نمی‌کند.",14,green(),true)); Button leader=button("تغییر تصویر رهبر (فقط بخش‌های دیگر برنامه)");leader.setOnClickListener(v->pickImage(REQ_LEADER));b.addView(leader,lp);''',1)
s=s.replace('if(req==REQ_HERO){String p=copyUriToFile(u,"hero_cover");prefs.edit().putString("theme.heroCoverPath",p).apply();toast("قاب همراه معنوی ذخیره شد");showImageEditor();return;}','if(req==REQ_HERO){toast("کاور اصلی قفل است");return;}',1)
s=s.replace('deletePrefFile("theme.heroCoverPath");','',1)
s=s.replace('.remove("theme.heroCoverPath").remove("theme.heroCoverAlpha").remove("theme.heroOverlayAlpha")','.remove("theme.heroCoverAlpha").remove("theme.heroOverlayAlpha")',1)
p.write_text(s,encoding='utf-8')
assert 'R.drawable.nadaye_locked_cover' in s
assert 'کاور اصلی صفحه قفل است' in s
print('v5.1 locked cover applied')
