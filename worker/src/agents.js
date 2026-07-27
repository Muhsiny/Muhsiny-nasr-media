const safeJson=async r=>{const t=await r.text();try{return JSON.parse(t)}catch{return {text:t}}};
async function callModel(env,system,user){
 if(!env.OPENAI_API_KEY)return {mode:'simulation',content:user,confidence:70};
 const res=await fetch('https://api.openai.com/v1/chat/completions',{method:'POST',headers:{'content-type':'application/json','authorization':`Bearer ${env.OPENAI_API_KEY}`},body:JSON.stringify({model:env.OPENAI_MODEL||'gpt-4.1-mini',temperature:.2,response_format:{type:'json_object'},messages:[{role:'system',content:system},{role:'user',content:user}]})});
 if(!res.ok)throw new Error(`model_${res.status}`);const data=await safeJson(res);const raw=data.choices?.[0]?.message?.content||'{}';try{return JSON.parse(raw)}catch{return {content:raw,confidence:60}}
}
const prompts={
 rasad:'خبر و منابع ورودی را استاندارد کن. خروجی JSON شامل facts, sources, language باشد.',
 palayesh:'تکرار، تبلیغ و ادعاهای بی‌ارزش را حذف کن. خروجی JSON شامل cleaned_text و removed_items باشد.',
 mizan:'ادعاها را از نظر انسجام و اتکاپذیری ارزیابی کن. چیزی جعل نکن. خروجی JSON شامل claims, warnings, confidence باشد.',
 nabz:'خلاصه دقیق، اشخاص، مکان، زمان و ارقام را استخراج کن. خروجی JSON باشد.',
 basir:'خبر را از تحلیل جدا نگه دار و زمینه، علت‌ها، پیامدها و سناریوها را با برچسب تحلیل ارائه کن. خروجی JSON باشد.',
 dabir:'تیتر، لید، خلاصه و متن حرفه‌ای رسانه‌ای تولید کن؛ ادعا یا نقل قول تازه نساز. خروجی JSON شامل title, lead, summary, body باشد.',
 lesan:'متن را به زبان‌های fa, ps, ar, en, tr, fr, ur ترجمه طبیعی کن. خروجی JSON با کلید هر زبان باشد.',
 nashr:'کنترل نهایی حقوقی و تحریری انجام بده. خروجی JSON شامل decision, risks, confidence, editor_note باشد و هرگز خودکار نشر نکن.'
};
export async function runPipeline(env,article){
 let payload={article};const results=[];
 for(const key of Object.keys(prompts)){
  const output=await callModel(env,prompts[key],JSON.stringify(payload));
  results.push({key,output});payload={...payload,[key]:output};
 }
 return results;
}
