# 桂电美食分享 · 部署说明

完整的逐项申请步骤、最小权限、共享密码设置及轮换方法见 [全部凭据申请指南](CREDENTIALS.md)。以该文档为准；旧版合并 Token 和传统 OAuth App 配置不再推荐。

## 生产入口

- 唯一应用域名：https://food.zxymiku.top
- Worker：guet-food-share；D1：guet-food-share，仅保存索引与加密信息。
- 主项目：zxymiku/guet-food-share；Web：zxymiku/guet-food-share-web；媒体：zxymiku/guet-food-share-media。
- workers.dev 和预览入口已关闭，不作为连接或下载失败后的备用地址。Android API_BASE_URL 固定为项目域名。

## 推荐配置顺序

1. 在 Worker 绑定 DB、ASSETS，配置 DATA_ENCRYPTION_KEY、APP_ORIGIN 和生产 DEV_MODE=false。
2. 申请 MEDIA_GITHUB_TOKEN（仅媒体仓库 Contents write）与 DISPATCH_GITHUB_TOKEN（仅主仓库 Contents write）。
3. 设置 INTAKE_SECRET（只读待审稿）和 PUBLISH_SECRET（只发布），分别匹配主仓库 repository secret 和 production Environment secret。
4. 在 web 目录执行 npm run password:setup，设置同一个双端共享密码，再私下分发原密码。
5. 设置 FEEDBACK_GITHUB_TOKEN（你的账号，只选主/Web 两仓库 Issues write），密码用户即可由你代发反馈。
6. 注册公开 GitHub App，仅开 Issues write、安装到主/Web 两仓库，配置 Client ID/Secret 与两个仓库数字 ID。GitHub 用户反馈使用自己的账号。
7. 将个人高德申请教程提供给用户；用户在 Web/Android 的“地图 API”中自行填写，不再在 Worker 或构建时设置公共 Key。
8. 绑定 Web 仓库到 Workers Builds，或选择备用 GitHub Actions 部署，二者不要同时启用。
9. 核对主仓库 Android 签名 Secrets、API_BASE_URL 后，手动运行发布工作流。

## 自动化

Workers Builds：Cloudflare → Workers & Pages → guet-food-share → Settings → Builds → Connect repository，仅选 zxymiku/guet-food-share-web，main 分支，根目录 `/`，部署命令 `npm run db:remote && npm run deploy`。

现有 Wrangler OAuth 没有 Builds Configuration 管理权限，首次仓库连接需在控制台完成；不能将代码已部署误认为仓库绑定已完成。

主仓库 normalize 工作流使用 INTAKE_SECRET 拉稿并以临时 GITHUB_TOKEN 创建审核 PR。人审合并 main 后，production 环境的 PUBLISH_SECRET 才用于回调发布。两把密钥不能互换，客户端不持有它们。

Android 每周一北京时间 10:00 自动签名构建与发布，也支持 Run workflow。用户登录凭据、服务端 GitHub Token 不会写进 APK；应用启动从自己的域名获取更新，有新版才提醒并进入系统安装确认。

## 权限与验证

共享密码可看、可上传、可反馈，不可删、改、审核、管理。密码更换使旧密码会话失效。GitHub 用户的 App 令牌按平台与对应仓库保存，加密后留在 D1，过期可刷新；失效要求重新登录，不回退到管理员代发。

本轮仅集中验证登录、角色、反馈作者与权限边界，不在每一步重复运行旧测试。记录见 [VERIFICATION.md](VERIFICATION.md)。

## 个人地图版本升级

部署前运行 D1 迁移，新增 `0006_places_pagination.sql` 为稳定分页建立索引，无需改写既有地点。客户端自动请求后续页面，公开 API 不需要地图 Key。旧 `/api/location` 与 `/_AMapService/` 返回 410 和迁移提示；应一起发布新版 Web 与 Android，旧客户端地址解析将不可用，仍可手填。

教程源码在 `web/public/amap-guide.md`。Web 内运行 `npm run guide:build`，主仓库运行 `node scripts/sync-map-guide.mjs` 同步生成 Android 离线 HTML；生成物随代码发布。源码修改后应重新生成。
