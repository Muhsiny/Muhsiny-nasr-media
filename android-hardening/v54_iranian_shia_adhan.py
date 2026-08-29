from pathlib import Path

# Public UI: one fully bundled Iranian adhan + three curated Shia media streams.
p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/MainActivity.java')
s=p.read_text(encoding='utf-8')
s=s.replace('final String[] builtInAdhanNames={"اذان کامل ۱","اذان کامل ۲","اذان کامل ۳"};',
'''final String[] builtInAdhanNames={"حسین صبحدل — ایران (آفلاین)","مؤذن‌زاده اردبیلی — صوت‌الشیعه","اباذر الحلواجي — صوت‌الشيعة","سعید طوسی — صوت‌الشيعة"};''')
s=s.replace('final String[] builtInAdhanUrls={"","",""};',
'''final String[] builtInAdhanUrls={"","https://shiavoice.com/stream-j7q3b","https://shiavoice.com/stream-0wonG","https://shiavoice.com/stream-JFFgY"};''')
s=s.replace('سه اذان کامل و بلند از قبل داخل برنامه قرار دارد و بدون اینترنت پخش می‌شود؛ علاوه بر آن هشت جایگاه برای فایل‌های شخصی شما موجود است.',
'''اذان کامل استاد حسین صبحدل از ایران داخل خود برنامه و آفلاین است. سه انتخاب شیعی دیگر از رسانه صوت‌الشیعه برای مؤذن‌زاده اردبیلی، اباذر الحلواجي و سعید طوسی در دسترس است؛ اگر اینترنت آن‌ها قطع شود، اذان آفلاین صبحدل خودکار پخش می‌شود.''')
s=s.replace('سه فایل داخلی همراه خود APK نصب می‌شود؛ پخش اذان به اینترنت یا پنل مخفی وابسته نیست. صدای اذان از کانال Alarm پخش می‌شود.',
'''اذان حسین صبحدل همراه APK نصب می‌شود و انتخاب پیش‌فرض و fallback آفلاین است. مؤذن‌زاده، اباذر الحلواجي و سعید طوسی از صوت‌الشیعه پخش می‌شوند. صدای اذان از کانال Alarm پخش می‌شود.''')
s=s.replace('void cacheSelectedBuiltInAdhan(int index){toast(index>=0&&index<builtInAdhanNames.length?"این اذان از قبل داخل برنامه و کاملاً آفلاین است":"فایل موذن شخصی روی دستگاه شما ذخیره می‌شود");}',
'''void cacheSelectedBuiltInAdhan(int index){toast(index==0?"اذان حسین صبحدل داخل برنامه و کاملاً آفلاین است":(index>0&&index<builtInAdhanNames.length?"این انتخاب از رسانه صوت‌الشیعه آنلاین پخش می‌شود و در قطع اینترنت به صبحدل برمی‌گردد":"فایل موذن شخصی روی دستگاه شما ذخیره می‌شود"));}''')
p.write_text(s,encoding='utf-8')

# Runtime service: index 0 is the bundled Iranian adhan. Indexes 1..3 are curated
# ShiaVoice streams. Network/media failure always falls back to the bundled Iranian file.
a=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/AdhanService.java')
t=a.read_text(encoding='utf-8')
t=t.replace('static final int[] BUILTIN_RAW={R.raw.default_adhan,R.raw.adhan_beautiful,R.raw.adhan_azan};',
'''static final int[] BUILTIN_RAW={R.raw.default_adhan,R.raw.adhan_beautiful,R.raw.adhan_azan};
    static final String[] REMOTE_SHIA={"https://shiavoice.com/stream-j7q3b","https://shiavoice.com/stream-0wonG","https://shiavoice.com/stream-JFFgY"};''')
start=t.index('    Uri selectedUri(){')
end=t.index('\n    void playSelected(){',start)
new='''    Uri selectedUri(){
        SharedPreferences p=getSharedPreferences("nadaye",MODE_PRIVATE);
        int selected=p.getInt("adhan.selected",0);
        if(selected==0)return Uri.parse("android.resource://"+getPackageName()+"/"+R.raw.default_adhan);
        if(selected>=1&&selected<=REMOTE_SHIA.length)return Uri.parse(REMOTE_SHIA[selected-1]);
        int slot=selected-(REMOTE_SHIA.length+1)+1;
        if(slot>=1&&slot<=8){
            String custom=p.getString("adhan.slot."+slot+".uri","");
            if(!custom.isEmpty())return Uri.parse(custom);
        }
        return Uri.parse("android.resource://"+getPackageName()+"/"+R.raw.default_adhan);
    }
'''
t=t[:start]+new+t[end:]
t=t.replace('player.setDataSource(this,u);','''if(u!=null && ("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()))) player.setDataSource(u.toString()); else player.setDataSource(this,u);''')
a.write_text(t,encoding='utf-8')

assert 'حسین صبحدل — ایران (آفلاین)' in s
assert 'stream-j7q3b' in s and 'stream-0wonG' in s and 'stream-JFFgY' in s
assert 'REMOTE_SHIA' in t and 'https://shiavoice.com/stream-j7q3b' in t
print('v5.4 Iranian/Shia adhan profiles applied')
