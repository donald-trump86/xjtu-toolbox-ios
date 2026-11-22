<p align="center">
  <img src="iosApp/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png" width="112" alt="应用图标：交小西 Q 版形象" />
</p>

# XJTU Toolbox for iOS

**这个 Fork 主要为了在 iPhone 和 iPad 上使用 XJTU Toolbox（岱宗盒子）。** 它基于 [yeliqin666/xjtu-toolbox-android](https://github.com/yeliqin666/xjtu-toolbox-android)，复用 Kotlin / Compose 页面与领域逻辑，使用轻量 UIKit 宿主。当前主分支 `main` 只维护 iOS / iPadOS，最低系统版本为 **16.0**。

课表、成绩和校园认证直接连接学校系统。凭据、会话与个人缓存保存在设备 Keychain，运行时不需要自建后端。

**[SideStore 侧载教程](docs/ios-sidestore.md)** · **[从源码构建](docs/ios-build.md)** · **[功能与验证状态](docs/ios-feature-matrix.md)** · **[测试记录](docs/ios-validation.md)**

## 安装到 iPhone / iPad

使用 **SideStore** 安装设备版 IPA。首次配置需要一台电脑、USB 数据线、Apple Account、Wi-Fi，以及 LocalDevVPN；之后可在设备上刷新签名。

1. 按 [完整教程](docs/ios-sidestore.md) 安装并初始化 SideStore，完成开发者信任与开发者模式设置。
2. 获取设备版 `XJTUToolbox-unsigned.ipa`，保存到设备“文件”应用。本机生成方法见下方；CI 也提供手动打包选项，执行成功后可下载 artifact。
3. 连接 Wi-Fi，打开 LocalDevVPN，在 SideStore 的 **My Apps → +** 中选择 IPA，重签并安装。
4. 打开“岱宗盒子”，在“我的”中输入校园账号，选择校园直连或学校 WebVPN。短信验证在应用内完成。
5. 免费 Apple Account 的签名通常 **7 天到期**，到期前在 SideStore 刷新。SideStore 自身也要刷新。

教程包含首次安装、IPA 导入、覆盖更新、续签和常见故障。SideStore 用于安装/续签的 LocalDevVPN 与应用访问学校的 WebVPN 是两项独立功能。

## 当前功能

| 功能 | 本分支范围 |
| --- | --- |
| 账号 | CAS、短信 MFA、校园直连 / WebVPN、会话恢复与退出清理；当前显示 NetID |
| 课表 | 学期与周切换、课程详情、教师/教室、调停课、考试、自定义课程、刷新与离线缓存 |
| 成绩 | 学期成绩、学分、GPA、成绩明细、刷新与离线缓存 |
| 界面 | 中文、浅色/深色、触感反馈、iPhone 与 iPad 布局、键盘与安全区 |

学校接口可能调整。模拟器和离线样本测试不等同于真实账号验证；真机、SideStore 重签、校园认证和个人数据的实际可用性分别记录在 [功能矩阵](docs/ios-feature-matrix.md)。上游的其他功能不自动属于 iOS 版本。

## 与上游的同步

本 Fork 从上游 tag `v5.2.1`（提交 `23b835c5`）分出，之后只维护 iOS / iPadOS。v0.2.0（build 2）重新同步了一批上游可移植的改动，逐项对应如下。左列是上游提交，中间是同步的内容摘要，右列是该改动在本分支的实际表现。

| 上游提交 | 内容 | iPhone / iPad 上能看到的变化 |
| --- | --- | --- |
| `1b0be2dc`、`1ff1d66d` | 课表左轴改「周一…周日」、日期改「9/1」，没课的节次不再压扁，午休 / 晚休空着时整行消失；「更多」菜单把「切换学期」移到最后一行 | 课表页与周视图排版随之变化 |
| `12b1070d` | 屁岱卡右下角增加「关闭」与「不再提示」 | 代码已同步；iOS 版没有接入洞察数据源，卡片不会出现，界面上看不到这两个按钮 |
| `f7f23fe1`、`f0cfad9d` | 首页自定义背景可调模糊（0–10）与亮度（20–100%）；宽屏弹窗被限高时先压列表、不裁底部按钮 | 长按首页头卡进入的外观对话框可调模糊与亮度，横屏平板上按钮完整；底栏配置入口 iOS 未开放，那处修正界面上看不到 |
| `f5b5fc85`、`943e1c5e`、`a40ccb34` | 致谢移入二级页并补贡献者、许可证与图标授权分组，加滑入滑出过渡（上游同时把小游戏一并移入） | 设置 →「关于」→「致谢与开源许可」；本 Fork 没有小游戏模块，只同步致谢部分 |
| `c69dd0b9`、`8a512f6a` | 应用图标换成「交小西」Q 版形象，补授权声明与 GPL 边界说明 | 桌面图标；授权说明见[第三方声明](THIRD_PARTY_NOTICES.md) |
| `5dec9789` | 电子成绩单入口副标题改为说明用途，去掉「下载分享」 | 只有文案对齐；iOS 版未接入该入口，界面不新增内容 |

同步范围以 iOS 界面能落地的部分为限。课表「更多」菜单里的桌面壁纸、上课自动勿扰和「导出日历 (ICS)」依赖本分支没有实现的能力（`ScheduleCapability` 未 honor），iOS 界面照旧不画这些入口，详情见 [功能矩阵](docs/ios-feature-matrix.md)。

## 自行构建 IPA

需要 Apple Silicon Mac、**Xcode 27.1、JDK 21、XcodeGen**。首次构建会下载 Gradle 和 Kotlin/Native 依赖。

```sh
git clone --branch main https://github.com/donald-trump86/xjtu-toolbox-ios.git
cd xjtu-toolbox-ios
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
brew install xcodegen
sh scripts/build-ios-ipa.sh unsigned
```

产物为 `build/ios/XJTUToolbox-unsigned.ipa`，需在 SideStore 中重签。脚本会核对可执行文件是 **iOS 设备 arm64**；同为 arm64 的模拟器产物不能用于侧载。

已有开发者团队的本机签名方式、模拟器测试命令和完整工具链见 [构建说明](docs/ios-build.md)。

## 工程结构

```text
iosApp/              UIKit SceneDelegate、Xcode 工程、图标、宿主与交互测试
iosShared/           Compose 根界面和 ToolboxShared 静态 framework
shared/campus/       Ktor Darwin 校园协议、认证、严格 cookie jar、Keychain
shared/model/        被 iOS 页面使用的模型、接口和算法
shared/navigation/   页面路由与会话失效处理
shared/designsystem/ Compose / MIUIX 主题、通用组件和 iOS 平台实现
feature/             账号、首页、课表、成绩、设置
scripts/             framework 构建、设备打包、模拟器测试和图标生成
docs/                iOS 使用、构建、功能和验证说明
```

Kotlin 库仅声明 `iosArm64` 与 `iosSimulatorArm64`。`commonMain` / `commonTest` 用于这两个架构共用的逻辑；Gradle 本身使用 JDK 运行。校园协议测试采用合成 fixture 和 Ktor MockEngine，不访问真实账号。

## 图标与授权

图标沿用上游的「交小西」Q 版形象，不是学校官方标识，也不由交小西制作组出品。形象版权归交小西制作组所有；上游项目声明已获授权，本 Fork 沿用上游图标素材与同一授权的范围（保持开源、免费、无广告、无内购）。该形象不属于本仓库 GPL-3.0 的授权范围。

母版为 [artwork/app-icon-master.png](artwork/app-icon-master.png)，取自上游 `web/src/wasmJsMain/resources/icons/icon-512.png`（512 × 512 出血构图），放大到 1024 × 1024 不透明资源后由 iOS 自行切圆角；生成命令见 [构建说明](docs/ios-build.md#应用图标)。授权细节见 [第三方声明](THIRD_PARTY_NOTICES.md) 的「美术素材」一节。

## 隐私与反馈

校园账号和短信验证码只在应用内输入。默认使用系统 TLS 验证；会话、路由与账号缓存分开管理，退出时撤销旧请求写入缓存的权限并清理账号数据。

反馈请提供机型、系统、应用版本、复现步骤及脱敏错误文案，提交到 [本 Fork 的 Issues](https://github.com/donald-trump86/xjtu-toolbox-ios/issues)。请勿上传密码、验证码、Cookie、令牌、配对文件、证书或未经处理的个人课表/成绩。

## 来源与许可

感谢 [yeliqin666/xjtu-toolbox-android](https://github.com/yeliqin666/xjtu-toolbox-android) 及其贡献者提供的界面、模型与校园协议基础。本 Fork 保留上游版权，iOS 适配与本分支修改使用 [GPL-3.0](LICENSE)；第三方代码与素材见 [声明](THIRD_PARTY_NOTICES.md)。分发 IPA 时应同时提供对应源码、修改说明和许可证。

这是个人维护的非官方客户端，与西安交通大学无隶属关系。
