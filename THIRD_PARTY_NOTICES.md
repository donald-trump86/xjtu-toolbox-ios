# 第三方代码与资源声明

本 Fork 基于 [yeliqin666/xjtu-toolbox-android](https://github.com/yeliqin666/xjtu-toolbox-android)。上游及其贡献者的版权继续保留；iOS 适配和本分支修改以 [GPL-3.0](LICENSE) 发布。分发应用时应同时提供对应源码、修改说明和许可证。上游所致谢的 [yan-xiaoo/XJTUToolBox](https://github.com/yan-xiaoo/XJTUToolBox) 仍在应用内保留来源链接。

以下记录当前 iOS 分支实际保留的第三方内容。各依赖的原始版权、许可证及传递依赖声明应随其发布元数据或发行包保留。

## 美术素材

### 应用图标中的「交小西」形象

- **版权**：图标形象版权归交小西制作组所有（[B 站主页](https://space.bilibili.com/3494379805215567)）。
- **授权**：上游项目「岱宗盒子」声明已获得交小西制作组授权。上游声明同时写明「下游 fork 或再分发不得沿用该图标，需自行取得授权或替换图标」。本 Fork 并未单独取得授权，只是照上游声明的授权条件使用（保持开源、免费、无广告、无内购），并受上游「下游不得沿用」这条限制约束；若版权方或交小西制作组提出要求，本 Fork 会更换图标。
- **出品关系**：本应用为开源项目，不由交小西制作组出品。
- **授权条件**：项目保持开源、免费、无广告、无内购；项目性质变更时授权失效，须重新协商。
- **许可边界**：该形象**不属于**本仓库 GPL-3.0 的授权范围；再分发本 Fork 时同样不能把该图标当作可沿用的 GPL 素材。

## Compose、网络及基础库

| 项目 | 用途 | 许可 |
| --- | --- | --- |
| [Kotlin](https://github.com/JetBrains/kotlin) / kotlinx.coroutines / kotlinx.serialization / kotlinx-datetime | Kotlin/Native、协程、序列化和日期时间 | Apache-2.0 |
| [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) / JetBrains Lifecycle | 共享 iOS 界面、状态与生命周期 | Apache-2.0 |
| [MIUIX](https://github.com/compose-miuix-ui/miuix) | Compose 界面与导航组件 | Apache-2.0 |
| [Ktor](https://github.com/ktorio/ktor) | Darwin 网络引擎与 MockEngine | Apache-2.0 |
| [Ksoup](https://github.com/fleeksoft/ksoup) | CAS HTML DOM 解析 | MIT |
| [AndroidLiquidGlass / backdrop](https://github.com/Kyant0/AndroidLiquidGlass) | 玻璃效果依赖与改编组件 | Apache-2.0 |

具体版本固定在 [版本目录](gradle/libs.versions.toml)。Skiko / Skia、图像编解码及其他传递依赖分别遵循其原始许可证；本 Fork 不改变这些许可证。

## Kyant 玻璃组件

Copyright 2025 Kyant. Licensed under the Apache License, Version 2.0；[许可证全文](licenses/Apache-2.0.txt)。

以下文件由上游从 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) 的 `kmp` 示例改编，本分支继续保留：

| 原示例路径（`app/src/commonMain/kotlin/com/kyant/backdrop/catalog/` 下） | 本分支文件（`shared/designsystem/src/commonMain/kotlin/com/xjtu/toolbox/ui/glass/` 下） |
| --- | --- |
| `components/LiquidBottomTabs.kt` | `GlassBottomTabs.kt` |
| `components/LiquidBottomTab.kt` | `GlassNavTabSlot.kt` |
| `utils/DampedDragAnimation.kt` | `GlassDampedDragAnimation.kt` |
| `utils/InteractiveHighlight.kt` | `GlassInteractiveHighlight.kt` |
| `utils/DragGestureInspector.kt` | `GlassDragGestureInspector.kt` |

原有改动包括包名/类名调整、应用主题与深色模式、胶囊形状替换、可选染色和显式回调，以及用 Compose `withFrameNanos` 适配动画。文件头保留了来源与改动说明。Maven 引入的 `io.github.kyant0:backdrop` 未由本 Fork 修改。

## 校园地图数据

`shared/model/src/commonMain/kotlin/com/xjtu/toolbox/campusmap/CampusMapsGenerated.kt` 中的校区边界、地点与路径来自 **© OpenStreetMap contributors**，数据遵循 [Open Database License 1.0](https://opendatacommons.org/licenses/odbl/1-0/)。来源和署名见 [OpenStreetMap 版权页](https://www.openstreetmap.org/copyright)。

数据生成方式与区域范围保留在 [生成脚本](tools/gen_campus_map.py) 中；课表教室别名由应用另行匹配。数据库许可不随应用源码 GPL-3.0 改变。

## 图像资源

- **应用图标**：沿用上游的「交小西」Q 版形象，取自上游 `web/src/wasmJsMain/resources/icons/icon-512.png`（512 × 512），存为母版 [app-icon-master.png](artwork/app-icon-master.png)，再由 [图标生成脚本](scripts/generate-ios-icons.swift) 放大为 1024 × 1024、不透明的 iOS 资源，圆角由 iOS 裁切。源图本身带烘焙圆角：左上、右上、右下三处露出浅灰底（#E5E5E5；取边长为图标 1/8 的角块统计，三处各约 60%），左下齐边。烘焙圆角半径约 116 px（22.7%），与 iOS 的 22.4% 几乎重合，按 iOS 遮罩裁切后，圆角外缘残留的灰边在 1024 尺度上约 2 px。`maskable-512.png` 则是白卡片内缩、四周露出浅灰底，放大即可见灰色边框，故未采用。形象的版权与授权见上文「美术素材」一节。
- **森林背景**：`shared/designsystem/src/iosMain/composeResources/drawable/hero_forest.webp` 为上游已有资源，随其余上游代码保留。iOS 的 `HeroForest` 平台实现仍引用它。

学校名称、地标及有关标识归相应权利方所有；保留来源不表示学校为本 Fork 背书，也不表示交小西制作组为本 Fork 背书。
