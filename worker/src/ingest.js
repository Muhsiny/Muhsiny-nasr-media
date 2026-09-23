const decodeXml = (value = '') => value
  .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
  .replace(/&amp;/g, '&')
  .replace(/&lt;/g, '<')
  .replace(/&gt;/g, '>')
  .replace(/&quot;/g, '"')
  .replace(/&#39;/g, "'")
  .trim();

const stripHtml = (value = '') => decodeXml(value.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' '));

const field = (item, tag) => {
  const match = item.match(new RegExp(`<${tag}(?:\\s[^>]*)?>([\\s\\S]*?)<\\/${tag}>`, 'i'));
  return match ? decodeXml(match[1]) : '';
};

const slugify = (value = '') => value.toLowerCase().normalize('NFKD').replace(/[^\p{L}\p{N}]+/gu, '-').replace(/(^-|-$)/g, '').slice(0, 120);

function mediaUrl(item) {
  return item.match(/<media:(?:content|thumbnail)[^>]+url=["']([^"']+)["']/i)?.[1]
    || item.match(/<enclosure[^>]+url=["']([^"']+)["'][^>]+type=["']image\//i)?.[1]
    || '';
}

function parseFeed(xml) {
  const rssItems = [...xml.matchAll(/<item(?:\s[^>]*)?>([\s\S]*?)<\/item>/gi)].map((m) => m[1]);
  const atomItems = [...xml.matchAll(/<entry(?:\s[^>]*)?>([\s\S]*?)<\/entry>/gi)].map((m) => m[1]);

  return [...rssItems, ...atomItems].slice(0, 30).map((item) => {
    const atomLink = item.match(/<link[^>]+href=["']([^"']+)["'][^>]*>/i)?.[1] || '';
    return {
      title: stripHtml(field(item, 'title')),
      link: decodeXml(field(item, 'link') || atomLink || field(item, 'guid')),
      description: stripHtml(field(item, 'description') || field(item, 'summary') || field(item, 'content')),
      published: stripHtml(field(item, 'pubDate') || field(item, 'published') || field(item, 'updated')),
      image: decodeXml(mediaUrl(item))
    };
  }).filter((item) => item.title && item.link);
}

function inferCategory(title = '') {
  const t = title.toLowerCase();
  const rules = [
    ['اقتصاد', ['econom', 'market', 'bank', 'trade', 'اقتصاد', 'بازار', 'بانک', 'تجارت']],
    ['فناوری', ['tech', 'ai ', 'digital', 'cyber', 'فناوری', 'هوش مصنوعی', 'دیجیتال']],
    ['سلامت', ['health', 'medical', 'disease', 'سلامت', 'صحی', 'بیمار']],
    ['ورزش', ['sport', 'football', 'cricket', 'ورزش', 'فوتبال', 'کریکت']],
    ['افغانستان', ['afghanistan', 'kabul', 'herat', 'balkh', 'افغانستان', 'کابل', 'هرات', 'بلخ']]
  ];
  return rules.find(([, words]) => words.some((word) => t.includes(word)))?.[0] || 'جهان';
}

function isSafeHttpUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' || url.protocol === 'http:';
  } catch {
    return false;
  }
}

function lawNasrEligible(source, item, env) {
  const trust = Number(source.trust_score || 0);
  const minimum = Number(env.AUTO_PUBLISH_MIN_TRUST || 85);
  const blockedHost = /(^|\.)news\.google\.com$/i;
  let host = '';
  try { host = new URL(item.link).hostname; } catch {}

  if (trust < minimum || !isSafeHttpUrl(item.link) || blockedHost.test(host)) return false;
  if (item.title.length < 16 || item.title.length > 220) return false;
  if (!item.description || item.description.length < 40) return false;
  if (/(casino|betting|bonus|slot|قمار|شرط‌بندی)/i.test(`${item.title} ${item.description}`)) return false;
  return String(env.AUTO_PUBLISH || 'true').toLowerCase() === 'true';
}

