from pathlib import Path
p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/MainActivity.java')
s=p.read_text(encoding='utf-8')
# Three verified redistributable adhan recordings are bundled in the APK.  Do not depend on owner-panel downloads.
s=s.replace('final String[] builtInAdhanNames={"اباذر الحلواجي","موذن‌زاده اردبیلی","هادی محمدجواد شمس‌الدین","الحاج بوعباس"};','final String[] builtInAdhanNames={"اذان کامل ۱","اذان کامل ۲","اذان کامل ۳"};')
s=s.replace('final String[] builtInAdhanUrls={"https://shiavoice.com/stream-0wonG","https://shiavoice.com/stream-j7q3b","https://shiavoice.com/stream-7Lntd","https://shiavoice.com/stream-EPfRi"};','final String[] builtInAdhanUrls={"","",""};')
start=s.find('    File builtInAdhanCache(int index)')
end=s.find('\n    void showAdhan(){',start)
if start>=0 and end>start:
    s=s[:start]+'''    File builtInAdhanCache(int index){return new File(getFilesDir(),"bundled_adhan_"+index);}\n    void cacheSelectedBuiltInAdhan(int index){toast(index>=0&&index<builtInAdhanNames.length?"این اذان از قبل داخل برنامه و کاملاً آفلاین است":"فایل موذن شخصی روی دستگاه شما ذخیره می‌شود");}\n'''+s[end:]
s=s.replace('چهار موذن شیعه مستقل از مجموعه صوت‌الشیعة در دسترس است؛ علاوه بر آن هشت جایگاه برای فایل‌های شخصی خودتان دارید. فایل غیرجعفری نسخه قبل حذف شده است.','سه اذان کامل و بلند از قبل داخل برنامه قرار دارد و بدون اینترنت پخش می‌شود؛ علاوه بر آن هشت جایگاه برای فایل‌های شخصی شما موجود است.')
s=s.replace('Button offline=button("ذخیره موذن انتخابی برای آفلاین");offline.setOnClickListener(v->cacheSelectedBuiltInAdhan(sp.getSelectedItemPosition()));b.addView(offline,lp);','Button offline=button("وضعیت آفلاین اذان");offline.setOnClickListener(v->cacheSelectedBuiltInAdhan(sp.getSelectedItemPosition()));b.addView(offline,lp);')
s=s.replace('منبع چهار پروفایل داخلی: ShiaVoice / صوت الشيعة. اگر یک‌بار گزینه آفلاین را بزنید، همان موذن در حافظه برنامه ذخیره می‌شود.','سه فایل داخلی همراه خود APK نصب می‌شود؛ پخش اذان به اینترنت یا پنل مخفی وابسته نیست. صدای اذان از کانال Alarm پخش می‌شود.')
p.write_text(s,encoding='utf-8')
assert 'سه اذان کامل و بلند' in s
print('Bundled offline adhan UI applied')
