import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  Search, Globe2, Menu, X, ShieldCheck, Clock3, ExternalLink, LockKeyhole,
  RefreshCw, Radio, Languages, CheckCircle2, Newspaper, ArrowLeft, WifiOff, Link2
} from 'lucide-react';
import { newsroom } from './api';
import './styles.css';

const EMPTY_NEWS = [{
  id: 'welcome',
  slug: 'nasr-media-ready',
  category: 'تحریریه',
  title: 'NASR MEDIA برای نشر خبرهای منبع‌دار و قابل‌بررسی آماده است',
  summary: 'در صورت قطع API، سایت به‌جای ساختن خبر جعلی این پیام شفاف را نشان می‌دهد. با وصل‌شدن اتاق خبر، تازه‌ترین خبرها به‌صورت خودکار جایگزین می‌شوند.',
  created_at: '—',
  confidence_score: 100,
  source_name: 'NASR MEDIA',
  source_url: '',
  status: 'published'
}];

const categories = ['همه', 'افغانستان', 'جهان', 'اقتصاد', 'سیاست', 'فرهنگ', 'سلامت', 'فناوری', 'ورزش', 'تحلیل'];
const localAgents = [
  { name: 'رصد', task: 'گردآوری خبر از منابع منتخب' },
  { name: 'پالایش', task: 'حذف تکرار و محتوای کم‌ارزش' },
  { name: 'میزان', task: 'بررسی منبع و امتیاز اعتبار' },
  { name: 'نبض', task: 'استخراج نکات و داده‌های کلیدی' },
  { name: 'بصیر', task: 'زمینه، پیامد و تفکیک تحلیل از خبر' },
  { name: 'دبیر', task: 'تیتر، لید و ویرایش تحریریه' },
  { name: 'لسان', task: 'نسخه‌های چندزبانه' },
  { name: 'نشر', task: 'کنترل نهایی و ثبت منبع' }
];

