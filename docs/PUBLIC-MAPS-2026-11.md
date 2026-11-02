# 公共地图与外部导航（2026-11-02）

## 使用方案

普通用户不用申请高德Key：可直接打开外部地图、查看设备位置、从地点详情选择高德/百度路线。项目内部底图则由开发者一次性配置Web和Android应用Key，用户同意地图服务后启用。个人API保留为高级选项，用于自管额度、投稿选点和地址辅助填写。

| 路径 | 用户申请Key | 开发者配置 | 说明 |
|---|---|---|---|
| 外部高德/百度地图、位置查看与路线 | 不需要 | 不需要 | 通过官方产品URI/网页入口；对应地图自行提供底图和导航 |
| 本应用内公共Web底图与标记 | 不需要 | JSAPI Key与配套安全密钥 | 域名白名单；安全密钥只在Worker |
| 本应用内公共Android底图与标记 | 不需要 | Android Key | 同一应用包名、正式签名SHA-1；与Web Key分开 |
| 高级个人模式 | 用户自行申请 | 不需要项目公共Key | 用户Key只存本机、设备直连高德；共享记录和导出不携带它 |

“公共地图仅浏览”是应用行为约束：不将普通点击当投稿选点，不在公共模式执行投稿地址查询；投稿可手填坐标，需登录和公开授权。它不是高德平台保证的“只能查看、无法调用其他能力”的Key类型，更不是绝对防滥用承诺。开发者必须在平台核对授权、绑定范围、额度/费用和停用方式，不承诺无限免费。不能把访问者的个人Key拿来作为公共Key。

## 开发者一次性配置

账号注册、实名、创建应用仍需账号持有人在官方控制台完成；代理不能替你提交实名资料或凭空生成有效Key。完整流程继续见 [高德README教程](../web/public/amap-guide.md)。

1. 创建独立的项目应用，给Web添加 **Web端(JSAPI)** Key和配套securityJsCode，域名白名单限制为 `food.zxymiku.top`。不要提交安全密钥到Git。
2. 给Android添加 **Android平台** Key，包名 `com.guet.foodshare`，SHA-1使用已发布APK的正式签名。可在安装后的高级设置复制。调试版签名不同，不能用其SHA-1代替正式版。
3. 在Cloudflare Worker `guet-food-share` 的Secrets配置下表。为避免后续Wrangler发布覆盖控制台普通vars，推荐这几项统一用Secrets；公开应用Key依然会按需返回客户端，这是SDK应用标识，不是服务端安全密钥。

| Worker环境项 | 内容 | 可否返回客户端 |
|---|---|---|
| AMAP_PUBLIC_MAP_ENABLED | 字符串 `true`；停用设为 `false` | 只返回是否就绪 |
| AMAP_PUBLIC_WEB_KEY | 项目专用32位JSAPI Key | 公共Web模式需要，公开标识 |
| AMAP_PUBLIC_SECURITY_JS_CODE | 该Key配套32位安全密钥 | **绝不返回** |
| AMAP_PUBLIC_ANDROID_KEY | 绑定包名/正式SHA-1的32位Android Key | 公共原生SDK需要，公开标识 |
| AMAP_PUBLIC_PROXY_DAILY_LIMIT | 可选，本站代理每日总请求上限，默认5000 | 不返回 |
| AMAP_PUBLIC_PROXY_IP_HOURLY_LIMIT | 可选，本站代理每IP每小时上限，默认200 | 不返回 |

也可由管理员在Web目录逐项运行 `npx wrangler secret put 环境项名称` 并在终端私下填写，避免将值写入命令历史/仓库。只填Web三项即可启用Web公共底图，Android项单独配置。未配置的端仍可立即使用外部地图。

4. 首次发布先执行远程D1迁移 `0009_public_map_usage.sql`，再发布Worker。`GET /api/config` 应显示对应webReady/androidReady，绝不能包含securityJsCode。Web公共Key可能出现在浏览器请求中，依赖高德域名限制保护；Android依赖签名/包名绑定，不能把这些标识当保密令牌。
5. 用户在“地图与导航”启用公共底图，仅需一次同意，无需输入Key。切换模式会重新初始化SDK；关闭嵌入地图后外部入口仍可用。个人Key优先或用户主动选择公共模式，不偷偷将个人Key转给公共代理。
6. 以真实Key检查底图/共享标记、定位、额度耗尽及两端系统权限。代码和模拟SDK通过不能代替真实认证。若高德SDK升级后改变内部认证路径，先依据官方文档审查，再调整白名单，不直接开放全REST代理。

