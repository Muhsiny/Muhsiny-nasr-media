type ApiResponse<T=any>={data:T;status:number};

let currentUser:any=null;

async function request<T=any>(method:string,path:string,body?:unknown):Promise<ApiResponse<T>>{
  const headers:Record<string,string>={accept:'application/json'};
  const init:RequestInit={method,credentials:'include',headers};
  if(body!==undefined){headers['content-type']='application/json';init.body=JSON.stringify(body)}
  const res=await fetch(path,init);
  const type=res.headers.get('content-type')||'';
  const data:any=type.includes('application/json')?await res.json().catch(()=>({})):await res.text().catch(()=>'');
  if(!res.ok){
    const e:any=new Error(data?.error||data?.message||String(data)||('request_'+res.status));
    e.response={status:res.status,data};e.status=res.status;throw e;
  }
  return{data,status:res.status};
}

export const api={
  get:<T=any>(path:string)=>request<T>('GET',path),
  post:<T=any>(path:string,body?:unknown)=>request<T>('POST',path,body)
};

async function refreshUser(){
  try{const r=await request<any>('GET','/api/auth/me');currentUser=r.data?.user||r.data||null;return currentUser}
  catch{currentUser=null;return null}
}

export const auth={
  isSignedIn:()=>Boolean(currentUser),
  getUser:()=>refreshUser(),
  async signIn(){
    const popup=window.open('/admin-login?return='+encodeURIComponent(location.href),'nasr-auth','popup=yes,width=520,height=720');
    if(!popup){const e:any=new Error('popup_blocked');e.code='popup_blocked';throw e}
    const started=Date.now();
    while(Date.now()-started<120000){
      const u=await refreshUser();
      if(u){try{popup.close()}catch{};return u}
      if(popup.closed){const e:any=new Error('popup_closed');e.code='popup_closed';throw e}
      await new Promise(r=>setTimeout(r,1200));
    }
    try{popup.close()}catch{}
    const e:any=new Error('auth_timeout');e.code='auth_timeout';throw e;
  },
  async signOut(){
    currentUser=null;
    try{await request('POST','/api/auth/logout',{})}catch{}
    window.location.assign('/cdn-cgi/access/logout');
  }
};

function readAsDataURL(file:Blob){return new Promise<string>((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(String(r.result||''));r.onerror=()=>reject(r.error||new Error('file_read_failed'));r.readAsDataURL(file)})}

export const image={
  async resizeIfNeeded(file:File,opts:{maxDimension?:number;maxPixels?:number;quality?:number;mimeType?:string}={}){
    const source=await createImageBitmap(file);
    const maxDimension=Math.max(64,Number(opts.maxDimension||source.width||source.height));
    const maxPixels=Math.max(4096,Number(opts.maxPixels||source.width*source.height));
    let scale=Math.min(1,maxDimension/Math.max(source.width,source.height),Math.sqrt(maxPixels/(source.width*source.height)));
    if(!Number.isFinite(scale)||scale<=0)scale=1;
    const width=Math.max(1,Math.round(source.width*scale)),height=Math.max(1,Math.round(source.height*scale));
    const canvas=document.createElement('canvas');canvas.width=width;canvas.height=height;
    const ctx=canvas.getContext('2d');if(!ctx)throw new Error('canvas_unavailable');
    ctx.drawImage(source,0,0,width,height);source.close();
    const mimeType=opts.mimeType||file.type||'image/png',quality=Math.min(1,Math.max(.2,Number(opts.quality||.9)));
    const blob=await new Promise<Blob>((resolve,reject)=>canvas.toBlob(b=>b?resolve(b):reject(new Error('image_encode_failed')),mimeType,quality));
    const url=await readAsDataURL(blob),data=url.split(',')[1]||'';
    return{data,mimeType:blob.type||mimeType,width,height};
  }
};