const fmtDate = (value) => {
  if (!value || value === '—') return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('fa-AF', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
};

function NewsImage({ article, hero = false }) {
  const [failed, setFailed] = useState(false);
  if (!article?.image_url || failed) {
    return <div className={`news-fallback ${hero ? 'hero-image' : ''}`} aria-label="تصویر خبر در دسترس نیست"><span>NASR</span></div>;
  }
  return <img className={hero ? 'hero-image' : ''} src={article.image_url} alt={article.image_alt || article.title} loading={hero ? 'eager' : 'lazy'} onError={() => setFailed(true)} />;
}

function App() {
  const [articles, setArticles] = useState(EMPTY_NEWS);
  const [agents, setAgents] = useState(localAgents);
  const [q, setQ] = useState('');
  const [category, setCategory] = useState('همه');
  const [loading, setLoading] = useState(false);
  const [apiOnline, setApiOnline] = useState(true);
  const [menuOpen, setMenuOpen] = useState(false);
  const [reader, setReader] = useState(null);
  const [readerLoading, setReaderLoading] = useState(false);
  const [adminOpen, setAdminOpen] = useState(false);
  const [adminToken, setAdminToken] = useState(() => sessionStorage.getItem('nasr_admin_token') || '');
  const [adminUnlocked, setAdminUnlocked] = useState(false);
  const [queue, setQueue] = useState([]);
  const [adminNotice, setAdminNotice] = useState('');

  const load = async () => {
    setLoading(true);
    try {
      const [health, data, agentData] = await Promise.all([
        newsroom.health().catch(() => null),
        newsroom.articles('?status=published&language=fa'),
        newsroom.agents().catch(() => null)
      ]);
      setApiOnline(Boolean(health?.ok));
      setArticles(Array.isArray(data) && data.length ? data : EMPTY_NEWS);
      if (agentData?.agents?.length) setAgents(agentData.agents.map((a) => ({ name: a.name, task: a.task })));
    } catch {
      setApiOnline(false);
      setArticles(EMPTY_NEWS);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const filtered = useMemo(() => {
    const needle = q.trim().toLocaleLowerCase('fa-AF');
    return articles.filter((item) => {
      const categoryMatch = category === 'همه' || item.category === category;
      const text = `${item.title || ''} ${item.summary || ''} ${item.category || ''}`.toLocaleLowerCase('fa-AF');
      return categoryMatch && (!needle || text.includes(needle));
    });
  }, [articles, q, category]);

  const lead = filtered[0] || articles[0] || EMPTY_NEWS[0];

  const openArticle = async (article) => {
    setReader(article);
    if (!article?.id || article.id === 'welcome') return;
    setReaderLoading(true);
    try {
      const full = await newsroom.article(article.id);
      if (full) setReader({ ...article, ...full });
    } catch {
      // Card data remains readable if detail endpoint is temporarily unavailable.
    } finally {
      setReaderLoading(false);
    }
  };

  const unlockAdmin = async (event) => {
    event.preventDefault();
    setAdminNotice('');
    sessionStorage.setItem('nasr_admin_token', adminToken);
    try {
      const items = await newsroom.reviewQueue(adminToken);
      setQueue(Array.isArray(items) ? items : []);
      setAdminUnlocked(true);
    } catch {
      setAdminUnlocked(false);
      setAdminNotice('کلید مدیریت پذیرفته نشد یا API مدیریت در دسترس نیست.');
    }
  };

  const approve = async (id) => {
    setAdminNotice('');
    try {
      await newsroom.approve({ articleId: id, decision: 'approved' }, adminToken);
      setQueue((items) => items.filter((item) => item.id !== id));
      setAdminNotice('خبر تأیید و نشر شد.');
      load();
    } catch {
      setAdminNotice('نشر انجام نشد. وضعیت API یا کلید مدیریت را بررسی کنید.');
    }
  };

  const latest = filtered.slice(1, 6);
  const grid = filtered.slice(1);

  return <>
    <header className="site-header">
      <div className="utility">
        <span>رسانه مستقل، منبع‌دار و چندزبانه</span>
        <span className={`api-state ${apiOnline ? 'online' : 'offline'}`}>
          {apiOnline ? <CheckCircle2 size={14} /> : <WifiOff size={14} />}
          {apiOnline ? 'اتاق خبر آنلاین' : 'حالت امن آفلاین'}
        </span>
      </div>

      <div className="masthead">
        <button className="menu-button" onClick={() => setMenuOpen(true)} aria-label="بازکردن منو"><Menu /></button>
        <a className="brand" href="#" aria-label="NASR MEDIA">
          <span className="brand-mark">N</span>
          <span><strong>NASR MEDIA</strong><small>نصر مدیا | روایت دقیق رویدادها</small></span>
        </a>
        <label className="search-box">
          <Search size={18} />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="جست‌وجوی خبر، موضوع یا مکان" />
        </label>
        <button className="admin-button" onClick={() => setAdminOpen(true)}><LockKeyhole size={17} /> تحریریه</button>
      </div>

      <nav className="desktop-nav" aria-label="دسته‌بندی خبر">
        {categories.slice(1).map((item) => <button key={item} className={category === item ? 'active' : ''} onClick={() => setCategory(item)}>{item}</button>)}
      </nav>
    </header>

    {menuOpen && <div className="sheet-backdrop" onClick={() => setMenuOpen(false)}>
      <aside className="mobile-sheet" onClick={(e) => e.stopPropagation()}>
        <button className="icon-button" onClick={() => setMenuOpen(false)}><X /></button>
        <div className="sheet-brand">NASR MEDIA</div>
        {categories.map((item) => <button key={item} onClick={() => { setCategory(item); setMenuOpen(false); }}>{item}</button>)}
      </aside>
    </div>}

    <main>
      <section className="breaking">
        <strong><Radio size={16} /> تازه‌ترین</strong>
        <span>{lead?.title || 'در حال دریافت تازه‌ترین خبرها'}</span>
        <button onClick={load} disabled={loading}><RefreshCw size={15} className={loading ? 'spin' : ''} /> {loading ? 'در حال دریافت' : 'تازه‌سازی'}</button>
      </section>

      <section className="hero-grid">
        <article className="lead-card" onClick={() => openArticle(lead)}>
          <NewsImage article={lead} hero />
          <div className="lead-overlay" />
          <div className="lead-copy">
            <div className="meta-row"><span>{lead.category || 'خبر'}</span><time>{fmtDate(lead.published_at || lead.created_at)}</time></div>
            <h1>{lead.title}</h1>
            <p>{lead.summary}</p>
            <div className="source-line"><Link2 size={14} /> {lead.source_name || 'NASR MEDIA'} <span>•</span> اعتبار {lead.confidence_score ?? '—'}٪</div>
          </div>
        </article>

        <aside className="latest-panel">
          <div className="panel-title"><div><Newspaper size={18} /><h2>آخرین خبرها</h2></div><span>{filtered.length} خبر</span></div>
          {latest.length ? latest.map((item) => <button key={item.id || item.slug} className="latest-item" onClick={() => openArticle(item)}>
            <span className="latest-category">{item.category || 'خبر'}</span>
            <strong>{item.title}</strong>
            <small>{fmtDate(item.published_at || item.created_at)}</small>
          </button>) : <p className="empty-note">خبر دیگری در این دسته موجود نیست.</p>}
        </aside>
      </section>

      <section className="category-strip" aria-label="فیلتر خبر">
        {categories.map((item) => <button key={item} className={category === item ? 'active' : ''} onClick={() => setCategory(item)}>{item}</button>)}
      </section>

      <div className="section-heading">
        <div><span className="eyebrow">NEWSROOM</span><h2>{category === 'همه' ? 'تازه‌ترین رویدادها' : category}</h2></div>
        <p>هر خبر با منبع اصلی و زمان نشر نگهداری می‌شود.</p>
      </div>

      <section className="news-grid">
        {grid.length ? grid.map((item) => <article className="news-card" key={item.id || item.slug} onClick={() => openArticle(item)}>
          <div className="card-media"><NewsImage article={item} /><span>{item.category || 'خبر'}</span></div>
          <div className="card-body">
            <div className="card-time"><Clock3 size={13} /> {fmtDate(item.published_at || item.created_at)}</div>
            <h3>{item.title}</h3>
            <p>{item.summary}</p>
            <div className="card-footer"><span>{item.source_name || 'NASR MEDIA'}</span><button>بخوانید <ArrowLeft size={15} /></button></div>
          </div>
        </article>) : <div className="no-results">برای این جست‌وجو خبری پیدا نشد.</div>}
      </section>

      <section className="principles" id="principles">
        <div><ShieldCheck /><h3>منبع پنهان نمی‌شود</h3><p>منبع اصلی هر خبر در صفحه خبر ثبت می‌شود تا مخاطب بتواند گزارش مبنا را مستقیم بررسی کند.</p></div>
        <div><Languages /><h3>خبر از تحلیل جداست</h3><p>زمینه و تحلیل با برچسب روشن ارائه می‌شود و ادعاهای تازه بدون منبع وارد متن خبر نمی‌شود.</p></div>
        <div><CheckCircle2 /><h3>قانون نصر</h3><p>نشر خودکار فقط برای محتوای واجد معیار منبع، شفافیت و حداقل امتیاز اعتماد مجاز است؛ موارد حساس به صف بازبینی می‌روند.</p></div>
      </section>
    </main>

    <footer>
      <div className="footer-brand"><span className="brand-mark">N</span><div><strong>NASR MEDIA</strong><small>دقت، استقلال، مسئولیت</small></div></div>
      <div className="footer-links"><a href="#principles">اصول تحریریه</a><a href="#sources">منابع</a><a href="#contact">تماس</a></div>
      <small>© ۲۰۲۶ NASR MEDIA — مالکیت داده و تحریریه نزد نصر مدیا.</small>
    </footer>

    {reader && <div className="dialog-backdrop" onClick={() => setReader(null)}>
      <article className="reader" onClick={(e) => e.stopPropagation()}>
        <button className="reader-close" onClick={() => setReader(null)}><X /></button>
        <div className="reader-media"><NewsImage article={reader} hero /></div>
        <div className="reader-content">
          <div className="meta-row"><span>{reader.category || 'خبر'}</span><time>{fmtDate(reader.published_at || reader.created_at)}</time></div>
          <h1>{reader.title}</h1>
          <p className="reader-summary">{reader.summary}</p>
          {readerLoading ? <p className="loading-line">در حال دریافت متن کامل…</p> : <div className="reader-body">{(reader.body || '').split('\n').map((line, i) => line ? <p key={i}>{line}</p> : <br key={i} />)}</div>}
          <div className="source-box">
            <div><ShieldCheck size={20} /><span><strong>منبع ثبت‌شده</strong><small>{reader.source_name || 'NASR MEDIA'}</small></span></div>
            {reader.source_url && <a href={reader.source_url} target="_blank" rel="noreferrer">مشاهده منبع اصلی <ExternalLink size={15} /></a>}
          </div>
        </div>
      </article>
    </div>}

    {adminOpen && <div className="dialog-backdrop" onClick={() => setAdminOpen(false)}>
      <section className="admin-dialog" onClick={(e) => e.stopPropagation()}>
        <button className="reader-close" onClick={() => setAdminOpen(false)}><X /></button>
        <div className="admin-head"><LockKeyhole /><div><h2>مرکز فرمان تحریریه</h2><p>این بخش عمومی نیست و فقط با کلید مدیریت Worker باز می‌شود.</p></div></div>
        {!adminUnlocked ? <form className="unlock-form" onSubmit={unlockAdmin}>
          <label>کلید مدیریت<input type="password" value={adminToken} onChange={(e) => setAdminToken(e.target.value)} autoComplete="current-password" required /></label>
          <button type="submit">ورود امن</button>
          {adminNotice && <p className="admin-notice error">{adminNotice}</p>}
        </form> : <>
          <div className="admin-stats"><div><strong>{queue.length}</strong><span>در صف بازبینی</span></div><div><strong>{agents.length}</strong><span>مرحله تحریریه</span></div><div><strong>۷</strong><span>زبان هدف</span></div></div>
          <div className="agent-grid">{agents.map((agent, index) => <div key={agent.name}><span>{index + 1}</span><strong>{agent.name}</strong><p>{agent.task}</p></div>)}</div>
          <div className="review-queue"><h3>صف بازبینی</h3>{queue.length ? queue.map((item) => <div className="queue-item" key={item.id}><span><strong>{item.title}</strong><small>{item.source_name || item.status}</small></span><button onClick={() => approve(item.id)}>تأیید و نشر</button></div>) : <p className="empty-note">صف بازبینی خالی است.</p>}</div>
          {adminNotice && <p className="admin-notice">{adminNotice}</p>}
        </>}
      </section>
    </div>}
  </>;
}

createRoot(document.getElementById('root')).render(<App />);
