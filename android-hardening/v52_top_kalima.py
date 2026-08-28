from pathlib import Path
p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/MainActivity.java')
s=p.read_text(encoding='utf-8')
# Replace only the top app brand text default; keep rest of UI untouched.
s=s.replace('prefs.getString("label.title","ندای بهشتی")','prefs.getString("label.title","لا إله إلا الله محمد رسول الله")')
s=s.replace('prefs.getString("label.title", "ندای بهشتی")','prefs.getString("label.title", "لا إله إلا الله محمد رسول الله")')
# Use the bundled SIL-OFL Amiri font directly from assets: no AndroidX dependency is required.
old='void applyTopTitleStyle(TextView v){v.setTextSize(prefs.getInt("title.size",titleSize()));v.setTextColor(safeColor("title.color",green()));v.setTypeface(topTitleTypeface(),prefs.getBoolean("title.bold",true)?Typeface.BOLD:Typeface.NORMAL);if(Build.VERSION.SDK_INT>=21)v.setLetterSpacing(Math.max(-.05f,Math.min(.20f,prefs.getFloat("title.letterSpacing",0f))));}'
if old in s:
    new='void applyTopTitleStyle(TextView v){v.setTextSize(prefs.getInt("title.size",22));v.setTextColor(safeColor("title.color",green()));try{v.setTypeface(Typeface.createFromAsset(getAssets(),"amiri_regular.ttf"),Typeface.NORMAL);}catch(Exception e){v.setTypeface(Typeface.SERIF,Typeface.NORMAL);}if(Build.VERSION.SDK_INT>=21)v.setLetterSpacing(0f);v.setTextDirection(View.TEXT_DIRECTION_RTL);v.setGravity(Gravity.CENTER);v.setSingleLine(true);}'
    s=s.replace(old,new,1)
# Hide the decorative symbols above the brand by default so the Kalima remains clean and elegant.
s=s.replace('prefs.getBoolean("title.showOrnament",true)?View.VISIBLE:View.GONE','prefs.getBoolean("title.showOrnament",false)?View.VISIBLE:View.GONE')
p.write_text(s,encoding='utf-8')
assert 'لا إله إلا الله محمد رسول الله' in s
print('Top title changed to Kalima Tayyiba with bundled Amiri Arabic styling')
