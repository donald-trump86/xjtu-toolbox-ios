# iOS-only 分支整理验收记录

日期：2026-10-29，v0.2.0 同步复跑补记于 2026-11-23。验收时分支：`feat/ios-port`，现已并入主分支 `main`。整理从 `159c7f17` 开始，保留先前 iOS 移植与上游版权，按新的分支范围移除其他平台。以下只记录实际执行结果；模拟器成功不等于真机、SideStore 或真实校园账号成功。

## 文件与构建范围

- 源码文件清单由 **1,664 个减至 288 个**，包含本次新增的图标母版、Native 测试和许可证。统计只计入当前保留的源码、资源与文档，不计 Git 历史或忽略的构建缓存。
- 保留 **10 个模块**：`iosShared`，`shared` 下的 campus / model / navigation / designsystem，以及 account / home / schedule / score / settings 五个功能库。
- Kotlin 应用库仅声明 `iosArm64` 与 `iosSimulatorArm64`。其他平台工程、source set、旧 JVM 数据层、无用业务页与素材、旧工作流和历史文档已移除；Gradle 的 JDK 仅为构建工具所需。
- 复用现有 Compose 页面和必要的间接模型。原 JVM 的课表、成绩和缓存测试中可复用的断言已迁到 `commonTest`；移除仅能依赖 Swing / AWT 的测试，不为它保留另一应用平台。
- 应用图标沿用上游「交小西」Q 版形象，替换原图标：母版取上游 `web/src/wasmJsMain/resources/icons/icon-512.png`（512 × 512），放大为 1024 × 1024 不透明资源，圆角由 iOS 裁切。源图本身带烘焙圆角：左上、右上、右下三处露出浅灰底（#E5E5E5，各角块约 60%），左下齐边；烘焙半径约 116 px（22.7%）与 iOS 的 22.4% 几乎重合，遮罩后圆角外缘残留的灰边在 1024 尺度上约 2 px。版权与授权见 [第三方声明](../THIRD_PARTY_NOTICES.md) 的「美术素材」一节；该形象不属于本仓库 GPL-3.0 范围。应用内项目主页、致谢和隐私正文同步为本 Fork 的实际实现。

## 本机构建环境与命令

Apple Silicon，Xcode 27.1（27A9275）、JDK 21.0.13、Gradle wrapper 9.6.1、Kotlin 2.4.20、Compose 1.12.1、Ktor 3.6.0、Ksoup 0.2.6。依赖与 Kotlin/Native 缓存放在仓库忽略目录，未改写 `HOME` 或关闭 TLS。

```sh
# 模拟器 Debug 与设备 Release framework。
sh scripts/gradle-ios.sh \
  :iosShared:linkDebugFrameworkIosSimulatorArm64 \
  :iosShared:linkReleaseFrameworkIosArm64

# 保留模块的 Native 测试，使用已安装的普通 iPhone 模拟器。
sh scripts/gradle-ios.sh \
  :shared:campus:iosSimulatorArm64Test \
  :shared:model:iosSimulatorArm64Test \
  :shared:navigation:iosSimulatorArm64Test \
  :shared:designsystem:iosSimulatorArm64Test \
  :feature:account:iosSimulatorArm64Test \
  :feature:home:iosSimulatorArm64Test \
  :feature:schedule:iosSimulatorArm64Test \
  :feature:score:iosSimulatorArm64Test \
  :feature:settings:iosSimulatorArm64Test \
  -PiosSimulatorDevice=D2376C90-F526-4ED5-B227-4CD67486B388

# 本地 ad-hoc 应用身份；合成输入，不提交校园登录。
sh scripts/test-ios-simulator.sh D2376C90-F526-4ED5-B227-4CD67486B388
sh scripts/test-ios-simulator.sh 367B2A3A-C5B5-4254-8563-DF618D57A5DB

# 设备包与 SHA-256 文件。
sh scripts/build-ios-ipa.sh unsigned
```

## Native 测试结果

| 模块 | 通过数 |
| --- | ---: |
| shared:campus | 46 |
| shared:model | 103 |
| shared:navigation | 13 |
| shared:designsystem | 27 |
| feature:home | 21 |
| feature:schedule | 45 |
| feature:score | 5 |
| **合计** | **260** |

XML 结果合计 **260 项、零失败、零错误、零跳过**。account 与 settings 无独立 Native 单元测试，页面导航、登录表单与设置交互由下面的 XCTest 覆盖。

协议断言包括两种 CAS 短信分支、Safety 原地址 / Referer、错误 / 超时 / 次数限制、后台不触发 MFA、站点独立认证、网关合法代理落地、过期恢复、一次重放、严格重定向与 Cookie 范围、课表与成绩解析、无明细、退出清理、跨实例索引并发、失败写入和迟到写入。RSA / CSPRNG 使用真实 Security.framework；学校响应和密钥均为合成 fixture。

## v0.2.0 上游同步复跑

2026-11-23 同步上游 `23b835c5..53085677` 中的可移植改动后，用同一台机器、同一模拟器设备 `D2376C90-F526-4ED5-B227-4CD67486B388` 重跑 Native 测试并重新编译 `iosShared`：

| 项目 | 结果 |
| --- | --- |
| Native 测试 | **262 项通过**：campus 46、model 104、navigation 13、designsystem 28、home 21、schedule 45、score 5；零失败、零错误、零跳过 |
| 与本次同步前基线之差 | +2。同步前基线为 260 项（model 103、designsystem 27），新增的 2 项是同步时随首页背景模糊 / 亮度功能补的持久化与摘要断言 |
| `:iosShared:compileKotlinIosSimulatorArm64` | 通过 |

