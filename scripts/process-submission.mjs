import {normalize} from '../web/src/validation.js';
const {SUBMISSION_ID:id,WORKER_URL,GITHUB_TOKEN,GITHUB_REPOSITORY:repo}=process.env;
const intakeSecret=process.env.INTAKE_SECRET||process.env.PIPELINE_SECRET;
if(!/^[0-9a-f-]{36}$/.test(id||''))throw new Error('Invalid submission ID');
if(!WORKER_URL?.startsWith('https://'))throw new Error('WORKER_URL must use HTTPS');
async function gh(path,method='GET',body){const r=await fetch(`https://api.github.com/repos/${repo}${path}`,{method,headers:{Authorization:`Bearer ${GITHUB_TOKEN}`,Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28'},body:body?JSON.stringify(body):undefined});if(!r.ok)throw new Error(`GitHub request failed: ${r.status}`);return r.status===204?null:r.json();}
const source=await fetch(`${WORKER_URL}/api/internal/submissions/${id}`,{headers:{Authorization:`Bearer ${intakeSecret}`}});if(!source.ok)throw new Error(`Intake fetch failed: ${source.status}`);
const raw=await source.json();if(raw.id!==id)throw new Error('ID mismatch');
const data={id,...normalize(raw,{categories:[{name:raw.category,children:[raw.subcategory||'']}]})}, branch=`food/${id}`,path=`data/places/${id}.json`;
const metadata=await gh(''),base=metadata.default_branch;
const ref=await gh(`/git/ref/heads/${encodeURIComponent(base)}`);
const existing=await fetch(`https://api.github.com/repos/${repo}/git/ref/heads/${branch}`,{headers:{Authorization:`Bearer ${GITHUB_TOKEN}`}});
if(existing.status===404)await gh('/git/refs','POST',{ref:`refs/heads/${branch}`,sha:ref.object.sha});else if(!existing.ok)throw new Error(`Branch lookup failed ${existing.status}`);
const oldResponse=await fetch(`https://api.github.com/repos/${repo}/contents/${path}?ref=${branch}`,{headers:{Authorization:`Bearer ${GITHUB_TOKEN}`}});let sha;if(oldResponse.ok)sha=(await oldResponse.json()).sha;else if(oldResponse.status!==404)throw new Error('Content lookup failed');
const content=Buffer.from(JSON.stringify(data,null,2)+'\n').toString('base64');
await gh(`/contents/${path}`,'PUT',{message:`Normalize food submission ${id}`,content,branch,...(sha?{sha}:{})});
const pulls=await gh(`/pulls?head=${encodeURIComponent(repo.split('/')[0]+':'+branch)}&state=all`);
if(!pulls.length)await gh('/pulls','POST',{title:`美食投稿审核 ${id}`,head:branch,base,body:`此投稿已完成结构校验、文本清理、坐标精度与分类规范化。\n\n[查看文字、坐标和门店照片](${WORKER_URL}/review.html?id=${id})（需审核员登录）。\n\n请审核店名、位置、前往方法、照片授权及内容真实性。合并此 PR 后，发布工作流会将该地点同步到地图。\n\n提交者内容仅作为 JSON 数据处理，不作为命令执行。`});
console.log(`Submission ${id} normalized for human review.`);
