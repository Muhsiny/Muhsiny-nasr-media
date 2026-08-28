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

# Owner credential recovery: the random owner token is encrypted with AES-GCM using
# the existing local PIN hash as the 256-bit key. Only the encrypted recovery blob is public.
old_owner='''    String centralOwnerToken(){String t=prefs.getString("central.ownerToken","");if(t.length()>=32)return t;t=UUID.randomUUID().toString()+UUID.randomUUID().toString();prefs.edit().putString("central.ownerToken",t).apply();return t;}'''
assert old_owner in s
new_owner=r'''    byte[] centralHexBytes(String h)throws Exception{if(h==null||h.length()!=64)throw new IOException("bad_pin_hash");byte[] b=new byte[32];for(int i=0;i<32;i++)b[i]=(byte)Integer.parseInt(h.substring(i*2,i*2+2),16);return b;}
    String centralEncryptRecovery(String token,String pinHash)throws Exception{byte[] iv=new byte[12];new java.security.SecureRandom().nextBytes(iv);javax.crypto.Cipher c=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");javax.crypto.spec.SecretKeySpec k=new javax.crypto.spec.SecretKeySpec(centralHexBytes(pinHash),"AES");c.init(javax.crypto.Cipher.ENCRYPT_MODE,k,new javax.crypto.spec.GCMParameterSpec(128,iv));byte[] enc=c.doFinal(token.getBytes("UTF-8")),all=new byte[iv.length+enc.length];System.arraycopy(iv,0,all,0,iv.length);System.arraycopy(enc,0,all,iv.length,enc.length);return "v1."+android.util.Base64.encodeToString(all,android.util.Base64.NO_WRAP);}
    String centralDecryptRecovery(String blob,String pinHash)throws Exception{if(blob==null||!blob.startsWith("v1."))throw new IOException("bad_recovery_blob");byte[] all=android.util.Base64.decode(blob.substring(3),android.util.Base64.DEFAULT);if(all.length<29)throw new IOException("bad_recovery_blob");byte[] iv=java.util.Arrays.copyOfRange(all,0,12),enc=java.util.Arrays.copyOfRange(all,12,all.length);javax.crypto.Cipher c=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");javax.crypto.spec.SecretKeySpec k=new javax.crypto.spec.SecretKeySpec(centralHexBytes(pinHash),"AES");c.init(javax.crypto.Cipher.DECRYPT_MODE,k,new javax.crypto.spec.GCMParameterSpec(128,iv));return new String(c.doFinal(enc),"UTF-8");}
    String centralOwnerToken()throws Exception{String t=prefs.getString("central.ownerToken","");if(t.length()>=32)return t;String rec=prefs.getString("central.ownerRecovery","");if(rec.startsWith("v1.")){try{t=centralDecryptRecovery(rec,centralPinHash());if(t.length()>=32){prefs.edit().putString("central.ownerToken",t).apply();return t;}}catch(Exception e){throw new IOException("owner_recovery_failed");}}t=UUID.randomUUID().toString()+UUID.randomUUID().toString();prefs.edit().putString("central.ownerToken",t).apply();return t;}'''
s=s.replace(old_owner,new_owner,1)

# Every publication refreshes the encrypted recovery blob. This lets a fresh install
# regain owner authority after it downloads the published configuration and the owner
# enters the same hidden-panel PIN.
s=s.replace('payload.put("schema",2);payload.put("publishedFrom","owner-panel");return payload;}','payload.put("schema",2);payload.put("ownerRecovery",centralEncryptRecovery(token,pinHash));payload.put("publishedFrom","owner-panel");return payload;}',1)

# Cache recovery material whenever a public configuration is downloaded; it is encrypted
# and cannot be used until the correct hidden-panel PIN is supplied locally.
needle='JSONObject payload=cfg.optJSONObject("payload");if(payload==null)throw new IOException("empty_remote_payload");JSONObject vals=payload.optJSONObject("prefs");'
assert needle in s
s=s.replace(needle,'JSONObject payload=cfg.optJSONObject("payload");if(payload==null)throw new IOException("empty_remote_payload");String recovery=payload.optString("ownerRecovery","");if(!recovery.isEmpty())prefs.edit().putString("central.ownerRecovery",recovery).apply();JSONObject vals=payload.optJSONObject("prefs");',1)

# Keep old compatibility workflow: first device supplies its own random token and becomes permanent owner.
s=s.replace('if(m.contains("bridge_not_ready")||m.contains("bridge_load_error")||m.contains("central_bridge"))return "پل امن ارتباط با مرکز آماده نشد؛ اینترنت را بررسی و دوباره تلاش کنید.";','if(m.contains("owner_recovery_failed"))return "بازیابی مالکیت با رمز پنل انجام نشد؛ همان رمز مالک قبلی را وارد کنید.";if(m.contains("central_non_json")||m.contains("central_invalid_json"))return "مرکز پاسخ معتبر JSON نداد؛ دوباره تلاش کنید.";',1)

# Slightly clearer success copy for the durable backend.
s=s.replace('هر تغییری که شما در همین پنل مخفی ذخیره کرده‌اید، با دکمه زیر به نسخه عمومی تبدیل می‌شود. پس از نشر، سرور دوباره خوانده می‌شود تا موفقیت واقعی تأیید شود.','هر تغییری که شما در همین پنل مخفی ذخیره کرده‌اید، با دکمه زیر مستقیماً در مرکز دائمی ذخیره می‌شود. پس از نشر، همان نسخه دوباره از سرور خوانده می‌شود تا موفقیت واقعی تأیید شود.',1)

p.write_text(s,encoding='utf-8')
assert 'https://nadaye-beheshti-central-api.lovable.app' in s
assert 'new android.webkit.WebView' not in s[s.index('    synchronized JSONObject centralHttp('):s.index('\n    byte[] centralHexBytes')]
assert 'central_non_json' in s
assert 'ownerRecovery' in s and 'AES/GCM/NoPadding' in s
print('v5.0 direct durable central JSON transport + secure owner recovery applied')
