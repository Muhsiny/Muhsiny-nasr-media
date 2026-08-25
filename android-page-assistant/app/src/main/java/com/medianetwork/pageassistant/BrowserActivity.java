package com.medianetwork.pageassistant;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;

public class BrowserActivity extends Activity {
    private WebView web;
    private TextView currentView;
    private boolean firstLoad = true;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
        configureWebView();
        openCreatePage();
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        currentView = new TextView(this);
        currentView.setTextSize(14);
        currentView.setTextColor(Color.rgb(25, 70, 50));
        currentView.setGravity(Gravity.RIGHT);
        currentView.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(currentView, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER);
        tools.setPadding(dp(4), dp(3), dp(4), dp(3));
        root.addView(tools, new LinearLayout.LayoutParams(-1, -2));

        Button back = small("↶");
        back.setOnClickListener(v -> { if (web.canGoBack()) web.goBack(); });
        tools.addView(back, weight());
        Button reload = small("↻");
        reload.setOnClickListener(v -> web.reload());
        tools.addView(reload, weight());
        Button open = small("Create");
        open.setOnClickListener(v -> openCreatePage());
        tools.addView(open, weight());
        Button fill = small("پرکردن");
        fill.setOnClickListener(v -> fillCurrent());
        tools.addView(fill, weight());
        Button category = small("دسته");
        category.setOnClickListener(v -> chooseCategory());
        tools.addView(category, weight());
        Button next = small("ساخته شد ← بعدی");
        next.setOnClickListener(v -> advanceAndNext());
        tools.addView(next, new LinearLayout.LayoutParams(0, -2, 1.6f));

        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));
        refreshHeader();
        return root;
    }

    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setBuiltInZoomControls(false);
        String ua = s.getUserAgentString();
        if (ua != null && !ua.contains("Chrome/")) {
            s.setUserAgentString(ua + " Chrome/124.0.0.0 Mobile Safari/537.36");
        }
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.contains("facebook.com")) {
                    PlanStore.setStatus(BrowserActivity.this, "Facebook باز است. در صورت نیاز وارد حساب خودت شو.");
                    if (!firstLoad || url.contains("pages/creation")) {
                        view.postDelayed(() -> fillCurrent(), 1200);
                    }
                    firstLoad = false;
                }
            }
        });
    }

    private void openCreatePage() {
        PlanStore.Plan p = PlanStore.current(this);
        if (p == null) { toast("صف تمام شده است."); return; }
        refreshHeader();
        PlanStore.setStatus(this, "در حال بازکردن ساخت صفحه برای " + p.name);
        web.loadUrl("https://www.facebook.com/pages/creation/");
    }

    private void fillCurrent() {
        PlanStore.Plan p = PlanStore.current(this);
        if (p == null) { toast("مورد فعالی وجود ندارد."); return; }
        String name = JSONObject.quote(p.name);
        String category = JSONObject.quote(p.category);
        String bio = JSONObject.quote(p.bio);
        String js = "(function(){" +
                "function meta(e){var p=e.parentElement?e.parentElement.innerText:'';return ((e.getAttribute('aria-label')||'')+' '+(e.getAttribute('placeholder')||'')+' '+(e.getAttribute('name')||'')+' '+(e.id||'')+' '+p).toLowerCase();}" +
                "function setv(e,v){try{e.focus();if(e.isContentEditable){e.innerText=v;}else{var proto=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;var d=Object.getOwnPropertyDescriptor(proto,'value');if(d&&d.set)d.set.call(e,v);else e.value=v;}e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));e.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,key:'a'}));return true;}catch(x){return false;}}" +
                "function pick(tokens){var a=[].slice.call(document.querySelectorAll('input,textarea,[contenteditable=true]'));var best=null,bscore=0;a.forEach(function(e){var m=meta(e),s=0;tokens.forEach(function(t){if(m.indexOf(t)>=0)s+=5;});if(e.offsetParent!==null)s+=1;if(s>bscore){best=e;bscore=s;}});return best;}" +
                "var r=[];var n=pick(['page name','name your page','نام صفحه','نام پیج','اسم صفحه']);if(n&&setv(n,"+name+"))r.push('name');" +
                "var c=pick(['category','دسته‌بندی','دسته بندی','دسته']);if(c&&setv(c,"+category+"))r.push('category');" +
                "var b=pick(['bio','description','about','معرفی','توضیح','درباره']);if(b&&setv(b,"+bio+"))r.push('bio');" +
                "return r.join(',');" +
                "})()";
        web.evaluateJavascript(js, value -> {
            PlanStore.setStatus(this, "فرم مورد فعلی تکمیل شد؛ Create نهایی را خودت تأیید کن.");
            toast("فرم تکمیل شد. اگر دسته پیشنهاد شد، «دسته» را بزن.");
        });
    }

    private void chooseCategory() {
        PlanStore.Plan p = PlanStore.current(this);
        if (p == null) return;
        String category = JSONObject.quote(p.category.toLowerCase());
        String js = "(function(){var w="+category+";var a=[].slice.call(document.querySelectorAll('div,span,li,[role=option],[role=button]'));for(var i=0;i<a.length;i++){var t=(a[i].innerText||'').trim().toLowerCase();if(t===w||t.indexOf(w)>=0){a[i].click();return 'clicked';}}return 'not-found';})()";
        web.evaluateJavascript(js, value -> toast(value != null && value.contains("clicked") ? "دسته انتخاب شد." : "پیشنهاد دسته پیدا نشد؛ یک‌بار روی پیشنهاد Facebook بزن."));
    }

    private void advanceAndNext() {
        PlanStore.advance(this);
        PlanStore.Plan p = PlanStore.current(this);
        refreshHeader();
        if (p == null) {
            toast("صف تمام شد.");
            PlanStore.setStatus(this, "صف تمام شد");
            return;
        }
        openCreatePage();
    }

    private void refreshHeader() {
        if (currentView == null) return;
        int total = PlanStore.size(this);
        int idx = PlanStore.index(this);
        PlanStore.Plan p = PlanStore.current(this);
        currentView.setText("مورد " + Math.min(idx + 1, Math.max(total, 1)) + " از " + total + "   |   " + (p == null ? "پایان صف" : p.name));
    }

    private Button small(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setPadding(dp(3), dp(3), dp(3), dp(3));
        return b;
    }

    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1f); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
