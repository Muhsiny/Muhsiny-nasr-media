import React,{useEffect,useMemo,useState}from'react';
import{createRoot}from'react-dom/client';
import{Search,Globe2,Radio,ShieldCheck,Languages,CheckCircle2,X,Plus,RefreshCw}from'lucide-react';
import{newsroom}from'./api';
import'./styles.css';

const fallback=[
{id:'f1',category:'افغانستان',title:'گفت‌وگوهای تازه درباره همکاری‌های منطقه‌ای آغاز شد',summary:'نمایندگان چند کشور بر توسعه همکاری‌های اقتصادی و ثبات منطقه‌ای تأکید کردند.',created_at:'اکنون'},
{id:'f2',category:'جهان',title:'نشست بین‌المللی با محوریت امنیت غذایی برگزار شد',summary:'کارشناسان درباره کاهش آسیب‌پذیری جوامع در برابر بحران‌های غذایی بحث کردند.',created_at:'۱ ساعت پیش'},
{id:'f3',category:'اقتصاد',title:'بازارهای منطقه با نوسان محدود آغاز به کار کردند',summary:'شاخص‌های اصلی در ساعات نخست معاملات تغییرات اندکی را ثبت کردند.',created_at:'۲ ساعت پیش'}
];
const localAgents=[
{name:'رصد',task:'گردآوری خبر از منابع منتخب'},
{name:'پالایش',task:'حذف تکرار و محتوای کم‌ارزش'},
{name:'میزان',task:'مقایسه منابع و امتیاز اعتبار'},
{name:'نبض',task:'استخراج نکات کلیدی'},
{name:'بصیر',task:'پیشینه، پیامدها و سناریوها'},
{name:'دبیر',task:'تیتر، لید و بازنویسی'},
{name:'لسان',task:'تولید هفت نسخه زبانی'},
{name:'نشر',task:'کنترل نهایی و ارجاع به انسان'}
];
function App(){
 const[admin,setAdmin]=useState(false),[q,setQ]=useState(''),[articles,setArticles]=useState(fallback),[agents,setAgents]=useState(localAgents),[loading,setLoading]=useState(false),[form,setForm]=useState({title:'',summary:'',body:'',category:'افغانستان',language:'fa'}),[notice,setNotice]=useState('');
 const load=async()=>{setLoading(true);try{const data=await newsroom.articles('?status=published&language=fa');if(data?.length)setArticles(data);const a=await newsroom.agents();if(a?.agents)setAgents(a.agents.map(x=>({name:x.name,task:x.task})));}catch{}finally{setLoading(false)}};
 useEffect(()=>{load()},[]);
 const filtered=useMemo(()=>articles.filter(n=>(`${n.title} ${n.summary||''} ${n.category||''}`).includes(q)),[articles,q]);
 const createArticle=async e=>{e.preventDefault();setNotice('');try{const created=await newsroom.create(form);setNotice(`پیش‌نویس ساخته شد: ${created.slug}`);setForm({title:'',summary:'',body:'',category:'افغانستان',language:'fa'});}catch{setNotice('API هنوز نشر نشده؛ پس از اتصال Worker فعال می‌شود.')}};
 const approve=async id=>{try{await newsroom.approve({articleId:id,decision:'approved'});setNotice('خبر تأیید و منتشر شد.');load()}catch{setNotice('تأیید پس از نشر Worker فعال می‌شود.')}};
 const lead=filtered[0]||fallback[0];
 return <>
 <header><div className="top"><span>رسانه مستقل، سریع و چندزبانه</span><span><Globe2 size={15}/> دری · پښتو · العربية · English · Türkçe · Français · اردو</span></div><div className="brandrow"><div className="brand"><i>N</i><div><b>NASR MEDIA</b><small>صدای دقیق رویدادها</small></div></div><label><Search size={18}/><input value={q} onChange={e=>setQ(e.target.value)} placeholder="جست‌وجوی خبر یا موضوع"/></label><button onClick={()=>setAdmin(true)}>پنل مدیریت</button></div><nav>خانه　 افغانستان　 جهان　 اقتصاد　 ورزش　 سلامت　 فناوری　 تحلیل　 مصاحبه　 آرشیف</nav></header>
 <main><div className="breaking"><b><Radio size={16}/> خبر فوری</b><span>اتاق خبر NASR MEDIA با کنترل نهایی سردبیر انسانی طراحی شده است.</span></div><section className="hero"><article className="lead"><div className="visual">NASR</div><div><em>{lead.category||'ویژه'}</em><h1>{lead.title}</h1><p>{lead.summary}</p><small>{lead.published_at||lead.created_at||'تازه'}</small></div></article><aside><h3>آخرین خبرها</h3>{filtered.slice(1,5).map((n,i)=><div key={n.id||i}><span>{n.category||'خبر'}</span><b>{n.title}</b><small>{n.published_at||n.created_at||'تازه'}</small></div>)}</aside></section><div className="sectionTitle"><h2>تازه‌ترین رویدادها</h2><button className="refresh" onClick={load}><RefreshCw size={16}/>{loading?'در حال دریافت':'به‌روزرسانی'}</button></div><section className="grid">{filtered.map((n,i)=><article className="card" key={n.id||i}><div className={'thumb t'+(i%6)}></div><div><span>{n.category||'خبر'}</span><h3>{n.title}</h3><p>{n.summary}</p><small>اعتبار: {n.confidence_score??'—'}%</small></div></article>)}</section><section className="trust"><div><ShieldCheck/><h3>اعتبار پیش از سرعت</h3><p>منابع، امتیاز اطمینان و تاریخچه تغییرات ثبت می‌شود.</p></div><div><Languages/><h3>هفت زبان، یک تحریریه</h3><p>همه زبان‌ها از یک هسته مدیریتی واحد کنترل می‌شوند.</p></div><div><CheckCircle2/><h3>تأیید انسانی</h3><p>هیچ محتوای نهایی بدون تأیید سردبیر انسانی نشر نمی‌شود.</p></div></section></main>
 <footer>© ۲۰۲۶ NASR MEDIA — دقت، استقلال، مسئولیت</footer>
 {admin&&<div className="modal"><div className="dash"><button className="close" onClick={()=>setAdmin(false)}><X/></button><h2>مرکز فرمان تحریریه</h2><div className="stats"><b>{articles.length}<small>خبر موجود</small></b><b>۸<small>عامل تحریریه</small></b><b>۷<small>زبان نشر</small></b><b>۱۰۰٪<small>تأیید انسانی</small></b></div><div className="adminGrid"><section><h3>زنجیره هشت‌عامل</h3><div className="agents">{agents.map((a,i)=><div key={a.name}><i>{i+1}</i><b>{a.name}</b><p>{a.task}</p><em>فعال</em></div>)}</div></section><section className="editor"><h3><Plus size={18}/> ایجاد خبر</h3><form onSubmit={createArticle}><input required placeholder="عنوان خبر" value={form.title} onChange={e=>setForm({...form,title:e.target.value})}/><input placeholder="خلاصه" value={form.summary} onChange={e=>setForm({...form,summary:e.target.value})}/><textarea placeholder="متن خبر" value={form.body} onChange={e=>setForm({...form,body:e.target.value})}/><div><select value={form.category} onChange={e=>setForm({...form,category:e.target.value})}><option>افغانستان</option><option>جهان</option><option>اقتصاد</option><option>سلامت</option><option>فناوری</option></select><select value={form.language} onChange={e=>setForm({...form,language:e.target.value})}><option value="fa">دری</option><option value="ps">پښتو</option><option value="ar">العربية</option><option value="en">English</option></select></div><button type="submit">ذخیره پیش‌نویس</button></form>{notice&&<p className="notice">{notice}</p>}</section></div><section className="queue"><h3>صف تأیید انسانی</h3>{articles.filter(a=>a.status!=='published').slice(0,5).map(a=><div key={a.id}><span><b>{a.title}</b><small>{a.status}</small></span><button onClick={()=>approve(a.id)}>تأیید و نشر</button></div>)}{!articles.some(a=>a.status!=='published')&&<p>در حال حاضر پرونده‌ای در صف نیست.</p>}</section></div></div>}
 </>}
createRoot(document.getElementById('root')).render(<App/>);
