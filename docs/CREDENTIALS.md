# 桂电美食分享：全部凭据申请、配置与最小权限指南

本文对应当前源码。生产站点统一使用 **https://food.zxymiku.top**；Web、Android、登录回调、截图与安装包下载均使用这个应用域名。已关闭 workers.dev 与预览入口。无需访问 GitHub 的 Android 最新安装包入口：https://food.zxymiku.top/download/android 。只有用户主动选择 GitHub 登录/手动反馈时跳转 GitHub；地图 SDK 使用高德自己的服务。

## 1. 先确定要启用哪些功能

| 功能 | 需要什么 | 不需要什么 |
| --- | --- | --- |
| 查看公开美食 | 已部署的 Worker 和 D1 | 个人 GitHub 账号、共享密码 |
| 共享密码登录、提交美食 | PASSWORD_CREDENTIAL、DATA_ENCRYPTION_KEY | 用户注册 GitHub |
| 图片/视频上传 | MEDIA_GITHUB_TOKEN | 用户的仓库写权限 |
| 美食自动整理到审核 PR | DISPATCH_GITHUB_TOKEN、INTAKE_SECRET、Actions 临时 token | 普通用户的 GitHub Token |
| 合并审核 PR 后公开 | PUBLISH_SECRET | 密码用户的审核/删除权限 |
| 密码用户自动反馈 | FEEDBACK_GITHUB_TOKEN | 用户自己的 GitHub 账号；Issue 由管理员账号代发 |
| GitHub 用户自动反馈 | GitHub App Client ID/Secret 和用户授权 | 管理员代发；Issue 使用当前登录用户的账号 |
| 地图、坐标转地址 | 三种高德 Key/安全码 | 用户的 GitHub 凭据 |
| 每周签名发布 Android | 签名文件与签名密码、Actions 临时 token | Cloudflare 的部署 Token |
| Cloudflare 自动部署 | Workers Builds 绑定，或专用部署 Token（二选一） | Global API Key、全部 GitHub 仓库访问权 |

不要把 GitHub CLI 的个人全账号登录 Token 放入 Worker，也不要把管理员 Token、签名私钥、加密根密钥发给共享密码用户。

## 2. 两种登录身份及权限边界

### 2.1 共享密码身份

Web 和 Android 共用 Worker 中的**同一个** PASSWORD_CREDENTIAL。管理员设置原密码，再私下发给需要参与的人。用户不需要注册 GitHub。

允许：查看公开美食，上传图片/视频，提交美食，查看本浏览器/安装实例的投稿，定位填写，以及提交自动反馈。密码用户的反馈使用管理员账号，只能由服务端投递到固定的平台仓库。

不允许：修改或删除美食、图片、视频、反馈记录；审核或直接公开美食；访问管理员接口；取得任何 GitHub/Cloudflare 凭据。限制由服务端的接口允许列表执行，修改前端、伪造 role 字段或直接发送 DELETE/PUT/PATCH 也不能提升权限。

同一浏览器或 Android 安装实例有一个加密的匿名设备凭证，用于重新登录后找回自己的投稿。各人的身份不同；同一个共享密码不会让所有人看到彼此的待审稿。清除浏览器数据/卸载应用后会失去该设备身份，公开内容仍可查看。

### 2.2 GitHub 身份

使用 GitHub App 登录后，服务端加密保存该用户的 GitHub App user access token。自动反馈使用该用户的 Token，GitHub 显示该用户为 Issue 作者。不会在授权失效时偷偷改用管理员账号代发。

密码用户依然可以退出后切换为 GitHub 登录。审核权限仅授予 REVIEWER_GITHUB_IDS 中的 GitHub 数字 ID；密码身份不会因出现在这份列表中而成为审核员。

## 3. 设置、更换或关闭共享密码

### 推荐：交互命令，输入不回显

在自己电脑的交互终端运行：

```powershell
cd C:\dev\food\web
npm ci
npm run password:setup
```

