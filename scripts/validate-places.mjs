import {readdir,readFile} from 'node:fs/promises';
import {normalize} from '../web/src/validation.js';
for(const file of await readdir(new URL('../data/places/',import.meta.url))){if(!file.endsWith('.json'))continue;const data=JSON.parse(await readFile(new URL('../data/places/'+file,import.meta.url),'utf8'));if(!/^[0-9a-f-]{36}$/.test(data.id)||file!==data.id+'.json')throw new Error('Invalid ID');if(JSON.stringify({id:data.id,...normalize(data,{categories:[{name:data.category,children:[data.subcategory||'']}]})})!==JSON.stringify(data))throw new Error(`Noncanonical data: ${file}`);}
console.log('All published data validated.');
