# iOS 构建与验证

本 Fork 使用 UIKit SceneDelegate 承载 Compose Multiplatform，复用账号、首页、课表、成绩和设置页面。设备直接连接学校 CAS、教务和 WebVPN；不需要桌面端、本项目 server 或个人代理。源码审计见 [移植计划](ios-port-plan.md)，实现与验收范围见 [功能矩阵](ios-feature-matrix.md)。

## 环境

- Apple Silicon Mac、Xcode 27.1、iOS SDK 和 Command Line Tools。
- JDK 21。仓库固定 Gradle 9.6.1、Kotlin 2.4.20、Compose 1.12.1、Ktor 3.6.0。
- Android SDK Platform 37.0、Build Tools 37.0.0；AGP 在本次构建中还安装了默认的 Build Tools 36.0.0。根工程会配置 Android 模块，iOS 编译也应准备 SDK。
- 运行测试前安装兼容的模拟器运行时。纯设备 framework 编译不要求运行时。
- XcodeGen 用于重新生成宿主工程和运行模拟器测试脚本；生成后的工程也保存在仓库中。

[gradle-ios.sh](../scripts/gradle-ios.sh) 保留调用者设置的 JAVA_HOME、GRADLE_USER_HOME、KONAN_DATA_DIR、ANDROID_USER_HOME 和 ANDROID_HOME。未设置时优先使用仓库内的可选工具链与忽略目录，不改写 HOME。

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export ANDROID_HOME="$HOME/Library/Android/sdk"
sh scripts/gradle-ios.sh :iosShared:tasks --all
```

## framework 与协议测试

```sh
sh scripts/gradle-ios.sh \
  :iosShared:linkDebugFrameworkIosSimulatorArm64 \
  :iosShared:linkReleaseFrameworkIosArm64
sh scripts/gradle-ios.sh \
  :shared:campus:jvmTest :shared:model:jvmTest \
  :feature:schedule:jvmTest :feature:score:jvmTest :shared:data:jvmTest
```

两个 framework 分别面向模拟器 arm64 和设备 arm64，不能互换。JVM MockEngine/fixture 测试不连接学校，不使用个人凭据，只证明样本解析、状态机与回归结果。

原生协议及 Security.framework RSA/CSPRNG 测试使用指定的模拟器：

```sh
xcrun simctl list devices available
SIMULATOR_UDID='<已安装模拟器的 UDID>'
sh scripts/gradle-ios.sh :shared:campus:iosSimulatorArm64Test \
  -PiosSimulatorDevice="$SIMULATOR_UDID"
```

Keychain 必须在具备应用身份的宿主中测试。裸 Native 命令行进程曾返回 `-25291`，完全禁用签名的宿主曾返回 `-34018`；这两种执行方式不能替代 App 的 Keychain 验证。Keychain 往返、跨实例恢复、类型、删除与命名空间隔离由下面的 App 宿主 XCTest 覆盖。

## Xcode 宿主与模拟器交互

用 Xcode 打开 [宿主工程](../iosApp/XJTUToolbox.xcodeproj/project.pbxproj)，选择 XJTUToolbox scheme 和 iPhone/iPad 模拟器。最低系统为 iOS/iPadOS 16.0，支持两个设备系列。UIKit 使用 scene 生命周期；构建阶段调用 embedAndSignAppleFrameworkForXcode 同步静态 framework 和 Compose 资源。

只验证宿主编译时可以关闭签名：

```sh
xcodegen generate --spec iosApp/project.yml
xcodebuild -project iosApp/XJTUToolbox.xcodeproj -scheme XJTUToolbox \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build/ios/SimulatorDerivedData CODE_SIGNING_ALLOWED=NO build
```

运行交互与 Keychain 测试使用 [test-ios-simulator.sh](../scripts/test-ios-simulator.sh)：

```sh
sh scripts/test-ios-simulator.sh "$SIMULATOR_UDID"
```

脚本启动所选模拟器，使用本地 ad-hoc 身份并保留带时间戳的 xcresult。仅 iphonesimulator SDK 使用 [Simulator.entitlements](../iosApp/Simulator.entitlements) 的 `local.` 访问组，不需要 Apple Account、开发者证书或真实 Team ID。该身份不用于设备归档。脚本关闭失败时耗时的系统 sysdiagnose 收集，保留测试断言、日志和显式截图。

[交互测试](../iosApp/UITests/XJTUToolboxSmokeTests.swift) 使用合成输入且不提交登录；[Keychain 宿主测试](../iosApp/Tests/CampusKeychainHostTests.swift) 只访问随机测试命名空间。真实密码、短信验证码、cookie 和令牌不要放入命令、测试日志或聊天。

## 设备 IPA

```sh
sh scripts/build-ios-ipa.sh unsigned
```

[打包脚本](../scripts/build-ios-ipa.sh) 用 Release、generic/platform=iOS 和关闭签名构建设备应用，再检查可执行文件为 arm64、平台为 IOS。检查通过后才输出 `build/ios/XJTUToolbox-unsigned.ipa`。该包需要 SideStore 重新签名；模拟器应用不能用于设备安装。

本人已有开发者团队和导出配置时，也可在本机签名：

```sh
export DEVELOPMENT_TEAM='<本机团队 ID>'
export EXPORT_OPTIONS_PLIST='<本机 ExportOptions.plist 的绝对路径>'
sh scripts/build-ios-ipa.sh signed
```

证书、私钥、描述文件、本地团队设置与打包产物不提交。Apple Account 由本人在 Xcode/SideStore 中处理。[iOS CI](../.github/workflows/ios.yml) 使用官方 [Xcode 27 arm64 镜像](https://github.com/actions/runner-images/blob/main/images/macos/xcode-27-arm64-Readme.md)，固定 `DEVELOPER_DIR=/Applications/Xcode_27.1.app/Contents/Developer`。该镜像当前为 public preview；本次没有推送或运行远程 CI。配置包含双目标、JVM/Android、Native 与 iPhone/iPad 的 App 宿主/交互测试；未签名 IPA artifact 由手动输入选择。本地签名独立，不创建公开 Release。

## 数据、退出与验证边界

[CampusSession](../shared/campus/src/commonMain/kotlin/com/xjtu/toolbox/campus/CampusSession.kt) 分离账号、直连/WebVPN 路由和业务站点的 cookie、令牌与认证状态。iOS 凭据、会话及私有缓存进入 [Keychain](../shared/campus/src/iosMain/kotlin/com/xjtu/toolbox/campus/CampusPlatform.ios.kt)，要求解锁且不跨设备同步。设置使用非敏感偏好存储。网络采用系统 TLS，不配置 ATS 全局放行；Darwin 引擎关闭系统共享 cookie 和 URLCache，应用自管 cookie jar。

退出先撤销旧账号缓存写入权限、清理 ViewModel 和缓存，再删除会话。成绩缓存也绑定账号与会话代数，拒绝退出后的迟到写入。普通关页保留离线数据。锁定或不可用的 Keychain 读取会显示可恢复错误，不应导致启动崩溃。

编译、模拟器运行、设备运行、SideStore 安装与真实校园账号分别记录在 [功能矩阵](ios-feature-matrix.md)。真实短信、校园网/校外路由、掉线恢复和重签后的 Keychain 仍需目标设备验证。卸载或更换签名团队后不保证旧 Keychain 状态可恢复。

本 Fork 保留原版权与 [GPL-3.0](../LICENSE)。分发 IPA 时需提供相应源码、许可证和修改说明。
