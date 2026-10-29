# iOS / iPadOS 独立客户端实施计划

基线：`feat/ios-port`，HEAD `23b835c5716d38edfd0ff046c518de3804c1f70e`。Fork：`donald-trump86/xjtu-toolbox-ios`；upstream：`yeliqin666/xjtu-toolbox-android`。本计划依据实际源码，2026-10-29 首次记录。构建和真实账号验证分开记录，不把代码存在视为服务可用。

## 1. 环境与版本

| 项目 | 审计结果 |
|---|---|
| 主机 | macOS arm64；Xcode 27.1 (27A9275)；iOS / Simulator SDK 27.1 |
| 模拟器 | 初始无 runtime / device；正在下载官方 iOS 27.1 arm64 runtime |
| Java | 默认 Zulu 27.0.1；项目要求 21；已在忽略目录 `build/toolchains/jdk21` 安装 Temurin 21.0.13+9 |
| Gradle | wrapper 9.6.1；独立缓存 `.gradle-user-home`；实际执行结果见构建文档 |
| Kotlin / CMP | 2.4.20 / 1.12.1；MIUIX 0.9.4 |
| AGP / Android | 9.4.1；compileSdk 37，minSdk 31；初始无 Android SDK |
| Ktor / serialization / datetime | 3.6.0 / 1.11.0 / 0.8.0 |
| DOM | 现有 Jsoup 1.23.2 是 JVM；新增 Ksoup 0.2.6（Maven Central 已核实有发布） |

## 2. 实际模块与平台边界

```text
app (Android) ── core + data ── shared:* + feature:*
desktop (JVM) ── core + data ── shared:data (OkHttp/Jsoup)
web (wasmJs) ── core ── shared:network 的 /api 客户端 ── serve/campus-api

iosApp (Swift/UIKit，新增)
  └─ iosShared (ComposeUIViewController 静态 framework，新增)
       ├─ shared:model + navigation + designsystem + network
       ├─ feature:account + home + schedule + score + settings
       └─ shared:campus (新增，设备直连学校，Ktor / Ksoup / 平台加密与安全存储)
```

`core` 是所有功能模块的转发壳，不是 iOS 首阶段的最小依赖。`data` 是场馆验证码残留并转发 `shared:data`。`shared:data` 使用 `toolbox.kmp.jvm-android`，虽然代码位于 commonMain，公开 API 和内部实现仍依赖 OkHttp、Jsoup、java.io、JCA、JNA、文件与 CookieManager；不能直接加 iOS 编译目标。现有 Android/JVM 数据调用保持有效；抽取的可移植协议通过既有 Source 接口提供，不创建第二套 UI/业务模型。

`shared:network` 的 Ktor 工厂可补 Darwin actual；其中 `Campus*Api` 是 serve/campus-api 客户端，iOS 不实例化这些服务适配器。`docs/api-contract.md` 是 web/serve 契约，不是学校直连接口。

## 3. 复用页面与移植缝

- `feature:account`：`LoginScreen` / `MfaCodeDialog` 是可注入状态的共享表单；认证状态机由直连数据模块提供。
- `feature:schedule`：复用 `ScheduleScreen` / `ScheduleViewModel`、调停补课算法、详情与周视图；实现 `JwxtScheduleSource`、`ScheduleHost`、缓存与登录端口。
- `feature:score`：复用 `JwappScoreScreen` / ViewModel、TermScore/ScoreDetail、GPA 算法；实现 `JwappScoreSource`、可支持的 `JwxtScoreSupplement` 及 `JwappScoreCache`。
- `feature:home`：`HomeHost.openableRouteIds` 只放已支持页面；不向未移植功能展示空入口。
- `feature:settings`：`SettingsHost.honoredRows` 与 nullable sections 只开放实际接通的外观、连接模式、隐私和版本信息。
- `shared:model`：补 iOS KeyValueStore（只用于非敏感偏好）、SecureRandom、同步、Base64、压缩、二维码、日志、错误分类、设备 ID 与提示 actual。
- `shared:designsystem`：补图片解码、屏幕尺寸、状态栏、触感、电话/分享、日期选择和背景 actual；复用通用非 Android 实现，系统能力用 UIKit。
- `feature:settings`：补 iOS archive actual 或隐藏无实现的导入/导出入口，不能返回伪成功。

