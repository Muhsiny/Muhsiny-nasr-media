package org.sayeh.wificontrol;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class RouterWebConsoleActivity extends Activity {
    private WebView web;
    private String host, targetIp, targetMac;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        host=getIntent().getStringExtra("host"); if(host==null||host.trim().isEmpty()) host="192.168.1.1";
        targetIp=getIntent().getStringExtra("ip"); if(targetIp==null) targetIp="";
        targetMac=getIntent().getStringExtra("mac"); if(targetMac==null) targetMac="";

        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        TextView bar=new TextView(this); bar.setText("کنسول واقعی روتر  •  QoS / Wi‑Fi / Security\nIP: "+(targetIp.isEmpty()?"—":targetIp)+"   MAC: "+(targetMac.isEmpty()?"—":targetMac));
        bar.setTextSize(14); bar.setPadding(16,12,16,12); bar.setGravity(Gravity.CENTER_VERTICAL); root.addView(bar);
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button qos=button("رفتن به QoS"); Button copy=button("کپی IP/MAC"); Button reload=button("Reload");
        actions.addView(qos,new LinearLayout.LayoutParams(0,-2,1)); actions.addView(copy,new LinearLayout.LayoutParams(0,-2,1)); actions.addView(reload,new LinearLayout.LayoutParams(0,-2,1)); root.addView(actions);
        web=new WebView(this); root.addView(web,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        WebSettings ws=web.getSettings(); ws.setJavaScriptEnabled(true); ws.setDomStorageEnabled(true); ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW); ws.setBuiltInZoomControls(true); ws.setDisplayZoomControls(false);
        web.setWebViewClient(new WebViewClient()); web.setWebChromeClient(new WebChromeClient());
        web.loadUrl("http://"+host+"/");
        qos.setOnClickListener(v->jumpToQos());
        copy.setOnClickListener(v->{ ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE); cm.setPrimaryClip(ClipData.newPlainText("router target",targetIp+"\n"+targetMac)); Toast.makeText(this,"IP/MAC کپی شد",Toast.LENGTH_SHORT).show(); });
        reload.setOnClickListener(v->web.reload());
    }

    private Button button(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); return b; }

    private void jumpToQos(){
        String js="(function(){function scan(d){try{var a=d.querySelectorAll('a');for(var i=0;i<a.length;i++){var t=(a[i].innerText||a[i].textContent||'').toLowerCase();var h=(a[i].getAttribute('href')||'').toLowerCase();if(t.indexOf('qos')>=0||h.indexOf('qos')>=0){a[i].click();return true;}}var f=d.querySelectorAll('frame,iframe');for(var j=0;j<f.length;j++){try{if(scan(f[j].contentDocument))return true;}catch(e){}}}catch(e){}return false;}return scan(document)?'ok':'notfound';})()";
        web.evaluateJavascript(js, value->{ if(value==null||value.contains("notfound")) Toast.makeText(this,"پس از Login: Advanced Setup → QoS را باز کنید. IP انتخابی بالای صفحه آماده است.",Toast.LENGTH_LONG).show(); });
    }

    @Override public void onBackPressed(){ if(web!=null&&web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy(){ if(web!=null){ web.stopLoading(); web.destroy(); } super.onDestroy(); }
}
