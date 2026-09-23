import { runPipeline } from './agents.js';
import { ingestSources } from './ingest.js';

const agents = [
  { key: 'rasad', name: 'رصد', stage: 1, task: 'جمع‌آوری منابع' },
  { key: 'palayesh', name: 'پالایش', stage: 2, task: 'حذف تکرار و محتوای ضعیف' },
  { key: 'mizan', name: 'میزان', stage: 3, task: 'راستی‌آزمایی و امتیاز اعتبار' },
  { key: 'nabz', name: 'نبض', stage: 4, task: 'خلاصه‌سازی و استخراج نکات' },
  { key: 'basir', name: 'بصیر', stage: 5, task: 'تحلیل زمینه و پیامدها' },
  { key: 'dabir', name: 'دبیر', stage: 6, task: 'تیتر، لید و بازنویسی' },
  { key: 'lesan', name: 'لسان', stage: 7, task: 'ترجمه هفت‌زبانه' },
  { key: 'nashr', name: 'نشر', stage: 8, task: 'کنترل نهایی و ارجاع انسانی' }
];

const security = {
  'content-type': 'application/json;charset=UTF-8',
  'x-content-type-options': 'nosniff',
  'referrer-policy': 'no-referrer',
  'cache-control': 'no-store'
};

function allowedOrigin(request, env) {
  const origin = request.headers.get('origin');
  const configured = String(env.ALLOWED_ORIGINS || '').split(',').map((v) => v.trim()).filter(Boolean);
  if (!origin) return '*';
  if (!configured.length || configured.includes('*') || configured.includes(origin)) return origin;
  return 'null';
}

function headers(request, env) {
  return {
    ...security,
    'Access-Control-Allow-Origin': allowedOrigin(request, env),
    'Access-Control-Allow-Methods': 'GET,POST,PATCH,OPTIONS',
    'Access-Control-Allow-Headers': 'content-type,authorization,x-ingest-key',
    'Access-Control-Max-Age': '86400',
    'Vary': 'Origin'
  };
}

const json = (request, env, data, status = 200) => new Response(JSON.stringify(data), { status, headers: headers(request, env) });

const slugify = (value = '') => value.toLowerCase().normalize('NFKD').replace(/[^\p{L}\p{N}]+/gu, '-').replace(/(^-|-$)/g, '').slice(0, 120);
async function readBody(request) { try { return await request.json(); } catch { return {}; } }

function secureEqual(a = '', b = '') {
  const x = new TextEncoder().encode(String(a));
  const y = new TextEncoder().encode(String(b));
  if (!x.length || x.length !== y.length) return false;
  let diff = 0;
  for (let i = 0; i < x.length; i += 1) diff |= x[i] ^ y[i];
  return diff === 0;
}

function adminAuthorized(request, env) {
  const expected = String(env.ADMIN_KEY || '');
  if (!expected) return false;
  const bearer = (request.headers.get('authorization') || '').replace(/^Bearer\s+/i, '');
  return secureEqual(bearer, expected);
}

function ingestAuthorized(request, env) {
  const expected = String(env.INGEST_KEY || '');
  if (!expected) return false;
  return secureEqual(request.headers.get('x-ingest-key') || '', expected);
}

const articleSelect = `
SELECT a.id,a.slug,a.status,a.language,a.title,a.summary,a.body,a.category,
       a.confidence_score,a.featured,a.published_at,a.created_at,a.updated_at,
       (SELECT s.name FROM article_sources ars LEFT JOIN sources s ON s.id=ars.source_id WHERE ars.article_id=a.id ORDER BY ars.created_at LIMIT 1) AS source_name,
       (SELECT ars.source_url FROM article_sources ars WHERE ars.article_id=a.id ORDER BY ars.created_at LIMIT 1) AS source_url,
       (SELECT m.file_key FROM media_assets m WHERE m.article_id=a.id ORDER BY m.created_at LIMIT 1) AS image_url,
       (SELECT m.alt_text FROM media_assets m WHERE m.article_id=a.id ORDER BY m.created_at LIMIT 1) AS image_alt
FROM articles a`;

