# 2026-11-01 重构与验收

## 实现说明

使用 `.skills/ark-ui-skill-main/SKILL.md`，family=ark、depth=maximal；证据为 source-ledger 的 Ark 黑色导航、青色活动态、全屏舞台和边缘操作。原创代码、系统字体，无游戏素材。Web 重构目录轨、地图舞台、地点档案、投稿、设置、指引；Android 扩展原生 ArkUi、横屏分栏/底部导航、档案、设置、反馈。选择、筛选和任务状态驱动界面；支持减少动画。未经截图/真机验证，不能仅凭属性声明 maximal 视觉验收通过。

分类配置 `web/public/categories.json`：后街、前街、餐厅，餐厅含多种子分类。修改配置后部署资产，无需改业务代码；Web 每分钟读取（投稿时延后），Android 启动或“更多→刷新”读取。服务端验证错误配置并回退安全默认。历史分类不丢失。热更改需要发布配置资产，不等于免部署编辑。

地点白名单保存分类、坐标、描述、可选优缺点/菜单价格、媒体引用；评价独立表按稳定地点 ID 关联，公开分页。Key 不进入共享数据。撤回需匹配 user_id；共享密码按设备隔离身份，清除身份后需联系管理员。墓碑阻止旧 PR 重新发布，公开 Git 历史和他人副本不能自动删除。

防刷：服务端 D1 两条 UNIQUE 约束，对每地点、北京时间日期分别限制 IP 和随机设备 ID，只保存每日 HMAC，不采硬件指纹。共享网络可能误限，换网络且清数据可绕过，后续可接 Turnstile/账号信誉。摘要随评价保留。

图标已用内置 image_gen 生成，提示词见 `web/public/icons/README.md`；Web 192/512 PNG，Android 五种 mipmap 已接入。工具无模型选择/证明，指定 image2.5 未通过，需要对应模型渠道后替换。

## 部署排查与用户操作

两个 Git 仓库：主仓库和 web 子模块。只推主仓库不能触发绑定 Web 独立仓库的部署。历史 Web Actions 41046091322 等均 skipped，因为 DEPLOY_WITH_ACTIONS 未启用；本轮改为 push main 必触发并显式检查凭据。

当前是 Workers + assets + D1，不是纯 Pages：Worker 名 guet-food-share；独立 Web 仓库根目录 `/`，主仓库若绑定则根目录 `web`；生产分支 main；静态目录 public；入口 src/worker.js；没有 dist，无需前端编译。部署命令 `npm ci && npm run db:remote && npm run deploy`。先迁移再部署新 API。不要仅上传 public 丢失后端。

Web 仓库 Settings→Secrets and variables→Actions 添加 CLOUDFLARE_API_TOKEN Secret（目标账户 Workers Scripts Edit、D1 Edit，域名部署按错误补充 Zone Read），CLOUDFLARE_ACCOUNT_ID Variable 已由本轮设置；然后 Actions→Workers 部署→Run workflow。勿上传个人地图 Key。

如果用 Cloudflare Git 集成：Settings→Builds 核对仓库、main、根目录、Worker 名、自动构建开关、部署命令；GitHub 已安装 Cloudflare App 必须授权 Web 仓库；手动 Retry/首次部署并看日志。推荐 Actions 作为单一部署入口，避免双重发布。官方依据：https://developers.cloudflare.com/workers/ci-cd/builds/configuration/ 。

最初直接读取旧 OAuth 调用 /accounts 返回 403；用户随后明确授权本地 Workers 推送，Wrangler 刷新凭据后远程迁移、发布均成功。账户枚举现在可用，但 Workers Builds 的 triggers/builds API 仍为 403（10000/12004），不能检查或修改 Git 集成。已为 Web GitHub 仓库设置 CLOUDFLARE_ACCOUNT_ID 变量，仍缺 CLOUDFLARE_API_TOKEN Secret；自动部署未通过。现有 OAuth 不作为长期 CI Secret 保存。补齐 Secret 后重跑部署任务；如要直接修复 Workers Builds，需要用户级 Workers Builds Configuration Edit 权限。官方说明：https://developers.cloudflare.com/workers/ci-cd/builds/api-reference/ 。

