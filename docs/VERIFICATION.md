# 桂电美食分享 · 验证记录

2026-10-30，本轮按要求减少验证频率，在关键修改完成后集中执行。

- Web 14 项原有校验与权限测试通过；另对无需运行时 token 读取公开 Release 的修复单独执行 1 项针对性测试，通过。
- 隔离 D1 + 模拟 GitHub 集成通过：加密图片上传/解密、跨分块视频 Range、未审核媒体隔离、加密草稿、本人媒体归属、审核发布幂等、OAuth/PKCE 和审核员权限。
- Workers dry-run 打包通过，所有工作流 YAML 与 Android XML 解析通过。
- 实际创建三个公开 GitHub 仓库；Web 与媒体初始代码已上传。
- 实际部署 Worker 与 D1 迁移，公开服务地址为 https://food.zxymiku.top 。
- Android 签名已创建并保存在 GitHub encrypted secrets，本机凭据备份以 Windows DPAPI 加密。
- Android 已在 GitHub Actions 完成正式签名构建并发布 android-v1.0.5（后续版本以 Releases/latest 为准）；首次构建的过时 SDK 包与 Kotlin 编译问题已修复。构建链接：https://github.com/zxymiku/guet-food-share/actions/runs/41020775013 。
- 已验证自定义域名 /api/config 与 /api/places 返回正常，Android 1.0.5 已使用自定义域名；线上 /api/app-update 返回 available=true、versionCode=1005 与对应下载路径。
- 真实高德地图、逆地理编码、GitHub OAuth 和正式媒体上传尚待配置平台 Key、OAuth App 与受限服务端 GitHub token 后联调。
- Cloudflare API 的 Workers Builds 权限不足（403）。用户选择在控制台完成 Web 仓库绑定，绑定前不能声称每次提交已自动部署。

本轮集成测试均使用模拟 GitHub，不会往公开仓库上传测试照片或视频。

更新检查已使用公开 Releases/latest/download/update.json，规避 GitHub 匿名 REST 配额；最新清单和安装包无须用户 GitHub token。媒体上传和投稿投递仍需专用服务端 token。

## 双平台反馈功能

新增一次针对性集成验证通过：Web/Android 分流到各自仓库、未登录/未授权拒绝、他人截图拒绝、截图专用随机链接可显示但普通图片地址仍私有、草稿加密、明确失败后截图链接撤销，以及超时恢复不重复创建 Issue。使用临时 D1 与模拟 GitHub，没有向公开仓库发测试 Issue。Web JavaScript 语法与 Wrangler dry-run 打包通过；本轮未重复执行原有全套测试。

Android 反馈版本 1.0.6 已完成签名构建并发布： https://github.com/zxymiku/guet-food-share/releases/tag/android-v1.0.6 。构建工作流：https://github.com/zxymiku/guet-food-share/actions/runs/41022277851 。真实自动创建 Issue 仍需 Worker 端配置具有两个目标仓库 Issues write 权限的专用 token，并完成 GitHub OAuth 登录配置。

Web 反馈迁移和 Workers 部署已完成；线上 HTML 包含反馈表单，配置接口返回 Web/Android 的正确 Issues 地址。当前 automaticReady=false、screenshotsReady=false，需配置 GitHub 登录与服务端 Issues/媒体上传权限后才能真实自动发布。浏览器交互验收工具连接超时，本轮未完成页面交互验收；没有为此重复运行旧测试。

## 共享密码、反馈作者与最小权限（当前版本）

- 密码登录支持 Web/Android 共用同一验证器，无需注册 GitHub；可上传、查看和提交反馈，不可改删、审核或管理。会话按设备隔离，密码轮换使旧会话失效。
- 集中验证通过：密码验证、设备身份恢复、错误限流、无明文泄露、禁止越权删除/改写/审核、读稿/发布密钥分离；密码反馈使用管理员 Token，GitHub 反馈使用用户自己的、按平台保存的 App Token，失效不会代发；加密保存与刷新已覆盖。
- 媒体/OAuth/PKCE 的隔离集成检查及 Worker 打包通过。未每步重复运行旧的完整测试。
- Android 1.0.7 已签名发布，包含密码登录与域名限制：https://github.com/zxymiku/guet-food-share/releases/tag/android-v1.0.7 。
- Workers 生产入口已只保留 food.zxymiku.top，关闭 workers.dev 和预览 URL；Android 基础地址固定为自己的域名。
- 已为生产生成并分别配置 INTAKE_SECRET / PUBLISH_SECRET；发布密钥仅放主仓库 production Environment。实际接口权限检查通过，没有新增或发布测试记录；旧 Worker 与 GitHub 的 PIPELINE_SECRET 已撤销。
- 原密码由管理员通过交互命令自行设置，不生成公开默认密码。GitHub App、代发 PAT、媒体 PAT 等仍需按 CREDENTIALS.md 申请配置，未以本机 CLI Token 代替。
- 当前测试均使用隔离数据库和模拟 GitHub；未向公开仓库创建测试 Issue，也未声称已用真实 GitHub App 完成联调。
