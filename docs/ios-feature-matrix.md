# iOS 功能与验证矩阵

本表区分代码实现、离线协议测试、模拟器运行、真机运行和真实校园账号。iOS/iPadOS Fork 的首个可编译版本已具备账号、课表、成绩、首页与设置；真实 CAS/MFA、校外 WebVPN 和教务数据仍需本人在设备上验收。模拟器交互测试只输入合成账号，未提交登录。

## 功能范围

| 级别 | 功能 | 已实现及本次证据 | 尚未验证或未接入 |
|---|---|---|---|
| P0 | UIKit 宿主与共享 UI | SceneDelegate 承载同一个 ComposeUIViewController；复用账号、首页、课表、成绩、设置、MIUIX 与原图标 | 真机触感、分屏和内存压力 |
| P0 | CAS 与 RSA | KMP 直连状态机、Ksoup DOM 表单解析、标准 RSA/PKCS#1；JVM 与 Native 测试通过 | 真实校园登录 |
| P0 | 两种短信 MFA | detect 与 safety 分支、gid、原地址/Referer 恢复提交、三次/150 秒限制；fixture 通过 | 真实短信收发、验证码及学校风控；没有绕过流程 |
| P0 | 直连与 WebVPN | 用户明确选择路由；网关与业务认证分离；有效代理落地、过期恢复及一次重放测试通过 | 校内/校外真实网络；尚无自动探测切换 |
| P0 | 会话与安全存储 | 按账号、路由、业务站隔离 cookie/token；恢复、过期、退出、锁定存储错误及后台禁止触发 MFA 均有测试 | 真机冷启动、锁屏、重签/团队变化后的恢复 |
| P0 | Keychain 与私有缓存 | iPhone/iPad App 宿主均完成真实 Keychain 往返、跨实例读取、类型、删除和命名空间隔离 | 设备缓存容量、长时间离线及系统回收 |
| P0 | 基础身份 | 显示账号 NetID | 姓名、真实学号、院系接口未接入；不把 NetID 冒充学号 |
| P0 | 课表 | JWXT 学期、周次、课程详情、教师/教室、调停课、刷新、带时间戳缓存；复用共享模型、算法与页面 | 真实账号数据及真实离线重启 |
| P0 | 自建日程与退出隔离 | 共享存储适配器；课表/考试/自建日程/调课快照进入 Keychain；退出撤销旧任务写入权限；并发和迟到写入测试通过 | 大量真实数据下的性能 |
| P0 | 成绩 | JWAPP 学期筛选、学分、GPA、课程明细、无明细响应与缓存；共享页面和计算器 | 真实成绩/GPA 与明细权限 |
| P0 | 首页、设置与交互 | 仅开放已接入路由；中文导航、合成输入、密码遮罩、键盘、浅深色切换、横屏通过模拟器测试 | 无障碍完整审计、真机输入法和触感 |
| P1 | 考试只读 | 已纳入课表学期视图，离线 fixture 通过 | 真实考试安排 |
| P1 | 校园卡、图书馆、通知、思源学堂、下载、多账号管理 | 尚未接入，首页不展示入口 | 待 P0 真实账号验收后推进 |
| P1 | 教材、成绩报告、档案导入/导出 | 未接入；宿主隐藏入口并关闭未支持的教材请求 | 后续单独实现 |
| P1/P2 | 校园写操作 | 未开放 | 预约、取消、签到、支付等须逐项验证 |
| P2 | AI、交晓智、论坛、场馆、付款码、体测等 | 未接入，不提供空白页面 | 后续范围 |

## 构建与运行证据

| 项目 | 本次结果 | 证据或边界 |
|---|---|---|
| 基线审计 | 完成 | `23b835c5716d38edfd0ff046c518de3804c1f70e`，`feat/ios-port`；Fork origin 与原 upstream 保留 |
| `iosSimulatorArm64` | 通过 | Debug 静态 framework 与 UIKit 宿主实际构建、启动 |
| `iosArm64` | 通过 | Release framework 与 generic/platform=iOS 设备应用；打包脚本检查 `arm64` 和 `platform IOS` |
| JVM 回归 | **590 项通过** | campus 47、model 208、schedule 50、score 12、旧 shared:data 273；无失败/跳过 |
| Native 回归 | **44 项通过** | 通用协议 fixture 与 Security.framework RSA/CSPRNG；无失败/跳过 |
| iPhone | 通过 | 常规 iPhone 14 / iOS 27.0；交互与 App 宿主 Keychain；合成输入，不访问学校 |
| iPad | 通过 | iPad Pro 13-inch (M4) / iPadOS 27.0；宽屏、键盘、主题与 App 宿主 Keychain |
| 宽屏与旋转追加回归 | 两端通过 | 修正登录框限宽顺序；输入框实际宽度、横屏窗口宽于高及稳定后的截图均验证。Duo/iOS 27.1 双屏横屏断言仍超时，不宣称该场景支持 |
| Android Debug APK | 通过 | 最终限宽修复后 `:app:assembleDebug` 再次成功，APK 53,089,506 字节 |
| 未签名设备 IPA | 通过 | Release、`arm64`、`platform IOS`、iOS 16.0、iPhone/iPad；16,691,631 字节，ZIP 校验通过；见[验收记录](ios-validation.md#设备产物)。仍需本人重签 |
| 远程 CI | 仅配置，未运行 | 使用官方 Xcode 27 arm64 preview 镜像并固定 Xcode 27.1；不宣称远程通过 |
| 真机启动 / SideStore 安装 | 未验证 | 缺本人设备配对及签名/重签操作 |
| 真实 CAS / WebVPN / 课表 / 成绩 | 未验证 | 须本人在 App 内登录、正常完成必要 MFA |

## 安全与架构边界

[iosShared](../iosShared/build.gradle.kts) 只依赖选定的共享 UI、模型、导航、网络和新的 [shared:campus](../shared/campus/build.gradle.kts)，不引用 JVM 的 shared:data、core、data 或 server。设备直接访问学校，不要求桌面客户端、Node/JVM 服务或个人代理。

账号密码、cookie、令牌和私有缓存存入 iOS Keychain；普通偏好不承担凭据存储。Darwin 引擎使用 ephemeral session，关闭系统 cookie 和 URLCache，保留系统 TLS 与 ATS 验证。cookie 以账号、路由、业务站及域/路径/安全标志/期限匹配；退出同时撤销旧缓存句柄并清理账号数据。协议日志不输出凭据或个人数据。

新协议层位于 commonMain，平台差异集中在引擎、RSA/CSPRNG 和凭据存储。原 Android/JVM 适配器暂时保留，避免在未完成真实 iOS 验收前替换成熟登录路径；共享模型、课表调整和 GPA 算法继续复用。后续应按 Source 逐项切换到该公共协议层，再删除旧适配器，不能长期独立修补两份同一业务协议。

[构建说明](ios-build.md)、[SideStore 安装](ios-sidestore.md)、[源码审计与取舍](ios-port-plan.md)和[本次验收记录](ios-validation.md)包含命令、结果与未验证原因。