## 验证策略与文件清单

本地检查只执行 node --check（语法）、XML/JSON、文本和 diff 检查，未执行本地编译测试、构建测试、npm test、Wrangler dry-run 或完整冒烟；后续按用户追加授权执行发布所需打包，详见最终检测记录。Web 单测/Worker 打包交 Actions，Android 编译交 Android CI。高德真实认证需个人 Key 和 Android 真机；未虚报联调成功。

Web 改动：public/index.html、style.css、app.js、categories.json、manifest.webmanifest、icons/*、amap-guide.md/html；src/categories.js、reviews.js、validation.js、places.js、password-auth.js、worker.js；migrations/0007_reviews_deletions.sql；test/community.test.mjs、password.test.mjs、smoke.mjs；.github/workflows/check.yml、deploy.yml。

主仓库：android/app/src/main/java/com/guet/foodshare/MainActivity.kt、AndroidManifest.xml、res/mipmap-*、assets/amap-guide.html；scripts/process-submission.mjs、publish-places.mjs、validate-places.mjs；.github/workflows/android-check.yml；README.md、本文、web 子模块指针。

## 最终检测记录

- Web 最终代码 c40021f（包含教程 canonical redirect 修复）；主仓库代码 9afb30c，后续仅补验收文档。
- Web Actions：https://github.com/zxymiku/guet-food-share-web/actions/runs/41319993541 。单测 23 项、Worker dry-run 打包、重点界面检查均在 GitHub 上执行。前一版 41319639233 已成功并产出桌面/竖屏四张截图，已下载静态查看，未运行本地界面程序。
- Android Actions：https://github.com/zxymiku/guet-food-share/actions/runs/41319999009 。前一版 41319643976 成功编译 debug APK；未将 debug 工件冒称正式签名 Release。Android 真机与真实高德 Key 联调未执行。
- 部署 Actions：https://github.com/zxymiku/guet-food-share-web/actions/runs/41319993700 。凭据检查失败：缺 CLOUDFLARE_API_TOKEN；本轮较早任务还缺 Account ID，已补变量。自动部署未通过，不用手动发布代替这个结论。
- 用户追加授权后，本地执行 `npm run db:remote`，对 Cloudflare 远程 D1 应用 0006、0007 两条迁移成功；执行 `npm run deploy` 成功。最终 Cloudflare Worker 版本 **1298865f-8903-4863-99e9-41b993f0fa8b**，对应 Web c40021f；域名 https://food.zxymiku.top 。
- 线上只读部署检查：首页、/api/categories、/api/places?limit=1、申请教程均 200；分类实际为后街/前街/餐厅。教程 .html 自动跳转 /amap-guide，最终响应 X-Frame-Options=SAMEORIGIN，其余页面 DENY。未向生产写测试地点或评价。目前公开地点为空，不能以空列表证明真实高德标记联调成功。
- 最初 Web CI 旧测试缺 0007 迁移而失败，修复测试迁移；Android setup-android 默认安装已撤销 tools 包失败，改显式 SDK 35 包后成功。界面 CI 使用隔离 API fixture，不作为生产数据或真实地图认证证明。
- 本地没有运行编译测试、构建测试、npm test 或完整冒烟。用户后续授权的 Wrangler 发布会打包代码，这一发布操作作为原限制的明确例外记录；没有将本地打包输出当作测试通过依据。

## 逐项验收

“通过”仅表示列出的静态/CI 检查通过，不扩展为未经执行的 Android 真机或高德认证证明。

| 需求项 | 实现位置 | 验证方式 | 通过/未通过 | 备注 |
|---|---|---|---|---|
| 1 ark-ui-skill / Ark maximal | 仓库 skill、Web shell/CSS、Android ArkUi/MainActivity | 技能阅读、远程 Web 截图、Android CI | 未通过完整视觉验收 | 系统实现已完成，Android 真机视觉与 maximal 六轴复核待完成 |
| 2 双端高德教程与填写 | public/amap-guide.md、Web 设置、MapSettingsActivity | 静态内容、CI 设置交互、Android 编译 | 通过 | 包名/SHA1/平台注册/填写位置齐全，真实 Key 联调待测 |
| 3 README 渲染教程 | Web HTML/iframe、Android assets HTML | CI iframe + 线上 canonical 响应头 | 通过 | Cloudflare 扩展名跳转防嵌入已修复；Android 离线渲染复用 |
| 4 自申请/缴费/Key 不上传 | README 教程、设置文案、白名单 | 静态审查与 CI 凭据隔离 | 通过 | 用户直连高德认证，未提交个人 Key |
| 5 设置界面 | map-settings、MapSettingsActivity/SecureStore | Web CI、Android CI、存储回归 | 通过 | Web 本地/会话，Android 本机加密 |
| 6 初次指引五项 + 防刷 | guide-dialog、MainActivity.guide | Web CI 关闭/刷新/重开，Android 编译 | 通过 | 可跳过、可重开、不重复强制 |
| 7 共享数据读取与地图 | places API/分页、双端地图 | 501 条分页 CI，生产 API 200 | 未通过真实地图验收 | 数据契约与读取通过，生产无地点，待真实 Key 与已审地点验证 |
| 8 他人标记跨 Key 显示 | 稳定 ID/GCJ-02、双端 Marker | 凭据隔离/分页 CI、静态映射 | 未通过真实地图验收 | 已实现，不能将无 Key 的 fixture 当实测 |
| 9 分类热更改 | public/categories.json、/api/categories | 配置校验 CI、生产接口 | 通过 | 修改配置发布资产即可，无需改业务代码；Web 每分钟、Android 刷新读取 |
| 10 餐厅子分类 | categories children、双端表单 | 配置 CI、Android 编译 | 通过 | 多个子分类、可不指定、历史数据兼容 |
| 11 评分评价 | reviews.js/0007、双端详情 | D1 回归 CI、双端界面/编译 | 通过 | 公共接口分页；真实双端操作待真机 |
| 12 可选优缺点/菜单价格 | validation、双端表单/详情 | 白名单 CI、详情截图 | 通过 | 均非必填，缺省空值 |
| 13 服务端防刷 | reviews.js、D1 双 UNIQUE | 重复 IP、重复设备、跨地点 CI | 通过 | 北京时间每天一次；共享 IP 可能误限，有绕过边界说明 |
| 14 image2.5 图标 | Web icons/manifest、Android mipmap | 图像查看/资源检查/CI | 未通过指定模型要求 | 内置工具生成并接入，但工具未暴露模型选择与证明；需指定渠道后替换 |
| 15 Cloudflare 自动部署 | deploy.yml、GitHub 变量、Workers 配置 | Actions 失败日志、Builds API 403 | 未通过 | 手动生产发布成功；仍需 CLOUDFLARE_API_TOKEN Secret 或 Builds 配置权限 |
| 16 验证/提交/部署策略 | 两仓库提交、CI、本文 | GitHub 远程日志、Cloudflare 版本和线上响应 | 通过追加授权后的策略 | 未执行本地编译/构建测试/完整冒烟；用户授权后仅执行发布所需打包与远程迁移 |

## 尚需操作

1. Web 仓库添加长期 CLOUDFLARE_API_TOKEN Secret 后，重跑 Workers 部署 Actions，确认迁移与部署成功。Account ID 已设置，无需重复填写；不要粘贴个人地图 Key。
2. 若坚持用已绑定的 Cloudflare Git 集成，提供/自行使用 Workers Builds Configuration Edit 权限，核对 main、Web 独立仓库根目录、部署命令和 Cloudflare GitHub App 仓库授权；触发首次构建。当前没有权限证据证明是哪一个开关错误，不能臆断。
3. 用自己的高德 Key，在 Android 真机和 Web 发布一条经审核的真实地点，交叉查看标记与评分；完成 Android Ark maximal 视觉验收。无需把 Key 交给项目。
4. 有可明确指定 image2.5 的模型渠道后，重新生成并替换已接入的图标资源。