该命令使用现有 Wrangler 管理员会话或专用配置 Token。若单独创建密码配置 Token，最小权限为目标 Worker 的 Workers Scripts Edit（改 Secret），不需 D1、KV、R2、DNS 或 GitHub 权限；创建与资源收窄方法见第 10 节。可通过终端环境的 CLOUDFLARE_API_TOKEN 提供，勿写进仓库。已有管理员 OAuth 会话仅作本机配置使用，不复制到生产或 CI。

按提示输入两次密码，12～128 字符，建议使用密码管理器生成至少 16 字符的随机密码。不要把密码直接写进命令参数、聊天、Issue 或源代码。密码区分大小写，不自动去除首尾空格。

命令会随机生成盐值，使用 PBKDF2-SHA256 生成验证器，并通过标准输入上传到 Worker Secret **PASSWORD_CREDENTIAL**。生产环境不保存原密码，也不把原密码打进日志。验证器也不要公开；知道验证器的人可尝试离线猜测，因此它同样放在 Secret 中。

如果只想在控制台配置：

```powershell
npm run password:setup -- --print
```

复制输出的整行验证器，打开 Cloudflare → Workers & Pages → guet-food-share → Settings → Variables and Secrets → Add，名称 PASSWORD_CREDENTIAL，类型 Secret。不要填原密码。验证器格式为 `pbkdf2-sha256$100000$盐值$哈希`；不要手工改其中的数字或美元符号。

本地开发：

```powershell
npm run password:setup -- --local
```

它只把验证器写入被 Git 忽略的 web/.dev.vars。生产 Secret 和本地文件是两份独立配置。

**更换密码**：再次运行设置命令。新验证器生效后，旧密码登录的应用会话立即失效；新密码登录后，同设备仍可找回自己的投稿。GitHub 登录会话不受这次更换影响。

**关闭密码入口**：在 Worker 设置中删除 PASSWORD_CREDENTIAL，或运行 `npx wrangler secret delete PASSWORD_CREDENTIAL` 并确认。旧密码会话随之失效。

密码会话最长 7 天。连续错误尝试受同一出口地址的 15 分钟窗口限制，超过 20 次暂时拒绝；成功登录会清除当前窗口计数。只保存带服务端密钥的地址摘要，不保存原始 IP。学校共享出口可能共用这个限制，遇到限流请等待窗口结束，不要连续重试。

