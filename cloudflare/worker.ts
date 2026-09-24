import{handler,runArianaEveryFiveMinutes,runAnalysisHourly}from'../backend/index';
import{bindRuntime}from'../backend/cloudflare-sdk';

type Env={
  DB:any;
  MEDIA:any;
  AI:any;
  ASSETS:any;
  PUBLIC_ORIGIN?:string;
  AI_MODEL?:string;
};

function accessUser(request:Request){
  const email=(request.headers.get('cf-access-authenticated-user-email')||'').trim().toLowerCase();
  if(!email)return null;
  return{email,name:email.split('@')[0]||email};
}

async function media(request:Request,env:Env,url:URL){
  const key=url.pathname.slice('/media/'.length).split('/').map(decodeURIComponent).join('/');
  if(!key)return new Response('not_found',{status:404});
  const obj=await env.MEDIA.get(key);
  if(!obj)return new Response('not_found',{status:404});
  const headers=new Headers();
  obj.writeHttpMetadata?.(headers);
  headers.set('etag',obj.httpEtag||obj.etag||'');
  headers.set('cache-control','public, max-age=3600, immutable');
  return new Response(obj.body,{status:200,headers});
}

async function asset(request:Request,env:Env){
  let res=await env.ASSETS.fetch(request);
  if(res.status===404&&request.method==='GET'&&request.headers.get('accept')?.includes('text/html')){
    const indexUrl=new URL('/index.html',request.url);
    res=await env.ASSETS.fetch(new Request(indexUrl,request));
  }
  return res;
}

function loginWindow(user:any){
  const ok=Boolean(user);
  const body='<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8"><title>NASR MEDIA</title><body style="font-family:system-ui;padding:32px">'+
    (ok?'<h2>ورود مدیریت نصر مدیا تأیید شد.</h2><p>این پنجره بسته می‌شود.</p><script>setTimeout(()=>window.close(),500)</script>':'<h2>ورود مدیریتی هنوز تأیید نشده است.</h2><p>Cloudflare Access باید برای این مسیر فعال باشد.</p>')+
    '</body></html>';
  return new Response(body,{status:ok?200:401,headers:{'content-type':'text/html; charset=utf-8','cache-control':'no-store'}});
}

export default{
  async fetch(request:Request,env:Env,ctx:any){
    const url=new URL(request.url),user=accessUser(request);
    bindRuntime(env,request,user);

    if(url.pathname.startsWith('/media/'))return media(request,env,url);
    if(url.pathname==='/api/auth/me'){
      if(!user)return new Response(JSON.stringify({error:'unauthorized'}),{status:401,headers:{'content-type':'application/json; charset=utf-8','cache-control':'no-store'}});
      return new Response(JSON.stringify({user}),{headers:{'content-type':'application/json; charset=utf-8','cache-control':'no-store'}});
    }
    if(url.pathname==='/api/auth/logout'&&request.method==='POST'){
      return new Response(JSON.stringify({ok:true}),{headers:{'content-type':'application/json; charset=utf-8','cache-control':'no-store'}});
    }
    if(url.pathname==='/admin-login')return loginWindow(user);

    if(url.pathname.startsWith('/api/')||url.pathname.startsWith('/share/')||url.pathname==='/facebook-rss.xml'){
      return handler(request,env,user);
    }
    return asset(request,env);
  },
  async scheduled(event:any,env:Env,ctx:any){
    bindRuntime(env,null,null);
    const task=event.cron==='7 * * * *'?runAnalysisHourly():runArianaEveryFiveMinutes();
    ctx.waitUntil(task);
  }
};