## 公共代理与隐私边界

- 只接受GET、固定高德上游、项目公共Key；仅开放 `/v3/ias` 底图认证和 `/v3/assistant/coordinate/convert` 单点坐标转换，不代理搜索、逆地理编码、路线规划、自定义样式或任意URL。个人Key和客户端传入jscode会被拒绝。
- 检查浏览器Origin/Referer和跨站请求、限制回调/请求长度、拒绝重定向、上游超时和响应大小限制，并移除可能回显的服务端安全密钥。没有转发用户Cookie/Authorization。
- D1原子计数限制IP/小时及站点/日；IP仅保留每日HMAC摘要，不存原始IP、硬件指纹或定位坐标，清理之前日期。达到代理限额返回429并提示外部地图。共享出口可能共用限额。
- 这些限额仅覆盖**本站代理请求**。SDK脚本、瓦片以及Android SDK直接请求高德，不能由本站代理完全控制；公共Key也不是不可复制的秘密。平台的域名/签名、额度监控、计费及停用是必要的另一层控制。
- 公共嵌入地图的坐标转换请求会瞬时经过本站并转发高德，不存入D1/公开记录。外部位置查看使用设备原始GPS/WGS84，在用户选择后直接交给外部地图，不发送本站。设备位置不自动当投稿位置。
- 公开地点仍为GCJ-02；高德参数明确 `coordinate=gaode`，百度明确 `coord_type=gcj02`。外部原始GPS定位则分别使用WGS84参数。不会用非官方逆转换把坐标强塞给其他底图。

## 导航兼容性

| 入口 | 本轮实现 | 限制与页面提示 |
|---|---|---|
| 高德 | Web/Android官方HTTPS路线URI，移动端尝试唤起应用 | 客户端/浏览器可能阻止，支持网页降级，电脑请选起点 |
| 百度 | Web官方HTTPS路线URI；Android先尝试客户端scheme，失败转网页 | 明确GCJ-02，需核对起终点及定位权限 |
| 查看设备位置 | 两端官方marker入口，原始GPS注明WGS84 | 定位拒绝/超时可直接打开地图首页，由地图自己定位 |
| 腾讯、苹果、Google等 | 复制店名/地址到对应软件搜索 | 未完成其全平台协议与真机验证，不宣称直接精确导航支持 |
| 微信/QQ等内置浏览器 | 提示改用系统浏览器 | 客户端唤起可能被拦截，无法由本项目保证绕过 |

详情和设置页都能打开兼容性说明；不会展示一个无法确认正确坐标或必需参数的“已支持”入口。

## 官方依据

- [高德JSAPI安全密钥及服务端代理](https://lbs.amap.com/api/javascript-api-v2/guide/abc/jscode)：推荐securityJsCode留在服务器、SDK设置serviceHost。
- [高德URI入门/标点/WGS84](https://lbs.amap.com/api/uri-api/gettingstarted)、[路线URI](https://lbs.amap.com/api/uri-api/guide/travel/route)：官方产品链接及移动端限制。
- [百度Web URI](https://lbs.baidu.com/docs/webapi?title=mapadjustment/uri/web)、[百度Android URI](https://lbs.baidu.com/docs/webapi?title=mapadjustment/uri/andriod)：经纬顺序、coord_type、路线/marker、src。
- [腾讯Web URI](https://lbs.qq.com/webApi/uriV1/uriGuide/uriWebGuide)：存在独立产品调用接口；本轮未把未验证的参数/客户端兼容性当成完成支持。

## 变更与验证

Web：`src/public-map.js`、`worker.js`、`migrations/0009_public_map_usage.sql`；`public/navigation.js`、`app.js`、`index.html`、`style.css`、教程MD/HTML；`test/public-map.test.mjs`、`ui-ci.mjs`、`.dev.vars.example`、README。

Android：`Navigation.kt`、`MainActivity.kt`、`MapSettingsActivity.kt`、内置教程HTML。文档：本文件、CREDENTIALS、README及最终验证记录。

本地只做语法、文本、配置/HTML/XML和差异静态检查，不执行本地编译、构建测试或完整冒烟。测试、打包和Android编译均在GitHub Actions；通过后按此前授权执行远程迁移及Workers发布。远程结果和逐项验收在完成后追加；真实公共Key未提供时明确标注未配置，不虚报已启用。
