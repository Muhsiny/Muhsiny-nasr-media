from pathlib import Path
p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/MainActivity.java')
s=p.read_text(encoding='utf-8')

# Point the Android app at the production JSON backend.
s=s.replace('static final String CENTRAL_BASE="https://nadaye-beheshti-central-ziua8x.v2.appdeploy.ai";','static final String CENTRAL_BASE="https://nadaye-beheshti-central-api.lovable.app";',1)

# Replace the WebView bridge transport with direct HTTPS JSON transport.
start=s.index('    void centralDisposeBridge(')
end=s.index('\n    String centralOwnerToken', start)
transport=r'''    synchronized JSONObject centralHttp(String method,String path,JSONObject body)throws Exception{
        if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())throw new IOException("central_http_main_thread");
        URL u=new URL(CENTRAL_BASE+path);
        HttpURLConnection c=(HttpURLConnection)u.openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(45000);c.setUseCaches(false);c.setInstanceFollowRedirects(true);
        c.setRequestMethod(method);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","NadayeBeheshti/5.0 Android");
        if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");byte[] d=body.toString().getBytes("UTF-8");c.setFixedLengthStreamingMode(d.length);OutputStream o=c.getOutputStream();o.write(d);o.flush();o.close();}
        int code=c.getResponseCode();String ct=c.getContentType();InputStream in=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();String txt=in==null?"{}":centralReadText(in);c.disconnect();
        if(code<200||code>=300)throw new IOException("HTTP "+code+" "+txt);
        if(ct==null||!ct.toLowerCase(Locale.US).contains("application/json"))throw new IOException("central_non_json:"+txt.substring(0,Math.min(100,txt.length())));
        try{return new JSONObject(txt);}catch(Exception e){throw new IOException("central_invalid_json:"+txt.substring(0,Math.min(120,txt.length())));}
    }
'''
s=s[:start]+transport+s[end:]

# Keep old compatibility workflow: first device supplies its own token and becomes permanent owner.
# The new server accepts these routes and chunked asset uploads, so no design/content code changes are needed.
s=s.replace('if(m.contains("bridge_not_ready")||m.contains("bridge_load_error")||m.contains("central_bridge"))return "پل امن ارتباط با مرکز آماده نشد؛ اینترنت را بررسی و دوباره تلاش کنید.";','if(m.contains("central_non_json")||m.contains("central_invalid_json"))return "مرکز پاسخ معتبر JSON نداد؛ دوباره تلاش کنید.";',1)

# Slightly clearer success copy for the durable backend.
s=s.replace('هر تغییری که شما در همین پنل مخفی ذخیره کرده‌اید، با دکمه زیر به نسخه عمومی تبدیل می‌شود. پس از نشر، سرور دوباره خوانده می‌شود تا موفقیت واقعی تأیید شود.','هر تغییری که شما در همین پنل مخفی ذخیره کرده‌اید، با دکمه زیر مستقیماً در مرکز دائمی ذخیره می‌شود. پس از نشر، همان نسخه دوباره از سرور خوانده می‌شود تا موفقیت واقعی تأیید شود.',1)

p.write_text(s,encoding='utf-8')
assert 'https://nadaye-beheshti-central-api.lovable.app' in s
assert 'new android.webkit.WebView' not in s[s.index('    synchronized JSONObject centralHttp('):s.index('\n    String centralOwnerToken')]
assert 'central_non_json' in s
print('v5.0 direct durable central JSON transport applied')
