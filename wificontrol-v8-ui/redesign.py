from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
java = root / "app/src/main/java/org/sayeh/wificontrol"
p = java / "MainActivity.java"
s = p.read_text(encoding="utf-8")

s = s.replace(
    "import android.graphics.Typeface;\n",
    "import android.graphics.Typeface;\nimport android.graphics.drawable.GradientDrawable;\nimport android.os.Build;\n",
)

new_build = r'''    private void buildUi() {
        if(Build.VERSION.SDK_INT>=21){
            getWindow().setStatusBarColor(Color.rgb(246,246,248));
            getWindow().setNavigationBarColor(Color.WHITE);
        }
        if(Build.VERSION.SDK_INT>=23){
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(246,246,248));
        LinearLayout root=vertical();
        root.setPadding(dp(16),dp(18),dp(16),dp(36));
        scroll.addView(root);

        LinearLayout header=surface();
        header.setPadding(dp(20),dp(22),dp(20),dp(20));
        TextView eyebrow=text("ROUTER DIRECT",11,true);
        eyebrow.setTextColor(Color.rgb(0,122,255));
        eyebrow.setLetterSpacing(0.12f);
        header.addView(eyebrow);
        TextView title=text("Ayoubi Wifi -C",30,true);
        title.setPadding(0,dp(3),0,0);
        header.addView(title);
        TextView subtitle=text("کنترل مستقیم، محلی و مستقل از کمپیوتر",13,false);
        subtitle.setTextColor(Color.rgb(99,99,102));
        subtitle.setPadding(0,dp(6),0,0);
        header.addView(subtitle);
        root.addView(header,surfaceParams());

        root.addView(sectionLabel("اتصال"));
        LinearLayout connectCard=surface();
        connectCard.setPadding(dp(14),dp(14),dp(14),dp(14));
        hostInput=input("آدرس روتر",prefs.getString("router_host","192.168.1.1"),InputType.TYPE_CLASS_TEXT);
        passwordInput=input("رمز Telnet روتر","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        nodeInput=input("WLAN node",String.valueOf(prefs.getInt("wlan_node",1)),InputType.TYPE_CLASS_NUMBER);
        connectCard.addView(hostInput); connectCard.addView(passwordInput); connectCard.addView(nodeInput);
        LinearLayout row=horizontal();
        connectButton=button("اتصال");
        refreshButton=button("تازه‌سازی");
        stylePrimary(connectButton);
        row.addView(connectButton,weighted());
        row.addView(refreshButton,weighted());
        connectCard.addView(row);
        root.addView(connectCard,surfaceParams());

        root.addView(sectionLabel("کنترل و امنیت"));
        LinearLayout controlCard=surface();
        controlCard.setPadding(dp(10),dp(10),dp(10),dp(10));

        LinearLayout actions=horizontal();
        allowOnlyButton=button("فقط دستگاه‌های مجاز");
        emergencyButton=button("بازکردن اضطراری");
        styleDangerOutline(emergencyButton);
        actions.addView(allowOnlyButton,weighted());
        actions.addView(emergencyButton,weighted());
        controlCard.addView(actions);

        LinearLayout security=horizontal();
        quarantineButton=button("قرنطینه ناشناس‌ها");
        monitorButton=button("مانیتور زنده: خاموش");
        styleDangerOutline(quarantineButton);
        security.addView(quarantineButton,weighted());
        security.addView(monitorButton,weighted());
        controlCard.addView(security);

        historyButton=button("تاریخچه عملیات");
        controlCard.addView(historyButton,fullButtonParams());

        LinearLayout tools=horizontal();
        advancedButton=button("QoS / کنسول پیشرفته");
        diagButton=button("تشخیص شبکه");
        tools.addView(advancedButton,weighted());
        tools.addView(diagButton,weighted());
        controlCard.addView(tools);

        LinearLayout safety=horizontal();
        backupButton=button("Backup وضعیت امن");
        restoreButton=button("Restore وضعیت امن");
        styleOrangeOutline(restoreButton);
        safety.addView(backupButton,weighted());
        safety.addView(restoreButton,weighted());
        controlCard.addView(safety);
        root.addView(controlCard,surfaceParams());

        root.addView(sectionLabel("وضعیت"));
        LinearLayout statusCard=surface();
        statusCard.setPadding(dp(18),dp(16),dp(18),dp(16));
        statusText=text("آماده برای اتصال",15,true);
        statusCard.addView(statusText);
        managerText=text(managerMac.isEmpty()?"دستگاه مدیر: مشخص نشده":"دستگاه مدیر: "+managerMac,13,false);
        managerText.setTextColor(Color.rgb(99,99,102));
        managerText.setPadding(0,dp(8),0,0);
        statusCard.addView(managerText);
        diagnosticText=text("",12,false);
        diagnosticText.setTextColor(Color.rgb(142,142,147));
        diagnosticText.setPadding(0,dp(8),0,0);
        statusCard.addView(diagnosticText);
        root.addView(statusCard,surfaceParams());

        root.addView(sectionLabel("دستگاه‌های شبکه"));
        devicesContainer=vertical();
        root.addView(devicesContainer);

        connectButton.setOnClickListener(v->connectReal());
        refreshButton.setOnClickListener(v->refreshReal());
        allowOnlyButton.setOnClickListener(v->confirmAllowOnly());
        emergencyButton.setOnClickListener(v->confirmEmergency());
        quarantineButton.setOnClickListener(v->confirmQuarantineUnknown());
        monitorButton.setOnClickListener(v->toggleMonitor());
        historyButton.setOnClickListener(v->showHistory());
        advancedButton.setOnClickListener(v->openAdvanced("",""));
        diagButton.setOnClickListener(v->runDiagnostics());
        backupButton.setOnClickListener(v->saveCurrentBackup());
        restoreButton.setOnClickListener(v->restoreBackup());
        setContentView(scroll);
        enableControls(false);
    }'''

