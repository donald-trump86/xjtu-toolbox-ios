# 使用 SideStore 安装岱宗盒子

本教程适用于 **iOS / iPadOS 16.0 及以上**，使用免费 Apple Account 在个人设备上侧载本 Fork 的 IPA。首次配置需要电脑，此后可用 SideStore 在设备上刷新签名。

SideStore 的安装方式会变化。以下步骤对照了 2026-10-29 的 [官方前置条件](https://docs.sidestore.io/docs/installation/prerequisites)、[安装教程](https://docs.sidestore.io/docs/installation/install) 和 [FAQ](https://docs.sidestore.io/docs/faq)；界面有差异时以对应版本官方文档为准。

## 一、准备所需材料

| 材料 | 用途 |
| --- | --- |
| iPhone / iPad，已设置锁屏密码 | 安装和运行应用；本应用最低要求系统 16.0 |
| 一台 Mac、Windows 或 Linux 电脑及 USB 数据线 | 首次用 iloader 配置 SideStore 和设备配对 |
| Apple Account，可使用免费账号 | SideStore 用于为你自己的应用签名 |
| Wi-Fi | 安装和刷新 SideStore 应用时使用 |
| [LocalDevVPN](https://apps.apple.com/us/app/localdevvpn/id6755608044) | SideStore 当前官方要求的本机安装/刷新通道，从 App Store 安装 |
| `XJTUToolbox-unsigned.ipa` | 本应用的 iOS **设备版**安装包 |

Apple Account、验证码、设备配对文件由你本人在官方工具中处理。它们与稍后在岱宗盒子中使用的校园账号不同。

### 获取正确的 IPA

如果已有本 Fork 的构建产物，将 `XJTUToolbox-unsigned.ipa` 保存到设备的“文件”应用。也可在 Apple Silicon Mac 上从源码生成：

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
sh scripts/build-ios-ipa.sh unsigned
```

产物位于 `build/ios/XJTUToolbox-unsigned.ipa`。完整环境和 CI 打包选项见 [构建说明](ios-build.md)。`unsigned` 表示等待 SideStore 重签，不能直接用“文件”应用点开安装。

如果同时提供了 `.sha256` 文件，可以在电脑中核对下载是否完整：

```sh
shasum -a 256 -c XJTUToolbox-unsigned.ipa.sha256
```

构建脚本会验证最终可执行文件为设备 `arm64`、平台为 `IOS`。模拟器版本即使也是 arm64，也不能安装到真机。

## 二、首次安装 SideStore

1. 在 iPhone / iPad 的 App Store 安装 **LocalDevVPN**。
2. 从 [SideStore 官方安装教程](https://docs.sidestore.io/docs/installation/install) 所给入口下载 **iloader**，安装到电脑并启动。
3. 用 USB 连接设备，解锁，在设备上选择“信任此电脑”；按提示输入设备锁屏密码。
4. 在 iloader 中选择正确设备，按向导选择安装 SideStore。Apple Account 与双重认证均在工具/设备的对应界面中完成。
5. 按 iloader 提示完成配对、安装以及必要的设备操作。保留个人配对文件，不要将它提交到仓库或附在 Issue 中。
6. 在设备中进入 **设置 → 通用 → VPN 与设备管理**，找到此次安装使用的开发者账号并信任。系统版本不同，菜单名称可能略有变化。
7. 若系统提示需要开发者模式，进入 **设置 → 隐私与安全性 → 开发者模式**，开启并按系统提示重启、解锁和确认。
8. 连接 Wi-Fi，打开 LocalDevVPN，按提示允许 VPN 配置并连接，再打开 SideStore，完成其登录/首次设置。
9. **先刷新 SideStore 自身，再安装其他 IPA。** 官方 FAQ 说明 iloader 初装时可能使用不同的 App Group 标识，首次刷新会将其切换为 SideStore 的标识。

首次配置成功后，日常刷新通常不需要电脑。配对失效、系统更新或设备重置后，可能需要重新连接电脑处理。

## 三、导入本应用

1. 保持 Wi-Fi 和 LocalDevVPN 已连接。
2. 在“文件”应用中确认 IPA 已完整下载到本地，而不是仍显示云端下载图标。
3. 打开 SideStore，进入 **My Apps / 我的应用**，点 **+**。
4. 选择 `XJTUToolbox-unsigned.ipa`，等待 SideStore 完成签名和安装。
5. 回到主屏幕，打开新蓝色腾飞塔图标的 **岱宗盒子**。
6. 若再次出现开发者信任或开发者模式提示，按上一节对应步骤完成后再打开。

SideStore 使用 Apple Account 为 IPA 重签；校园账号此时尚未参与任何认证。

## 四、首次校园登录

1. 打开应用的 **“我的”** 页面，输入校园 NetID 和密码。
2. 在“校园直连 / 学校 WebVPN”之间选择与你当前网络相符的方式。直连需要能够访问相关学校服务；校外可尝试学校 WebVPN。
3. 点击登录。若学校要求短信 MFA，等待应用显示验证对话框，再输入本人收到的验证码。
4. 登录后分别打开课表与成绩，测试学期/周切换、详情和手动刷新；再断网检查已获取数据的缓存展示。
5. 退出账号后应清除该账号会话和个人缓存；重新登录后再验证恢复行为。

**LocalDevVPN 与学校 WebVPN 的用途不同。** 前者服务于 SideStore 的安装/刷新，后者是应用访问学校系统的一种路由。LocalDevVPN 显示已连接，不代表校园认证已成功。

当前基本信息仅展示 NetID；姓名、学号、学院尚未接入。真实账号访问和真机上的完整流程仍需由使用者实测，详见 [功能矩阵](ios-feature-matrix.md)。

## 五、续签和覆盖更新

### 免费账号限制

根据 SideStore 官方 FAQ，免费 Apple Account 通常有以下限制：

- 开发签名有效期 **7 天**。到期前刷新应用，**SideStore 自身也要刷新**。
- 同时最多 **3 个活跃侧载应用**，其中包括 SideStore。LocalDevVPN 从 App Store 安装，不占这一侧载名额。
- 每周最多创建 **10 个 App ID**。扩展和不同 Bundle ID 也可能消耗额度，以 SideStore 实际显示为准。

### 日常刷新

1. 连接 Wi-Fi，开启 LocalDevVPN。
2. 打开 SideStore 的 My Apps，查看 SideStore 与岱宗盒子的剩余天数。
3. 使用 **Refresh All / 全部刷新**，等待两者均刷新成功。
4. 可按官方说明启用后台刷新，但仍应定期查看剩余天数，不能把后台刷新视为一定成功。

### 安装新版 IPA

保留设备上现有应用，在 SideStore 中导入同一应用的新版 IPA 并覆盖安装。尽量保持相同 Apple Account、Bundle ID 与签名身份，以保留数据及 Keychain 访问条件。

覆盖更新通常可以保留数据，但签名身份、重签工具或系统变化可能影响 Keychain 访问。不要为了普通更新先删除旧应用；更换账号/身份时应做好重新登录的准备。

如果 SideStore 自身已经到期打不开，使用电脑和官方 iloader 流程恢复 SideStore，然后重新刷新应用。

## 六、常见问题

| 现象 | 排查方式 |
| --- | --- |
| 无法验证应用、提示开发者不受信任 | 检查开发者信任、开发者模式、网络和签名剩余时间；签名过期先在 SideStore 刷新 |
| SideStore 安装/刷新超时 | 检查 Wi-Fi、LocalDevVPN 是否连接、SideStore 是否能刷新自身；对照 [官方故障排查](https://docs.sidestore.io/docs/troubleshooting/common-issues) |
| 达到 3 个应用或 10 个 App ID 限制 | 在 SideStore 中核对已占名额和 App ID；按官方说明管理或等待额度恢复 |
| 提示配对文件无效 | 按官方流程重新连接电脑配对；系统更新/重置可能使旧配对失效 |
| Apple Account 登录异常或 Anisette 错误 | 查看 SideStore 官方 FAQ/服务状态，使用其官方支持的服务设置；这与学校 CAS 无关 |
| IPA 安装失败或架构不支持 | 确认来自设备构建，最低系统 16.0，文件下载完整；不要使用模拟器 `.app` 或 framework |
| 更换重签账号后 Keychain 报错 | 核对签名身份变化，记录脱敏错误码；先解决签名/访问条件，再在应用中重新登录 |
| 应用可以打开但课表/成绩请求失败 | 检查校园网络/学校 WebVPN、会话状态及学校接口错误；SideStore 安装成功只证明应用已安装 |
| 应用到期无法打开 | 先恢复 SideStore，再刷新本应用；必要时按官方流程从电脑重新配置 |

反馈时提供机型、系统、SideStore/应用版本、复现步骤和脱敏错误文字。请勿上传 Apple Account 密码、验证码、校园密码、Cookie、令牌、配对文件、证书或完整个人课表/成绩。

## 已验证范围

工程已做过离线协议、真实 Security.framework、应用宿主 Keychain，以及 iPhone / iPad 模拟器界面验证；最新结果见 [验证记录](ios-validation.md)。**尚未在真实设备上完成 SideStore 重签安装或使用真实校园账号验收。**

本教程介绍 SideStore 的操作流程，不代表本应用已完成上述真机验证。
