from pathlib import Path
p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/MainActivity.java')
s=p.read_text(encoding='utf-8')

# Public release must not depend on the old central/hidden-publisher experiment.
s=s.replace('    static final String CENTRAL_BASE="https://nadaye-beheshti-central-api.lovable.app";\n','')
s=s.replace('        centralSyncAsync(false);\n','')
start=s.find('    // ---------- Central owner publishing ----------')
end=s.find('    // ---------- Hidden owner studio ----------')
if start!=-1 and end!=-1 and end>start:
    s=s[:start]+s[end:]

old='String[] names={"انتشار طراحی فعلی برای همه","ویرایش مستقل عنوان بالای اپ — ندای بهشتی","رنگ‌ها و استایل","فونت عمومی و استایل کارت‌ها","تصاویر و قاب همراه معنوی","مدیریت تمام آیکن‌ها","زندگی‌نامه و اندیشه‌های آیت‌الله بهشتی","مدیریت صوت گنج معنوی","متن‌ها و عنوان‌ها","ابعاد و چیدمان دقیق صفحه اصلی","مدیریت موذن‌ها","ویرایش پیشرفته JSON","تغییر رمز پنل","بازنشانی فقط ظاهر"};\n        View.OnClickListener[] acts={v->showCentralPublisher(),v->showTopTitleEditor(),v->showColorEditor(),v->showFontEditor(),v->showImageEditor(),v->showIconEditor(),v->showBeheshtiEditor(),v->showSpiritualAudioEditor(),v->showLabelEditor(),v->showLayoutEditor(),v->showMuezzinManager(),v->showJsonEditor(),v->changeAdminPin(),v->resetTheme()};'
new='String[] names={"ویرایش عنوان بالای اپ","رنگ‌ها و استایل","فونت عمومی و استایل کارت‌ها","تصاویر محلی","مدیریت تمام آیکن‌ها","زندگی‌نامه و اندیشه‌های آیت‌الله بهشتی","مدیریت صوت گنج معنوی","متن‌ها و عنوان‌ها","ابعاد و چیدمان دقیق صفحه اصلی","مدیریت موذن‌ها","ویرایش پیشرفته JSON","تغییر رمز پنل","بازنشانی فقط ظاهر"};\n        View.OnClickListener[] acts={v->showTopTitleEditor(),v->showColorEditor(),v->showFontEditor(),v->showImageEditor(),v->showIconEditor(),v->showBeheshtiEditor(),v->showSpiritualAudioEditor(),v->showLabelEditor(),v->showLayoutEditor(),v->showMuezzinManager(),v->showJsonEditor(),v->changeAdminPin(),v->resetTheme()};'
if old in s:s=s.replace(old,new,1)

s=s.replace('عنوان بالای اپ — ندای بهشتی','عنوان بالای اپ — کلمه طیبه')
s=s.replace('این بخش فقط عنوان بزرگ «ندای بهشتی» در بالای صفحه اصلی را تغییر می‌دهد و روی متن‌ها یا فونت سایر قسمت‌های اپ اثری ندارد.','این بخش فقط کلمه طیبه در بالای صفحه اصلی را تغییر می‌دهد و روی سایر بخش‌های اپ اثری ندارد.')
s=s.replace('prefs.getBoolean("title.bold",true)','prefs.getBoolean("title.bold",false)')
s=s.replace('prefs.getBoolean("title.showOrnament",true)','prefs.getBoolean("title.showOrnament",false)')

# The canonical cover is release-owned and cannot be changed from the local studio.
s=s.replace('"تصاویر و قاب همراه معنوی"','"تصاویر محلی"')

p.write_text(s,encoding='utf-8')
assert 'Central owner publishing' not in s
assert 'centralSyncAsync(false)' not in s
assert 'showCentralPublisher()' not in s
print('v5.3 release cleanup applied: obsolete central publisher removed')