pattern = r"    private void buildUi\(\) \{.*?\n    \}\n\n    private void connectReal\(\)"
s, n = re.subn(pattern, lambda m: new_build + "\n\n    private void connectReal()", s, count=1, flags=re.S)
if n != 1:
    raise SystemExit("buildUi replacement failed")

new_card = r'''    private View deviceCard(Device d,RouterSnapshot s) {
        LinearLayout card=surface();
        card.setPadding(dp(16),dp(16),dp(16),dp(14));
        LinearLayout.LayoutParams cp=surfaceParams();
        cp.setMargins(0,0,0,dp(12));
        card.setLayoutParams(cp);

        boolean manager=d.mac.equals(managerMac);
        LinearLayout head=horizontal();
        TextView name=text((manager?"★  ":"")+aliasFor(d.mac),17,true);
        head.addView(name,new LinearLayout.LayoutParams(0,-2,1f));
        TextView state=text(manager?"مدیر":"آنلاین",11,true);
        state.setTextColor(manager?Color.rgb(255,149,0):Color.rgb(52,199,89));
        state.setGravity(Gravity.CENTER);
        state.setPadding(dp(10),dp(5),dp(10),dp(5));
        state.setBackground(rounded(Color.rgb(246,246,248),99));
        head.addView(state);
        card.addView(head);

        String signal=d.rssi==null?"نامعلوم":d.rssi+" dBm";
        TextView meta=text("IP  "+empty(d.ip,"—")+"\nMAC  "+d.mac+"   •   Signal  "+signal,12,false);
        meta.setTextColor(Color.rgb(99,99,102));
        meta.setPadding(0,dp(8),0,dp(10));
        card.addView(meta);

        EditText alias=input("نام دستگاه",aliasForStored(d.mac),InputType.TYPE_CLASS_TEXT);
        card.addView(alias);
        Button saveAlias=button("ذخیره نام");
        card.addView(saveAlias,fullButtonParams());
        saveAlias.setOnClickListener(v->{ prefs.edit().putString("alias_"+d.mac,alias.getText().toString().trim()).apply(); render(snapshot); });

        CheckBox approved=new CheckBox(this);
        approved.setText("مجاز / Approved");
        approved.setTextColor(Color.rgb(28,28,30));
        approved.setChecked(isApproved(d.mac)||manager);
        approved.setEnabled(!manager);
        approved.setPadding(dp(2),dp(6),0,dp(6));
        approved.setOnCheckedChangeListener((b,checked)->setApproved(d.mac,checked));
        card.addView(approved);

        LinearLayout row=horizontal();
        Button managerButton=button(manager?"مدیر ✓":"تعیین مدیر");
        Button blockButton;
        boolean blocked=s.policy==RouterSnapshot.POLICY_DENY&&s.acl.contains(d.mac);
        if(s.policy==RouterSnapshot.POLICY_ALLOW) blockButton=button("حالت Allow-List");
        else blockButton=button(blocked?"Unblock واقعی":"Block واقعی");
        managerButton.setEnabled(!manager);
        blockButton.setEnabled(!manager&&s.policy!=RouterSnapshot.POLICY_ALLOW&&!busy);
        if(!blocked) styleDangerOutline(blockButton); else stylePrimary(blockButton);
        managerButton.setOnClickListener(v->{ setManager(d.mac); });
        blockButton.setOnClickListener(v->runBlock(d,!blocked));
        row.addView(managerButton,weighted());
        row.addView(blockButton,weighted());
        card.addView(row);

        LinearLayout more=horizontal();
        Button qosButton=button("QoS این IP");
        Button scheduleButton=button("زمان‌بندی");
        qosButton.setEnabled(d.ip!=null&&!d.ip.trim().isEmpty());
        qosButton.setOnClickListener(v->openAdvanced(d.ip,d.mac));
        scheduleButton.setOnClickListener(v->scheduleDialog(d));
        more.addView(qosButton,weighted());
        more.addView(scheduleButton,weighted());
        card.addView(more);
        return card;
    }'''

