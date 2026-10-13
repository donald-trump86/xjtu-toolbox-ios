package com.xjtu.toolbox.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Web 这一端**做不到的那几格**的落点，以及「学辅」那一格自己的页。
 *
 * ## 为什么要单写一张降级页（而不是藏掉格子）
 *
 * 阶段 C 的验收点是「三端同壳」：web 的底栏与 `:app` 的 `BottomTab` **逐字一致**（见 `WebShell.kt`）。
 * 一致之后必然有几格在这一端**没有对应的屏**（屁岱要 `AgentHost`、首页要 `HomeHost`，两件都在
 * `:app`/桌面那一侧才有）。把格子藏掉就又变成"另一套层级"，所以照桌面 `HostUnavailableScreen` /
 * `NotPortedScreen` 的口径**落一张写清原因与缺哪一件的页**：用户看得见这一格、也看得见为什么。
 *
 * 三张页的分工：
 * - [WebUnavailableScreen]：通用降级页（首页 / 屁岱，以及壳里认不出的目标）；
 * - [WebServicesScreen]：「学辅」那一格 —— web 这一端的「本端有什么」（`:app` 那格是学业服务目录，
 *   本端没有那套目录与状态摘要，于是如实列出本端真能打开的入口）；
 * - [WebScheduleGapNote]：**日程页顶部那一行**。日程是本端真接上的共享屏（唯一一格），
 *   但它缺三个端点（见那段 KDoc），这一行把它写在屏幕上而不是只写在代码注释里。
 */

/**
 * 「这一端没有这一屏」——标题 + 为什么 + 缺哪几件 + 可选的动作。
 *
 * 口径与桌面那两张插屏一致：宁可写清楚，也不拿别的屏冒充（搬迁前 `?route=notification` 会静默
 * 显示课表 —— 看着像 bug，也分不清「没实现」与「坏了」）。
 */
@Composable
internal fun WebUnavailableScreen(
    title: String,
    why: String,
    missing: List<String>,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    val cs = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = cs.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(why, color = cs.onSurface, fontSize = 13.sp)
        if (missing.isNotEmpty()) {
            Text("这一端缺的东西：", color = cs.primary, fontSize = 12.sp)
            missing.forEach { line ->
                Text("· $line", color = cs.onBackgroundVariant, fontSize = 12.sp)
            }
        }
        Text(
            "Web 只渲染 :core/commonMain 里的屏：一屏搬过来 = 屏 + 模型 + 取数端口进 :core，" +
                "两端各自注入宿主面。搬过来的那些可以在底栏或 ?route= 里直接看。",
            color = cs.onBackgroundVariant,
            fontSize = 12.sp,
        )
        actions()
    }
}

/**
 * 「学辅」那一格：**web 这一端的「本端有什么」**。
 *
 * `:app` 的第四格是学业服务目录（`:core` 的 `AppServices` 宫格 + 每个功能此刻的状态摘要）。
 * 本端没有那份目录所需要的宿主面（状态摘要要各功能自己落盘写一份、图标与配色按目录下标取），
 * 所以这一格落成本端能力清单：真能打开的东西列出来、没有的东西写清楚 —— **不伪造一个宫格**。
 *
 * 已登录还想登出（serve 模式）：会话屏是 Web 专有的一屏，不进五格（见 `WebShell.kt` 的 KDoc），
 * 这里的 [onNavigate] 入口就是它的一条明路。
 *
 * @param serveMode 这一份 `:web` 在对 serve（`:server`）说话 —— 决定会话那一行画不画
 *   （campus-api 那套没有「页面上登录」这回事，画了只会点进一个用不到的屏）。
 */
