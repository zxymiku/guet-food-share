import {readdir,readFile} from 'node:fs/promises';
import {normalize} from '../web/src/validation.js';
const {WORKER_URL}=process.env;
const publishSecret=process.env.PUBLISH_SECRET||process.env.PIPELINE_SECRET;
if(!WORKER_URL?.startsWith('https://')||!publishSecret)throw new Error('Configure WORKER_URL and PUBLISH_SECRET');
for(const file of await readdir(new URL('../data/places/',import.meta.url))){
 if(!file.endsWith('.json'))continue;
 const data=JSON.parse(await readFile(new URL('../data/places/'+file,import.meta.url),'utf8'));
 if(!/^[0-9a-f-]{36}$/.test(data.id)||file!==data.id+'.json')throw new Error('Invalid published ID');
 const r=await fetch(`${WORKER_URL}/api/internal/publish/${data.id}`,{method:'POST',headers:{Authorization:`Bearer ${publishSecret}`,'Content-Type':'application/json'},body:JSON.stringify(normalize(data,{categories:[{name:data.category,children:[data.subcategory||'']}]}))});
 if(!r.ok)throw new Error(`Publish ${data.id} failed: ${r.status}`);
 console.log(`Published ${data.id}`);
}
