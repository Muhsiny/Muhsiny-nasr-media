import{runPipeline}from'./agents.js';
import{ingestSources}from'./ingest.js';
const cors={"Access-Control-Allow-Origin":"*","Access-Control-Allow-Methods":"GET,POST,PATCH,DELETE,OPTIONS","Access-Control-Allow-Headers":"content-type,authorization,x-ingest-key"};
const json=(data,status=200)=>new Response(JSON.stringify(data),{status,headers:{...cors,"content-type":"application/json;charset=UTF-8"}});
const agents=[
 {key:'rasad',name:'رصد',stage:1,task:'جمع‌آوری منابع'},
 {key:'palayesh',name:'پالایش',stage:2,task:'حذف تکرار و محتوای ضعیف'},
 {key:'mizan',name:'میزان',stage:3,task:'راستی‌آزمایی و امتیاز اعتبار'},
 {key:'nabz',name:'نبض',stage:4,task:'خلاصه‌سازی و استخراج نکات'},
 {key:'basir',name:'بصیر',stage:5,task:'تحلیل زمینه و پیامدها'},
 {key:'dabir',name:'دبیر',stage:6,task:'تیتر، لید و بازنویسی'},
 {key:'lesan',name:'لسان',stage:7,task:'ترجمه هفت‌زبانه'},
 {key:'nashr',name:'نشر',stage:8,task:'کنترل نهایی و ارجاع انسانی'}
];
const slugify=s=>s.toLowerCase().replace(/[^\p{L}\p{N}]+/gu,'-').replace(/(^-|-$)/g,'');
async function body(req){try{return await req.json()}catch{return {}}}
function ingestAuthorized(request,env){return !env.INGEST_KEY||request.headers.get('x-ingest-key')===env.INGEST_KEY}
export default{
 async scheduled(_event,env,ctx){ctx.waitUntil(ingestSources(env));},
 async fetch(request,env){
  const url=new URL(request.url);if(request.method==='OPTIONS')return new Response(null,{headers:cors});
  if(url.pathname==='/api/health'){
   let db=false,lastIngestion=null;try{await env.DB.prepare('SELECT 1').first();db=true;lastIngestion=await env.DB.prepare("SELECT created_at,details_json FROM audit_logs WHERE action='scheduled_ingestion' ORDER BY created_at DESC LIMIT 1").first()}catch{}
   return json({ok:db,service:'NASR MEDIA API',database:db?'connected':'unavailable',lastIngestion,time:new Date().toISOString()},db?200:503);
  }
  if(url.pathname==='/api/ingest'&&request.method==='POST'){
   if(!ingestAuthorized(request,env))return json({error:'unauthorized'},401);
   try{return json({ok:true,report:await ingestSources(env)})}catch(error){return json({error:'ingestion_failed',detail:String(error.message||error)},500)}
  }
  if(url.pathname==='/api/agents')return json({agents});
  if(url.pathname==='/api/articles'&&request.method==='GET'){
   const status=url.searchParams.get('status'),language=url.searchParams.get('language');
   let sql='SELECT id,slug,status,language,title,summary,category,confidence_score,published_at,created_at FROM articles WHERE 1=1';const args=[];
   if(status){sql+=' AND status=?';args.push(status)}if(language){sql+=' AND language=?';args.push(language)}sql+=' ORDER BY COALESCE(published_at,created_at) DESC LIMIT 100';
   const rows=await env.DB.prepare(sql).bind(...args).all();return json(rows.results);
  }
  if(url.pathname==='/api/articles'&&request.method==='POST'){
   const b=await body(request);if(!b.title)return json({error:'title_required'},400);
   const id=crypto.randomUUID(),slug=`${slugify(b.slug||b.title)}-${id.slice(0,8)}`;
   await env.DB.prepare("INSERT INTO articles(id,slug,status,language,title,summary,body,category,confidence_score,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,datetime('now'),datetime('now'))").bind(id,slug,'draft',b.language||'fa',b.title,b.summary||'',b.body||'',b.category||'general',0).run();
   for(const a of agents)await env.DB.prepare("INSERT INTO pipeline_runs(id,article_id,stage,agent_key,status,created_at) VALUES(?,?,?,?,?,datetime('now'))").bind(crypto.randomUUID(),id,a.stage,a.key,'queued').run();
   return json({id,slug,status:'draft'},201);
  }
  const processMatch=url.pathname.match(/^\/api\/articles\/([^/]+)\/process$/);
  if(processMatch&&request.method==='POST'){
   const article=await env.DB.prepare('SELECT * FROM articles WHERE id=?').bind(processMatch[1]).first();if(!article)return json({error:'not_found'},404);
   await env.DB.prepare("UPDATE pipeline_runs SET status='running',started_at=datetime('now') WHERE article_id=?").bind(article.id).run();
   try{
    const outputs=await runPipeline(env,article);
    for(const item of outputs){const confidence=Number(item.output?.confidence||item.output?.confidence_score||70);await env.DB.prepare("UPDATE pipeline_runs SET status='completed',output_json=?,confidence_score=?,completed_at=datetime('now') WHERE article_id=? AND agent_key=?").bind(JSON.stringify(item.output),confidence,article.id,item.key).run();}
    const editorial=outputs.find(x=>x.key==='dabir')?.output||{},chief=outputs.find(x=>x.key==='nashr')?.output||{};
    await env.DB.prepare("UPDATE articles SET title=?,summary=?,body=?,confidence_score=?,status='review',updated_at=datetime('now') WHERE id=?").bind(editorial.title||article.title,editorial.summary||article.summary,editorial.body||article.body,Number(chief.confidence||70),article.id).run();
    return json({ok:true,status:'review',outputs});
   }catch(error){await env.DB.prepare("UPDATE pipeline_runs SET status='failed',completed_at=datetime('now') WHERE article_id=? AND status='running'").bind(article.id).run();return json({error:'pipeline_failed',detail:String(error.message||error)},500)}
  }
  const articleMatch=url.pathname.match(/^\/api\/articles\/([^/]+)$/);
  if(articleMatch&&request.method==='GET'){const row=await env.DB.prepare('SELECT * FROM articles WHERE id=? OR slug=?').bind(articleMatch[1],articleMatch[1]).first();return row?json(row):json({error:'not_found'},404);}
  if(articleMatch&&request.method==='PATCH'){
   const b=await body(request),current=await env.DB.prepare('SELECT * FROM articles WHERE id=?').bind(articleMatch[1]).first();if(!current)return json({error:'not_found'},404);
   await env.DB.prepare("INSERT INTO revisions(id,article_id,snapshot_json,reason,created_at) VALUES(?,?,?,?,datetime('now'))").bind(crypto.randomUUID(),current.id,JSON.stringify(current),b.reason||'editor_update').run();
   await env.DB.prepare("UPDATE articles SET title=?,summary=?,body=?,category=?,language=?,updated_at=datetime('now') WHERE id=?").bind(b.title??current.title,b.summary??current.summary,b.body??current.body,b.category??current.category,b.language??current.language,current.id).run();return json({ok:true});
  }
  if(url.pathname==='/api/approvals'&&request.method==='POST'){
   const b=await body(request);if(!b.articleId||!['approved','rejected','changes_requested'].includes(b.decision))return json({error:'invalid_approval'},400);
   await env.DB.prepare("INSERT INTO approvals(id,article_id,reviewer_id,decision,note,created_at) VALUES(?,?,?,?,?,datetime('now'))").bind(crypto.randomUUID(),b.articleId,b.reviewerId||null,b.decision,b.note||'').run();
   const next=b.decision==='approved'?'published':b.decision==='rejected'?'rejected':'changes_requested';
   await env.DB.prepare("UPDATE articles SET status=?,published_at=CASE WHEN ?='published' THEN datetime('now') ELSE published_at END,updated_at=datetime('now') WHERE id=?").bind(next,next,b.articleId).run();return json({ok:true,status:next});
  }
  if(url.pathname==='/api/pipeline'&&request.method==='GET'){const articleId=url.searchParams.get('articleId');if(!articleId)return json({error:'articleId_required'},400);const rows=await env.DB.prepare('SELECT * FROM pipeline_runs WHERE article_id=? ORDER BY stage').bind(articleId).all();return json(rows.results);}
  return json({error:'not_found'},404);
 }
};
