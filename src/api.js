const API=import.meta.env.VITE_API_URL||'';
async function request(path,options={}){
 const res=await fetch(`${API}${path}`,{headers:{'content-type':'application/json',...(options.headers||{})},...options});
 const data=await res.json().catch(()=>({}));
 if(!res.ok)throw new Error(data.error||'request_failed');
 return data;
}
export const newsroom={
 health:()=>request('/api/health'),
 agents:()=>request('/api/agents'),
 articles:(params='')=>request(`/api/articles${params}`),
 article:id=>request(`/api/articles/${id}`),
 create:payload=>request('/api/articles',{method:'POST',body:JSON.stringify(payload)}),
 update:(id,payload)=>request(`/api/articles/${id}`,{method:'PATCH',body:JSON.stringify(payload)}),
 approve:payload=>request('/api/approvals',{method:'POST',body:JSON.stringify(payload)}),
 pipeline:articleId=>request(`/api/pipeline?articleId=${encodeURIComponent(articleId)}`)
};
