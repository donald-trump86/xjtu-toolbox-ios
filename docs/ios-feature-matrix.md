# iOS 功能与验证矩阵

当前分支仅维护 iPhone / iPad，最低系统为 16.0。代码实现、模拟器验证、真机与真实校园账号分开记录。协议测试使用合成 fixture；交互测试输入合成账号且不提交登录。

## 当前功能

| 功能 | 已实现与证据 | 验收边界 |
| --- | --- | --- |
| UIKit 与 Compose | SceneDelegate 承载 ComposeUIViewController；账号、首页、课表、成绩和设置共用 Compose / MIUIX 页面 | 真机触感、分屏与内存压力待验证 |
| 应用图标 | 新绘蓝白 / 米金色腾飞塔与书页，1024 × 1024 不透明资源已打入设备包 | 非学校官方标识 |
| CAS / RSA | 设备直连、Ksoup DOM 表单、标准 RSA / PKCS#1；Native fixture 与 Security.framework 测试通过 | 真实校园登录待验证 |
| 短信 MFA | detect 与 safety 分支；gid、原地址 / Referer、三次 / 150 秒限制；后台不主动发送短信 | 真实短信与学校风控待验证 |
| 直连 / WebVPN | 用户选择路由；网关与业务认证分离；合法代理落地、过期恢复和一次重放有回归 | 校内 / 校外网络待验证；无自动路由切换 |
| 会话与退出 | 账号、路由、业务站隔离 Cookie / token；恢复、过期、锁定错误、退出与迟到写入有测试 | 真机冷启动、锁屏与重签恢复待验证 |
| Keychain | iPhone / iPad 宿主均完成真实往返、跨实例读取、类型、删除与命名空间隔离 | 设备容量、长期离线及系统回收待验证 |
| 基础身份 | 当前显示 NetID | 姓名、真实学号与院系未接入 |
| 课表 | 学期 / 周切换、教师 / 教室、课程详情、调停课、考试、自建课程、刷新与时间戳缓存 | 真实教务数据与离线重启待验证 |
| 成绩 | 学期、学分、GPA、明细、无明细响应及缓存；页面状态与算法运行 Native 测试 | 真实成绩、GPA 与明细权限待验证 |
| 交互与设置 | 中文、合成输入、密码遮罩、键盘、登录限宽、浅深色和实际横屏尺寸 | 完整无障碍与真机输入法 / 触感待验证 |

校园卡、图书馆、通知、思源学堂、教材、成绩报告、档案导入 / 导出、多账号管理、AI、社区、场馆和支付等业务未在本分支接入，界面不提供空白入口。校园预约、取消、签到等写操作也未开放。

## 本次 iOS-only 清理后的证据

| 项目 | 结果 | 范围 |
| --- | --- | --- |
| 构建结构 | 通过 | 10 个模块；Kotlin 库仅 `iosArm64` / `iosSimulatorArm64`；旧平台模块及 source set 已移除 |
| 模拟器 framework | 通过 | Debug `iosSimulatorArm64` 与 UIKit 宿主实际构建 |
| 设备 framework / App | 通过 | Release、`generic/platform=iOS`、设备 `arm64` / `IOS` |
| Native 回归 | **260 项通过** | campus 46、model 103、navigation 13、designsystem 27、home 21、schedule 45、score 5；无失败、错误或跳过 |
| iPhone 14 / iOS 27.0 | 通过 | 清理后重新运行交互与 App 宿主 Keychain；合成输入，不访问学校 |
| iPad Pro 13-inch (M4) / iPadOS 27.0 | 通过 | 清理后重新验证宽屏、输入限宽、键盘、主题、横屏与 App 宿主 Keychain |
| 未签名设备 IPA | 通过 | 18,488,179 字节；ZIP 校验通过，新图标资源与构建一致；需本人重签 |
| 远程 CI | 仅配置 | iOS 双架构、Native、iPhone / iPad 与可选设备 IPA；未推送或远程运行 |
| 真机 / SideStore | 未验证 | 需要本人设备配对和签名 / 重签 |
| 真实 CAS / WebVPN / 课表 / 成绩 | 未验证 | 由本人在 App 内正常登录并完成 MFA |

Duo / iOS 27.1 的双屏横屏断言在前次移植验收中超时，当前不宣称支持该场景。常规 iPhone 与 iPad 是本次交互基线。

## 架构与数据

[iosShared](../iosShared/build.gradle.kts) 只连接选定的 UI、模型、导航、设计系统和 [shared:campus](../shared/campus/build.gradle.kts)。协议位于 `commonMain`，平台实现集中在 Darwin 引擎、Security.framework RSA / 随机数和 Keychain；保留的 `commonTest` 在 iOS 模拟器运行。Gradle 的 JDK 用于构建工具，不是额外应用平台。

账号凭据、Cookie、令牌与个人缓存进入 Keychain；普通偏好不存储凭据。Darwin 使用 ephemeral session，关闭系统共享 Cookie / URLCache，保留系统 TLS / ATS 验证。Cookie 按账号、路由、业务站、域、路径、Secure 标志和期限匹配。退出撤销旧缓存句柄并清理账号数据，拒绝旧任务写回。认证日志不输出凭据或个人数据。

当前协议只有这一份 Native 共用实现，旧 Android / JVM 网络适配器及其他平台工程已从分支移除；上游版权与历史保留。命令、产物校验和最终结果见 [构建说明](ios-build.md)、[SideStore 指南](ios-sidestore.md) 和 [验收记录](ios-validation.md)。