pattern = r"    private View deviceCard\(Device d,RouterSnapshot s\) \{.*?\n    \}\n\n\n    private void openAdvanced"
s, n = re.subn(pattern, lambda m: new_card + "\n\n    private void openAdvanced", s, count=1, flags=re.S)
if n != 1:
    raise SystemExit("deviceCard replacement failed")

helpers = r'''    private GradientDrawable rounded(int color,int radiusDp){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private GradientDrawable outlined(int fill,int stroke,int radiusDp){
        GradientDrawable g=rounded(fill,radiusDp);
        g.setStroke(dp(1),stroke);
        return g;
    }

    private LinearLayout surface(){
        LinearLayout l=vertical();
        l.setBackground(rounded(Color.WHITE,22));
        if(Build.VERSION.SDK_INT>=21) l.setElevation(dp(1));
        return l;
    }

    private LinearLayout.LayoutParams surfaceParams(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,0,0,dp(14));
        return p;
    }

    private TextView sectionLabel(String value){
        TextView t=text(value,13,true);
        t.setTextColor(Color.rgb(99,99,102));
        t.setPadding(dp(4),dp(5),0,dp(8));
        return t;
    }

    private EditText input(String hint,String value,int type){
        EditText e=new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(142,142,147));
        e.setText(value);
        e.setInputType(type);
        e.setTextSize(15);
        e.setTextColor(Color.rgb(28,28,30));
        e.setSingleLine(true);
        e.setPadding(dp(14),0,dp(14),0);
        e.setMinHeight(dp(50));
        e.setBackground(rounded(Color.rgb(242,242,247),14));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,0,0,dp(10));
        e.setLayoutParams(lp);
        return e;
    }

    private Button button(String label){
        Button b=new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setTextColor(Color.rgb(0,122,255));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(9),0,dp(9),0);
        b.setMinHeight(dp(48));
        b.setBackground(rounded(Color.rgb(242,242,247),14));
        return b;
    }

    private void stylePrimary(Button b){
        b.setTextColor(Color.WHITE);
        b.setBackground(rounded(Color.rgb(0,122,255),14));
    }

    private void styleDangerOutline(Button b){
        b.setTextColor(Color.rgb(255,59,48));
        b.setBackground(outlined(Color.rgb(255,250,250),Color.rgb(255,204,201),14));
    }

    private void styleOrangeOutline(Button b){
        b.setTextColor(Color.rgb(255,149,0));
        b.setBackground(outlined(Color.rgb(255,252,247),Color.rgb(255,221,173),14));
    }

    private TextView text(String value,int size,boolean bold){
        TextView t=new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(Color.rgb(28,28,30));
        t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
        t.setLineSpacing(0,1.08f);
        return t;
    }

    private LinearLayout vertical(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }

    private LinearLayout.LayoutParams weighted(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);
        p.setMargins(dp(4),dp(5),dp(4),dp(5));
        return p;
    }

    private LinearLayout.LayoutParams fullButtonParams(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(dp(4),dp(5),dp(4),dp(5));
        return p;
    }

    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
'''

pattern = r"    private EditText input\(.*?    private int dp\(int v\)\{ return Math\.round\(v\*getResources\(\)\.getDisplayMetrics\(\)\.density\); \}\n"
s, n = re.subn(pattern, lambda m: helpers, s, count=1, flags=re.S)
if n != 1:
    raise SystemExit("UI helper replacement failed")

p.write_text(s, encoding="utf-8")

manifest = root / "app/src/main/AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")
m = m.replace("WiFi Control V8 Router Manager", "Ayoubi Wifi -C")
manifest.write_text(m, encoding="utf-8")

gradle = root / "app/build.gradle"
g = gradle.read_text(encoding="utf-8")
g = g.replace("versionCode 80", "versionCode 81")
g = g.replace("versionName '8.0.0-router-direct'", "versionName '8.1.0-ayoubi-ios'")
gradle.write_text(g, encoding="utf-8")
