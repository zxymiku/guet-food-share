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

Web 仓库 Settings→Secrets and variables→Actions 添加 CLOUDFLARE_API_TOKEN Secret（目标账户 Workers Scripts Edit、D1 Edit，域名部署按错误补充 Zone Read），添加 CLOUDFLARE_ACCOUNT_ID Variable；然后 Actions→Workers 部署→Run workflow。勿上传个人地图 Key。

如果用 Cloudflare Git 集成：Settings→Builds 核对仓库、main、根目录、Worker 名、自动构建开关、部署命令；GitHub 已安装 Cloudflare App 必须授权 Web 仓库；手动 Retry/首次部署并看日志。推荐 Actions 作为单一部署入口，避免双重发布。官方依据：https://developers.cloudflare.com/workers/ci-cd/builds/configuration/ 。

本机 Cloudflare OAuth 调用 /accounts 返回 HTTP 403，无法查看控制台 Git 集成与日志；Web 仓库 Secret/Variable 列表为空。需要所有者补凭据，不能声称账户配置或生产部署通过。查看最新 Actions 失败步骤，补齐配置后手动重跑；Cloudflare 部署记录必须对应提交 SHA。

## 验证策略与文件清单

本地只执行 node --check（语法）、XML/JSON、文本和 diff 检查，未执行本地编译、构建、npm test、Wrangler dry-run 或完整冒烟。Web 单测/Worker 打包交 Actions，Android 编译交 Android CI。高德真实认证需个人 Key 和 Android 真机；未虚报联调成功。

Web 改动：public/index.html、style.css、app.js、categories.json、manifest.webmanifest、icons/*、amap-guide.md/html；src/categories.js、reviews.js、validation.js、places.js、password-auth.js、worker.js；migrations/0007_reviews_deletions.sql；test/community.test.mjs、password.test.mjs、smoke.mjs；.github/workflows/check.yml、deploy.yml。

主仓库：android/app/src/main/java/com/guet/foodshare/MainActivity.kt、AndroidManifest.xml、res/mipmap-*、assets/amap-guide.html；scripts/process-submission.mjs、publish-places.mjs、validate-places.mjs；.github/workflows/android-check.yml；README.md、本文、web 子模块指针。

远程检测与验收表在后续记录。