本轮除上述 Gradle 任务外只重新构建了未签名设备包，没有启动模拟器跑交互测试。课表排版、「更多」菜单顺序、首页头卡外观对话框、致谢二级页转场与图标替换的界面表现因此只有编译与代码层证据，未做设备目视验收。

iOS 未实现 `ScheduleCapability.SHARE_FILE`，课表「更多」菜单里没有「导出日历 (ICS)」一项；这是既有行为，不是本次同步的回归。电子成绩单入口需要宿主传 `onOpenTranscript` 才绘制，iOS 未接入该入口，因此新副标题只体现为文案与上游一致，界面上不显示。

## 最终 iPhone / iPad 宿主回归

| 设备 | Keychain 宿主测试 | 完整交互测试 | 结果 |
| --- | ---: | ---: | --- |
| iPhone 14 / iOS 27.0 | 0.018 秒 | 27.281 秒 | 通过 |
| iPad Pro 13-inch (M4) / iPadOS 27.0 | 0.023 秒 | 27.032 秒 | 通过 |

Keychain 测试覆盖真实存储往返、跨实例读取、字符串 / 整数 / 布尔 / 集合、删除与命名空间隔离。交互覆盖中文导航、输入框限宽、合成账号、密码遮罩、键盘、浅深色切换和实际横屏窗口尺寸，并保留屏幕级截图。

本机结果包位于忽略目录：

- iPhone：`build/ios/xctest-D2376C90-F526-4ED5-B227-4CD67486B388-20261029-230028.xcresult`。
- iPad：`build/ios/xctest-367B2A3A-C5B5-4254-8563-DF618D57A5DB-20261029-230444.xcresult`。

日志为 `build/ios-only-validation.log`、`build/ios-only-tests-fixed.log`、`build/ios-only-ui-iphone14.log`、`build/ios-only-ui-ipad.log` 与 `build/ios-only-final-ipa-build.log`。首次批量测试中不兼容的 Kotlin/Native 测试函数名、CharArray 构造方式和已删功能的孤立测试已修正，受影响模块随后重跑成功。Xcode 的伪终端启动被沙箱拒绝时，使用获准的更广权限重跑同一命令后通过，未改用绕过路径。

此前移植阶段已解决 UIScene 启动要求、模拟器 Keychain 身份、退出迟到缓存写入、WebVPN 恢复和 iPad 限宽问题。本次清理后重新执行上述基线。Duo / iOS 27.1 的双屏横屏断言此前仍超时，当前不宣称支持该场景。

## 最终设备产物

文件：`build/ios/XJTUToolbox-unsigned.ipa`，**17,283,703 字节**。SHA-256：

```text
1be6d785aa3c473030580c1210103102821cd73795815ad9c35a9c4dcbe26a8f
```

脚本同时输出 `build/ios/XJTUToolbox-unsigned.ipa.sha256`，内容与上表一致。解包核查：ZIP CRC 正常；`Info.plist` 的 `CFBundleShortVersionString` / `CFBundleVersion` 为 `0.2.0` / `2`，与 `SettingsHost.versionText` 及仓库内 Xcode 工程一致；最低系统 `16.0`；设备系列 `[1, 2]`；显示名称“岱宗盒子”；bundle ID `me.luvch1nabest.xjtutoolbox`；可执行文件为设备 `arm64` / `IOS`，SDK 27.2；未签名，包内无 `embedded.mobileprovision`。`Assets.car` 中的 `AppIcon` 为 1024 × 1024，phone 与 pad 两种 idiom 各一份，另含 `AppIcon60x60@2x.png` 与 `AppIcon76x76@2x~ipad.png`。

这是等待重签的设备 IPA；需要本人使用 SideStore 或本机开发者身份签名。设备构建不等于真机启动。

## 文档、许可与交付边界

README 已改为 iOS Fork 定位、功能、获取 IPA 与构建入口；[SideStore 指南](ios-sidestore.md) 覆盖 iloader 首次安装、LocalDevVPN、开发者信任与模式、IPA 导入、校园登录、7 天续签、覆盖更新、额度和故障排查。[第三方声明](../THIRD_PARTY_NOTICES.md) 保留 GPL 上游、Kyant Apache-2.0、OpenStreetMap ODbL、实际依赖与图像来源；新图标不是学校官方标识。

[iOS CI](../.github/workflows/ios.yml) 仅配置 Apple 双架构、Native、iPhone / iPad 宿主和交互测试、设备构建与可选 IPA artifact，使用官方 Xcode 27 arm64 preview 镜像和 Xcode 27.1。上面的本地结果全部来自本机直接执行，不是远程 CI 产物：CI 只在手动触发或 `pull_request` 时运行，本次同步没有对应的远程运行记录。随 v0.2.0 发布的产物是未签名 IPA（即上面记过校验值的 17,283,703 字节包），不附带证书、描述文件或公证；分发时同时提供对应源码、修改说明和许可证。

v0.2.0 的发布动作与本文记录分开：本文只覆盖本机构建、测试与设备包的核查结果；tag、GitHub Release 与 artifact 的上传由维护者在全部同步任务完成后统一执行，本文不预先把它们写成已完成。

尚未完成真实设备配对、SideStore 重签安装 / 刷新、真实 CAS / MFA / WebVPN、个人课表与成绩验收。校园密码与短信应由本人在 App 内输入。当前身份仅显示 NetID；姓名、学号、院系及其他未接入功能见 [功能矩阵](ios-feature-matrix.md)。
