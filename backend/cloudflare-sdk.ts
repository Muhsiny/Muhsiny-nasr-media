type EnvLike={
  DB:any;
  MEDIA:any;
  AI?:any;
  ASSETS?:any;
  PUBLIC_ORIGIN?:string;
  AI_MODEL?:string;
};

type User={email?:string;name?:string};

type RuntimeState={env:EnvLike|null;request:Request|null;user:User|null};
const runtime:RuntimeState={env:null,request:null,user:null};
let dbReady:Promise<void>|null=null;

export function bindRuntime(env:EnvLike,request:Request|null=null,user:User|null=null){
  runtime.env=env;runtime.request=request;runtime.user=user;
}

function env(){if(!runtime.env)throw new Error('cloudflare_runtime_not_bound');return runtime.env}

async function ensureDb(){
  if(!dbReady){
    dbReady=(async()=>{
      await env().DB.exec(`
        CREATE TABLE IF NOT EXISTS app_records (
          table_name TEXT NOT NULL,
          id TEXT NOT NULL,
          data TEXT NOT NULL,
          created_at INTEGER NOT NULL,
          updated_at INTEGER NOT NULL,
          PRIMARY KEY (table_name,id)
        );
        CREATE INDEX IF NOT EXISTS idx_app_records_table_updated
          ON app_records(table_name,updated_at DESC);
      `);
    })().catch(e=>{dbReady=null;throw e});
  }
  return dbReady;
}

function cleanRecord(record:any){
  const copy={...(record||{})};
  delete copy.id;
  return copy;
}

export const db={
  async list<T=any>(table:string,opts:{limit?:number;nextToken?:string}={}){
    await ensureDb();
    const limit=Math.min(500,Math.max(1,Number(opts.limit||50)));
    const offset=Math.max(0,Number(opts.nextToken||0)||0);
    const result=await env().DB.prepare(
      'SELECT id,data FROM app_records WHERE table_name=? ORDER BY updated_at DESC, created_at DESC LIMIT ? OFFSET ?'
    ).bind(table,limit+1,offset).all();
    const rows=(result?.results||[]) as Array<{id:string;data:string}>;
    const more=rows.length>limit, selected=rows.slice(0,limit);
    const items=selected.map(row=>({...JSON.parse(row.data),id:row.id})) as T[];
    return{items,nextToken:more?String(offset+limit):undefined};
  },
  async get<T=any>(table:string,ids:string[]){
    await ensureDb();
    const out:Array<T|null>=[];
    for(const id of ids){
      const row=await env().DB.prepare('SELECT data FROM app_records WHERE table_name=? AND id=?').bind(table,id).first();
      out.push(row?({...JSON.parse(String((row as any).data)),id} as T):null);
    }
    return out as T[];
  },
  async add(table:string,records:any[]){
    await ensureDb();
    const ids:string[]=[];
    for(const record of records){
      const id=crypto.randomUUID(),now=Date.now();
      await env().DB.prepare('INSERT INTO app_records(table_name,id,data,created_at,updated_at) VALUES(?,?,?,?,?)')
        .bind(table,id,JSON.stringify(cleanRecord(record)),now,now).run();
      ids.push(id);
    }
    return ids;
  },
  async update(table:string,updates:Array<{id:string;record:any}>){
    await ensureDb();
    const out:boolean[]=[];
    for(const item of updates){
      const now=Date.now();
      const r=await env().DB.prepare('UPDATE app_records SET data=?,updated_at=? WHERE table_name=? AND id=?')
        .bind(JSON.stringify(cleanRecord(item.record)),now,table,item.id).run();
      out.push(Number(r?.meta?.changes||0)>0);
    }
    return out;
  },
  async delete(table:string,ids:string[]){
    await ensureDb();
    const out:boolean[]=[];
    for(const id of ids){
      const r=await env().DB.prepare('DELETE FROM app_records WHERE table_name=? AND id=?').bind(table,id).run();
      out.push(Number(r?.meta?.changes||0)>0);
    }
    return out;
  }
};

function baseOrigin(){
  try{return new URL(runtime.request?.url||'').origin}catch{}
  return env().PUBLIC_ORIGIN||'https://nasr-media.un-beha.org';
}
function b64ToBytes(b64:string){
  const raw=atob(b64),bytes=new Uint8Array(raw.length);
  for(let i=0;i<raw.length;i++)bytes[i]=raw.charCodeAt(i);
  return bytes;
}
function bytesToB64(bytes:Uint8Array){
  let s='';const step=0x8000;
  for(let i=0;i<bytes.length;i+=step)s+=String.fromCharCode(...bytes.subarray(i,i+step));
  return btoa(s);
}
export const storage={
  async write(items:Array<{path:string;content:string;contentType?:string}>){
    const out:boolean[]=[];
    for(const item of items){
      try{await env().MEDIA.put(item.path,b64ToBytes(item.content),{httpMetadata:{contentType:item.contentType||'application/octet-stream'}});out.push(true)}
      catch{out.push(false)}
    }
    return out;
  },
  async url(paths:string[]){
    return paths.map(path=>({path,url:baseOrigin()+'/media/'+path.split('/').map(encodeURIComponent).join('/')}));
  },
  async read(paths:string[]){
    const out:any[]=[];
    for(const path of paths){
      const obj=await env().MEDIA.get(path);
      if(!obj){out.push({path,content:'',contentType:''});continue}
      const bytes=new Uint8Array(await obj.arrayBuffer());
      out.push({path,content:bytesToB64(bytes),contentType:obj.httpMetadata?.contentType||'application/octet-stream'});
    }
    return out;
  },
  async list(opts:{prefix?:string;limit?:number;nextToken?:string}={}){
    const r=await env().MEDIA.list({prefix:opts.prefix||'',limit:Math.min(1000,Math.max(1,Number(opts.limit||100))),cursor:opts.nextToken});
    return{paths:(r.objects||[]).map((x:any)=>x.key),nextToken:r.truncated?r.cursor:undefined};
  },
  async delete(paths:string[]){
    const out:boolean[]=[];
    for(const path of paths){try{await env().MEDIA.delete(path);out.push(true)}catch{out.push(false)}}
    return out;
  }
};

