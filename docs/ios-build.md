# iOS 构建与验证

本分支仅维护 iOS / iPadOS。UIKit SceneDelegate 承载 Compose Multiplatform 界面，Kotlin 库只声明 `iosArm64` 与 `iosSimulatorArm64`；账号、课表、成绩、首页和设置共用 `commonMain`。设备直接连接学校系统，功能与验收边界见 [功能矩阵](ios-feature-matrix.md)。

## 环境

- Apple Silicon Mac、Xcode **27.1**（本次为 27A9275）、iOS SDK 和 Command Line Tools。
- **JDK 21**，供 Gradle 构建工具运行。仓库固定 Gradle 9.6.1、Kotlin 2.4.20、Compose 1.12.1、Ktor 3.6.0、Ksoup 0.2.6。
- **XcodeGen**，用于生成 UIKit 宿主工程；`brew install xcodegen`。
- 运行测试时需要兼容的 iPhone / iPad 模拟器运行时。编译设备 framework 和未签名 IPA 不要求模拟器运行时。

[gradle-ios.sh](../scripts/gradle-ios.sh) 保留调用者的 `JAVA_HOME`、`GRADLE_USER_HOME` 和 `KONAN_DATA_DIR`。未指定 JDK 时会使用仓库内可选的 JDK 21；缓存默认放在 `.gradle-user-home/` 和 `build/konan/`，不改写 `HOME`。干净克隆可按下列方式使用已安装的 JDK：

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
sh scripts/gradle-ios.sh :iosShared:tasks --all
```

## 两种 Apple 架构

```sh
sh scripts/gradle-ios.sh \
  :iosShared:linkDebugFrameworkIosSimulatorArm64 \
  :iosShared:linkReleaseFrameworkIosArm64
```

两者都生成 `ToolboxShared` 静态 framework，分别面向模拟器和 iOS 设备，不能互换。工程只包含 `iosShared`、四个 `shared` 库和五个 `feature` 库。

## Native 协议与逻辑测试

先选择一个已安装的常规模拟器：

```sh
xcrun simctl list devices available
SIMULATOR_UDID='<iPhone 模拟器的 UDID>'
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
  -PiosSimulatorDevice="$SIMULATOR_UDID"
```

测试在 iOS 模拟器中运行，包括 Ktor MockEngine 合成校园站、DOM 解析、CAS/MFA、WebVPN、cookie、课表、成绩、退出与并发缓存，以及领域算法和页面状态。本次保留模块共 **260 项通过**。测试不会连接学校或提交真实凭据。

Keychain 需要具备应用身份的宿主。裸 Native 命令行进程曾返回 `-25291`，完全不签名的宿主曾返回 `-34018`；下面的宿主 XCTest 才用于验证实际 Keychain 往返、跨实例恢复、类型、删除和命名空间隔离。

## Xcode 宿主、Keychain 与交互测试

可用 Xcode 打开 [宿主工程](../iosApp/XJTUToolbox.xcodeproj/project.pbxproj)，选择 `XJTUToolbox` scheme 和 iPhone / iPad 模拟器。最低系统为 **iOS / iPadOS 16.0**。构建阶段调用 `embedAndSignAppleFrameworkForXcode`，同步静态 framework 与 Compose 资源。

也可运行 [模拟器测试脚本](../scripts/test-ios-simulator.sh)：

```sh
sh scripts/test-ios-simulator.sh "$SIMULATOR_UDID"
```

脚本生成工程、启动模拟器，使用本地 ad-hoc 身份并保存带时间戳的 xcresult。[Simulator.entitlements](../iosApp/Simulator.entitlements) 的 `local.` 访问组仅用于 `iphonesimulator` SDK，无需 Apple Account、真实 Team ID 或开发者证书，也不用于设备归档。

[交互测试](../iosApp/UITests/XJTUToolboxSmokeTests.swift) 检查中文导航、输入限宽、键盘、合成账号、密码遮罩、浅深色切换与实际横屏尺寸，使用屏幕级截图保留证据。[Keychain 宿主测试](../iosApp/Tests/CampusKeychainHostTests.swift) 只访问随机测试命名空间。密码、短信验证码、Cookie 和令牌不要放进命令或日志。

只检查宿主编译时可使用 `CODE_SIGNING_ALLOWED=NO build`，但不能据此宣称 Keychain 可用。

## 设备 IPA 与校验文件

```sh
sh scripts/build-ios-ipa.sh unsigned
```

[打包脚本](../scripts/build-ios-ipa.sh) 先运行 XcodeGen，再以 Release、`generic/platform=iOS` 和关闭签名构建设备应用。检查可执行文件为 `arm64` 且 Mach-O 平台为 `IOS` 后输出：

- `build/ios/XJTUToolbox-unsigned.ipa`
- `build/ios/XJTUToolbox-unsigned.ipa.sha256`

这是设备包，仍需通过 [SideStore](ios-sidestore.md) 重签后安装。已有开发者团队与本机导出配置时，可在本机归档签名：

```sh
export DEVELOPMENT_TEAM='<本机团队 ID>'
export EXPORT_OPTIONS_PLIST='<本机 ExportOptions.plist 的绝对路径>'
sh scripts/build-ios-ipa.sh signed
```

证书、私钥、描述文件、团队本地设置与构建产物不提交。Apple Account 由本人在相应工具中处理。

## 应用图标

母版为 [app-icon-master.png](../artwork/app-icon-master.png)。通过 CoreGraphics / ImageIO 生成 1024 × 1024、不透明 sRGB 图标：

```sh
swift scripts/generate-ios-icons.swift \
  artwork/app-icon-master.png \
  iosApp/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png
```

图标以蓝色背景、腾飞塔与展开书页构成，未预切圆角。造型参考及第三方资源声明见 [THIRD_PARTY_NOTICES](../THIRD_PARTY_NOTICES.md)。

## CI、隐私与验证边界

[iOS CI](../.github/workflows/ios.yml) 使用官方 [Xcode 27 arm64 镜像](https://github.com/actions/runner-images/blob/main/images/macos/xcode-27-arm64-Readme.md)，固定 Xcode 27.1；镜像当前为 public preview。配置包含双架构 framework、Native 测试、iPhone / iPad 宿主与交互测试、设备构建和可选未签名 IPA artifact。本次未推送或执行远程 CI，未配置公开 Release 或证书签名流水线。

[CampusSession](../shared/campus/src/commonMain/kotlin/com/xjtu/toolbox/campus/CampusSession.kt) 分离账号、直连 / WebVPN 路由及业务站会话。[Keychain 实现](../shared/campus/src/iosMain/kotlin/com/xjtu/toolbox/campus/CampusPlatform.ios.kt) 要求设备解锁且不跨设备同步。Darwin 使用 ephemeral session，关闭系统共享 Cookie 与 URLCache，保留系统 TLS / ATS 验证。退出撤销旧缓存句柄、清理 ViewModel 与私有缓存，再删除会话，拒绝迟到请求写回。

编译、模拟器、真机、SideStore 和真实校园账号分别记录在 [验证记录](ios-validation.md)。真实 MFA、校外网络、掉线恢复、缓存容量和重签后的 Keychain 仍需目标设备验收；卸载或更换签名团队后不保证旧状态可恢复。

本 Fork 保留上游版权与 [GPL-3.0](../LICENSE)。分发 IPA 时应同时提供对应源码、许可证与修改说明。
