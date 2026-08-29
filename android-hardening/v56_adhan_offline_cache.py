from pathlib import Path

p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/MainActivity.java')
s=p.read_text(encoding='utf-8')
old='void cacheSelectedBuiltInAdhan(int index){toast(index==0?"اذان حسین صبحدل داخل برنامه و کاملاً آفلاین است":(index>0&&index<builtInAdhanNames.length?"این انتخاب از رسانه صوت‌الشیعه آنلاین پخش می‌شود و در قطع اینترنت به صبحدل برمی‌گردد":"فایل موذن شخصی روی دستگاه شما ذخیره می‌شود"));}'
assert old in s
new=r'''void cacheSelectedBuiltInAdhan(int index){
        if(index==0){toast("اذان حسین صبحدل داخل برنامه و کاملاً آفلاین است");return;}
        if(index>0&&index<builtInAdhanNames.length){String u=builtInAdhanUrls[index];if(u==null||u.isEmpty()){toast("آدرس صوت موجود نیست");return;}File dir=new File(getFilesDir(),"adhan_media");if(!dir.exists())dir.mkdirs();File dest=new File(dir,"shia_"+index+".audio"),tmp=new File(dest.getAbsolutePath()+".part");toast("دانلود موذن برای آفلاین شروع شد");new Thread(()->{boolean ok=false;try{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(90000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","NadayeBeheshti/5.2");try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(tmp)){byte[] b=new byte[32768];int n;while((n=in.read(b))>0)out.write(b,0,n);}ok=tmp.length()>10000&&tmp.renameTo(dest);}catch(Exception e){tmp.delete();}final boolean done=ok;runOnUiThread(()->toast(done?"این اذان برای پخش بدون اینترنت ذخیره شد":"دانلود کامل نشد؛ هر زمان اینترنت بهتر بود دوباره بزنید"));}).start();return;}
        toast("فایل موذن شخصی روی دستگاه شما ذخیره می‌شود");
    }'''
s=s.replace(old,new,1)
p.write_text(s,encoding='utf-8')

# Adhan service prefers cached Shia media before network.
a=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/AdhanService.java')
t=a.read_text(encoding='utf-8')
old='if(selected>=1&&selected<=REMOTE_SHIA.length)return Uri.parse(REMOTE_SHIA[selected-1]);'
assert old in t
new='if(selected>=1&&selected<=REMOTE_SHIA.length){File cached=new File(new File(getFilesDir(),"adhan_media"),"shia_"+selected+".audio");if(cached.exists()&&cached.length()>10000)return Uri.fromFile(cached);return Uri.parse(REMOTE_SHIA[selected-1]);}'
t=t.replace(old,new,1)
if 'import java.io.File;' not in t:
    t=t.replace('import android.os.*;','import android.os.*;\nimport java.io.File;',1)
a.write_text(t,encoding='utf-8')
print('v5.6 Shia adhan offline cache applied')
