# SideStore 安装与验证

本应用面向 iOS/iPadOS 16.0 及以上设备，使用 Kotlin/Native 预编译代码，不需要 JIT。SideStore 的系统最低版本与本应用最低版本是两件事：以本应用的 16.0 为准。

以下步骤参考 2026-10-29 阅读的 [SideStore 官方前置条件](https://docs.sidestore.io/docs/installation/prerequisites)、[安装说明](https://docs.sidestore.io/docs/installation/install) 和 [FAQ](https://docs.sidestore.io/docs/faq)。SideStore 的安装工具与本机 VPN 方案可能变化，应优先按当前官方说明操作。

## 准备设备

按照官方文档完成 SideStore 自身安装、本人 Apple Account 登录和设备配对。当前官方前置条件要求有密码锁的设备、Wi-Fi，以及用于安装/刷新时连接设备内部服务的 LocalDevVPN。Apple 账号、配对文件和二次验证码都由本人在本机处理，不应提交到本仓库或发到聊天中。

免费 Apple Account 的官方限制包括同时三个活跃应用（包含 SideStore）、每周十个 App ID，以及通常七天的开发签名有效期。安装后需要在到期前刷新；启用 SideStore 的自动刷新不能替代检查是否实际成功。具体限额、付费开发者账号与异常处理见官方 FAQ。

## 导入本应用

1. 按 [构建说明](ios-build.md) 运行设备 IPA 打包命令。脚本必须成功，且设备平台检查通过。
2. 把 `build/ios/XJTUToolbox-unsigned.ipa` 传入设备的“文件”应用。
3. 打开 SideStore 的 My Apps，通过“+”选择该 IPA，使用本人账号重新签名并安装。安装或刷新期间遵守 SideStore 当前的 Wi-Fi 和 LocalDevVPN 要求。
4. 按系统提示启用必要的开发者模式、信任开发者，并打开“岱宗盒子”。开发者模式和信任路径随系统版本变化。

未签名 IPA 是待重签的设备包。设备 arm64 和模拟器 arm64 的可执行文件平台标记不同，模拟器构建不能用于 SideStore。脚本已检查这一点；打包成功也不代表已完成 SideStore 安装。

## 首次运行检查

先检查首页、课表、成绩、我的、设置能否切换，中文和图标是否正常。再在应用内输入校园账号，选择“校园直连”或“WebVPN”。需要短信时只在应用内输入验证码。不要把真实账号、密码、cookie、令牌、课表或成绩截图作为公开 issue 附件。

SideStore 的 LocalDevVPN 用于安装和刷新；本应用的“WebVPN”是学校 WebVPN 的 HTTPS 路由。两者用途不同。真实账号需要分别测试校园网直连与校外 WebVPN，不应仅凭安装成功判断学校访问成功。

建议在真机记录以下结果：

- 密码登录、学校短信 MFA、取消和错误验证码的行为。
- 退出重开后的会话恢复；过期后重新登录；退出后对应账号数据清理。
- 课表学期/周切换、课程详情、调停课、刷新失败后的缓存；成绩学期、GPA、明细与离线查看。
- iPhone 键盘、安全区、横竖屏；iPad 分栏与弹出面板；浅色和深色模式。
- 七天内刷新及一次覆盖更新后的启动、缓存和 Keychain 状态。

## 失败时

“无法验证/完整性”通常需要先核实签名、描述文件、开发者信任和系统要求；不能通过替换成模拟器 IPA 解决。安装失败先查 SideStore 的 [常见问题](https://docs.sidestore.io/docs/troubleshooting/common-issues)。应用能启动但登录失败时，区分网络、学校认证、短信和页面协议变化；记录已脱敏的错误文案和操作顺序即可。

当前验收状态见 [功能矩阵](ios-feature-matrix.md)。尚未在本人设备实际完成安装与刷新前，应保持“SideStore 未验证”。
