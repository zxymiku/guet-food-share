# 桂电美食分享

面向桂林电子科技大学的美食位置分享项目，包含 Android 原生应用与 Web 版本。

| 仓库 | 作用 |
| --- | --- |
| [桂电美食分享](https://github.com/zxymiku/guet-food-share) | Android、每周发布、投稿规范化及审核 |
| [桂电美食分享 Web](https://github.com/zxymiku/guet-food-share-web) | Workers 页面与 API，绑定 Cloudflare 后 push 自动部署 |
| [桂电美食分享媒体](https://github.com/zxymiku/guet-food-share-media) | 图片、视频的加密 Releases 附件 |

三个仓库均公开。GitHub 仓库名采用兼容平台的 ASCII 标识 `guet-food-share`，界面、应用名、Gradle 项目名和文档名统一为“桂电美食分享”。Android 包名为 `com.guet.foodshare`。

## 代码布局

- `android/`：Kotlin 原生地图、定位填表、图片/视频、GitHub 登录、静默版本检查与下载更新。
- `web/`：独立 Web 仓库的 Git submodule，同步保留本地两个平台文件夹。
- `.github/workflows/`：投稿整理、人工审核后发布、Android 定时/手动构建 Release。
- `data/places/`：规范化投稿 JSON；公开 PR 由人工审核后合并。
- `scripts/`、`docs/`：自动化脚本与配置说明。

```powershell
git clone --recurse-submodules https://github.com/zxymiku/guet-food-share.git
```

Web 更新直接在 `web/` 内 commit、push 到独立仓库 main；完成 Cloudflare 的一次绑定后即自动部署。主仓库可再提交 Web 子模块版本指针。不是两份需要反复复制的 Web 源码。

## 功能

- 高德地图选点、导航、搜索与分类；默认展示桂林，实际地点由用户分享，不预置虚构门店。
- 定位获授权后填写 GCJ-02 坐标、逆地理编码地址，建议附近店名；地图选点也可解析地址，提交前请核对。
- 必填门店名、地址、地图位置和公开授权；可选推荐理由、前往方法、6 张图片与 2 个视频。
- 媒体分块加密上传到 GitHub Releases，D1 只存元数据与加密草稿，不使用 R2 存储新媒体。
- 用户登录 → 自动规范化 → 公开审核 PR → 人工合并 → 发布至地图。待审媒体限投稿者/审核员访问。
- 双端共用共享密码：无需注册 GitHub 即可查看、上传美食并自动反馈，不能删除、修改或审核记录。管理员在 web 目录运行 `npm run password:setup` 设置密码。
- 双平台意见反馈：密码用户由管理员账号代发；GitHub App 登录用户由自己的账号发布。反馈只投递到对应平台仓库，权限限定为 Issues。
- Android 每周一北京时间 10:00 构建最新代码、签名并发布 GitHub Release，也可手动触发。GitHub 调度可能延迟。
- Android 启动检查最新 Release；当前已是最新版时没有弹窗或提醒。新版本点击更新后下载并校验 SHA-256、包名、版本号与签名，再进入系统安装确认界面。

## 运行与配置

详细见 [全部凭据申请与最小权限指南](docs/CREDENTIALS.md)、[部署说明](docs/DEPLOYMENT.md) 与 [Web 说明](web/README.md)。

```powershell
cd web
npm ci
Copy-Item .dev.vars.example .dev.vars
# 先设置随机 DATA_ENCRYPTION_KEY
npm run db:local
npm run dev
```

高德 Key、GitHub App 和专用受限 GitHub 服务端 token 需要配置后才能使用真实地图、登录、媒体上传及投稿投递。本机 CLI 登录只用于创建/上传仓库，没有被复制为应用服务端凭据。

[下载最新 Android APK](https://food.zxymiku.top/download/android) · [打开 Web](https://food.zxymiku.top)

## Android 发布与更新

仓库 Actions 中选择“桂电美食分享 · Android 发布”→ Run workflow。正式签名在 GitHub encrypted secrets 中；本机签名备份放在被 Git 忽略的 `.secrets/`，密码备份使用 Windows DPAPI 加密，不上传仓库。不要删除签名备份或随意替换签名，否则既有安装无法直接升级。

正式环境需设置主仓库变量 `API_BASE_URL`、Secret `AMAP_ANDROID_KEY`。签名 Secrets 为 `ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`。Android 安装权限与最终安装操作由用户在系统界面确认，应用不会绕过该流程。

## 验证频率

全套 Web 测试改为手动运行；不在每次代码推送时重复执行。只有数据 PR 做轻量格式校验；Android 按每周或手动构建发布。验证记录见 [docs/VERIFICATION.md](docs/VERIFICATION.md)。

首版暂未接入拒稿通知、用户删除/撤回与媒体清理后台；公开列表最多返回最新 500 个地点。上传的文字和坐标会进入公开审核 PR，媒体密文进入公开仓库；公开授权提示已写入两端表单。GitHub Releases 不是无限容量或无限流量的承诺，后续规模增长可替换媒体后端。
