from pathlib import Path
p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/MainActivity.java')
s=p.read_text(encoding='utf-8')

start=s.index('    JSONObject centralHttp(')
end=s.index('\n    String centralOwnerToken',start)
bridge=r'''    void centralDisposeBridge(final android.webkit.WebView w){if(w==null)return;runOnUiThread(()->{try{android.view.ViewParent parent=w.getParent();if(parent instanceof android.view.ViewGroup)((android.view.ViewGroup)parent).removeView(w);w.removeJavascriptInterface("NadayeNative");w.stopLoading();w.destroy();}catch(Exception ignored){}});}
    synchronized JSONObject centralHttp(String method,String path,JSONObject body)throws Exception{
        if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())throw new IOException("central_bridge_main_thread");
        final java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicReference<String> result=new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<String> failure=new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<android.webkit.WebView> webRef=new java.util.concurrent.atomic.AtomicReference<>();
        final String id=UUID.randomUUID().toString(),m=method,pth=path,bodyText=body==null?"":body.toString();
        runOnUiThread(()->{try{
            final android.webkit.WebView w=new android.webkit.WebView(MainActivity.this);webRef.set(w);w.setVisibility(View.INVISIBLE);w.getSettings().setJavaScriptEnabled(true);w.getSettings().setDomStorageEnabled(true);w.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);
            w.addJavascriptInterface(new Object(){
                @android.webkit.JavascriptInterface public void onResult(String rid,String payload){if(id.equals(rid)){result.compareAndSet(null,payload);latch.countDown();}}
                @android.webkit.JavascriptInterface public void onError(String rid,String message){if(id.equals(rid)){failure.compareAndSet(null,message);latch.countDown();}}
            },"NadayeNative");
            w.setWebViewClient(new android.webkit.WebViewClient(){
                @Override public void onPageFinished(android.webkit.WebView view,String url){super.onPageFinished(view,url);String js="(function(){var n=0;function go(){if(window.nadayeDo){window.nadayeDo("+JSONObject.quote(id)+","+JSONObject.quote(m)+","+JSONObject.quote(pth)+","+JSONObject.quote(bodyText)+");}else if(n++<120){setTimeout(go,100);}else{NadayeNative.onError("+JSONObject.quote(id)+",'bridge_not_ready');}}go();})();";view.evaluateJavascript(js,null);}
                @Override public void onReceivedError(android.webkit.WebView view,int errorCode,String description,String failingUrl){if(failingUrl!=null&&failingUrl.startsWith(CENTRAL_BASE)){failure.compareAndSet(null,"bridge_load_error:"+description);latch.countDown();}}
            });
            addContentView(w,new android.view.ViewGroup.LayoutParams(1,1));w.loadUrl(CENTRAL_BASE+"/?nativeBridge=1&v=49");
        }catch(Exception e){failure.compareAndSet(null,String.valueOf(e.getMessage()));latch.countDown();}});
        boolean done=latch.await(70,java.util.concurrent.TimeUnit.SECONDS);centralDisposeBridge(webRef.get());if(!done)throw new IOException("central_bridge_timeout");if(failure.get()!=null)throw new IOException(failure.get());String txt=result.get();if(txt==null||txt.trim().isEmpty())throw new IOException("central_bridge_empty");try{return new JSONObject(txt);}catch(Exception e){throw new IOException("central_bridge_invalid_json:"+txt.substring(0,Math.min(120,txt.length())));}
    }
'''
s=s[:start]+bridge+s[end:]

start=s.index('    String centralUploadAsset(')
end=s.index('\n    JSONObject buildCentralPayload',start)
upload=r'''    String centralUploadAsset(String token,String pinHash,String key,String value)throws Exception{
        InputStream in=centralOpenLocal(value);if(in==null)return null;byte[] data=centralReadBytes(in);String mime=centralMime(value);String ext=mime.contains("png")?".png":mime.contains("webp")?".webp":mime.contains("jpeg")?".jpg":mime.contains("ttf")?".ttf":mime.contains("otf")?".otf":mime.contains("mpeg")?".mp3":mime.contains("ogg")?".ogg":".bin";String name="asset_"+Math.abs(key.hashCode())+ext;String uploadId=UUID.randomUUID().toString().replace("-","");int chunkSize=120*1024,total=Math.max(1,(data.length+chunkSize-1)/chunkSize);
        for(int i=0;i<total;i++){int a=i*chunkSize,b=Math.min(data.length,a+chunkSize);byte[] piece=java.util.Arrays.copyOfRange(data,a,b);JSONObject q=new JSONObject();q.put("token",token);q.put("pinHash",pinHash);q.put("uploadId",uploadId);q.put("index",i);q.put("content",android.util.Base64.encodeToString(piece,android.util.Base64.NO_WRAP));centralHttp("POST","/api/asset-chunk",q);}
        JSONObject f=new JSONObject();f.put("token",token);f.put("pinHash",pinHash);f.put("uploadId",uploadId);f.put("total",total);f.put("name",name);f.put("contentType",mime);return centralHttp("POST","/api/asset-finalize",f).optString("path","");
    }
'''
s=s[:start]+upload+s[end:]

# Make the visible error useful if the bridge itself cannot load.
s=s.replace('if(m.contains("UnknownHost")||m.contains("connect")||m.contains("timeout"))return "اتصال به سرور مرکزی برقرار نشد.";','if(m.contains("bridge_not_ready")||m.contains("bridge_load_error")||m.contains("central_bridge"))return "پل امن ارتباط با مرکز آماده نشد؛ اینترنت را بررسی و دوباره تلاش کنید.";if(m.contains("UnknownHost")||m.contains("connect")||m.contains("timeout"))return "اتصال به سرور مرکزی برقرار نشد.";',1)

p.write_text(s,encoding='utf-8')
assert 'nativeBridge=1&v=49' in s
assert '/api/asset-chunk' in s and '/api/asset-finalize' in s
assert 'new android.webkit.WebView' in s
print('v4.9 same-origin WebView bridge and chunked publishing applied')
