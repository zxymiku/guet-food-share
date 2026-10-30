# 桂电美食分享 · 验证记录

2026-10-30，本轮按要求减少验证频率，在关键修改完成后集中执行。

- Web 14 项原有校验与权限测试通过；另对无需运行时 token 读取公开 Release 的修复单独执行 1 项针对性测试，通过。
- 隔离 D1 + 模拟 GitHub 集成通过：加密图片上传/解密、跨分块视频 Range、未审核媒体隔离、加密草稿、本人媒体归属、审核发布幂等、OAuth/PKCE 和审核员权限。
- Workers dry-run 打包通过，所有工作流 YAML 与 Android XML 解析通过。
- 实际创建三个公开 GitHub 仓库；Web 与媒体初始代码已上传。
- 实际部署 Worker 与 D1 迁移，公开服务地址为 https://food.zxymiku.top 。
- Android 签名已创建并保存在 GitHub encrypted secrets，本机凭据备份以 Windows DPAPI 加密。
- Android 已在 GitHub Actions 完成正式签名构建并发布 android-v1.0.4（后续版本以 Releases/latest 为准）；首次构建的过时 SDK 包与 Kotlin 编译问题已修复。
- 已验证自定义域名 /api/config 与 /api/places 返回正常，正在同步后续 Android 版本使用自定义域名。
- 真实高德地图、逆地理编码、GitHub OAuth 和正式媒体上传尚待配置平台 Key、OAuth App 与受限服务端 GitHub token 后联调。
- Cloudflare API 的 Workers Builds 权限不足（403）。用户选择在控制台完成 Web 仓库绑定，绑定前不能声称每次提交已自动部署。

本轮集成测试均使用模拟 GitHub，不会往公开仓库上传测试照片或视频。
