import React,{useState}from'react';
import{createRoot}from'react-dom/client';
import{Search,Globe2,Radio,ShieldCheck,Languages,CheckCircle2,X}from'lucide-react';
import'./styles.css';

const news=[
['افغانستان','گفت‌وگوهای تازه درباره همکاری‌های منطقه‌ای آغاز شد','نمایندگان چند کشور بر توسعه همکاری‌های اقتصادی و ثبات منطقه‌ای تأکید کردند.'],
['جهان','نشست بین‌المللی با محوریت امنیت غذایی برگزار شد','کارشناسان درباره کاهش آسیب‌پذیری جوامع در برابر بحران‌های غذایی بحث کردند.'],
['اقتصاد','بازارهای منطقه با نوسان محدود آغاز به کار کردند','شاخص‌های اصلی در ساعات نخست معاملات تغییرات اندکی را ثبت کردند.'],
['علم و فناوری','پژوهشگران از پیشرفت تازه در فناوری انرژی خبر دادند','این پژوهش می‌تواند هزینه ذخیره‌سازی انرژی را در بلندمدت کاهش دهد.'],
['سلامت','تأکید متخصصان بر گسترش خدمات سلامت روان جامعه‌محور','دسترسی زودهنگام به خدمات حمایتی می‌تواند از شدت مشکلات روانی بکاهد.'],
['ورزش','رقابت‌های منطقه‌ای با حضور تیم‌های جوانان آغاز شد','برگزارکنندگان هدف اصلی مسابقات را رشد استعدادهای جوان اعلام کردند.']
];
const agents=[
['رصد','دیده‌بان منابع','گردآوری خبر از منابع منتخب'],['پالایش','پاک‌سازی','حذف تکرار و محتوای کم‌ارزش'],['میزان','راستی‌آزما','مقایسه منابع و امتیاز اعتبار'],['نبض','خلاصه‌ساز','استخراج نکات کلیدی'],['بصیر','تحلیلگر','پیشینه، پیامدها و سناریوها'],['دبیر','ویراستار خبر','تیتر، لید و بازنویسی'],['لسان','مترجم','تولید هفت نسخه زبانی'],['نشر','سردبیر هوشمند','کنترل نهایی و ارجاع به انسان']
];
function App(){const[admin,setAdmin]=useState(false);const[q,setQ]=useState('');const filtered=news.filter(n=>n.join(' ').includes(q));return <>
<header><div className="top"><span>رسانه مستقل، سریع و چندزبانه</span><span><Globe2 size={15}/> دری · پښتو · العربية · English · Türkçe · Français · اردو</span></div><div className="brandrow"><div className="brand"><i>N</i><div><b>NASR MEDIA</b><small>صدای دقیق رویدادها</small></div></div><label><Search size={18}/><input value={q} onChange={e=>setQ(e.target.value)} placeholder="جست‌وجوی خبر یا موضوع"/></label><button onClick={()=>setAdmin(true)}>پنل مدیریت</button></div><nav>خانه　 افغانستان　 جهان　 اقتصاد　 ورزش　 سلامت　 فناوری　 تحلیل　 مصاحبه　 آرشیف</nav></header>
<main><div className="breaking"><b><Radio size={16}/> خبر فوری</b><span>نسخه نخست NASR MEDIA برای آزمایش تحریریه و تجربه کاربری فعال شد.</span></div><section className="hero"><article className="lead"><div className="visual">NASR</div><div><em>ویژه</em><h1>{news[0][1]}</h1><p>{news[0][2]}</p><small>۱۲ دقیقه پیش</small></div></article><aside><h3>آخرین خبرها</h3>{news.slice(1,5).map((n,i)=><div key={i}><span>{n[0]}</span><b>{n[1]}</b><small>{i+1} ساعت پیش</small></div>)}</aside></section><h2>تازه‌ترین رویدادها</h2><section className="grid">{filtered.map((n,i)=><article className="card" key={i}><div className={'thumb t'+i}></div><div><span>{n[0]}</span><h3>{n[1]}</h3><p>{n[2]}</p><small>{i+1} ساعت پیش</small></div></article>)}</section><section className="trust"><div><ShieldCheck/><h3>اعتبار پیش از سرعت</h3><p>منابع، امتیاز اطمینان و تاریخچه تغییرات ثبت می‌شود.</p></div><div><Languages/><h3>هفت زبان، یک تحریریه</h3><p>همه زبان‌ها از یک هسته مدیریتی واحد کنترل می‌شوند.</p></div><div><CheckCircle2/><h3>تأیید انسانی</h3><p>هیچ محتوای نهایی بدون تأیید سردبیر انسانی نشر نمی‌شود.</p></div></section></main>
<footer>© ۲۰۲۶ NASR MEDIA — دقت، استقلال، مسئولیت</footer>
{admin&&<div className="modal"><div className="dash"><button className="close" onClick={()=>setAdmin(false)}><X/></button><h2>مرکز فرمان تحریریه</h2><div className="stats"><b>۲۴<small>در صف بررسی</small></b><b>۷<small>نیازمند راستی‌آزمایی</small></b><b>۱۲<small>آماده تأیید</small></b><b>۳۸<small>نشرشده امروز</small></b></div><h3>زنجیره هشت‌عامل</h3><div className="agents">{agents.map((a,i)=><div key={a[0]}><i>{i+1}</i><b>{a[0]}</b><small>{a[1]}</small><p>{a[2]}</p><em>فعال</em></div>)}</div></div></div>}
</>}
createRoot(document.getElementById('root')).render(<App/>);
