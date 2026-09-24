import{handler,runArianaEveryFiveMinutes,runAnalysisHourly}from'../backend/index';
import{bindRuntime}from'../backend/cloudflare-sdk';

type Env={
  DB:any;
  MEDIA:any;
  AI:any;
  ASSETS:any;
  PUBLIC_ORIGIN?:string;
  AI_MODEL?:string;
  AUTH_SECRET:string;
};

const SESSION_COOKIE='nasr_admin_session';

function accessUser(request:Request){
  const email=(request.headers.get('cf-access-authenticated-user-email')||'').trim().toLowerCase();
  if(!email)return null;
  return{email,name:email.split('@')[0]||email};
}
function cookieValue(request:Request,name:string){
  const raw=request.headers.get('cookie')||'';
  for(const part of raw.split(';')){
    const p=part.trim(),i=p.indexOf('=');
    if(i>0&&p.slice(0,i)===name)return decodeURIComponent(p.slice(i+1));
  }
  return'';
}
function b64url(bytes:Uint8Array){
  let s='';for(let i=0;i<bytes.length;i++)s+=String.fromCharCode(bytes[i]);
  return btoa(s).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
}
function unb64url(value:string){
  const s=value.replace(/-/g,'+').replace(/_/g,'/').padEnd(Math.ceil(value.length/4)*4,'=');
  const raw=atob(s),out=new Uint8Array(raw.length);for(let i=0;i<raw.length;i++)out[i]=raw.charCodeAt(i);return out;
}
async function key(secret:string,usage:KeyUsage[]){
  return crypto.subtle.importKey('raw',new TextEncoder().encode(secret),{name:'HMAC',hash:'SHA-256'},false,usage);
}
async function signSession(email:string,secret:string){
  const payload=b64url(new TextEncoder().encode(JSON.stringify({email:email.toLowerCase(),exp:Date.now()+8*60*60*1000})));
  const sig=new Uint8Array(await crypto.subtle.sign('HMAC',await key(secret,['sign']),new TextEncoder().encode(payload)));
  return payload+'.'+b64url(sig);
}
async function sessionUser(request:Request,env:Env){
  const token=cookieValue(request,SESSION_COOKIE);if(!token||!env.AUTH_SECRET)return null;
  const [payload,sig]=token.split('.');if(!payload||!sig)return null;
  try{
    const ok=await crypto.subtle.verify('HMAC',await key(env.AUTH_SECRET,['verify']),unb64url(sig),new TextEncoder().encode(payload));
    if(!ok)return null;
    const data=JSON.parse(new TextDecoder().decode(unb64url(payload)));
    if(!data.email||Number(data.exp||0)<=Date.now())return null;
    const email=String(data.email).toLowerCase();return{email,name:email.split('@')[0]||email};
  }catch{return null}
}
async function currentUser(request:Request,env:Env){return accessUser(request)||await sessionUser(request,env)}

async function media(request:Request,env:Env,url:URL){
  const key=url.pathname.slice('/media/'.length).split('/').map(decodeURIComponent).join('/');
  if(!key)return new Response('not_found',{status:404});
  const obj=await env.MEDIA.get(key);
  if(!obj)return new Response('not_found',{status:404});
  const headers=new Headers();
  obj.writeHttpMetadata?.(headers);
  if(obj.httpEtag||obj.etag)headers.set('etag',obj.httpEtag||obj.etag);
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

function loginHtml(ok:boolean){
  const body='<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8"><title>NASR MEDIA</title><body style="font-family:system-ui;padding:32px">'+
    (ok?'<h2>ورود مدیریت نصر مدیا تأیید شد.</h2><p>این پنجره بسته می‌شود.</p><script>setTimeout(()=>window.close(),500)</script>':'<h2>ورود مدیریتی تأیید نشد.</h2>')+
    '</body></html>';
  return body;
}

export default{
  async fetch(request:Request,env:Env,ctx:any){
    const url=new URL(request.url);
    if(url.pathname.startsWith('/media/'))return media(request,env,url);

    if(url.pathname==='/admin-login'){
      const viaAccess=accessUser(request);
      if(!viaAccess||!env.AUTH_SECRET)return new Response(loginHtml(false),{status:401,headers:{'content-type':'text/html; charset=utf-8','cache-control':'no-store'}});
      const session=await signSession(viaAccess.email,env.AUTH_SECRET);
      return new Response(loginHtml(true),{status:200,headers:{
        'content-type':'text/html; charset=utf-8',
        'cache-control':'no-store',
        'set-cookie':SESSION_COOKIE+'='+encodeURIComponent(session)+'; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=28800'
      }});
    }

    const user=await currentUser(request,env);
    bindRuntime(env,request,user);

    if(url.pathname==='/api/auth/me'){
      if(!user)return new Response(JSON.stringify({error:'unauthorized'}),{status:401,headers:{'content-type':'application/json; charset=utf-8','cache-control':'no-store'}});
      return new Response(JSON.stringify({user}),{headers:{'content-type':'application/json; charset=utf-8','cache-control':'no-store'}});
    }
    if(url.pathname==='/api/auth/logout'&&request.method==='POST'){
      return new Response(JSON.stringify({ok:true}),{headers:{
        'content-type':'application/json; charset=utf-8',
        'cache-control':'no-store',
        'set-cookie':SESSION_COOKIE+'=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0'
      }});
    }

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