function nasrCopy(source, item) {
  const published = item.published ? ` زمان گزارش منبع: ${item.published}.` : '';
  return {
    summary: `براساس گزارش ${source.name}، موضوع این خبر «${item.title}» است. نصر مدیا منبع اصلی را برای بررسی مستقیم مخاطب حفظ کرده است.`,
    body: [
      'آنچه ثبت شده است',
      `موضوع: ${item.title}`,
      `منبع اولیه: ${source.name}.${published}`,
      '',
      'یادداشت تحریریه',
      'این ورودی به‌صورت خودکار از یک منبع ثبت شده است. متن منبع عیناً بازنشر نشده و پیوند گزارش اصلی برای بررسی مستقیم حفظ می‌شود. در موضوعات حساس یا منابع با امتیاز پایین‌تر، خبر پیش از نشر عمومی وارد صف بازبینی می‌شود.'
    ].join('\n')
  };
}

async function insertArticle(env, source, item) {
  const exists = await env.DB.prepare('SELECT id FROM article_sources WHERE source_url=? LIMIT 1').bind(item.link).first();
  if (exists) return { inserted: false, reason: 'duplicate' };

  const id = crypto.randomUUID();
  const slug = `${slugify(item.title) || 'news'}-${id.slice(0, 8)}`;
  const publish = lawNasrEligible(source, item, env);
  const status = publish ? 'published' : 'review';
  const copy = nasrCopy(source, item);
  const category = inferCategory(item.title);
  const score = Math.min(100, Math.max(0, Number(source.trust_score || 70)));

  await env.DB.prepare("INSERT INTO articles(id,slug,status,language,title,summary,body,category,confidence_score,published_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,CASE WHEN ?='published' THEN datetime('now') ELSE NULL END,datetime('now'),datetime('now'))")
    .bind(id, slug, status, source.language || 'fa', item.title, copy.summary, copy.body, category, score, status)
    .run();

  await env.DB.prepare("INSERT INTO article_sources(id,article_id,source_id,source_url,claim,verification_status,created_at) VALUES(?,?,?,?,?,?,datetime('now'))")
    .bind(crypto.randomUUID(), id, source.id, item.link, item.title, publish ? 'law_nasr_pass' : 'review_required')
    .run();

  if (item.image && isSafeHttpUrl(item.image)) {
    await env.DB.prepare("INSERT INTO media_assets(id,article_id,file_key,mime_type,caption,alt_text,created_at) VALUES(?,?,?,?,?,?,datetime('now'))")
      .bind(crypto.randomUUID(), id, item.image, 'image/remote', `تصویر همراه گزارش ${source.name}`, item.title)
      .run();
  }

  return { inserted: true, id, status };
}

export async function ingestSources(env) {
  const rows = await env.DB.prepare("SELECT id,name,url,source_type,trust_score,language FROM sources WHERE active=1 AND source_type IN ('rss','atom') AND url IS NOT NULL ORDER BY trust_score DESC LIMIT 100").all();
  const report = { checked: 0, inserted: 0, published: 0, review: 0, duplicates: 0, failed: 0, sources: [] };

  for (const source of rows.results || []) {
    report.checked += 1;
    try {
      const response = await fetch(source.url, {
        headers: { 'user-agent': 'NASR-MEDIA-Newsroom/2.0', accept: 'application/rss+xml, application/atom+xml, text/xml, */*' },
        redirect: 'follow'
      });
      if (!response.ok) throw new Error(`feed_${response.status}`);

      const items = parseFeed(await response.text());
      let inserted = 0;
      for (const item of items) {
        const result = await insertArticle(env, source, item);
        if (result.inserted) {
          inserted += 1;
          report.inserted += 1;
          report[result.status] += 1;
        } else {
          report.duplicates += 1;
        }
      }
      report.sources.push({ id: source.id, name: source.name, ok: true, items: items.length, inserted });
    } catch (error) {
      report.failed += 1;
      report.sources.push({ id: source.id, name: source.name, ok: false, error: String(error.message || error) });
    }
  }

  await env.DB.prepare("INSERT INTO audit_logs(id,action,entity_type,details_json,created_at) VALUES(?,?,?,?,datetime('now'))")
    .bind(crypto.randomUUID(), 'scheduled_ingestion', 'newsroom', JSON.stringify(report))
    .run();

  return report;
}
