# 公开地点数据契约

地图凭据属于每位访问者，地点记录属于公开数据集。查询 `/api/places` 不需要高德 Key；该公开 GET 接口允许不带登录凭据的跨域读取（`Access-Control-Allow-Origin: *`）；任何客户端先读取记录，再使用自己的有效高德 Key 绘制 GCJ-02 点位。数据读取不触发高德地址查询，已有地址不会随访问者 Key 改变。

## 记录字段

| 字段 | 类型与含义 |
| --- | --- |
| id | 服务端生成的稳定 UUID，与 Key 和设备无关 |
| schemaVersion | 地点结构版本，当前为 2；不是导出封装的版本 |
| name / category / address | 店名、有效分类和核实后的地址 |
| description / directions | 推荐理由和前往方法；未填为空字符串 |
| subcategory / pros / cons / menuPrice | 子分类、优缺点、菜单价格文本；均选填 |
| pricePerPerson | 可选参考人均，0～10000 的数值；未知为 null，不等同免费 |
| businessHours / tags | 投稿营业时间文本最多200字、标签最多8个且每个20字；非实时营业/饮食安全保证 |
| rating | 公开评价摘要 `{count,average}`；无评价时 average 为 null |
| location | 数值 lng、lat（保留 6 位小数）及 crs='GCJ-02' |
| photos / videos | 有序、去重的本站公开媒体相对路径，最多 6 张/2 段 |
| consent | 明确授权公开；只有 true 的有效投稿进入审核 |
| createdAt | 原始投稿创建时间；历史导入记录无法追溯时为 null，不伪造 |
| publishedAt | 第一次写入公开表的 UTC 时间；重复发布不重置 |

用户输入经规范化和白名单提取后加密存入投稿表，审核流水线将业务字段与 ID 写入 `data/places/{id}.json`，人工审核后写入公开表。时间由服务端表维护，公开接口联表返回。不会公开账号标识、登录令牌、地图 Key、安全码或非白名单对象；这也意味着只有明确的业务字段是记录契约，客户端不得指望额外字段被保存。

媒体引用在发布前验证归属，仅投稿者/审核员能访问待审媒体。发布后任何访问者可通过同一公开路径读取；地图 Key 不参与媒体授权。原始位置、地址、照片等是否准确仍由投稿者核对、审核员审查。

被撤回或审核隐藏的地点不再出现在列表、单条查询及公共媒体授权中。离线缓存、公开 Git 历史和第三方已经保存的副本无法由应用保证清除。

## 完整分页

`GET /api/places?limit=200` 返回 `{schemaVersion:1, crs:'GCJ-02', places:[...], nextCursor:...}`。limit 可为 1～500，默认 200。若 nextCursor 非 null，URL 编码后请求 `?limit=200&cursor=...`，重复至 null。不要截断第一页。

排序键为发布时间倒序、ID 倒序；同一毫秒发布的记录不会遗漏或重复。游标是不可擅自解释的分页位置，不是鉴权凭据。分页期间新发布的记录位于开头，完成后刷新一次可获得新内容；接口不是固定时间点数据库快照。双端自动加载所有页并按 ID 去重。

`GET /api/places/{id}` 返回 `{place}`，不存在或已撤回/隐藏时为404。分享链接 `/?place={id}` 只包含稳定 ID，不含私人清单或个人 Key。

## 评价、本人管理与治理

`GET /api/places/{id}/reviews` 返回 `{count,average,reviews,nextCursor}`。每页最多100条，按 `created_at DESC,id DESC`；后续用 `?before={URL编码游标}` 直到 null。评价只有 id、score、comment、created_at 和当前会话的 canDelete，不公开作者账号、IP或设备哈希。GET 不要求地图 Key；跨域匿名 CORS 当前只为 `/api/places` 列表配置，其他读取需同源或自行提供合规代理。

登录后 POST 同一路径提交 `{score:1..5,comment,deviceId}`；分数须显式选择，文字最多1000字，可只打分。北京时间每天每地点对账号、IP、设备分别唯一，冲突429。IP/随机设备ID仅存每日 HMAC，不采硬件指纹；共享出口可能误限，非绝对反作弊。`DELETE /api/reviews/{id}` 只允许本人，撤回清空文字并保留限额记录。旧无作者记录不猜测归属，走举报。

`GET /api/submissions` 为当前身份的投稿，每页100条，通过 `?cursor=...` 完整读取；`DELETE /api/submissions/{id}` 仍核验归属。共享密码的设备身份丢失后不能恢复原所有权。

`POST /api/reports` 接受 `{type:'place'|'review',targetId,reason,detail}`，须登录，同目标同日一次，每账号每天最多20条。不会因举报自动下架。`GET/POST /api/moderation` 仅配置的 GitHub 审核员可访问，支持处理、隐藏和地点恢复并写入私有审计。队列目前展示最近100条；大规模使用前需增加游标及检索。举报者身份与审计不进入公开导出。

地点导出包含评分摘要；评价明细通过上述分页接口读取，不把内部防刷记录或举报数据库打包公开。收藏/想去/吃过属于本机私有清单，不属于公开地点。

## 导出与复用

双端导出的 JSON 封装使用 `format='guet-food-places'`、`schemaVersion=1`，含 `source`、`mediaBaseUrl`、`exportedAt`、`crs` 和 `places`。它保存全部已公开业务信息及媒体引用，不是照片/视频二进制备份。新访问者可从公开接口加载相同数据；开发者也可读取导出文件并将 `places` 交给自己的地图视图。

```js
// amap 已由读取者使用自己的 JSAPI Key 初始化。
for (const place of archive.places) {
  if (place.location.crs !== 'GCJ-02') throw new Error('需要明确转换坐标系');
  const marker = new AMap.Marker({
    position: [place.location.lng, place.location.lat],
    title: place.name
  });
  amap.add(marker);
  // 详情用 textContent 渲染用户文字，不用 innerHTML。
  const imageUrls = place.photos.map(path => new URL(path, archive.mediaBaseUrl).href);
}
```

Android `LatLng(latitude, longitude)` 参数顺序相反。GPS/WGS-84 只有首次定位时转换成 GCJ-02；记录内的高德坐标不可再次转换。JSON 不携带地图 Key，读取者在本机设置自己的 Key。A 的 Key 被撤销不会影响 B 显示 A 已公开的标记。

可复用的媒体仍依赖原站服务和存储；如果原站不可用，单凭相对链接不能恢复二进制。需要灾备时，运维应备份 D1、媒体对象和服务端数据加密密钥，并在授权范围内提供公开媒体副本。不要将服务端解密密钥放进用户导出文件。
