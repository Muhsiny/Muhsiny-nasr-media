package af.muhsiny.docstudio

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var script: EditText
    private lateinit var engineUrl: EditText
    private lateinit var projectTitle: EditText
    private lateinit var youtubeUrl: EditText
    private lateinit var voiceName: EditText
    private lateinit var voiceSpinner: Spinner
    private lateinit var voiceRights: CheckBox
    private lateinit var mediaRights: CheckBox
    private lateinit var useYoutube: CheckBox
    private lateinit var burnSubs: CheckBox
    private lateinit var openResultButton: Button
    private var lastResultUrl: String? = null
    private var selectedVoiceUri: Uri? = null
    private val voiceIds = mutableListOf("piper-ganji-adabi")
    private val voiceLabels = mutableListOf("Ganji Adabi")
    private val pickVoiceCode = 7001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(10, 15, 20)
        window.navigationBarColor = Color.rgb(10, 15, 20)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        setContentView(buildUi())
        restore()
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(10, 15, 20)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(22), dp(16), dp(42))
        }
        root.addView(label("استدیوی مستند ۱.۰", 30, Color.WHITE, true))
        root.addView(label("سناریو ← نریشن ← ویدیوی واقعی/آرشیفی ← موسیقی و افکت ← زیرنویس ← MP4", 13, Color.rgb(174,184,194), false).apply { setPadding(0,dp(7),0,dp(18)) })

        root.addView(sectionTitle("۱. پروژه و متن"))
        projectTitle=edit("نام مستند",false); root.addView(projectTitle)
        script=edit("متن کامل مستند را اینجا وارد کن…",true).apply{ minLines=10 }; root.addView(script)

        root.addView(sectionTitle("۲. موتور شخصی"))
        engineUrl=edit("آدرس موتور؛ مثال: http://192.168.1.10:8188",false).apply{inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI}; root.addView(engineUrl)
        root.addView(rowButtons("بررسی همهٔ موتور‌ها",::testEngine,"ذخیره",::saveProject))

        root.addView(sectionTitle("۳. صدای فارسی"))
        voiceSpinner=Spinner(this)
        refreshVoiceSpinner()
        root.addView(voiceSpinner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(56)).apply{bottomMargin=dp(8)})
        root.addView(button("تازه‌سازی فهرست صداها",::loadVoices))
        root.addView(cardText("صداهای پایه: Amir · Ganji · Ganji-Adabi · Aava HQ\nبرای کلون: فقط صدای خودت یا نمونه‌ای که اجازهٔ روشن استفاده از آن داری."))
        voiceName=edit("نام پروفایل صدای شخصی",false); root.addView(voiceName)
        voiceRights=check("تأیید می‌کنم حق استفاده و کلون این نمونهٔ صوتی را دارم",false); root.addView(voiceRights)
        root.addView(rowButtons("انتخاب نمونهٔ صدا",::pickVoiceFile,"ساخت پروفایل صوتی",::createVoiceProfile))
        root.addView(button("آزمایش نریشن فارسی",::testNarration))

        root.addView(sectionTitle("۴. ویدیوهای مرتبط و کتابخانه"))
        useYoutube=check("استفادهٔ خودکار از ویدیوهای مرتبطِ دارای مجوز",true); root.addView(useYoutube)
        root.addView(button("جستجوی ویدیوهای مرتبط با متن",::searchYoutube))
        youtubeUrl=edit("لینک YouTube برای بررسی/افزودن به کتابخانه",false); root.addView(youtubeUrl)
        mediaRights=check("برای این لینک حق استفاده دارم (فقط اگر مجوز خودکار تأیید نشد)",false); root.addView(mediaRights)
        root.addView(rowButtons("بررسی مجوز لینک",::inspectYoutube,"افزودن ویدیو",::importYoutubeVideo))
        root.addView(rowButtons("افزودن موزیک",{importYoutubeAudio("music")},"افزودن افکت",{importYoutubeAudio("sfx")}))

        root.addView(sectionTitle("۵. تصویر و ویدیوی تولیدی"))
        root.addView(cardText("پیش‌فرض: Photorealistic Documentary؛ بدون Anime/Cartoon/CGI. اگر ویدیوی آرشیفی مجاز پیدا نشود، موتور تصویر/ویدیوی واقع‌گرایانه را تولید می‌کند."))
        root.addView(rowButtons("تولید تصویر",{generateVisual("image")},"تولید ویدیو",{generateVisual("video")}))

        root.addView(sectionTitle("۶. خروجی نهایی"))
        burnSubs=check("زیرنویس فارسی روی ویدیوی نهایی درج شود",true); root.addView(burnSubs)
        root.addView(button("ساخت طرح صحنه‌ها",::planProject))
        val final=button("تولید کامل مستند",::buildFullDocumentary).apply{textSize=17f;minHeight=dp(64)}; root.addView(final)
        openResultButton=button("باز کردن آخرین خروجی",::openLastResult).apply{isEnabled=false;alpha=.45f}; root.addView(openResultButton)

        status=label("آماده",13,Color.rgb(215,181,109),true).apply{setPadding(dp(10),dp(16),dp(10),dp(8));gravity=Gravity.START}; root.addView(status)
        root.addView(label("اپ فقط وقتی «ساخته شد» می‌گوید که موتور محلی فایل خروجی واقعی برگرداند.",12,Color.rgb(130,142,153),false))
        scroll.addView(root); return scroll
    }

    private fun sectionTitle(t:String)=label(t,16,Color.rgb(215,181,109),true).apply{gravity=Gravity.START;setPadding(0,dp(20),0,dp(8))}
    private fun label(t:String,sp:Int,c:Int,b:Boolean)=TextView(this).apply{text=t;textSize=sp.toFloat();setTextColor(c);gravity=Gravity.CENTER_HORIZONTAL;if(b)setTypeface(typeface,android.graphics.Typeface.BOLD)}
    private fun cardText(t:String)=TextView(this).apply{text=t;textSize=13f;setTextColor(Color.WHITE);setBackgroundColor(Color.rgb(20,28,36));setPadding(dp(14),dp(13),dp(14),dp(13));gravity=Gravity.START;layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)}}
    private fun edit(h:String,multi:Boolean)=EditText(this).apply{hint=h;setHintTextColor(Color.rgb(118,130,141));setTextColor(Color.WHITE);setBackgroundColor(Color.rgb(20,28,36));setPadding(dp(13),dp(12),dp(13),dp(12));gravity=Gravity.TOP or Gravity.START;layoutParams=LinearLayout.LayoutParams(-1,if(multi)dp(220) else dp(56)).apply{bottomMargin=dp(9)}}
    private fun check(t:String,on:Boolean)=CheckBox(this).apply{text=t;isChecked=on;setTextColor(Color.WHITE);textSize=13f;layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)}}
    private fun button(t:String,a:()->Unit)=Button(this).apply{text=t;textSize=14f;setTextColor(Color.rgb(10,15,20));setBackgroundColor(Color.rgb(215,181,109));isAllCaps=false;setOnClickListener{a()};layoutParams=LinearLayout.LayoutParams(-1,dp(54)).apply{bottomMargin=dp(9)}}
    private fun rowButtons(a:String,aa:()->Unit,b:String,bb:()->Unit):View{
        val r=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        r.addView(button(a,aa),LinearLayout.LayoutParams(0,dp(54),1f).apply{marginEnd=dp(5);bottomMargin=dp(9)})
        r.addView(button(b,bb),LinearLayout.LayoutParams(0,dp(54),1f).apply{marginStart=dp(5);bottomMargin=dp(9)})
        return r
    }

    private fun base():String=engineUrl.text.toString().trim().trimEnd('/')
    private fun currentVoice():String=voiceIds.getOrElse(voiceSpinner.selectedItemPosition){"piper-ganji-adabi"}
    private fun show(t:String){status.text=t}
    private fun refreshVoiceSpinner(){voiceSpinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,voiceLabels)}

    private fun getJson(path:String,timeout:Int=30000):JSONObject{
        val c=URL(base()+path).openConnection() as HttpURLConnection;c.connectTimeout=8000;c.readTimeout=timeout;c.requestMethod="GET"
        val code=c.responseCode;val stream=if(code in 200..299)c.inputStream else c.errorStream;val raw=BufferedReader(InputStreamReader(stream)).use{it.readText()};val o=JSONObject(raw)
        if(code !in 200..299 || !o.optBoolean("ok",code in 200..299))throw RuntimeException(o.optString("error","HTTP $code"));return o
    }
    private fun postJson(path:String,payload:JSONObject,timeout:Int=1800000):JSONObject{
        val c=URL(base()+path).openConnection() as HttpURLConnection;c.connectTimeout=10000;c.readTimeout=timeout;c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.outputStream.use{it.write(payload.toString().toByteArray(Charsets.UTF_8))}
        val code=c.responseCode;val stream=if(code in 200..299)c.inputStream else c.errorStream;val raw=BufferedReader(InputStreamReader(stream)).use{it.readText()};val o=try{JSONObject(raw)}catch(_:Exception){JSONObject().put("error",raw)}
        if(code !in 200..299 || !o.optBoolean("ok",false))throw RuntimeException(o.optString("error","HTTP $code"));return o
    }

    private fun testEngine(){if(base().isBlank())return show("آدرس موتور را وارد کن");show("در حال بررسی…");io.execute{val msg=try{val o=getJson("/system_stats");val tools=o.optJSONObject("tools");val models=o.optJSONObject("models");val piper=models?.optJSONObject("piper_presets");val names=piper?.names();val readyVoices=if(piper!=null&&names!=null)(0 until names.length()).count{piper.optBoolean(names.getString(it))}else 0;val comfy=models?.optJSONObject("comfyui");"وصل ✓ | FFmpeg ${yes(tools?.optBoolean("ffmpeg") == true)} | YouTube ${yes(tools?.optBoolean("yt-dlp") == true)} | صدا $readyVoices | تصویر ${yes(comfy?.optBoolean("reachable") == true)}"}catch(e:Exception){"خطا: ${e.message}"};runOnUiThread{show(msg);loadVoices()}}}
    private fun yes(v:Boolean)=if(v)"✓" else "✗"

    private fun loadVoices(){if(base().isBlank())return;io.execute{try{val a=getJson("/voices").getJSONArray("voices");val ids=mutableListOf<String>();val labels=mutableListOf<String>();for(i in 0 until a.length()){val v=a.getJSONObject(i);if(v.optBoolean("ready",true)){ids.add(v.optString("id",v.optString("name")));labels.add(v.optString("name",v.optString("id")))}};if(ids.isEmpty()){ids.add("piper-ganji-adabi");labels.add("Ganji Adabi (نیاز به نصب موتور)")};runOnUiThread{voiceIds.clear();voiceIds.addAll(ids);voiceLabels.clear();voiceLabels.addAll(labels);refreshVoiceSpinner()}}catch(_:Exception){}}}

    private fun pickVoiceFile(){val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="audio/*"};startActivityForResult(i,pickVoiceCode)}
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==pickVoiceCode&&resultCode==RESULT_OK){selectedVoiceUri=data?.data;selectedVoiceUri?.let{try{contentResolver.takePersistableUriPermission(it,(data?.flags?:0) and Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};show("نمونهٔ صدا انتخاب شد: ${displayName(it)}")}}}
    private fun displayName(uri:Uri):String{contentResolver.query(uri,null,null,null,null)?.use{c->val ix=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(ix>=0&&c.moveToFirst())return c.getString(ix)};return "audio"}
    private fun createVoiceProfile(){val uri=selectedVoiceUri?:return show("نخست نمونهٔ صوتی را انتخاب کن");if(!voiceRights.isChecked)return show("تأیید حق استفاده از صدا لازم است");val name=voiceName.text.toString().trim().ifBlank{"صدای_شخصی"};show("در حال ساخت پروفایل صوتی…");io.execute{val msg=try{val bytes=contentResolver.openInputStream(uri)!!.use{it.readBytes()};if(bytes.size>25*1024*1024)throw RuntimeException("نمونهٔ صدا بیشتر از ۲۵ مگابایت است");val p=JSONObject().put("name",name).put("audio_base64",Base64.encodeToString(bytes,Base64.NO_WRAP)).put("rights_confirmed",true).put("source_type","user_upload");postJson("/voice/profile",p,120000);"پروفایل صوتی ساخته شد ✓"}catch(e:Exception){"ساخت صدا ناموفق: ${e.message}"};runOnUiThread{show(msg);loadVoices()}}}

    private fun testNarration(){val t=script.text.toString().trim();if(t.isBlank())return show("متن را وارد کن");show("در حال تولید نریشن…");io.execute{try{val p=JSONObject().put("text",t.take(550)).put("voice",currentVoice()).put("backend","auto");val o=postJson("/narration/generate",p,900000);val u=o.optString("url");runOnUiThread{show("نریشن ساخته شد ✓ | ${o.optString("backend")}");setResultUrl(u)}}catch(e:Exception){runOnUiThread{show("نریشن ناموفق: ${e.message}")}}}}

    private fun planProject(){val t=script.text.toString().trim();if(t.isBlank())return show("متن مستند را وارد کن");show("در حال صحنه‌بندی…");io.execute{try{val o=createPlan();val p=o.getJSONObject("project");runOnUiThread{show("طرح ساخته شد ✓ | ${p.getJSONArray("scenes").length()} صحنه")}}catch(e:Exception){runOnUiThread{show("صحنه‌بندی ناموفق: ${e.message}")}}}}
    private fun createPlan():JSONObject{return postJson("/project/plan",JSONObject().put("title",projectTitle.text.toString().trim().ifBlank{"مستند"}).put("script",script.text.toString().trim()).put("voice",currentVoice()).put("visual_mode","mixed"),120000)}

    private fun searchYoutube(){val q=script.text.toString().trim().take(180);if(q.isBlank())return show("متن مستند را وارد کن");show("در حال جستجوی و بررسی مجوز…");io.execute{try{val enc=URLEncoder.encode(q,"UTF-8");val arr=getJson("/youtube/search?q=$enc&limit=8",240000).getJSONArray("results");runOnUiThread{showYoutubeResults(arr)}}catch(e:Exception){runOnUiThread{show("جستجو ناموفق: ${e.message}")}}}}
    private fun showYoutubeResults(arr:JSONArray){if(arr.length()==0)return show("نتیجه‌ای پیدا نشد");val items=Array(arr.length()){i->val o=arr.getJSONObject(i);(if(o.optBoolean("verified_license"))"✓ مجاز  " else "؟ بررسی لازم  ")+o.optString("title")};AlertDialog.Builder(this).setTitle("ویدیوهای مرتبط").setItems(items){_,i->val u=arr.getJSONObject(i).optString("url");youtubeUrl.setText(u);show(if(arr.getJSONObject(i).optBoolean("verified_license"))"این نتیجه مجوز قابل‌استفاده دارد ✓" else "این نتیجه خودکار مجاز تأیید نشده")}.setNegativeButton("بستن",null).show()}
    private fun inspectYoutube(){val u=youtubeUrl.text.toString().trim();if(u.isBlank())return show("لینک را وارد کن");show("در حال بررسی مجوز…");io.execute{try{val v=postJson("/youtube/inspect",JSONObject().put("url",u),120000).getJSONObject("video");runOnUiThread{show("مجوز: ${v.optString("license","نامشخص")} | ${if(v.optBoolean("verified_license"))"قابل استفاده ✓" else "تأیید خودکار نشد"}")}}catch(e:Exception){runOnUiThread{show("بررسی ناموفق: ${e.message}")}}}}
    private fun importYoutubeVideo(){importYoutube("/youtube/import","video")}
    private fun importYoutubeAudio(kind:String){importYoutube("/youtube/import_audio",kind)}
    private fun importYoutube(path:String,kind:String){val u=youtubeUrl.text.toString().trim();if(u.isBlank())return show("لینک را وارد کن");show("در حال واردکردن $kind…");io.execute{try{val p=JSONObject().put("url",u).put("rights_confirmed",mediaRights.isChecked);if(path.endsWith("audio"))p.put("kind",kind);postJson(path,p,900000);runOnUiThread{show("به کتابخانه افزوده شد ✓")}}catch(e:Exception){runOnUiThread{show("واردکردن ناموفق: ${e.message}")}}}}

    private fun generateVisual(kind:String){val t=script.text.toString().trim().take(500);if(t.isBlank())return show("متن را وارد کن");show(if(kind=="video")"در حال تولید ویدیوی واقع‌گرایانه…" else "در حال تولید تصویر واقع‌گرایانه…");io.execute{try{val o=postJson("/visual/generate",JSONObject().put("type",kind).put("prompt",t),2400000);runOnUiThread{show("خروجی ساخته شد ✓ | ${o.optString("backend")}");setResultUrl(o.optString("url"))}}catch(e:Exception){runOnUiThread{show("تولید ناموفق: ${e.message}")}}}}

    private fun buildFullDocumentary(){val t=script.text.toString().trim();if(t.isBlank())return show("متن مستند را وارد کن");show("شروع خط تولید کامل مستند…");openResultButton.isEnabled=false;openResultButton.alpha=.45f;io.execute{try{val plan=createPlan();val pid=plan.getJSONObject("project").getString("id");runOnUiThread{show("صحنه‌بندی شد؛ نریشن و تصاویر در حال تولید…")};val payload=JSONObject().put("project_id",pid).put("voice",currentVoice()).put("tts_backend","auto").put("visual_mode","mixed").put("use_youtube",useYoutube.isChecked).put("rights_confirmed",false).put("burn_subtitles",burnSubs.isChecked);val o=postJson("/documentary/build",payload,7200000);runOnUiThread{show("مستند کامل ساخته شد ✓ | ${o.optInt("scenes")} صحنه | زیرنویس ${if(o.optBoolean("subtitles_burned"))"✓" else "SRT"}");setResultUrl(o.optString("url"))}}catch(e:Exception){runOnUiThread{show("ساخت مستند متوقف شد: ${e.message}")}}}}

    private fun setResultUrl(relative:String){if(relative.isBlank())return;lastResultUrl=if(relative.startsWith("http"))relative else base()+relative;openResultButton.isEnabled=true;openResultButton.alpha=1f}
    private fun openLastResult(){val u=lastResultUrl?:return show("هنوز خروجی ساخته نشده");try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(u)))}catch(e:Exception){show("باز کردن خروجی ممکن نشد: ${e.message}")}}
    private fun saveProject(){getSharedPreferences("docstudio",MODE_PRIVATE).edit().putString("title",projectTitle.text.toString()).putString("script",script.text.toString()).putString("engine",engineUrl.text.toString()).apply();show("ذخیره شد ✓")}
    private fun restore(){val p=getSharedPreferences("docstudio",MODE_PRIVATE);projectTitle.setText(p.getString("title",""));script.setText(p.getString("script",""));engineUrl.setText(p.getString("engine",""))}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