function htmlToText(html:string){
  return html
    .replace(/<script\b[\s\S]*?<\/script>/gi,' ')
    .replace(/<style\b[\s\S]*?<\/style>/gi,' ')
    .replace(/<[^>]+>/g,' ')
    .replace(/&nbsp;/gi,' ')
    .replace(/&amp;/gi,'&')
    .replace(/&quot;/gi,'"')
    .replace(/&#39;/gi,"'")
    .replace(/\s+/g,' ')
    .trim();
}
function parseJsonFromModel(value:any){
  if(value&&typeof value==='object'&&!Array.isArray(value)&&!('response' in value))return value;
  const text=typeof value==='string'?value:String(value?.response||value?.result||value?.text||'');
  const cleaned=text.replace(/^\s*```(?:json)?/i,'').replace(/```\s*$/,'').trim();
  try{return JSON.parse(cleaned)}catch{}
  const first=Math.min(...['{','['].map(c=>{const i=cleaned.indexOf(c);return i<0?Number.MAX_SAFE_INTEGER:i}));
  if(Number.isFinite(first)&&first<Number.MAX_SAFE_INTEGER){
    const open=cleaned[first],close=open==='{'?'}':']',last=cleaned.lastIndexOf(close);
    if(last>first)try{return JSON.parse(cleaned.slice(first,last+1))}catch{}
  }
  throw new Error('ai_json_parse_failed');
}

export const ai={
  async scrape({url}:{url:string}){
    const res=await fetch(url,{headers:{'user-agent':'NASR-MEDIA-CLOUDFLARE/1.0','accept':'text/html,application/xhtml+xml,*/*'}});
    const text=htmlToText((await res.text()).slice(0,2000000));
    return{status:res.status,text};
  },
  async extract(opts:any){
    if(!env().AI)throw new Error('workers_ai_not_bound');
    const model=env().AI_MODEL||'@cf/zai-org/glm-4.7-flash';
    const system='You are the NASR MEDIA structured editorial engine. Follow the user instruction exactly. Return ONLY valid JSON matching the supplied JSON Schema. Never wrap JSON in markdown.';
    const user=[
      opts.prompt||'',
      'JSON Schema:',
      JSON.stringify(opts.schema||{}),
      'Input:',
      String(opts.content||'')
    ].join('\n\n');
    const result=await env().AI.run(model,{messages:[{role:'system',content:system},{role:'user',content:user}],temperature:Number(opts.temperature??0.05),max_tokens:Number(opts.maxTokens||4096)});
    return{data:parseJsonFromModel(result)};
  }
};

export function json(data:any,statusCode=200){
  return{statusCode,headers:{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'},body:JSON.stringify(data)};
}
export function error(message:string,statusCode=400){
  return json({error:message},statusCode);
}

export function requireAuth(){
  return async(ctx:any)=>ctx.user?undefined:error('unauthorized',401);
}

function compilePath(pattern:string){
  const names:string[]=[];
  const source=pattern.split('/').map(part=>{
    if(part.startsWith(':')){names.push(part.slice(1));return'([^/]+)'}
    return part.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');
  }).join('/');
  return{regex:new RegExp('^'+source+'$'),names};
}
function toResponse(result:any){
  if(result instanceof Response)return result;
  if(!result)return new Response(null,{status:204});
  const status=Number(result.statusCode||200),headers=new Headers(result.headers||{});
  return new Response(status===204?null:String(result.body??''),{status,headers});
}

export function router(routes:Record<string,Array<(ctx:any)=>any>>){
  const compiled=Object.entries(routes).map(([key,handlers])=>{
    const space=key.indexOf(' '),method=key.slice(0,space).toUpperCase(),path=key.slice(space+1);
    return{method,...compilePath(path),handlers};
  });
  return async function handle(request:Request,workerEnv:EnvLike,user:User|null=null){
    bindRuntime(workerEnv,request,user);
    const url=new URL(request.url),method=request.method.toUpperCase();
    for(const route of compiled){
      if(route.method!==method)continue;
      const m=url.pathname.match(route.regex);if(!m)continue;
      const params:Record<string,string>={};route.names.forEach((n,i)=>params[n]=decodeURIComponent(m[i+1]||''));
      const query:Object=Object.fromEntries(url.searchParams.entries());
      let body:any=null;
      if(!['GET','HEAD'].includes(method)){
        const type=request.headers.get('content-type')||'';
        if(type.includes('application/json'))body=await request.json().catch(()=>null);
        else body=await request.text().catch(()=>null);
      }
      const ctx={request,body,query,params,user};
      let result:any;
      for(const h of route.handlers){
        result=await h(ctx);
        if(result!==undefined&&result!==null)break;
      }
      return toResponse(result);
    }
    return new Response(JSON.stringify({error:'not_found'}),{status:404,headers:{'Content-Type':'application/json; charset=utf-8'}});
  };
}
