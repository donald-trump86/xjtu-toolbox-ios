package com.xjtu.toolbox.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.xjtu.toolbox.library.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.xjtu.toolbox.card.MealReport
import com.xjtu.toolbox.card.MealReportPoster
import com.xjtu.toolbox.card.Transaction
import com.xjtu.toolbox.notification.*
import com.xjtu.toolbox.ui.theme.XJTUToolBoxTheme
import java.io.File
import javax.swing.SwingUtilities
import kotlin.system.exitProcess
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Offline visual review; all transactions and notices below are fabricated. */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "build/campus-experience-preview").apply { mkdirs() }
    fun find(root: SemanticsNode, text: String): SemanticsNode? =
        if (root.config.getOrNull(SemanticsActions.OnClick) != null &&
            (root.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text } ||
                text in root.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty())) root
        else root.children.firstNotNullOfOrNull { find(it, text) }
    fun capture(name: String, width: Int, height: Int, dark: Boolean = false, clicks: List<String> = emptyList(), verifyBook: Boolean = false, content: @Composable () -> Unit) {
        lateinit var scene: ImageComposeScene
        SwingUtilities.invokeAndWait {
            scene = ImageComposeScene(width, height, Density(1f)) {
                XJTUToolBoxTheme(darkModeOverride = if (dark) "dark" else "light") {
                    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) { content() }
                }
            }
            scene.render(0)
        }
        repeat(8) { frame ->
            Thread.sleep(100)
            SwingUtilities.invokeAndWait { scene.render((frame + 1) * 200_000_000L) }
        }
        clicks.forEachIndexed { step, label ->
            SwingUtilities.invokeAndWait {
                val entry = requireNotNull(scene.semanticsOwners.reversed().firstNotNullOfOrNull { find(it.rootSemanticsNode, label) }) { "Missing control: $label" }
                entry.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke()
            }
            repeat(8) { frame ->
                Thread.sleep(100)
                SwingUtilities.invokeAndWait { scene.render(2_000_000_000L + step * 1_000_000_000L + frame * 100_000_000L) }
            }
        }
        SwingUtilities.invokeAndWait {
            File(output, name).writeBytes(requireNotNull(scene.render(3_000_000_000L + clicks.size * 1_000_000_000L).encodeToData()).bytes)
            if (verifyBook) {
                val button = requireNotNull(find(scene.semanticsOwners.first().rootSemanticsNode, "预约"))
                check(button.boundsInRoot.left >= width - (width * 0.25f).coerceIn(148f, 240f)) { "Booking button must be in the right sidebar" }
                button.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke()
            }
            scene.close()
        }
    }
    val transactions = (1..120).map { i ->
        Transaction(time = "2026-10-${(i % 20 + 1).toString().padStart(2, '0')} ${listOf("07:30", "12:10", "18:00")[i % 3]}:00",
            merchant = listOf("梧桐苑一楼", "康桥苑二楼", "梧桐苑二楼")[i % 3], amount = -(8.0 + i % 16), description = "餐饮消费")
    }
    val report = requireNotNull(MealReport.build(transactions) { true })
    val notices = listOf(
        Notification("关于本学期课程安排的通知", "https://example.invalid/1", NotificationSource.JWC),
        Notification("大学生创新训练计划报名通知", "https://example.invalid/2", NotificationSource.JWC),
    )
    val source = object : NoticeSource {
        override suspend fun page(source: NotificationSource, page: Int) = NotificationPage(notices.map { it.copy(source = source) }, false)
        override suspend fun merged(sources: List<NotificationSource>, page: Int) = MergedNotificationPage(sources.flatMap { s -> notices.map { it.copy(source = s, link = "${it.link}/${s.name}") } }, emptySet(), false)
        override suspend fun search(sources: List<NotificationSource>, keyword: String) = MergedNotificationPage(sources.flatMap { s -> notices.map { it.copy(source = s, link = "${it.link}/${s.name}") } }, emptySet(), false)
    }
    capture("meal-report.png", 412, 1480) { MealReportPoster(report) }
    capture("notices-light.png", 412, 915) { NotificationScreen(source, onBack = {}) }
    capture("notices-dark.png", 412, 915, dark = true) { NotificationScreen(source, onBack = {}) }
    capture("sources-light.png", 412, 915, clicks = listOf("选择通知来源")) { NotificationScreen(source, onBack = {}) }
    capture("sources-dark.png", 412, 915, dark = true, clicks = listOf("选择通知来源")) { NotificationScreen(source, onBack = {}) }
    capture("sources-merged.png", 412, 915, clicks = listOf("选择通知来源", "研究生院")) { NotificationScreen(source, onBack = {}) }
    capture("sources-applied.png", 412, 915, clicks = listOf("选择通知来源", "研究生院", "查看 2 个来源")) { NotificationScreen(source, onBack = {}) }
    val seats = (0 until 60).map { index ->
        PlanSeat("A${(index + 1).toString().padStart(3, '0')}", 65f + index % 12 * 72f,
            55f + index / 12 * 94f, 28f, 32f, if (index % 7 == 0) PlanSeat.BOOKED else PlanSeat.FREE)
    }
    val bitmap = ImageBitmap(960, 540)
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
    canvas.drawRect(Rect(0f, 0f, 960f, 540f), Paint().apply { color = Color(0xFFF0EADF) })
    seats.forEach { seat -> canvas.drawRect(Rect(seat.left, seat.top, seat.right, seat.bottom),
        Paint().apply { color = Color(0xFFB8A68A) }) }
    val plan = PlanImages(bitmap, 960, 540, emptyMap())
    var booked: String? = null
    val seatPreview: @Composable () -> Unit = {
        val state = remember { SeatPlanUiState().apply { selected.value = "A012"; fullscreen.value = true } }
        SeatPlanFullscreenContent(state, SeatLayout(seats), plan, "三楼北区", "空闲 51 / 60",
            setOf("A012"), emptyList(), true, false, false, { booked = it }, {})
    }
    capture("seats-landscape.png", 915, 412, verifyBook = true, content = seatPreview)
    check(booked == "A012")
    capture("seats-compact.png", 740, 320, verifyBook = true, content = seatPreview)
    capture("seats-dark.png", 915, 412, dark = true, verifyBook = true, content = seatPreview)
    capture("seats-portrait.png", 412, 915, verifyBook = true, content = seatPreview)
    println("Previews: ${output.absolutePath}")
    exitProcess(0)
}
