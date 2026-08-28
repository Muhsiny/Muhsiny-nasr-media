from pathlib import Path
p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/MainActivity.java')
s=p.read_text(encoding='utf-8')
start=s.index('    View makeHeroCard(){')
end=s.index('\n    View makePrayerPanel(){',start)
hero=r'''    View makeHeroCard(){
        String coverPath=prefs.getString("theme.heroCoverPath","");
        if(!coverPath.isEmpty()&&new File(coverPath).exists()){
            FrameLayout frame=new FrameLayout(this);frame.setClipToOutline(true);frame.setElevation(dp(4));
            GradientDrawable bg=new GradientDrawable();bg.setColor(cardColor());bg.setCornerRadius(dp(radius()));bg.setStroke(dp(Math.max(1,stroke())),gold());frame.setBackground(bg);
            ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);cover.setAdjustViewBounds(true);cover.setImageURI(Uri.fromFile(new File(coverPath)));cover.setAlpha(1f);cover.setOnLongClickListener(v->{showAdminGate();return true;});
            frame.addView(cover,new FrameLayout.LayoutParams(-1,-1));
            return frame;
        }
        FrameLayout frame=new FrameLayout(this);frame.setClipToOutline(true);frame.setElevation(dp(4));
        GradientDrawable base=new GradientDrawable();base.setColor(cardColor());base.setCornerRadius(dp(radius()));base.setStroke(dp(Math.max(1,stroke())),gold());frame.setBackground(base);
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.HORIZONTAL);hero.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);hero.setGravity(Gravity.CENTER_VERTICAL);hero.setPadding(dp(7),dp(7),dp(7),dp(7));
        ImageView leader=new ImageView(this);String leaderFit=prefs.getString("theme.leaderFit","crop");leader.setScaleType("fit".equals(leaderFit)?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP);float leaderWidth=Math.max(.15f,Math.min(.80f,prefs.getFloat("theme.leaderWidth",.45f)));int leaderHeight=Math.max(64,Math.min(Math.max(64,heroHeight()-14),prefs.getInt("theme.leaderHeight",Math.max(64,heroHeight()-14))));float leaderScale=Math.max(.60f,Math.min(1.40f,prefs.getFloat("theme.leaderScale",1f)));int leaderX=Math.max(-80,Math.min(80,prefs.getInt("theme.leaderX",0))),leaderY=Math.max(-80,Math.min(80,prefs.getInt("theme.leaderY",0)));leader.setScaleX(leaderScale);leader.setScaleY(leaderScale);leader.setTranslationX(dp(leaderX));leader.setTranslationY(dp(leaderY));String leaderPath=prefs.getString("theme.leaderPath","");
        if(!leaderPath.isEmpty()&&new File(leaderPath).exists())leader.setImageURI(Uri.fromFile(new File(leaderPath)));else leader.setImageResource(R.drawable.leader_hero);
        GradientDrawable ibg=new GradientDrawable();ibg.setColor(withAlpha(cardColor(),210));ibg.setCornerRadius(dp(Math.max(18,radius()-3)));ibg.setStroke(dp(1),gold());leader.setBackground(ibg);leader.setClipToOutline(true);leader.setOnLongClickListener(v->{showAdminGate();return true;});
        LinearLayout.LayoutParams leaderLp=new LinearLayout.LayoutParams(0,dp(leaderHeight),leaderWidth);leaderLp.gravity=Gravity.CENTER_VERTICAL;hero.addView(leader,leaderLp);
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.setGravity(Gravity.CENTER);tx.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);tx.setPadding(dp(12),dp(12),dp(8),dp(12));
        TextView h=text(prefs.getString("label.heroTitle","ندای\nبهشتی"),heroTitleSize(),green(),true);h.setGravity(Gravity.CENTER);h.setPadding(0,0,0,0);h.setLineSpacing(0,0.94f);
        TextView tag=text(prefs.getString("label.heroSubtitle","همراه معنوی شما"),14,textColor(),false);tag.setGravity(Gravity.CENTER);tag.setPadding(0,dp(3),0,dp(3));
        TextView chip=text(prefs.getBoolean("notifications",true)?"● اذان فعال  •  جعفری":"○ اذان خاموش  •  جعفری",11,prefs.getBoolean("notifications",true)?green():Color.DKGRAY,true);chip.setGravity(Gravity.CENTER);chip.setBackground(pillBg(0x33FFFFFF,gold()));chip.setPadding(dp(9),dp(5),dp(9),dp(5));
        tx.addView(h,new LinearLayout.LayoutParams(-1,0,1.7f));tx.addView(tag,new LinearLayout.LayoutParams(-1,0,.55f));LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-2,dp(31));clp.gravity=Gravity.CENTER;tx.addView(chip,clp);
        hero.addView(tx,new LinearLayout.LayoutParams(0,-1,Math.max(.20f,1f-leaderWidth)));frame.addView(hero,new FrameLayout.LayoutParams(-1,-1));return frame;
    }
'''
s=s[:start]+hero+s[end:]
# Cover mode: no extra overlay controls are needed; chosen banner is the complete visual.
s=s.replace('EditText ca=field("شفافیت تصویر قاب 0.15 تا 1",String.valueOf(prefs.getFloat("theme.heroCoverAlpha",1f)));ca.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);addCard(b,ca);EditText oa=field("لایه روشن روی قاب 0 تا 0.92",String.valueOf(prefs.getFloat("theme.heroOverlayAlpha",.18f)));oa.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);addCard(b,oa); ', '')
s=s.replace('.putFloat("theme.heroCoverAlpha",Math.max(.15f,Math.min(1f,Float.parseFloat(ca.getText().toString())))).putFloat("theme.heroOverlayAlpha",Math.max(0f,Math.min(.92f,Float.parseFloat(oa.getText().toString()))))', '')
p.write_text(s,encoding='utf-8')
print('Single clean hero cover mode applied')
