package com.nadaye.beheshti;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.*;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity implements SensorEventListener {
    static final int CREAM = Color.rgb(250,246,238);
    static final int GREEN = Color.rgb(5,66,50);
    static final int GREEN2 = Color.rgb(9,82,62);
    static final int GOLD = Color.rgb(202,156,66);
    static final int TEXT = Color.rgb(19,57,46);
    SharedPreferences prefs;
    boolean atHome = true;
    FrameLayout homeRoot;
    SensorManager sensorManager;
    Sensor rotationSensor;
    QiblaView qiblaView;
    boolean qiblaActive = false;
    float qiblaAngle = 0f;
    MediaPlayer player;
    JSONArray quranVerses;
    final String[] surahNames = {"الفاتحة","البقرة","آل عمران","النساء","المائدة","الأنعام","الأعراف","الأنفال","التوبة","يونس","هود","یوسف","الرعد","إبراهیم","الحجر","النحل","الإسراء","الکهف","مریم","طه","الأنبیاء","الحج","المؤمنون","النور","الفرقان","الشعراء","النمل","القصص","العنکبوت","الروم","لقمان","السجدة","الأحزاب","سبأ","فاطر","یس","الصافات","ص","الزمر","غافر","فصلت","الشوری","الزخرف","الدخان","الجاثیة","الأحقاف","محمد","الفتح","الحجرات","ق","الذاریات","الطور","النجم","القمر","الرحمن","الواقعة","الحدید","المجادلة","الحشر","الممتحنة","الصف","الجمعة","المنافقون","التغابن","الطلاق","التحریم","الملک","القلم","الحاقة","المعارج","نوح","الجن","المزمل","المدثر","القیامة","الإنسان","المرسلات","النبأ","النازعات","عبس","التکویر","الإنفطار","المطففین","الإنشقاق","البروج","الطارق","الأعلی","الغاشیة","الفجر","البلد","الشمس","اللیل","الضحی","الشرح","التین","العلق","القدر","البینة","الزلزلة","العادیات","القارعة","التکاثر","العصر","الهمزة","الفیل","قریش","الماعون","الکوثر","الکافرون","النصر","المسد","الإخلاص","الفلق","الناس"};

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("nadaye", MODE_PRIVATE);
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        requestNeededPermissions();
        showHome();
    }

    void immersive(boolean yes) {
        int v = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (yes) v |= View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        getWindow().getDecorView().setSystemUiVisibility(v);
        getWindow().setStatusBarColor(CREAM); getWindow().setNavigationBarColor(CREAM);
    }

    void requestNeededPermissions() {
        ArrayList<String> p = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.POST_NOTIFICATIONS);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), 500);
        else refreshLocation(false);
    }

    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) { super.onRequestPermissionsResult(r,p,g); refreshLocation(false); }

    void showHome() {
        atHome = true; qiblaActive = false; if (sensorManager != null) sensorManager.unregisterListener(this); immersive(true);
        homeRoot = new FrameLayout(this); homeRoot.setBackgroundColor(CREAM);
        ImageView bg = new ImageView(this); bg.setImageResource(getResources().getIdentifier("home_reference","drawable",getPackageName())); bg.setScaleType(ImageView.ScaleType.FIT_XY);
        homeRoot.addView(bg, new FrameLayout.LayoutParams(-1,-1));
        addHotspot(.845f,.045f,.125f,.075f, v->showGlobalSearch());
        addHotspot(.025f,.045f,.12f,.075f, v->showNotifications());
        addHotspot(.02f,.375f,.96f,.195f, v->showPrayerTimes());
        addHotspot(.025f,.575f,.30f,.13f, v->showQuran());
        addHotspot(.345f,.575f,.30f,.13f, v->showDuas());
        addHotspot(.665f,.575f,.31f,.13f, v->showAdhan());
        addHotspot(.025f,.705f,.30f,.13f, v->showQibla());
        addHotspot(.345f,.705f,.30f,.13f, v->showTasbih());
        addHotspot(.665f,.705f,.31f,.13f, v->showSettings());
        addHotspot(.025f,.91f,.18f,.085f, v->showProfile());
        addHotspot(.205f,.91f,.18f,.085f, v->showMedia());
        addHotspot(.405f,.90f,.19f,.10f, v->showHome());
        addHotspot(.60f,.91f,.20f,.085f, v->showArticles());
        addHotspot(.80f,.91f,.19f,.085f, v->showMore());
        setContentView(homeRoot);
        overlayLiveTimes();
    }

    void addHotspot(float x,float y,float w,float h, View.OnClickListener l) {
        View v = new View(this); v.setBackgroundColor(Color.TRANSPARENT); v.setOnClickListener(l); homeRoot.addView(v);
        homeRoot.post(() -> { int W=homeRoot.getWidth(), H=homeRoot.getHeight(); FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams((int)(W*w),(int)(H*h)); lp.leftMargin=(int)(W*x); lp.topMargin=(int)(H*y); v.setLayoutParams(lp); });
    }

    void overlayLiveTimes() {
        PrayerTimes.Result t = todayTimes();
        String[] keys={"عشاء","مغرب","ظهر","طلوع","فجر"}; float[] xs={.078f,.275f,.475f,.675f,.835f};
        for(int i=0;i<keys.length;i++){
            TextView tv=new TextView(this); tv.setText(t.get(keys[i])); tv.setTextColor(i==2?GOLD:Color.WHITE); tv.setTextSize(14); tv.setGravity(Gravity.CENTER); tv.setTypeface(null,Typeface.BOLD);
            GradientDrawable gd=new GradientDrawable(); gd.setColor(i==2?Color.rgb(11,81,61):Color.rgb(7,70,54)); gd.setCornerRadius(dp(8)); tv.setBackground(gd); homeRoot.addView(tv);
            final float x=xs[i]; homeRoot.post(()->{int W=homeRoot.getWidth(),H=homeRoot.getHeight();FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams((int)(W*.105f),(int)(H*.033f));lp.leftMargin=(int)(W*x);lp.topMargin=(int)(H*.523f);tv.setLayoutParams(lp);});
        }
    }

    double lat(){ return Double.longBitsToDouble(prefs.getLong("lat",Double.doubleToLongBits(34.5553))); }
    double lon(){ return Double.longBitsToDouble(prefs.getLong("lon",Double.doubleToLongBits(69.2075))); }
    PrayerTimes.Result todayTimes(){ return PrayerTimes.calculate(System.currentTimeMillis(),lat(),lon()); }

    void refreshLocation(boolean toast) {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) return;
        try{
            LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE); Location best=null;
            for(String p:lm.getProviders(true)){ Location l=lm.getLastKnownLocation(p); if(l!=null&&(best==null||l.getAccuracy()<best.getAccuracy())) best=l; }
            if(best!=null){ prefs.edit().putLong("lat",Double.doubleToLongBits(best.getLatitude())).putLong("lon",Double.doubleToLongBits(best.getLongitude())).apply(); if(toast) toast("موقعیت به‌روزرسانی شد"); }
            else if(toast) toast("موقعیت تازه در دسترس نیست؛ GPS را روشن کنید");
        }catch(Exception e){ if(toast) toast("دسترسی موقعیت ممکن نشد"); }
    }

    LinearLayout page(String title) {
        atHome=false; immersive(false); qiblaActive=false; if(sensorManager!=null) sensorManager.unregisterListener(this);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); root.setBackgroundColor(CREAM);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(14),dp(14),dp(14),dp(10));
        Button back=smallButton("‹ خانه");back.setOnClickListener(v->showHome());
        TextView titleV=text(title,24,GREEN,true); titleV.setGravity(Gravity.CENTER); LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(54),1);bar.addView(titleV,tp);bar.addView(back,new LinearLayout.LayoutParams(dp(92),dp(46)));
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(72))); setContentView(root); return root;
    }

    ScrollView scrollPage(String title){ LinearLayout root=page(title);ScrollView s=new ScrollView(this); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);root.addView(s,sp);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(4),dp(16),dp(28));body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);s.addView(body);s.setTag(body);return s; }
    LinearLayout body(ScrollView s){return (LinearLayout)s.getTag();}

    TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.RIGHT);t.setLineSpacing(dp(4),1f);t.setPadding(dp(12),dp(8),dp(12),dp(8));if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(17);b.setAllCaps(false);GradientDrawable g=new GradientDrawable();g.setColor(GREEN);g.setCornerRadius(dp(18));g.setStroke(dp(1),GOLD);b.setBackground(g);b.setPadding(dp(12),0,dp(12),0);return b;}
    Button smallButton(String s){Button b=button(s);b.setTextSize(14);return b;}
    GradientDrawable cardBg(){GradientDrawable g=new GradientDrawable();g.setColor(Color.WHITE);g.setCornerRadius(dp(22));g.setStroke(dp(1),0x40C79B43);return g;}
    void addCard(LinearLayout p, View v){ LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(10),dp(10),dp(10),dp(10));c.setBackground(cardBg());c.addView(v);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(8),0,dp(8));p.addView(c,lp); }
    int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);} void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    void showPrayerTimes(){
        ScrollView s=scrollPage("اوقات شرعی");LinearLayout b=body(s);PrayerTimes.Result t=todayTimes();
        addCard(b,text("محاسبه جعفری بر اساس موقعیت دستگاه\nعرض: "+String.format(Locale.US,"%.4f",lat())+"   طول: "+String.format(Locale.US,"%.4f",lon()),16,TEXT,false));
        for(Map.Entry<String,Long> e:t.millis.entrySet()){TextView row=text(e.getKey()+"        "+PrayerTimes.fmt(e.getValue()),22,e.getKey().equals("ظهر")?GOLD:GREEN,true);row.setGravity(Gravity.CENTER);addCard(b,row);}
        Button loc=button("به‌روزرسانی موقعیت GPS");loc.setOnClickListener(v->{refreshLocation(true);showPrayerTimes();});b.addView(loc,new LinearLayout.LayoutParams(-1,dp(56)));
        Button sch=button("فعال‌سازی اذان برای اوقات امروز");sch.setOnClickListener(v->{AlarmReceiver.scheduleToday(this);toast("اوقات اذان تنظیم شد");});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.setMargins(0,dp(10),0,0);b.addView(sch,lp);
        addCard(b,text("نماز بعدی: "+PrayerTimes.nextPrayer(t,System.currentTimeMillis()),17,TEXT,true));
    }

    void loadQuran(){ if(quranVerses!=null)return; try{InputStream in=getAssets().open("quran.json");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);JSONObject o=new JSONObject(out.toString("UTF-8"));quranVerses=o.getJSONArray("verses");}catch(Exception e){quranVerses=new JSONArray();}}

    void showQuran(){
        LinearLayout root=page("قرآن کریم");LinearLayout ctl=new LinearLayout(this);ctl.setPadding(dp(12),dp(6),dp(12),dp(6));ctl.setOrientation(LinearLayout.VERTICAL);
        Spinner sp=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,surahNames);sp.setAdapter(a);ctl.addView(sp,new LinearLayout.LayoutParams(-1,dp(54)));
        LinearLayout audio=new LinearLayout(this);EditText ayah=new EditText(this);ayah.setHint("شماره آیه");ayah.setInputType(2);Button play=smallButton("پخش/دانلود صوت");audio.addView(play,new LinearLayout.LayoutParams(0,dp(50),2));audio.addView(ayah,new LinearLayout.LayoutParams(0,dp(50),1));ctl.addView(audio);
        root.addView(ctl,new LinearLayout.LayoutParams(-1,-2));ScrollView sv=new ScrollView(this);TextView q=text("در حال بارگذاری...",24,TEXT,false);q.setGravity(Gravity.RIGHT);q.setTextIsSelectable(true);sv.addView(q);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        Runnable render=()->{loadQuran();int n=sp.getSelectedItemPosition()+1;StringBuilder x=new StringBuilder();x.append("﷽\n\n");for(int i=0;i<quranVerses.length();i++){try{JSONObject v=quranVerses.getJSONObject(i);if(v.optInt("surah")==n)x.append(v.optString("text_ar")).append("  ﴿").append(v.optInt("ayah")).append("﴾  ");}catch(Exception ignored){}}q.setText(x.length()>4?x.toString():"متن قرآن در بسته نصب یافت نشد.");};
        sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?>p,View v,int pos,long id){render.run();}public void onNothingSelected(android.widget.AdapterView<?>p){}});render.run();
        play.setOnClickListener(v->{int n=sp.getSelectedItemPosition()+1;int av;try{av=Integer.parseInt(ayah.getText().toString().trim());}catch(Exception e){av=1;}downloadAndPlayAyah(n,av);});
    }

    void downloadAndPlayAyah(int surah,int ayah){
        File dir=new File(getFilesDir(),"quran_audio");dir.mkdirs();File f=new File(dir,String.format(Locale.US,"%03d%03d.mp3",surah,ayah));if(f.exists()){playFile(f);return;}toast("در حال دانلود؛ پس از آن آفلاین هم پخش می‌شود");
        new Thread(()->{try{URL u=new URL("https://everyayah.com/data/Alafasy_128kbps/"+f.getName());HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(f)){byte[]buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);}runOnUiThread(()->playFile(f));}catch(Exception e){runOnUiThread(()->toast("دانلود صوت ممکن نشد"));}},"quran-download").start();
    }
    void playFile(File f){try{if(player!=null)player.release();player=new MediaPlayer();player.setDataSource(f.getAbsolutePath());player.setOnPreparedListener(MediaPlayer::start);player.prepareAsync();toast("پخش آیه");}catch(Exception e){toast("پخش ممکن نشد");}}

    LinkedHashMap<String,String> duas(){LinkedHashMap<String,String>d=new LinkedHashMap<>();d.put("دعای فرج","إِلٰهِی عَظُمَ الْبَلاءُ، وَبَرِحَ الْخَفاءُ، وَانْکَشَفَ الْغِطاءُ، وَانْقَطَعَ الرَّجاءُ، وَضاقَتِ الْأَرْضُ، وَمُنِعَتِ السَّماءُ، وَأَنْتَ الْمُسْتَعانُ وَإِلَیْکَ الْمُشْتَکیٰ، وَعَلَیْکَ الْمُعَوَّلُ فِی الشِّدَّةِ وَالرَّخاءِ. اَللّٰهُمَّ صَلِّ عَلیٰ مُحَمَّدٍ وَآلِ مُحَمَّدٍ... یا مُحَمَّدُ یا عَلِیُّ، یا عَلِیُّ یا مُحَمَّدُ، اِکْفِیانی فَإِنَّکُما کافِیانِ، وَانْصُرانی فَإِنَّکُما ناصِرانِ. یا مَوْلانا یا صاحِبَ الزَّمانِ، الْغَوْثَ الْغَوْثَ الْغَوْثَ، أَدْرِکْنی أَدْرِکْنی أَدْرِکْنی.");d.put("دعای سلامتی امام زمان","اَللّٰهُمَّ کُنْ لِوَلِیِّکَ الْحُجَّةِ بْنِ الْحَسَنِ صَلَواتُکَ عَلَیْهِ وَعَلیٰ آبائِهِ، فِی هٰذِهِ السّاعَةِ وَفِی کُلِّ ساعَةٍ، وَلِیّاً وَحافِظاً وَقائِداً وَناصِراً وَدَلِیلاً وَعَیْناً، حَتّیٰ تُسْکِنَهُ أَرْضَکَ طَوْعاً وَتُمَتِّعَهُ فِیها طَوِیلاً.");d.put("آیت‌الکرسی","اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَیُّ الْقَیُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَهُ مَا فِی السَّمَاوَاتِ وَمَا فِی الْأَرْضِ...");d.put("ذکر استغفار","أَسْتَغْفِرُ اللّٰهَ رَبِّی وَأَتُوبُ إِلَیْهِ");return d;}
    void showDuas(){ScrollView s=scrollPage("دعاها");LinearLayout b=body(s);for(Map.Entry<String,String>e:duas().entrySet()){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setBackground(cardBg());c.setPadding(dp(12),dp(12),dp(12),dp(12));c.addView(text(e.getKey(),20,GREEN,true));TextView tx=text(e.getValue(),21,TEXT,false);tx.setTextIsSelectable(true);c.addView(tx);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(7),0,dp(7));b.addView(c,lp);}}

    void showAdhan(){
        ScrollView s=scrollPage("اذان و اعلان نماز");LinearLayout b=body(s);addCard(b,text("اذان در وقت‌های فجر، ظهر، مغرب و عشاء با AlarmManager تنظیم می‌شود. برای صوت می‌توانید فایل دلخواه MP3 خود را یک‌بار انتخاب کنید؛ فایل روی دستگاه باقی می‌ماند و بدون اینترنت پخش می‌شود.",17,TEXT,false));
        Button pick=button("انتخاب فایل صوت اذان");pick.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("audio/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,700);});b.addView(pick,new LinearLayout.LayoutParams(-1,dp(58)));
        Button test=button("پخش آزمایشی اذان");test.setOnClickListener(v->AdhanService.start(this,"آزمایش اذان"));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));lp.setMargins(0,dp(10),0,0);b.addView(test,lp);
        Button sch=button("تنظیم همه اذان‌های امروز");sch.setOnClickListener(v->{requestExactAlarmIfNeeded();AlarmReceiver.scheduleToday(this);toast("اذان‌های امروز زمان‌بندی شد");});b.addView(sch,lp);
    }

    void requestExactAlarmIfNeeded(){if(Build.VERSION.SDK_INT>=31){AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);if(!am.canScheduleExactAlarms()){try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}catch(Exception ignored){}}}}

    @Override protected void onActivityResult(int r,int c,Intent data){super.onActivityResult(r,c,data);if(r==700&&c==RESULT_OK&&data!=null&&data.getData()!=null){Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}prefs.edit().putString("adhan_uri",u.toString()).apply();toast("صوت اذان ذخیره شد");}}

    void showQibla(){
        LinearLayout root=page("قبله‌نما");qiblaActive=true;qiblaView=new QiblaView(this);root.addView(qiblaView,new LinearLayout.LayoutParams(-1,0,1));TextView info=text("فلش طلایی جهت قبله را نشان می‌دهد. برای دقت بهتر، GPS و حسگر قطب‌نما را فعال نگه دارید.",16,TEXT,false);info.setGravity(Gravity.CENTER);root.addView(info,new LinearLayout.LayoutParams(-1,dp(86)));if(rotationSensor!=null)sensorManager.registerListener(this,rotationSensor,SensorManager.SENSOR_DELAY_UI);else toast("این دستگاه حسگر جهت‌گیری ندارد");refreshLocation(false);
    }

    double qiblaBearing(){double lat1=Math.toRadians(lat()),lon1=Math.toRadians(lon()),lat2=Math.toRadians(21.422487),lon2=Math.toRadians(39.826206);double d=lon2-lon1;double y=Math.sin(d)*Math.cos(lat2),x=Math.cos(lat1)*Math.sin(lat2)-Math.sin(lat1)*Math.cos(lat2)*Math.cos(d);return (Math.toDegrees(Math.atan2(y,x))+360)%360;}
    @Override public void onSensorChanged(SensorEvent e){if(!qiblaActive||e.sensor.getType()!=Sensor.TYPE_ROTATION_VECTOR)return;float[]R=new float[9],o=new float[3];SensorManager.getRotationMatrixFromVector(R,e.values);SensorManager.getOrientation(R,o);float az=(float)Math.toDegrees(o[0]);if(az<0)az+=360;qiblaAngle=(float)(qiblaBearing()-az);if(qiblaView!=null)qiblaView.invalidate();}
    @Override public void onAccuracyChanged(Sensor s,int a){}
    class QiblaView extends View{Paint p=new Paint(1);public QiblaView(Context c){super(c);setBackgroundColor(CREAM);}protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.34f;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(5));p.setColor(GREEN);c.drawCircle(cx,cy,r,p);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(dp(22));p.setColor(TEXT);p.setStyle(Paint.Style.FILL);c.drawText("شمال",cx,cy-r-dp(18),p);c.save();c.rotate(qiblaAngle,cx,cy);Path path=new Path();path.moveTo(cx,cy-r*.85f);path.lineTo(cx-dp(18),cy-dp(10));path.lineTo(cx+dp(18),cy-dp(10));path.close();p.setColor(GOLD);c.drawPath(path,p);p.setStrokeWidth(dp(8));c.drawLine(cx,cy-dp(8),cx,cy-r*.72f,p);c.restore();p.setTextSize(dp(20));p.setColor(GREEN);c.drawText("کعبه  "+String.format(Locale.US,"%.0f°",qiblaBearing()),cx,cy+r+dp(45),p);}}

    void showTasbih(){
        LinearLayout root=page("ذکرشمار");root.setGravity(Gravity.CENTER_HORIZONTAL);TextView label=text(prefs.getString("zekr_name","صلوات"),22,GREEN,true);label.setGravity(Gravity.CENTER);root.addView(label,new LinearLayout.LayoutParams(-1,dp(70)));TextView count=text(String.valueOf(prefs.getInt("zekr_count",0)),72,GOLD,true);count.setGravity(Gravity.CENTER);root.addView(count,new LinearLayout.LayoutParams(-1,0,1));Button add=button("ذکر + ۱");add.setTextSize(26);add.setOnClickListener(v->{int n=prefs.getInt("zekr_count",0)+1;prefs.edit().putInt("zekr_count",n).apply();count.setText(String.valueOf(n));if(n%100==0)((android.os.Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(80);});root.addView(add,new LinearLayout.LayoutParams(-1,dp(72)));Button reset=smallButton("صفر کردن شمارش");reset.setOnClickListener(v->{prefs.edit().putInt("zekr_count",0).apply();count.setText("0");});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.setMargins(dp(24),dp(12),dp(24),dp(24));root.addView(reset,lp);
    }

    void showSettings(){
        ScrollView s=scrollPage("تنظیمات");LinearLayout b=body(s);addCard(b,text("روش محاسبه: جعفری (فجر ۱۶°، مغرب ۴°، عشاء ۱۴°)\nموقعیت فعلی: "+String.format(Locale.US,"%.5f, %.5f",lat(),lon()),16,TEXT,false));
        Button loc=button("گرفتن موقعیت از GPS");loc.setOnClickListener(v->{refreshLocation(true);showSettings();});b.addView(loc,new LinearLayout.LayoutParams(-1,dp(56)));
        EditText la=new EditText(this);la.setHint("عرض جغرافیایی دستی");la.setText(String.valueOf(lat()));la.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);EditText lo=new EditText(this);lo.setHint("طول جغرافیایی دستی");lo.setText(String.valueOf(lon()));lo.setInputType(la.getInputType());addCard(b,la);addCard(b,lo);
        Button save=button("ذخیره موقعیت دستی");save.setOnClickListener(v->{try{double x=Double.parseDouble(la.getText().toString()),y=Double.parseDouble(lo.getText().toString());prefs.edit().putLong("lat",Double.doubleToLongBits(x)).putLong("lon",Double.doubleToLongBits(y)).apply();AlarmReceiver.scheduleToday(this);toast("ذخیره شد");}catch(Exception e){toast("اعداد موقعیت درست نیست");}});b.addView(save,new LinearLayout.LayoutParams(-1,dp(56)));
        CheckBox cb=new CheckBox(this);cb.setText("اذان و اعلان نماز فعال باشد");cb.setTextSize(17);cb.setChecked(prefs.getBoolean("notifications",true));cb.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("notifications",on).apply();if(on)AlarmReceiver.scheduleToday(this);else AlarmReceiver.cancelAll(this);});addCard(b,cb);
        Button audio=button("تغییر فایل صوت اذان");audio.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("audio/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,700);});b.addView(audio,new LinearLayout.LayoutParams(-1,dp(56)));
        Button exact=button("اجازه آلارم دقیق اندروید");exact.setOnClickListener(v->requestExactAlarmIfNeeded());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.setMargins(0,dp(10),0,0);b.addView(exact,lp);
    }

    void showGlobalSearch(){
        LinearLayout root=page("جستجو");LinearLayout box=new LinearLayout(this);box.setPadding(dp(12),dp(8),dp(12),dp(8));EditText q=new EditText(this);q.setHint("جستجو در قرآن و دعاها");Button go=smallButton("جستجو");box.addView(go,new LinearLayout.LayoutParams(dp(100),dp(52)));box.addView(q,new LinearLayout.LayoutParams(0,dp(52),1));root.addView(box);ScrollView sv=new ScrollView(this);TextView result=text("واژه‌ای بنویسید.",18,TEXT,false);sv.addView(result);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));go.setOnClickListener(v->{String needle=q.getText().toString().trim();if(needle.length()<2){toast("حداقل دو حرف");return;}StringBuilder out=new StringBuilder();for(Map.Entry<String,String>e:duas().entrySet())if(e.getValue().contains(needle)||e.getKey().contains(needle))out.append("◆ ").append(e.getKey()).append("\n").append(e.getValue()).append("\n\n");loadQuran();int hits=0;for(int i=0;i<quranVerses.length()&&hits<80;i++){try{JSONObject a=quranVerses.getJSONObject(i);String tx=a.optString("text_ar");if(tx.contains(needle)){out.append("سوره ").append(a.optInt("surah")).append("، آیه ").append(a.optInt("ayah")).append(": ").append(tx).append("\n\n");hits++;}}catch(Exception ignored){}}result.setText(out.length()==0?"نتیجه‌ای پیدا نشد.":out.toString());});
    }

    void showNotifications(){ScrollView s=scrollPage("اعلان‌ها");LinearLayout b=body(s);PrayerTimes.Result t=todayTimes();addCard(b,text("نماز بعدی\n"+PrayerTimes.nextPrayer(t,System.currentTimeMillis()),24,GREEN,true));addCard(b,text("برای دریافت اذان دقیق، اعلان‌های برنامه و اجازه آلارم دقیق اندروید باید فعال باشد.",16,TEXT,false));Button x=button("تنظیم دوباره اعلان‌های امروز");x.setOnClickListener(v->{requestExactAlarmIfNeeded();AlarmReceiver.scheduleToday(this);toast("تنظیم شد");});b.addView(x,new LinearLayout.LayoutParams(-1,dp(56)));}
    void showProfile(){ScrollView s=scrollPage("من");LinearLayout b=body(s);addCard(b,text("ندای بهشتی\nهمراه معنوی شما\n\nتنظیمات، فایل صوت اذان، شمارش ذکر و صوت‌های دانلودشده قرآن در همین دستگاه نگهداری می‌شوند.",18,TEXT,false));Button st=button("تنظیمات من");st.setOnClickListener(v->showSettings());b.addView(st,new LinearLayout.LayoutParams(-1,dp(56)));}
    void showMedia(){ScrollView s=scrollPage("رسانه");LinearLayout b=body(s);addCard(b,text("کتابخانه صوتی آفلاین\nهر آیه‌ای که از بخش قرآن دانلود شود، در حافظه خصوصی برنامه ذخیره می‌گردد.",18,TEXT,false));File dir=new File(getFilesDir(),"quran_audio");File[] fs=dir.listFiles();addCard(b,text("فایل‌های صوتی دانلودشده: "+(fs==null?0:fs.length),20,GREEN,true));Button q=button("رفتن به قرآن و صوت");q.setOnClickListener(v->showQuran());b.addView(q,new LinearLayout.LayoutParams(-1,dp(56)));}
    void showArticles(){ScrollView s=scrollPage("مقالات");LinearLayout b=body(s);String[] a={"نماز؛ نظم روزانه روح","قرآن؛ مونس خانه و سفر","ذکر؛ تمرین حضور قلب","قبله؛ جهت واحد عبادت"};String[] d={"اوقات نماز، روز را به ایستگاه‌های آگاهی و توجه تقسیم می‌کند.","خواندن روزانه حتی چند آیه، پیوند پیوسته با متن وحی ایجاد می‌کند.","ذکر کوتاه اما پیوسته، می‌تواند به تمرکز و مراقبه دینی کمک کند.","قبله‌نما با موقعیت جغرافیایی و حسگر دستگاه، جهت کعبه را محاسبه می‌کند."};for(int i=0;i<a.length;i++)addCard(b,text(a[i]+"\n\n"+d[i],18,TEXT,i==0));}
    void showMore(){ScrollView s=scrollPage("بیشتر");LinearLayout b=body(s);String[] names={"اوقات شرعی","قرآن","دعاها","اذان","قبله‌نما","ذکرشمار","تنظیمات"};Runnable[] rs={this::showPrayerTimes,this::showQuran,this::showDuas,this::showAdhan,this::showQibla,this::showTasbih,this::showSettings};for(int i=0;i<names.length;i++){Button x=button(names[i]);final int j=i;x.setOnClickListener(v->rs[j].run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.setMargins(0,dp(5),0,dp(5));b.addView(x,lp);}}

    @Override public void onBackPressed(){if(!atHome)showHome();else super.onBackPressed();}
    @Override protected void onPause(){super.onPause();if(sensorManager!=null)sensorManager.unregisterListener(this);}
    @Override protected void onResume(){super.onResume();if(qiblaActive&&rotationSensor!=null)sensorManager.registerListener(this,rotationSensor,SensorManager.SENSOR_DELAY_UI);}
}
