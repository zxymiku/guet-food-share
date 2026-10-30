import {execFileSync} from 'node:child_process';
import {mkdir,copyFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
execFileSync(process.execPath,[fileURLToPath(new URL('../web/scripts/build-guide.mjs',import.meta.url))],{stdio:'inherit'});
const target=new URL('../android/app/src/main/assets/',import.meta.url);
await mkdir(target,{recursive:true});
await copyFile(new URL('../web/public/amap-guide.html',import.meta.url),new URL('amap-guide.html',target));
console.log('Synced offline Android map guide');
