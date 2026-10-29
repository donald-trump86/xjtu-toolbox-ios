# iOS-only 分支整理验收记录

日期：2026-10-29。验收时分支：`feat/ios-port`，现已并入主分支 `main`。本次整理从 `159c7f17` 开始，保留先前 iOS 移植与上游版权，按新的分支范围移除其他平台。以下只记录实际执行结果；模拟器成功不等于真机、SideStore 或真实校园账号成功。

## 文件与构建范围

- 源码文件清单由 **1,664 个减至 288 个**，包含本次新增的图标母版、Native 测试和许可证。统计只计入当前保留的源码、资源与文档，不计 Git 历史或忽略的构建缓存。
- 保留 **10 个模块**：`iosShared`，`shared` 下的 campus / model / navigation / designsystem，以及 account / home / schedule / score / settings 五个功能库。
- Kotlin 应用库仅声明 `iosArm64` 与 `iosSimulatorArm64`。其他平台工程、source set、旧 JVM 数据层、无用业务页与素材、旧工作流和历史文档已移除；Gradle 的 JDK 仅为构建工具所需。
- 复用现有 Compose 页面和必要的间接模型。原 JVM 的课表、成绩和缓存测试中可复用的断言已迁到 `commonTest`；移除仅能依赖 Swing / AWT 的测试，不为它保留另一应用平台。
- 新图标为腾飞塔与展开书页的蓝白 / 米金色设计，替换原图标。应用内项目主页、致谢和隐私正文同步为本 Fork 的实际实现。

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

文件：`build/ios/XJTUToolbox-unsigned.ipa`，**18,488,179 字节**。SHA-256：

```text
b3adb189a33c5ce172f2d6783ac604a2fed26e66e932c5389fb2935ebbc9fb07
```

脚本同时输出 `build/ios/XJTUToolbox-unsigned.ipa.sha256`。独立核查确认 ZIP CRC 正常、可执行文件为设备 `arm64` / `IOS`、最低系统 `16.0`、设备系列 `[1, 2]`、显示名称“岱宗盒子”、bundle ID `me.luvch1nabest.xjtutoolbox`。包内 `Assets.car` 与新图标设备构建一致，未附带证书或描述文件。

这是等待重签的设备 IPA；需要本人使用 SideStore 或本机开发者身份签名。设备构建不等于真机启动。

## 文档、许可与交付边界

README 已改为 iOS Fork 定位、功能、获取 IPA 与构建入口；[SideStore 指南](ios-sidestore.md) 覆盖 iloader 首次安装、LocalDevVPN、开发者信任与模式、IPA 导入、校园登录、7 天续签、覆盖更新、额度和故障排查。[第三方声明](../THIRD_PARTY_NOTICES.md) 保留 GPL 上游、Kyant Apache-2.0、OpenStreetMap ODbL、实际依赖与图像来源；新图标不是学校官方标识。

[iOS CI](../.github/workflows/ios.yml) 仅配置 Apple 双架构、Native、iPhone / iPad 宿主和交互测试、设备构建与可选 IPA artifact，使用官方 Xcode 27 arm64 preview 镜像和 Xcode 27.1。**本次未推送、未运行远程 CI、未创建公开 Release。**

尚未完成真实设备配对、SideStore 重签安装 / 刷新、真实 CAS / MFA / WebVPN、个人课表与成绩验收。校园密码与短信应由本人在 App 内输入。当前身份仅显示 NetID；姓名、学号、院系及其他未接入功能见 [功能矩阵](ios-feature-matrix.md)。
