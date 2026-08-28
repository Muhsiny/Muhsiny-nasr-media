from pathlib import Path
p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/MainActivity.java')
s=p.read_text(encoding='utf-8')

# Clean native vector-style icon defaults. They are sharp at every Android density and
# replace the old hand-drawn feature/top/bottom defaults. Owner overrides still work.
anchor='    GradientDrawable pillBg(int fill,int strokeColor){'
assert anchor in s
helper=r'''    Drawable proIcon(String key,int color){
        final int c=color;
        return new Drawable(){Paint p=new Paint(3);Path q=new Path();@Override public void draw(Canvas x){float w=getBounds().width(),h=getBounds().height(),cx=w/2f,cy=h/2f,u=Math.min(w,h)/24f;p.setColor(c);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1.7f,u*1.55f));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);q.reset();
            if("quran".equals(key)){x.drawLine(cx,cy-7*u,cx,cy+8*u,p);q.moveTo(cx,cy-6*u);q.cubicTo(cx-4*u,cy-9*u,cx-9*u,cy-8*u,cx-10*u,cy-5*u);q.lineTo(cx-10*u,cy+7*u);q.cubicTo(cx-6*u,cy+5*u,cx-3*u,cy+6*u,cx,cy+9*u);x.drawPath(q,p);q.reset();q.moveTo(cx,cy-6*u);q.cubicTo(cx+4*u,cy-9*u,cx+9*u,cy-8*u,cx+10*u,cy-5*u);q.lineTo(cx+10*u,cy+7*u);q.cubicTo(cx+6*u,cy+5*u,cx+3*u,cy+6*u,cx,cy+9*u);x.drawPath(q,p);}
            else if("duas".equals(key)){x.drawCircle(cx-5*u,cy-5*u,2.1f*u,p);x.drawCircle(cx+5*u,cy-5*u,2.1f*u,p);q.moveTo(cx-8*u,cy+8*u);q.cubicTo(cx-9*u,cy+2*u,cx-6*u,cy,cx-3*u,cy+3*u);q.moveTo(cx+8*u,cy+8*u);q.cubicTo(cx+9*u,cy+2*u,cx+6*u,cy,cx+3*u,cy+3*u);q.moveTo(cx-3*u,cy+3*u);q.cubicTo(cx-1*u,cy+6*u,cx+1*u,cy+6*u,cx+3*u,cy+3*u);x.drawPath(q,p);}
            else if("adhan".equals(key)){q.moveTo(cx-9*u,cy+8*u);q.lineTo(cx-9*u,cy-1*u);q.quadTo(cx,cy-11*u,cx+9*u,cy-1*u);q.lineTo(cx+9*u,cy+8*u);q.moveTo(cx-12*u,cy+8*u);q.lineTo(cx+12*u,cy+8*u);q.moveTo(cx+6*u,cy-5*u);q.lineTo(cx+6*u,cy-10*u);x.drawPath(q,p);x.drawCircle(cx+6*u,cy-12*u,1.2f*u,p);}
            else if("tasbih".equals(key)){for(int i=0;i<11;i++){double a=-Math.PI*.15+i*Math.PI*1.3/10;float xx=cx+(float)Math.cos(a)*8*u,yy=cy+(float)Math.sin(a)*8*u;x.drawCircle(xx,yy,1.35f*u,p);}x.drawLine(cx+7*u,cy+5*u,cx+10*u,cy+10*u,p);}
            else if("qibla".equals(key)){x.drawCircle(cx,cy,10*u,p);q.moveTo(cx,cy-8*u);q.lineTo(cx+4*u,cy+3*u);q.lineTo(cx,cy+1*u);q.lineTo(cx-4*u,cy+3*u);q.close();x.drawPath(q,p);}
            else if("home".equals(key)){q.moveTo(cx-10*u,cy);q.lineTo(cx,cy-9*u);q.lineTo(cx+10*u,cy);q.moveTo(cx-7*u,cy-2*u);q.lineTo(cx-7*u,cy+9*u);q.lineTo(cx+7*u,cy+9*u);q.lineTo(cx+7*u,cy-2*u);x.drawPath(q,p);}
            else if("article".equals(key)){x.drawRoundRect(cx-8*u,cy-10*u,cx+8*u,cy+10*u,2*u,2*u,p);x.drawLine(cx-4*u,cy-5*u,cx+4*u,cy-5*u,p);x.drawLine(cx-4*u,cy,cx+5*u,cy,p);x.drawLine(cx-4*u,cy+5*u,cx+2*u,cy+5*u,p);}
            else if("media".equals(key)){x.drawCircle(cx,cy,10*u,p);q.moveTo(cx-3*u,cy-5*u);q.lineTo(cx+6*u,cy);q.lineTo(cx-3*u,cy+5*u);q.close();x.drawPath(q,p);}
            else if("person".equals(key)){x.drawCircle(cx,cy-6*u,4*u,p);q.moveTo(cx-9*u,cy+9*u);q.quadTo(cx,cy,cx+9*u,cy+9*u);x.drawPath(q,p);}
            else if("more".equals(key)){for(int yy=-1;yy<=1;yy++)for(int xx=-1;xx<=1;xx++)x.drawCircle(cx+xx*6*u,cy+yy*6*u,1.35f*u,p);}
            else if("search".equals(key)){x.drawCircle(cx-2*u,cy-2*u,7*u,p);x.drawLine(cx+3*u,cy+3*u,cx+10*u,cy+10*u,p);}
            else if("bell".equals(key)){q.moveTo(cx-8*u,cy+5*u);q.quadTo(cx-5*u,cy+2*u,cx-5*u,cy-3*u);q.quadTo(cx-5*u,cy-9*u,cx,cy-9*u);q.quadTo(cx+5*u,cy-9*u,cx+5*u,cy-3*u);q.quadTo(cx+5*u,cy+2*u,cx+8*u,cy+5*u);q.close();x.drawPath(q,p);x.drawCircle(cx,cy+9*u,1.2f*u,p);}
            else {x.drawCircle(cx,cy,9*u,p);}
        }@Override public void setAlpha(int a){p.setAlpha(a);}@Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}};
    }
    void applyProIcon(ImageView v,String key){String path=prefs.getString("icon."+key,"");if(path!=null&&!path.isEmpty()&&new File(path).exists()){v.setImageURI(Uri.fromFile(new File(path)));v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);}else{v.setImageDrawable(proIcon(key,green()));v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);}v.setPadding(dp(4),dp(4),dp(4),dp(4));}
'''
s=s.replace(anchor,helper+anchor,1)

# Replace the visible feature and bottom-navigation custom View icons with the clean ImageView defaults.
s=s.replace('FeatureIconView icon=new FeatureIconView(this,type);','ImageView icon=new ImageView(this);applyProIcon(icon,type);',1)
s=s.replace('BottomIconView icon=new BottomIconView(this,type,selected);','ImageView icon=new ImageView(this);applyProIcon(icon,type);if(selected){icon.setBackground(pillBg(green(),gold()));icon.setElevation(dp(4));}',1)
# Top search/bell icons use the same icon family.
s=s.replace('TopIconView bell=new TopIconView(this,"bell");','ImageView bell=new ImageView(this);applyProIcon(bell,"bell");bell.setBackground(pillBg(Color.WHITE,withAlpha(gold(),100)));',1)
s=s.replace('TopIconView search=new TopIconView(this,"search");','ImageView search=new ImageView(this);applyProIcon(search,"search");search.setBackground(pillBg(Color.WHITE,withAlpha(gold(),100)));',1)

p.write_text(s,encoding='utf-8')
assert 'applyProIcon(icon,type)' in s
print('Stage 3 professional native vector icon defaults installed')
