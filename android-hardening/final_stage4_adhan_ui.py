from pathlib import Path
p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/MainActivity.java')
s=p.read_text(encoding='utf-8')
# Three verified redistributable adhan recordings are bundled in the APK. Do not depend on owner-panel downloads.
s=s.replace('final String[] builtInAdhanNames={"اباذر الحلواجي","موذن‌زاده اردبیلی","هادی محمدجواد شمس‌الدین","الحاج بوعباس"};','final String[] builtInAdhanNames={"اذان کامل ۱","اذان کامل ۲","اذان کامل ۳"};')
s=s.replace('final String[] builtInAdhanUrls={"https://shiavoice.com/stream-0wonG","https://shiavoice.com/stream-j7q3b","https://shiavoice.com/stream-7Lntd","https://shiavoice.com/stream-EPfRi"};','final String[] builtInAdhanUrls={"","",""};')
start=s.find('    File builtInAdhanCache(int index)')
end=s.find('\n    void showAdhan(){',start)
if start>=0 and end>start:
    s=s[:start]+'''    File builtInAdhanCache(int index){return new File(getFilesDir(),"bundled_adhan_"+index);}\n    void cacheSelectedBuiltInAdhan(int index){toast(index>=0&&index<builtInAdhanNames.length?"این اذان از قبل داخل برنامه و کاملاً آفلاین است":"فایل موذن شخصی روی دستگاه شما ذخیره می‌شود");}\n'''+s[end:]
s=s.replace('چهار موذن شیعه مستقل از مجموعه صوت‌الشيعة در دسترس است؛ علاوه بر آن هشت جایگاه برای فایل‌های شخصی خودتان دارید. فایل غیرجعفری نسخه قبل حذف شده است.','سه اذان کامل و بلند از قبل داخل برنامه قرار دارد و بدون اینترنت پخش می‌شود؛ علاوه بر آن هشت جایگاه برای فایل‌های شخصی شما موجود است.')
s=s.replace('Button offline=button("ذخیره موذن انتخابی برای آفلاین");offline.setOnClickListener(v->cacheSelectedBuiltInAdhan(sp.getSelectedItemPosition()));b.addView(offline,lp);','Button offline=button("وضعیت آفلاین اذان");offline.setOnClickListener(v->cacheSelectedBuiltInAdhan(sp.getSelectedItemPosition()));b.addView(offline,lp);')
s=s.replace('منبع چهار پروفایل داخلی: ShiaVoice / صوت الشيعة. اگر یک‌بار گزینه آفلاین را بزنید، همان موذن در حافظه برنامه ذخیره می‌شود.','سه فایل داخلی همراه خود APK نصب می‌شود؛ پخش اذان به اینترنت یا پنل مخفی وابسته نیست. صدای اذان از کانال Alarm پخش می‌شود.')

# Release cleanup: remove obsolete central publisher/sync experiment so the public APK has no incomplete runtime dependency.
s=s.replace('    static final String CENTRAL_BASE="https://nadaye-beheshti-central-api.lovable.app";\n','')
s=s.replace('        centralSyncAsync(false);\n','')
cs=s.find('    // ---------- Central owner publishing ----------')
ce=s.find('    // ---------- Hidden owner studio ----------')
if cs!=-1 and ce!=-1 and ce>cs:
    s=s[:cs]+s[ce:]
old='String[] names={"انتشار طراحی فعلی برای همه","ویرایش مستقل عنوان بالای اپ — ندای بهشتی","رنگ‌ها و استایل","فونت عمومی و استایل کارت‌ها","تصاویر و قاب همراه معنوی","مدیریت تمام آیکن‌ها","زندگی‌نامه و اندیشه‌های آیت‌الله بهشتی","مدیریت صوت گنج معنوی","متن‌ها و عنوان‌ها","ابعاد و چیدمان دقیق صفحه اصلی","مدیریت موذن‌ها","ویرایش پیشرفته JSON","تغییر رمز پنل","بازنشانی فقط ظاهر"};\n        View.OnClickListener[] acts={v->showCentralPublisher(),v->showTopTitleEditor(),v->showColorEditor(),v->showFontEditor(),v->showImageEditor(),v->showIconEditor(),v->showBeheshtiEditor(),v->showSpiritualAudioEditor(),v->showLabelEditor(),v->showLayoutEditor(),v->showMuezzinManager(),v->showJsonEditor(),v->changeAdminPin(),v->resetTheme()};'
new='String[] names={"ویرایش عنوان بالای اپ","رنگ‌ها و استایل","فونت عمومی و استایل کارت‌ها","تصاویر محلی","مدیریت تمام آیکن‌ها","زندگی‌نامه و اندیشه‌های آیت‌الله بهشتی","مدیریت صوت گنج معنوی","متن‌ها و عنوان‌ها","ابعاد و چیدمان دقیق صفحه اصلی","مدیریت موذن‌ها","ویرایش پیشرفته JSON","تغییر رمز پنل","بازنشانی فقط ظاهر"};\n        View.OnClickListener[] acts={v->showTopTitleEditor(),v->showColorEditor(),v->showFontEditor(),v->showImageEditor(),v->showIconEditor(),v->showBeheshtiEditor(),v->showSpiritualAudioEditor(),v->showLabelEditor(),v->showLayoutEditor(),v->showMuezzinManager(),v->showJsonEditor(),v->changeAdminPin(),v->resetTheme()};'
if old in s:s=s.replace(old,new,1)
s=s.replace('عنوان بالای اپ — ندای بهشتی','عنوان بالای اپ — کلمه طیبه')
s=s.replace('این بخش فقط عنوان بزرگ «ندای بهشتی» در بالای صفحه اصلی را تغییر می‌دهد و روی متن‌ها یا فونت سایر قسمت‌های اپ اثری ندارد.','این بخش فقط کلمه طیبه در بالای صفحه اصلی را تغییر می‌دهد و روی سایر بخش‌های اپ اثری ندارد.')
s=s.replace('prefs.getBoolean("title.bold",true)','prefs.getBoolean("title.bold",false)')
s=s.replace('prefs.getBoolean("title.showOrnament",true)','prefs.getBoolean("title.showOrnament",false)')
p.write_text(s,encoding='utf-8')
assert 'سه اذان کامل و بلند' in s
assert 'Central owner publishing' not in s
assert 'centralSyncAsync(false)' not in s
assert 'showCentralPublisher()' not in s
print('Bundled offline adhan UI + release cleanup applied')