当前使用 100000 次 PBKDF2，兼容 Workers 的实现限制；务必使用足够长的随机密码。PBKDF2 是密码验证散列，不是可还原的“密码加密”。[Workers Web Crypto](https://developers.cloudflare.com/workers/runtime-apis/web-crypto/)、[workerd 的迭代上限说明](https://github.com/cloudflare/workerd/issues/1346)。

## 4. 管理员账号代发反馈：FEEDBACK_GITHUB_TOKEN

由 **zxymiku 本人**创建，Issue 才会以你的账号代发。

1. 打开 [Fine-grained PAT 页面](https://github.com/settings/personal-access-tokens/new)。也可从 Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token 进入。
2. 名称可填“桂电美食分享-反馈代发”；Resource owner 选 zxymiku；建议有效期 30 天，按到期日提前轮换。
3. Repository access 选 **Only select repositories**，只选择：
   - zxymiku/guet-food-share（Android 反馈）
   - zxymiku/guet-food-share-web（Web 反馈）
4. Repository permissions 只设置 **Issues: Read and write**。Metadata 的只读权限由 GitHub 自动附带。Contents、Actions、Administration、Pull requests、其他账号权限全部不选。
5. 创建后只显示一次，存入密码管理器，再作为 Worker Secret **FEEDBACK_GITHUB_TOKEN** 保存。

```powershell
cd C:\dev\food\web
npx wrangler secret put FEEDBACK_GITHUB_TOKEN
```

GitHub 没有单独的“只能创建、不能修改 Issue”的 Token 权限，Issues write 是创建接口可申请的最小权限组。应用进一步只提供创建接口，并把平台映射固定为上面两仓库，不接受用户传入任意 owner/repo。密码客户端永远拿不到这枚 Token，也没有删除接口。

不要为了省事选择 classic PAT 的 repo/public_repo，不要选 All repositories，也不要使用你现有 CLI 的宽权限 Token。[GitHub 创建 Issue 所需权限](https://docs.github.com/en/rest/issues/issues#create-an-issue)。

## 5. GitHub 用户以自己的账号反馈：申请 GitHub App

**使用 GitHub App，不使用要求 public_repo 范围的传统 OAuth App。** GitHub App 可以只开 Issues 权限并只安装到指定仓库，用户 Token 的权限为 App 与用户权限的交集。[用户令牌与权限](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app)。

### 5.1 创建 App

打开 [GitHub App 注册页面](https://github.com/settings/apps/new)，填写：

| 项目 | 填什么 |
| --- | --- |
| GitHub App name | 桂电美食分享；若名称被占用，加账号后缀保持唯一 |
| Homepage URL | https://food.zxymiku.top |
| Callback URL | https://food.zxymiku.top/auth/callback |
| Expire user authorization tokens | 保持启用 |
| Device flow | 不启用；本应用使用系统浏览器授权 |
| Webhook Active | 不启用；当前代码没有 Webhook 接收接口 |
| Webhook Secret | 不创建，无须配置 |
| Repository permissions | 仅 Issues: Read and write；Metadata 自动只读 |
| Account / Organization permissions | 不选额外权限 |
| 其他仓库权限 | Contents、Actions、Administration、Pull requests 等不启用 |

因为要让校内其他 GitHub 用户登录，App 需要公开（Any account），而不是只能拥有者登录的私人 App。**公开 App 并不等于授权你的全部仓库**：下一步仍只把它安装到指定两个仓库。[GitHub App 可见性与登录限制](https://github.blog/changelog/2025-06-24-security-updates-for-apps-and-api-access/)。

### 5.2 安装到对应仓库

App 设置 → Install App → 你的 zxymiku 账号 → **Only select repositories**，只勾 guet-food-share 与 guet-food-share-web。不勾媒体仓库，也不选 All repositories。

普通用户点击应用里的 GitHub 登录即可授权身份，不需要为美食上传申请仓库写权限。不要引导他们把 App 安装到个人的其他项目。

### 5.3 保存 Client ID 和 Client secret

回到 App 的 General 页面：

- **Client ID** → Worker 普通变量 GITHUB_CLIENT_ID。
- Generate a new client secret → Worker Secret GITHUB_CLIENT_SECRET。
- 不要把 App ID 误填为 Client ID。
- 当前采用用户授权令牌，不采用 installation JWT；**不需要生成或上传 App 私钥**。

```powershell
npx wrangler secret put GITHUB_CLIENT_SECRET
```

当前仓库的数字 ID 已配置在 wrangler.jsonc，属于公开配置：

| 变量 | 当前值 |
| --- | --- |
| GITHUB_WEB_REPO_ID | 1439394807 |
| GITHUB_ANDROID_REPO_ID | 1439394710 |

服务端按平台交换 GitHub user token 时传入对应的 repository_id，进一步收窄该 Token。两平台分别保存授权；在 Web 登录不自动代替 Android 的 GitHub 授权。迁移仓库后，用 `gh api repos/账号/仓库 --jq .id` 查询新 ID，并同步仓库名及这些变量。

令牌、refresh token 由服务端按用途加密后保存在 D1；客户端仅得到应用会话。默认 user token 8 小时过期，刷新令牌由服务端轮换；失效或撤销后要求重新登录，不能回退为管理员代发。[刷新规则](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/refreshing-user-access-tokens)。

如果之前配置的是传统 OAuth App，换成上述 GitHub App 的 Client ID/Secret，再让用户重新登录。旧的仅身份登录令牌不能代表用户创建 Issue；代码会明确提示，不能为解决错误而增加宽泛的 repo 权限。

## 6. 美食投递与媒体上传：分开申请两个 PAT

沿用第 4 节的 Fine-grained PAT 创建入口，分别创建，不要合并到一枚全能 Token。

| Worker Secret | 仅选择的仓库 | 仅开权限 | 用途 |
| --- | --- | --- | --- |
| DISPATCH_GITHUB_TOKEN | zxymiku/guet-food-share | Contents: Read and write | 调用 repository_dispatch 启动规范化 |
| MEDIA_GITHUB_TOKEN | zxymiku/guet-food-share-media | Contents: Read and write | 创建媒体 Release、上传/读取加密附件 |

Metadata 的只读附带权限保留，其他全部不选；设置明确有效期。

```powershell
npx wrangler secret put DISPATCH_GITHUB_TOKEN
npx wrangler secret put MEDIA_GITHUB_TOKEN
```

Contents write 是这两个 GitHub API 所需的最小权限组，并不是让普通用户获得仓库编辑权限。特别是媒体 API 没有更细的“只新增附件不删除”权限；Token 仅放服务端，用户接口按允许列表只做上传/读取。[Fine-grained 权限表](https://docs.github.com/en/rest/authentication/permissions-required-for-fine-grained-personal-access-tokens)。

旧版 Worker GITHUB_TOKEN 仅保留兼容回退，新安装不要设置它。迁移时先配置两个独立 Token，确认相应功能正常后删除旧 Worker GITHUB_TOKEN。密码用户的反馈只认 FEEDBACK_GITHUB_TOKEN；GitHub 用户的反馈只认该用户自己的授权，均不使用这个旧回退值。

## 7. D1 加密与 Actions 接口密钥：本地生成，不向平台申请

### DATA_ENCRYPTION_KEY

是 AES-GCM/HKDF 根密钥，至少 32 字符，建议生成 48 字节随机值。仅保存为 Worker Secret，并在密码管理器做离线备份。用于草稿、会话、设备凭证、媒体索引、媒体和 GitHub 用户令牌的不同派生密钥。

现有部署已配置此值；**不要为了启用密码登录重新生成它**，否则历史加密内容会失去可读性。普通轮换必须先设计旧密钥读取与数据迁移。

### INTAKE_SECRET 与 PUBLISH_SECRET

两者必须是不同的随机值：

| 名称 | 保存在哪里 | 能做什么 |
| --- | --- | --- |
| INTAKE_SECRET | Worker Secret + 主仓库 Actions repository secret | 只读取指定待审投稿供规范化使用 |
| PUBLISH_SECRET | Worker Secret + 主仓库 production Environment secret | 只调用审核后发布接口 |

已把读稿和发布权限分开：读稿密钥不能发布，发布密钥不能读取待审稿。production Environment 在 Settings → Environments → production 中设置；发布工作流声明该环境后才能取得它。按团队需要设置环境审核员。

如是全新安装，可在自己的终端生成随机值（显示后请直接存入密码管理器，不要发聊天）：

```powershell
node -e "process.stdout.write(require('node:crypto').randomBytes(48).toString('base64url'))"
```

每次执行生成一个不同值。再用 `npx wrangler secret put 名称` 存 Worker，用 GitHub 仓库 Settings → Secrets and variables → Actions 配同值。环境 Secret 在 production 的设置页面添加。

旧 PIPELINE_SECRET 是兼容项，不是推荐配置。迁移顺序：设置两枚新 Secret → 更新工作流和 Worker → 确认读/写分别成功 → 删除 Worker 与 GitHub 的旧 PIPELINE_SECRET。不要只更改一侧导致回调全部 401。

## 8. Actions 的 GitHub Token：优先用自动临时凭据

工作流中的 github.token / GITHUB_TOKEN 由 GitHub 每次任务自动生成、任务结束后失效，不需要人工申请。

- normalize.yml：仅主仓库 Contents write + Pull requests write，用于写规范化分支并创建人工审核 PR。
- publish.yml：仅 Contents read，发布能力来自 production 的 PUBLISH_SECRET。
- android-release.yml：仅 Contents write，用于创建自己的 Android Release。
- Web 检查/部署任务：GitHub 权限为 Contents read；Cloudflare 部署能力来自专用部署 Token 或 Workers Builds。

仓库 Settings → Actions → General 的默认 Workflow permissions 保持只读；需要写入的任务通过 YAML 显式声明。创建审核 PR 还需勾选允许工作流创建 PR。不要授权机器人绕过 main 的人工审核规则。

SUBMISSION_BOT_TOKEN 是可选兼容项，默认不要创建。如果组织策略确实需要独立机器人 PAT，选定主仓库，只给 Contents write 和 Pull requests write，并作为主仓库 Actions Secret 保存，不交给 Worker 或普通用户。

## 9. 高德地图：分别申请三种 Key

进入 [高德开放平台](https://console.amap.com/)，完成开发者注册/认证，控制台 → 应用管理 → 我的应用 → 创建应用“桂电美食分享”。创建不同平台的 Key，不混用。

### Web JSAPI

添加 Key 时平台选 **Web 端（JSAPI）**，域名白名单只填生产域名 food.zxymiku.top，按控制台格式填写，不用 `*`。把 Key 存 Worker 变量 AMAP_WEB_KEY；配套安全码 securityJsCode 存 Worker Secret AMAP_SECURITY_JS_CODE。

Web Key 会被浏览器使用，不能靠“藏进前端”保密；域名约束和配额才是有效边界。安全码经应用自己的 `/_AMapService` 代理使用，不发送给浏览器。开发 localhost 应使用单独开发 Key，不要清空正式 Key 的域名限制。[高德安全码说明](https://developer.amap.com/api/javascript-api-v2/guide/abc/jscode)。

### Web 服务 Key

另添加 **Web 服务** Key，作为 Worker Secret AMAP_REST_KEY。用于逆地理编码和附近 POI；如果控制台提供接口选择，只启用本项目需要的地理编码类能力，不申请其他数据管理权限。

Workers 默认出口 IP 不固定，不要把校园电脑 IP 填成服务端白名单。需要强 IP 白名单时，先配置固定出口再绑定真实出口地址；当前通过服务端鉴权、使用配额和客户端不接触这个 Key 来限制调用。

### Android Key

另添加 **Android 平台** Key：

- 包名：com.guet.foodshare。
- 正式签名 SHA1：`15:12:5E:C6:61:AA:F9:D6:A2:34:D7:49:67:E0:B8:E9:D9:C5:69:68`。
- 指纹也可在 android/signing-certificate.txt 核对。SHA1 是公开指纹，不是签名私钥。

将 Key 存主仓库 Actions Secret AMAP_ANDROID_KEY。本地构建可写入被忽略的 android/local.properties。debug 签名与 release 签名不同；开发调试使用另一个绑定 debug SHA1 的 Key，不要更换现有正式签名。[Android Key 申请](https://developer.amap.com/api/maps-sdk-for-android/guide/create-project/get-key)。

## 10. Cloudflare：选择一种自动部署方式

### 推荐：Workers Builds 绑定

Cloudflare → Workers & Pages → guet-food-share → Settings → Builds → Connect repository。GitHub App 安装范围只选 **guet-food-share-web**，不要允许全部仓库。

生产分支 main，根目录 `/`，部署命令 `npm run db:remote && npm run deploy`。保留自定义域名 food.zxymiku.top。无需在 GitHub 再保存 Cloudflare 部署 Token，也不需要把本机 Wrangler OAuth 文件上传。

Cloudflare 自动创建的 Build Token 可能附带本项目不用的 KV、R2 或全部 Zone 权限。请在个人 API Tokens 页面检查，并改用仅覆盖本项目所需资源的自定义用户 Token：

- 目标账号的 Workers Scripts Edit，用于 Worker/静态资源部署。
- 目标账号的 D1 Edit，用于迁移及数据库绑定。
- 如 Wrangler 需要查询自定义域名所在 Zone，只给 zxymiku.top 的 Zone Read。
- 新增或修改域名/路由时，如平台要求 Workers Routes Edit，只对 zxymiku.top 授权；日常只更新代码不额外申请 DNS Edit、全部 Zone 或其他产品权限。

资源选择界面若支持指定 Worker/D1，则只选 guet-food-share 和本项目数据库；若平台只支持账号级资源过滤，这就是它当前能表达的最小粒度，不能声称已隔离到单个 Worker。需要更强隔离时使用独立项目账号。[权限模型](https://developers.cloudflare.com/workers/authorization/workers/)、[Build Token 配置](https://developers.cloudflare.com/workers/ci-cd/builds/configuration/)。

仅通过 API 管理 Builds 触发器时，另需 Workers Builds Configuration Edit；查询 Worker tag 时需 Workers Scripts Read。该管理 Token 与构建部署 Token 是不同用途，不要放进应用 Worker。[Builds API 权限](https://developers.cloudflare.com/workers/ci-cd/builds/api-reference/)。

### 可选：GitHub Actions 部署

只在选择这条路线时，在 Web 仓库设置 DEPLOY_WITH_ACTIONS=true、CLOUDFLARE_ACCOUNT_ID 和 Secret CLOUDFLARE_API_TOKEN。Token 在 [Cloudflare API Tokens](https://dash.cloudflare.com/profile/api-tokens) → Create Custom Token 创建，权限和资源范围按上面的部署清单。不要使用 Global API Key；不要与 Workers Builds 同时自动部署。

## 11. Android 签名与发布凭据

现有签名已经生成，**不要重新生成来替换它**，否则已安装应用不能直接升级。

| 主仓库 Actions Secret | 从哪里取得 | 用途/限制 |
| --- | --- | --- |
| ANDROID_KEYSTORE_BASE64 | 现有 .secrets/guet-food-share-signing.p12 的 Base64 表示 | 仅签名构建任务使用，不是服务端管理凭据 |
| ANDROID_KEYSTORE_PASSWORD | 管理员保存的签名库密码 | 不能提供给普通用户 |
| ANDROID_KEY_ALIAS | 现有别名 guet-food-share | 别名本身不秘密，作为 Secret 是兼容现有工作流 |
| AMAP_ANDROID_KEY | 第 9 节申请的正式 Android Key | 包名与正式证书 SHA1 绑定 |

Base64 **不是加密**；签名库本身由密码保护，GitHub Secret 保护传输/存储，任务结束删除 runner 临时签名文件。签名只在主项目仓库使用，不放到 Web 或媒体仓库。

本机 credentials.dpapi 用 Windows DPAPI 保护密码备份，通常绑定当前 Windows 用户/机器；复制文件到另一台电脑并不等于能解密。正式私钥和密码需另做可恢复的安全备份，移机前由管理员在受控环境导出。切勿把备份、私钥或密码放入公开 Release。

全新项目可用 Android Studio → Build → Generate Signed Bundle/APK 创建签名；本项目已具备正式签名，无须重复这一步。应用包名、版本号、SHA-256 更新清单属于公开信息，不是管理员凭据。

## 12. 普通变量与绑定总表（不是秘密）

| 保存位置 | 名称 | 当前项目值/作用 |
| --- | --- | --- |
| Worker vars | APP_ORIGIN | https://food.zxymiku.top，不带末尾斜杠 |
| Worker vars | ALLOWED_ORIGINS | 当前留空，不加入 workers.dev |
| Worker vars | DEV_MODE | 生产必须 false |
| Worker vars | GITHUB_REPO | zxymiku/guet-food-share |
| Worker vars | FEEDBACK_WEB_REPO | 可省略，默认 zxymiku/guet-food-share-web |
| Worker vars | MEDIA_REPO | zxymiku/guet-food-share-media |
| Worker vars | GITHUB_ANDROID_REPO_ID / GITHUB_WEB_REPO_ID | 第 5 节的仓库数字 ID |
| Worker vars | REVIEWER_GITHUB_IDS | 审核员数字 ID，当前拥有者 95949338；不是用户名 |
| Worker vars | GITHUB_CLIENT_ID / AMAP_WEB_KEY | 对应平台公开客户端标识，不代替 Secret |
| Worker bindings | DB | D1 绑定；ID 在 wrangler.jsonc，运行时不再使用数据库账号密码 |
| Worker bindings | ASSETS | 静态文件绑定，不需要申请密钥 |
| 主仓库 Actions vars | API_BASE_URL / WORKER_URL | https://food.zxymiku.top |
| Web Actions vars（可选） | CLOUDFLARE_ACCOUNT_ID / DEPLOY_WITH_ACTIONS | 仅 Actions 部署方案使用 |

发布后界面配置可以显示 Key 是否已设置，不能返回真实 Secret。PASSWORD_CREDENTIAL、用户 Token、刷新令牌和签名密码都不会进入 /api/config。

## 13. 保存位置、轮换与排错

Cloudflare：Worker → Settings → Variables and Secrets，秘密字段选 Secret/Encrypt，普通标识选普通变量。wrangler.jsonc 中的 vars 是公开源代码，不写任何真实密码或 Token。本地 .dev.vars 与 local.properties 被 Git 忽略，不会自动同步到生产。

GitHub：主/Web 仓库各自的 Settings → Secrets and variables → Actions；PUBLISH_SECRET 放主仓库 production Environment；不要在两个仓库间无差别复制全部 Secrets。

常见问题：

- 密码登录“尚未启用”：PASSWORD_CREDENTIAL 没设置、误填原密码或验证器格式不完整；重新运行设置命令。
- 密码正确却被限流：同一出口 15 分钟内尝试过多，等待窗口结束；不要把共享密码改成过短常见词。
- 密码登录能提交美食但不能上传图片：缺少 MEDIA_GITHUB_TOKEN 或媒体仓库 Contents 写权限；这和共享密码本身无关。
- 密码用户反馈 403/503：检查 FEEDBACK_GITHUB_TOKEN 是否由 zxymiku 创建、是否只选两目标仓库并启用 Issues write。
- GitHub 用户反馈要求重新授权：检查是否注册了 GitHub App、App 是否公开并安装到两个正确仓库、Client ID/Secret 是否来自 App；重新登录，不能改为管理员代发掩盖错误。
- GitHub 用户反馈显示错误作者：共享密码才使用管理员 Token；GitHub 登录必须使用该用户的 App user token。不要把这两条流程合并。
- 自动规范化未启动：DISPATCH_GITHUB_TOKEN 只选主仓库且具备 Contents write，主分支存在工作流文件。
- 整理/发布回调 401：INTAKE_SECRET 与 PUBLISH_SECRET 不同；检查对应工作流保存位置和 Worker 对应值，不能互换。
- Android 地图拒绝 Key：核对平台类型、包名与正式 SHA1；更新 AMAP_ANDROID_KEY 后需重新构建 APK。
- 客户端访问到 workers.dev：更新旧 APK，并核对当前 API_BASE_URL；源码、生产变量和截图链接均应使用自己的域名，不用 workers.dev 作重试地址。

轮换顺序一般是“创建受限新值 → 更新消费方 → 确认该功能 → 撤销旧值”。加密根密钥例外，不能直接覆盖。共享密码更换会主动使旧密码会话失效；GitHub App 授权可在用户 Settings → Applications → Authorized GitHub Apps 随时撤销。