## 4. 独立认证、会话与安全

真实实现参照 `shared/data/.../auth/XJTULogin.kt`、`CasLoginPages.kt`、`CasSiteSession.kt` 及各站点登录器。CAS 存在 MFA_DETECT 与 SAFETY_VERIFY 两条路径；后者必须保存表单 secState/execution/_eventId 并回 POST 到原始 response URL。获取手机号/验证码上下文 gid 后才验证；常规短信验证，不绕过验证码或 MFA。

Ktor 使用独立持久 Cookie jar；CAS、教务、移动教务、WebVPN 的状态与账号隔离，设置合理超时、重定向上限与单次认证限流；手工重定向保留表单/票据语义。仅允许已知学校域名和 WebVPN 映射。URL/headers/body 中的密码、ticket、Cookie、短信码与 token 不进入日志。TLS 使用平台证书验证，不添加 trust-all。

密码加密迁移到可测试的公共协议接口；iOS 用系统 Security / 安全随机数，JVM 用 JCA 验证，WebVPN 复用已有公共 codec。Keychain 保存必要凭据/会话；普通 UserDefaults 仅存外观等非敏感设置。离线校园缓存按账号隔离，保护文件并在退出/切号时正确清除 UI 和会话；提供清除缓存/退出登录。

## 5. 实施顺序与验收

1. **构建与 platform actual**：便携 convention 增加 iosArm64 / iosSimulatorArm64，JVM-only convention 保持原目标；创建 iosShared framework 和 UIKit 宿主。先实际编译静态 framework，处理每一个 Native 依赖/actual 错误。
2. **P0 认证**：直连 CAS、RSA 与两条短信 MFA、WebVPN、会话恢复/过期/退出、基础身份。MockEngine 校验表单、重定向、票据、Cookie 和错误状态；密码输入只发生在用户设备应用内。
3. **P0 课表**：学期、周次、老师/教室、调停补课、详情、刷新、按账号缓存。用学校格式的脱敏 fixture 和现有算法交叉验证。
4. **P0 成绩**：学期筛选、精确分数/学分/GPA/分项，缓存、错误与重试；缺少补充上游时明确能力并隐藏入口。
5. **首页与设置**：共享页面完成 iPhone/iPad、安全区、键盘、横竖屏与系统深浅色验证；关闭未移植入口。
6. **P1**：只在 P0 已接通并有独立测试后增加考试/校园卡/图书馆只读；预约、取消、支付等写操作继续标记未验证并隐藏。
7. **交付**：Xcode project、图标/Info.plist、iOS 16.0+、scripts/build-ios-ipa.sh、构建/SideStore/能力矩阵文档。device arm64 unsigned .app 可封装供 SideStore 重签的 IPA，明确不是已签名可直接安装包；脚本禁止拿 Simulator 包伪装。

## 6. 验证命令与状态记录

命令以实际生成的 task 名称为准：

```sh
JAVA_HOME="$PWD/build/toolchains/jdk21/Contents/Home" GRADLE_USER_HOME="$PWD/.gradle-user-home" sh gradlew --version
sh scripts/gradle-ios.sh :shared:campus:jvmTest
sh scripts/gradle-ios.sh :shared:campus:iosSimulatorArm64Test
sh scripts/gradle-ios.sh :iosShared:linkDebugFrameworkIosSimulatorArm64
sh scripts/gradle-ios.sh :iosShared:linkReleaseFrameworkIosArm64
sh scripts/build-ios-ipa.sh unsigned
```

另运行受影响 model/network/feature JVM 测试、Android 配置和尽可能实际 assembleDebug；环境缺 SDK/依赖时记录真实失败原因与修复步骤。Xcode 使用 repository-local DerivedData。模拟器安装/启动与 iPhone+iPad 截图单独记，真实设备签名/SideStore安装及真实校园账号请求单独记。没有账号或设备时，完成所有独立编译和离线验证后再交接必要动作。

## 7. 提交与限制

保留 GPL-3.0、上游作者与改动说明。按构建/platform、数据与测试、宿主/交付分组本地提交在 feat/ios-port；不改 upstream，不创建公开 Release，不上传签名材料/账号数据。所有构建、模拟器、设备与真实请求状态在 `docs/ios-build.md` 和 `docs/ios-feature-matrix.md` 如实更新。
