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

const slugify = (value) => value
  .toLowerCase()
  .normalize('NFKD')
  .replace(/[^\p{L}\p{N}]+/gu, '-')
  .replace(/(^-|-$)/g, '')
  .slice(0, 120);

function parseFeed(xml) {
  const rssItems = [...xml.matchAll(/<item(?:\s[^>]*)?>([\s\S]*?)<\/item>/gi)].map((m) => m[1]);
  const atomItems = [...xml.matchAll(/<entry(?:\s[^>]*)?>([\s\S]*?)<\/entry>/gi)].map((m) => m[1]);
  return [...rssItems, ...atomItems].slice(0, 25).map((item) => {
    const atomLink = item.match(/<link[^>]+href=["']([^"']+)["'][^>]*>/i)?.[1] || '';
    const title = stripHtml(field(item, 'title'));
    const link = decodeXml(field(item, 'link') || atomLink || field(item, 'guid'));
    const description = stripHtml(field(item, 'description') || field(item, 'summary') || field(item, 'content'));
    const published = stripHtml(field(item, 'pubDate') || field(item, 'published') || field(item, 'updated'));
    return { title, link, description, published };
  }).filter((item) => item.title && item.link);
}

async function insertArticle(env, source, item) {
  const exists = await env.DB.prepare('SELECT id FROM article_sources WHERE source_url=? LIMIT 1').bind(item.link).first();
  if (exists) return { inserted: false, reason: 'duplicate' };

  const id = crypto.randomUUID();
  const baseSlug = slugify(item.title) || 'news';
  const slug = `${baseSlug}-${id.slice(0, 8)}`;
  const shouldPublish = String(env.AUTO_PUBLISH || 'true').toLowerCase() === 'true' && Number(source.trust_score || 0) >= Number(env.AUTO_PUBLISH_MIN_TRUST || 70);
  const status = shouldPublish ? 'published' : 'review';
  const summary = item.description ? item.description.slice(0, 500) : item.title;
  const body = `${summary}\n\nمنبع اصلی: ${source.name}`;

  await env.DB.prepare("INSERT INTO articles(id,slug,status,language,title,summary,body,category,confidence_score,published_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,CASE WHEN ?='published' THEN datetime('now') ELSE NULL END,datetime('now'),datetime('now'))")
    .bind(id, slug, status, source.language || 'fa', item.title, summary, body, source.category || 'general', Number(source.trust_score || 70), status)
    .run();

  await env.DB.prepare("INSERT INTO article_sources(id,article_id,source_id,source_url,claim,verification_status,created_at) VALUES(?,?,?,?,?,?,datetime('now'))")
    .bind(crypto.randomUUID(), id, source.id, item.link, item.title, 'source_confirmed')
    .run();

  return { inserted: true, id, status };
}

export async function ingestSources(env) {
  const rows = await env.DB.prepare("SELECT id,name,url,source_type,trust_score,language FROM sources WHERE active=1 AND source_type IN ('rss','atom') AND url IS NOT NULL ORDER BY trust_score DESC LIMIT 100").all();
  const report = { checked: 0, inserted: 0, duplicates: 0, failed: 0, sources: [] };

  for (const source of rows.results || []) {
    report.checked += 1;
    try {
      const response = await fetch(source.url, { headers: { 'user-agent': 'NASR-MEDIA-Newsroom/1.0', accept: 'application/rss+xml, application/atom+xml, text/xml, */*' } });
      if (!response.ok) throw new Error(`feed_${response.status}`);
      const items = parseFeed(await response.text());
      let inserted = 0;
      for (const item of items) {
        const result = await insertArticle(env, source, item);
        if (result.inserted) { inserted += 1; report.inserted += 1; }
        else report.duplicates += 1;
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
