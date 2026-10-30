# 桂电美食分享 · 部署说明

## 当前仓库与 Worker

- 主项目：`zxymiku/guet-food-share`
- Web：`zxymiku/guet-food-share-web`
- 媒体：`zxymiku/guet-food-share-media`
- Worker：`guet-food-share`
- 服务地址：`https://guet-food-share.zxymiku.workers.dev`

所有仓库公开。Web 是主项目的 submodule；主项目工作流读取 Web 内的共享规范化函数时会检出 submodule。

## Cloudflare 自动更新：需在控制台完成一次绑定

用户选择使用 Workers Builds 的 GitHub 绑定。现有 Wrangler OAuth 没有 Workers Builds Configuration 权限，绑定 API 返回 403，因此没有冒充已完成远程绑定。

打开 Cloudflare Worker guet-food-share → Settings → Builds → Connect repository → 选择 zxymiku/guet-food-share-web → main。Root `/`，构建命令留空，部署命令 `npm run db:remote && npm run deploy`，Node 22/24。完成后 Web 仓库 main 的每次新提交会自动部署。运行时 Secrets 留在 Worker 中，不写进仓库。

[Cloudflare 控制台](https://dash.cloudflare.com/38340ce71cb263fbafc3c9b1bbdd6801/workers/services/view/guet-food-share/production/settings)

已有 D1 仅保存索引、小型 JSON 和加密草稿；不新建 R2。migrations 会创建初始表与 media 索引表。

## Cloudflare Secrets

| 名称 | 用途 |
| --- | --- |
| DATA_ENCRYPTION_KEY | 草稿、会话与媒体的 AES-256-GCM 根密钥；不同用途用 HKDF 派生不同密钥 |
| PIPELINE_SECRET | GitHub Actions 拉稿/发布回调鉴权，与主仓库 Secret 同值 |
| GITHUB_TOKEN | 专用细粒度 token，仅授权主项目 Contents write、媒体仓库 Contents write |
| AMAP_SECURITY_JS_CODE | 高德 JSAPI 安全码，只在代理使用 |
| AMAP_REST_KEY | 高德 Web 服务 Key，逆地理编码地址与附近 POI |
| GITHUB_CLIENT_SECRET | GitHub OAuth App Secret |

普通 vars：APP_ORIGIN、AMAP_WEB_KEY、GITHUB_CLIENT_ID、GITHUB_REPO、MEDIA_REPO、REVIEWER_GITHUB_IDS。审核员数字 ID 当前为仓库拥有者 95949338。

本机 GitHub CLI 已登录，不代表应用可以永久使用该账号的全仓库 token。代码不会把 CLI token 写入 Worker 或 Android。请创建专用受限凭据并作为 Worker secret 设置。高德平台 Key 仍需你申请；Web JSAPI、Web 服务和 Android Key 是不同类型。

OAuth App 的 Homepage 为正式 Origin，Callback 为 `https://guet-food-share.zxymiku.workers.dev/auth/callback`，scope 为 read:user。Android 使用系统浏览器、一次性代码和 PKCE，回调为 guetfood://login。

## GitHub 审核自动化

主仓库变量 WORKER_URL 为正式 Origin；Secret PIPELINE_SECRET 与 Worker 一致。normalize 工作流默认使用本仓库 GITHUB_TOKEN 创建审核 PR，需要在 Settings → Actions → General 允许工作流创建 PR。也可配置 SUBMISSION_BOT_TOKEN 替代。GitHub_TOKEN 创建的 PR 不会触发新的 Actions；规范化脚本自身已经进行同样的数据校验，人工仍需检查内容。

保护 main 要求 PR 与人工批准，并避免给机器人绕过权限。不要给数据审核工作流检出并执行待审分支中的不可信代码。发布任务只运行 main 已合并代码，经 PIPELINE_SECRET 调用 Worker，并核对原始投稿内容。若修改实际店名、位置等内容，应让投稿者重新提交。

审核员在 GitHub PR 的 review.html 链接查看文字、位置、图片和视频。媒体未公开时只有投稿者及审核员可解密；文字和坐标因公开 PR 会在合并前可见，表单明确告知。

## 媒体

公开媒体仓库的 Releases 保存 `.gefm` 密文，AES-256-GCM 分块加密，每块 256 KiB、独立 IV，附加认证数据绑定媒体 ID、分块序号和原始长度，HKDF 为每个媒体派生密钥。D1 的 asset ID/类型/大小索引也加密。修改密钥会影响历史解密，必须备份并做迁移后再轮换。

图片最大 8 MB、最多 6 张；视频 MP4/WebM 最大 50 MB、最多 2 个；每用户每日 30 个媒体附件和 10 次投稿，属于基础应用限制。媒体按日 Release 分桶，900 附件后另起分桶。照片客户端去 EXIF；视频加密但不会自动删除其元数据。视频经公开审核后属于公开内容，不承诺 DRM。

播放支持单段 Range；首版 seek 从密文起点顺序解密，较大视频存在额外带宽和延迟。GitHub 下载或 API 配额不足时会显示错误，后续可替换为专用媒体服务。

## Android 每周发布

`.github/workflows/android-release.yml` 使用 cron `0 2 * * 1`，即北京时间周一 10:00；支持 Run workflow 手动发布。源码 push 本身不触发反复编译，符合减少测试/构建频率的要求。

主仓库 Secrets：ANDROID_KEYSTORE_BASE64、ANDROID_KEYSTORE_PASSWORD、ANDROID_KEY_ALIAS，以及 AMAP_ANDROID_KEY。变量 API_BASE_URL 指向 Worker。版本号基于工作流 run_number，versionCode 为 1000 + run_number；工作流名称/历史不要随意重建，以免版本号倒退。更大版本使用人工提高基数迁移。

每个 Release 包含签名 APK 和 update.json（包名、版本号、长度和 SHA-256）。应用启动调用 /api/app-update；最新版和网络失败保持安静，有新版本才提醒。点击更新流式下载至私有缓存，校验哈希、包名、版本和签名，再请求系统安装。若系统尚未允许本应用安装更新，会引导用户授权，返回后继续。不可静默安装，也不能用不同证书直接覆盖升级。

本机 `.secrets/guet-food-share-signing.p12` 是密码保护的签名备份，`.secrets/credentials.dpapi` 用 Windows DPAPI 保护密码与服务端随机密钥。它们被 Git 忽略，不会进入任一公开仓库。请自行保留备份并将高德 Android Key 绑定正式签名 SHA1。

## 按需验证

Web 仓库 Check 工作流仅手动。主仓库只给数据 PR 做格式检查。完整本地检查在关键改动完成后集中进行，不在开发每一步重复运行。

参考：[Workers GitHub 绑定](https://developers.cloudflare.com/workers/ci-cd/builds/git-integration/github-integration/)、[Builds API 权限](https://developers.cloudflare.com/workers/ci-cd/builds/api-reference/)、[GitHub Releases 限制](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)、[Android 安装确认](https://developer.android.com/reference/android/content/pm/PackageInstaller)。