@Composable
internal fun WebServicesScreen(serveMode: Boolean, onNavigate: (WebTarget) -> Unit) {
    WebUnavailableScreen(
        title = "学辅（Web 这一端）",
        why = "这一格在 App 上是学业服务目录（各功能的宫格 + 点击习惯排序 + 每项此刻的状态）。" +
            "Web 这一端没有那份目录的宿主面，所以这里如实换成「本端有什么」。",
        missing = listOf(
            "服务目录本身（`:core` 的 `AppServices` 需要各端自己的状态摘要与图标配色）",
            "点击习惯排序（`:app` 的 `ServiceUsageTracker` 记在 `DataCache` 里）",
        ),
    ) {
        Text("本端真能打开的共享屏", color = MiuixTheme.colorScheme.primary, fontSize = 12.sp)
        Text(
            "· 日程 —— `:core` 的共享日程屏 + 本端 `ScheduleHost`（底栏第二格）",
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = 12.sp,
        )
        Text(
            "· 我的 —— `:core` 的共享设置屏 + 本端 `SettingsHost`（底栏第五格）",
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = 12.sp,
        )
        Text(
            "· 其余已搬进 `:core` 的屏（黄页 / 校历 / 体测 / 成绩 / 全校课表 / 游戏 / 消息 / 空闲教室 / " +
                "校园卡 / 场馆 / 图书馆 / 教师检索 / 通知 / 评教 / 社区…）走 `?route=<AppRoute.id>` 打开。" +
                "本端不再列一份清单：那份清单会与 `AppPage` 的 `when` 分支各自漂移。",
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text("Web 专有的一屏", color = MiuixTheme.colorScheme.primary, fontSize = 12.sp)
        if (serveMode) {
            Text(
                "· 会话（登录 / 短信二验 / 登出）：serve 模式的这一屏不进底栏（见 `WebShell.kt`），" +
                    "过不了闸门时外壳会自动落到那里；从这里也能进。",
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = 12.sp,
            )
            TextButton(
                text = "打开会话屏",
                onClick = { onNavigate(WebTarget.Serve) },
                minWidth = 128.dp,
            )
        } else {
            Text(
                "· 会话屏要 serve 模式（`:server` 的 `GET /api/session`）才有意义；" +
                    "当前是 campus-api 形态，凭据托管在反代进程里，页面上没有登录这回事。",
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = 12.sp,
            )
        }
        TextButton(
            text = "后端自检",
            onClick = { onNavigate(WebTarget.Probe) },
            minWidth = 128.dp,
        )
    }
}

/**
 * 日程页顶部那一行**如实说明**（不是装饰：它说的是这一页少掉的三样）。
 *
 * 为什么要有它：日程是本端唯一接上共享屏的一格，`ScheduleHost` 的三条缝里
 * [com.xjtu.toolbox.schedule.ScheduleHost.cache] / `customCourses` / `login` 都是真的
 *（前两条用 `:core` 的 `keyValueStore` 缝，在 wasm 上是 **localStorage**，见
 * `core/src/wasmJsMain/.../KeyValueStore.wasmJs.kt`），但 `source`（教务取数）只能靠
 * **服务端契约里真有的那四个端点**拼：`/api/jwxt/term`、`/api/jwxt/term-start`、
 * `/api/jwxt/terms`、`/api/jwxt/schedule`。另外三个端点（调停补课 / 考试 / 教材）在
 * `docs/api-contract.md` §5 的八个端点清单里**不存在**（实测 serve 直接 404），
 * campus-api 那边虽然有 `/api/jwxt/changes` 与 `/api/jwxt/exams`，但本端没有对应客户端，
 * 而 `/api/jwxt/schedule` 端出去的是**排课原样**（契约里写明：调停补课没有合进这些行）。
 *
 * 于是这一页显示的是**排课原样**：调课 / 停课 / 换教室不会体现在格子上，考试与教材两栏空着。
 * 这一句不能只写在代码里 —— 用户看着一张"没有调课的课表"会以为教务就长这样。
 */
@Composable
internal fun WebScheduleGapNote() {
    val cs = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), insideMargin = PaddingValues(10.dp)) {
        Text(
            "Web 这一份的日程只显示排课原样：调停补课 / 考试 / 教材三个端点不在服务端契约里" +
                "（`docs/api-contract.md` §5 只有八个端点，实测 serve 对它们答 404），" +
                "所以调课、停课、换教室不体现在格子上，考试与教材两栏空着。",
            color = cs.onBackgroundVariant,
            fontSize = 11.sp,
        )
    }
}