export default {
  async scheduled(_event, env, ctx) {
    ctx.waitUntil(ingestSources(env));
  },

  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: headers(request, env) });

    if (url.pathname === '/api/health') {
      let db = false;
      let lastIngestion = null;
      try {
        await env.DB.prepare('SELECT 1').first();
        db = true;
        lastIngestion = await env.DB.prepare("SELECT created_at,details_json FROM audit_logs WHERE action='scheduled_ingestion' ORDER BY created_at DESC LIMIT 1").first();
      } catch {}
      return json(request, env, { ok: db, service: 'NASR MEDIA API', database: db ? 'connected' : 'unavailable', lastIngestion, time: new Date().toISOString() }, db ? 200 : 503);
    }

    if (url.pathname === '/api/agents' && request.method === 'GET') return json(request, env, { agents });

    if (url.pathname === '/api/ingest' && request.method === 'POST') {
      if (!ingestAuthorized(request, env)) return json(request, env, { error: 'unauthorized' }, 401);
      try {
        return json(request, env, { ok: true, report: await ingestSources(env) });
      } catch (error) {
        return json(request, env, { error: 'ingestion_failed', detail: String(error.message || error) }, 500);
      }
    }

    if (url.pathname === '/api/articles' && request.method === 'GET') {
      const requestedStatus = url.searchParams.get('status') || 'published';
      const language = url.searchParams.get('language');
      if (requestedStatus !== 'published' && !adminAuthorized(request, env)) return json(request, env, { error: 'unauthorized' }, 401);

      let sql = `${articleSelect} WHERE 1=1`;
      const args = [];
      if (requestedStatus) { sql += ' AND a.status=?'; args.push(requestedStatus); }
      if (language) { sql += ' AND a.language=?'; args.push(language); }
      sql += ' ORDER BY a.featured DESC, COALESCE(a.published_at,a.created_at) DESC LIMIT 100';
      const rows = await env.DB.prepare(sql).bind(...args).all();
      return json(request, env, rows.results || []);
    }

    if (url.pathname === '/api/articles' && request.method === 'POST') {
      if (!adminAuthorized(request, env)) return json(request, env, { error: 'unauthorized' }, 401);
      const payload = await readBody(request);
      if (!payload.title) return json(request, env, { error: 'title_required' }, 400);

      const id = crypto.randomUUID();
      const slug = `${slugify(payload.slug || payload.title) || 'news'}-${id.slice(0, 8)}`;
      await env.DB.prepare("INSERT INTO articles(id,slug,status,language,title,summary,body,category,confidence_score,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,datetime('now'),datetime('now'))")
        .bind(id, slug, 'draft', payload.language || 'fa', payload.title, payload.summary || '', payload.body || '', payload.category || 'خبر', 0).run();

      for (const agent of agents) {
        await env.DB.prepare("INSERT INTO pipeline_runs(id,article_id,stage,agent_key,status,created_at) VALUES(?,?,?,?,?,datetime('now'))")
          .bind(crypto.randomUUID(), id, agent.stage, agent.key, 'queued').run();
      }
      return json(request, env, { id, slug, status: 'draft' }, 201);
    }

    const processMatch = url.pathname.match(/^\/api\/articles\/([^/]+)\/process$/);
    if (processMatch && request.method === 'POST') {
      if (!adminAuthorized(request, env)) return json(request, env, { error: 'unauthorized' }, 401);
      const article = await env.DB.prepare('SELECT * FROM articles WHERE id=?').bind(processMatch[1]).first();
      if (!article) return json(request, env, { error: 'not_found' }, 404);

      await env.DB.prepare("UPDATE pipeline_runs SET status='running',started_at=datetime('now') WHERE article_id=?").bind(article.id).run();
      try {
        const outputs = await runPipeline(env, article);
        for (const item of outputs) {
          const confidence = Number(item.output?.confidence || item.output?.confidence_score || 70);
          await env.DB.prepare("UPDATE pipeline_runs SET status='completed',output_json=?,confidence_score=?,completed_at=datetime('now') WHERE article_id=? AND agent_key=?")
            .bind(JSON.stringify(item.output), confidence, article.id, item.key).run();
        }
        const editorial = outputs.find((x) => x.key === 'dabir')?.output || {};
        const chief = outputs.find((x) => x.key === 'nashr')?.output || {};
        await env.DB.prepare("UPDATE articles SET title=?,summary=?,body=?,confidence_score=?,status='review',updated_at=datetime('now') WHERE id=?")
          .bind(editorial.title || article.title, editorial.summary || article.summary, editorial.body || article.body, Number(chief.confidence || 70), article.id).run();
        return json(request, env, { ok: true, status: 'review', outputs });
      } catch (error) {
        await env.DB.prepare("UPDATE pipeline_runs SET status='failed',completed_at=datetime('now') WHERE article_id=? AND status='running'").bind(article.id).run();
        return json(request, env, { error: 'pipeline_failed', detail: String(error.message || error) }, 500);
      }
    }

    const articleMatch = url.pathname.match(/^\/api\/articles\/([^/]+)$/);
    if (articleMatch && request.method === 'GET') {
      const row = await env.DB.prepare(`${articleSelect} WHERE a.id=? OR a.slug=? LIMIT 1`).bind(articleMatch[1], articleMatch[1]).first();
      if (!row) return json(request, env, { error: 'not_found' }, 404);
      if (row.status !== 'published' && !adminAuthorized(request, env)) return json(request, env, { error: 'unauthorized' }, 401);
      return json(request, env, row);
    }

    if (articleMatch && request.method === 'PATCH') {
      if (!adminAuthorized(request, env)) return json(request, env, { error: 'unauthorized' }, 401);
      const payload = await readBody(request);
      const current = await env.DB.prepare('SELECT * FROM articles WHERE id=?').bind(articleMatch[1]).first();
      if (!current) return json(request, env, { error: 'not_found' }, 404);

      await env.DB.prepare("INSERT INTO revisions(id,article_id,snapshot_json,reason,created_at) VALUES(?,?,?,?,datetime('now'))")
        .bind(crypto.randomUUID(), current.id, JSON.stringify(current), payload.reason || 'editor_update').run();
      await env.DB.prepare("UPDATE articles SET title=?,summary=?,body=?,category=?,language=?,updated_at=datetime('now') WHERE id=?")
        .bind(payload.title ?? current.title, payload.summary ?? current.summary, payload.body ?? current.body, payload.category ?? current.category, payload.language ?? current.language, current.id).run();
      return json(request, env, { ok: true });
    }

    if (url.pathname === '/api/approvals' && request.method === 'POST') {
      if (!adminAuthorized(request, env)) return json(request, env, { error: 'unauthorized' }, 401);
      const payload = await readBody(request);
      if (!payload.articleId || !['approved', 'rejected', 'changes_requested'].includes(payload.decision)) return json(request, env, { error: 'invalid_approval' }, 400);

      await env.DB.prepare("INSERT INTO approvals(id,article_id,reviewer_id,decision,note,created_at) VALUES(?,?,?,?,?,datetime('now'))")
        .bind(crypto.randomUUID(), payload.articleId, payload.reviewerId || null, payload.decision, payload.note || '').run();

      const next = payload.decision === 'approved' ? 'published' : payload.decision === 'rejected' ? 'rejected' : 'changes_requested';
      await env.DB.prepare("UPDATE articles SET status=?,published_at=CASE WHEN ?='published' THEN datetime('now') ELSE published_at END,updated_at=datetime('now') WHERE id=?")
        .bind(next, next, payload.articleId).run();
      return json(request, env, { ok: true, status: next });
    }

    if (url.pathname === '/api/pipeline' && request.method === 'GET') {
      if (!adminAuthorized(request, env)) return json(request, env, { error: 'unauthorized' }, 401);
      const articleId = url.searchParams.get('articleId');
      if (!articleId) return json(request, env, { error: 'articleId_required' }, 400);
      const rows = await env.DB.prepare('SELECT * FROM pipeline_runs WHERE article_id=? ORDER BY stage').bind(articleId).all();
      return json(request, env, rows.results || []);
    }

    return json(request, env, { error: 'not_found' }, 404);
  }
};
