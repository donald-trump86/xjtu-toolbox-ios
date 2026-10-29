# 本次 iOS 移植验收记录

日期：2026-10-29。分支为 `feat/ios-port`，起点为 `23b835c5716d38edfd0ff046c518de3804c1f70e`；本次实现提交为 `83eb536f`。本记录只描述实际执行过的构建与测试；模拟器成功不等于真机、SideStore 或真实校园账号成功。

## 构建环境与命令

本机为 Apple Silicon，Xcode 27.1（27A9275），JDK 21.0.13，Gradle wrapper 9.6.1。沿用仓库 Kotlin 2.4.20、Compose 1.12.1、Ktor 3.6.0、AGP 9.4.1；新增 DOM 库 Ksoup 0.2.6。JDK、Gradle/Native 缓存和 Android SDK 放在仓库忽略目录；没有通过改写 HOME 或关闭 TLS 解决构建问题。

```sh
# 两种 Apple 目标；模拟器与设备不混用。
sh scripts/gradle-ios.sh \
  :iosShared:linkDebugFrameworkIosSimulatorArm64 \
  :iosShared:linkReleaseFrameworkIosArm64

# 新协议与现有逻辑回归。
sh scripts/gradle-ios.sh \
  :shared:campus:jvmTest :shared:model:jvmTest \
  :feature:schedule:jvmTest :feature:score:jvmTest :shared:data:jvmTest

# Native 协议、DOM、WebVPN 与 Security.framework。
sh scripts/gradle-ios.sh :shared:campus:iosSimulatorArm64Test \
  -PiosSimulatorDevice=966E47E3-5064-493A-9CE1-CF0F0E9F998F

# 本地 ad-hoc 应用身份，不使用 Apple Account。
sh scripts/test-ios-simulator.sh 367B2A3A-C5B5-4254-8563-DF618D57A5DB
sh scripts/test-ios-simulator.sh D2376C90-F526-4ED5-B227-4CD67486B388

# Android 与真正的设备 IPA。
sh scripts/gradle-ios.sh :app:assembleDebug
sh scripts/build-ios-ipa.sh unsigned
```

## 已取得的结果

| 验证 | 结果 |
|---|---|
| JVM：shared:campus | 47 项通过 |
| JVM：shared:model | 208 项通过 |
| JVM：feature:schedule | 50 项通过 |
| JVM：feature:score | 12 项通过 |
| JVM：旧 shared:data | 273 项通过 |
| JVM 合计 | **590 项，无失败、错误或跳过** |
| Native campus | **44 项，无失败、错误或跳过**；通用协议与 Security.framework RSA/CSPRNG |
| App 宿主 Keychain | iPhone/iPad 均实际通过；字符串、整数、布尔、集合、跨实例恢复、删除及命名空间隔离 |
| iPad Pro 13-inch (M4)，iPadOS 27.0 | 通过；宽屏导航、输入限宽、合成输入与密码遮罩、键盘、浅深色切换、实际横屏尺寸 |
| 常规 iPhone 14，iOS 27.0 | **通过**；Keychain 0.017 秒、完整交互 25.564 秒；包含输入限宽及实际横屏尺寸断言 |
| Android Debug | 最终限宽修复后再次通过；APK 53,089,506 字节 |
| 设备 Release IPA | 最终构建通过；16,691,631 字节，设备 `arm64` / `IOS`，ZIP CRC 校验通过 |

协议测试包含 CAS 两种短信分支、Safety 原地址/Referer 提交、错误/超时/次数限制、后台禁止触发 MFA、站点独立认证、网关合法代理落地、网关过期恢复、一次重放上限、严格重定向和 cookie 作用域、课表与成绩解析、无成绩明细、账号退出清理、跨实例索引并发、失败写入和退出后迟到写入。测试只使用可审计 fixture 与合成密钥。

## 过程中发现的问题及处理

- Objective-C category/枚举导入与 Native 类型错误已按实际编译器反馈修正；随后两个 Apple 目标构建通过。
- AGP 与 Skiko 默认写入宿主目录被沙箱阻止。使用工具本身支持的 ANDROID_USER_HOME、Gradle/Native 路径及 skiko.data.path，把测试输出留在仓库；没有绕过文件拒绝。
- 原有两组 JVM 测试在安装隔离数据根前创建 secure store。改为惰性创建后，旧 shared:data 的 273 项全部通过。
- 实际启动发现当前 SDK 要求 UIScene。增加标准 SceneDelegate 和 manifest 后，宿主启动并完成交互测试。
- 裸 Native 命令行进程访问 Keychain 返回 `-25291`；完全不签名的宿主返回 `-34018`。Keychain 验证移到真实 App 宿主，模拟器使用本地 ad-hoc 身份与仅模拟器适用的访问组，已通过。没有降级为明文存储，也没有忽略失败的测试。
- 复核修复了 WebVPN 网关与业务落地混淆、过期恢复缺口、账户缓存索引并发丢失、退出后的旧任务写回，以及离开账号页会取消认证协程的问题，并增加对应回归。
- 截图发现共享登录页的限宽顺序无效。先 widthIn 再 fillMaxWidth 后，iPad 输入框保持合理宽度；测试增加实际宽度断言。
- 横屏截图曾截在系统动画中途。测试现等待窗口宽于高并等待动画结束；iPad 和常规 iPhone 14 均已通过。iPad 的 XCUIApplication 裁剪截图仍会使用旧竖屏画布，因此改用 XCUIScreen 的屏幕级截图；最后一次结果已目视核实完整横屏与限宽布局。Duo/iOS 27.1 的增强横屏断言超时，双屏横屏仍未解决，不能用其早期只检查元素存在的通过结果宣称完整旋转支持。
- Xcode 测试启动曾被宿主以 `Pseudo Terminal Setup Error / Errno 1` 阻止；经明确放行后重跑。截图导出也曾因 Xcode TestReport 缓存权限被拒，经放行后导出，没有改用绕过方式。

## 设备产物

最终文件：`build/ios/XJTUToolbox-unsigned.ipa`，**16,691,631 字节**。SHA-256：

```text
2d122fd06c1a33bbe095c13a6a8ac05d2d63bdc7a23c8455195067a240c9ac11
```

脚本与最终核查确认可执行文件为 `arm64`、Mach-O 平台为 `IOS`，最低系统 `16.0`，设备系列 `[1, 2]`；包内为 `Payload/XJTUToolbox.app`，显示名称“岱宗盒子”，bundle ID 为 `me.luvch1nabest.xjtutoolbox`。ZIP CRC 校验通过，未携带证书或描述文件。该 IPA 未用开发者身份签名，须由 SideStore 或本人本机开发者身份重签；设备编译不等于真机启动。

最终本机结果包保存在忽略目录，可由 Xcode 打开：

- iPhone 14：`build/ios/xctest-D2376C90-F526-4ED5-B227-4CD67486B388-20261029-214424.xcresult`。
- iPad：`build/ios/xctest-367B2A3A-C5B5-4254-8563-DF618D57A5DB-20261029-215432.xcresult`；最后一次也验证了屏幕级截图，Keychain 0.025 秒、完整交互 25.800 秒。

日志为 `build/ios-simulator-iphone14.log`、`build/ios-simulator-ipad.log`、`build/android-final-regression.log` 和 `build/ios-final-device-ipa.log`。结果包与日志未提交源码仓库，重新测试会生成新的时间戳。

## 真实账号与设备边界

没有本人已配对的 iPhone/iPad、Apple Account 签名授权或主动授权的校园账号。因此未执行真实 CAS/MFA、校园网/校外 WebVPN、真实课表/成绩、真机冷启动、SideStore 安装/刷新与重签后的 Keychain 恢复。密码和短信应由本人在 App 内输入，不应发到聊天或测试命令。

基础身份目前只有 NetID；姓名、真实学号和院系接口尚未接入。校园卡、图书馆、通知、思源学堂、下载、多账号 UI 及 P2 功能没有开放入口。完整范围见[功能矩阵](ios-feature-matrix.md)。

## CI 与源码交付

[iOS CI](../.github/workflows/ios.yml) 配置了双目标、JVM/Android、Native 和 iPhone/iPad 宿主/交互测试，以及手动选择的未签名 IPA artifact。采用官方 [Xcode 27 arm64 镜像](https://github.com/actions/runner-images/blob/main/images/macos/xcode-27-arm64-Readme.md)，固定 Xcode 27.1；该镜像当前为 public preview。本次未推送、未运行远程 CI、未创建公开 Release，不能把配置存在当作 CI 通过。

Fork 保留原版权与 GPL-3.0。分发 IPA 时应同时提供相应源码、许可证和修改说明；[SideStore 指南](ios-sidestore.md)说明了本人安装与刷新所需操作。
