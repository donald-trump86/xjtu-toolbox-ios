package com.xjtu.toolbox.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xjtu.toolbox.bulletin.BulletinNoticePanel
import com.xjtu.toolbox.nav.AppRoute
import com.xjtu.toolbox.nav.expandOriginSource
import com.xjtu.toolbox.nav.rememberExpandOriginSource
import com.xjtu.toolbox.settings.SettingsStore
import com.xjtu.toolbox.ui.components.AppCardColor
import com.xjtu.toolbox.ui.components.ExpressiveIcon
import com.xjtu.toolbox.ui.components.GapNote
import com.xjtu.toolbox.ui.components.GradientAppIcon
import com.xjtu.toolbox.ui.components.MeshBackground
import com.xjtu.toolbox.ui.components.RollingNumberText
import com.xjtu.toolbox.ui.components.appCardShadow
import com.xjtu.toolbox.ui.components.enterOnce
import com.xjtu.toolbox.ui.glass.LocalGlassStyle
import com.xjtu.toolbox.ui.glass.glassBarSurface
import com.xjtu.toolbox.ui.glass.glassBarTint
import com.xjtu.toolbox.ui.isWideLayout
import com.xjtu.toolbox.ui.theme.Editorial
import com.xjtu.toolbox.ui.theme.EditorialRule
import com.xjtu.toolbox.ui.theme.LocalIsDarkTheme
import com.xjtu.toolbox.ui.theme.legacyColor
import com.xjtu.toolbox.ui.theme.serviceColor
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** 首页屏。 */
@Composable
fun HomeScreen(
    host: HomeHost,
    modifier: Modifier = Modifier,
    scrollBehavior: ScrollBehavior? = null,
    /** 悬浮底栏的总占位高度：底栏浮在内容之上，页面末尾得自己留出来。 */
    extraBottomPadding: Dp = 0.dp,
    /** 玻璃顶栏的高度：内容铺到顶栏下面，这段留白放进滚动内容里。经典风格为 0。 */
    contentTopPadding: Dp = 0.dp,
    /** 首页没有顶栏：消息、扫一扫、搜索这几个按钮由宿主给，放在页面最上面跟着内容一起滚。 */
    topActions: (@Composable () -> Unit)? = null,
) {
    val honored = host.honoredCards
    // 用目录全量：图标配色按它在目录里的下标取，过滤会让配色挪位。
    val catalog = remember(host.audience) { AppServices.homeFor(host.audience) }
    // `serviceColor` / `legacyColor` 是 @Composable，不能放进 `remember`。
    val iconColorByKey = catalog.mapIndexed { index, svc -> svc.route.id to serviceColor(index, catalog.size) }.toMap()
    val legacyColorByKey = catalog.associate { it.route.id to legacyColor(it.route.id) }
    val isIconTheme = host.homeTheme == SettingsStore.THEME_ICON

    fun rowOf(service: AppService, stat: HomeStat?): HomeServiceRow {
        val id = service.route.id
        return HomeServiceRow(
            key = service.route.id,
            icon = HomeIcons.of(service.route),
            title = service.title,
            subtitle = service.subtitle,
            color = if (isIconTheme) iconColorByKey[id] ?: legacyColorByKey[id] ?: Color.Unspecified
            else legacyColorByKey[id] ?: Color.Unspecified,
            onClick = { host.onRoute(service.route) },
            stat = stat?.value,
            statDetail = stat?.detail,
        )
    }

    val quickRows = host.quickRoutes.mapNotNull { route ->
        catalog.firstOrNull { it.route == route }?.let { rowOf(it, null) }
    }
    val quick: (@Composable (iconSize: Dp, solidIcon: Boolean) -> Unit)? =
        if (HomeCard.QUICK_ACTIONS in honored && (quickRows.isNotEmpty() || host.quickEditable)) {
            { iconSize, solidIcon ->
                QuickActionsGrid(
                    pinned = quickRows,
                    all = catalog.filter { host.openableRouteIds?.contains(it.route.id) != false }.map { rowOf(it, null) },
                    editable = host.quickEditable,
                    onChange = { keys -> host.onQuickRoutesChange(keys.mapNotNull { k -> catalog.firstOrNull { it.route.id == k }?.route }) },
                    iconSize = iconSize,
                    solidIcon = solidIcon,
                )
            }
        } else null
    val groups = HomeCards.groups(host.audience, host.stats, host.openableRouteIds)
    val frontInbox = host.inbox.takeIf { !isIconTheme && HomeCard.INBOX in honored }

    // 功能状态就地附在分类行里，不另设展示区。
    fun indexRow(service: AppService, stat: String?, detail: String?): HomeServiceRow {
        val glance = host.glances.takeIf { HomeCard.GLANCE in honored }
            ?.firstOrNull { it.route?.id == service.route.id && it.route != AppRoute.Schedule }
        val value = if (HomeCard.CATEGORY_STATS in honored) {
            stat ?: glance?.let { g -> g.number?.let { yuanText(it.toFloat()) } ?: g.value }
                ?: host.stats[service.route.id]?.value
        } else null
        return rowOf(service, value?.let { HomeStat(it, detail ?: glance?.caption) })
            .copy(tone = glance?.tone ?: HomeTone.NORMAL)
    }

    val scroll = rememberScrollState()
    // 有宿主给的按钮才有折叠顶栏（桌面、Web 的壳有自己的顶栏）
    val collapsible = topActions != null
    val masthead = !isIconTheme && HomeCard.GREETING in honored
    val barPx = with(LocalDensity.current) { BAR_HEIGHT.toPx() }
    var mastheadPx by remember { mutableIntStateOf(0) }
    // 刊头底边碰到顶栏底边时折叠完成；没有刊头时只让顶栏底色在一栏高度内浮出来
    val rangePx = if (masthead && mastheadPx > 0) (mastheadPx - barPx).coerceAtLeast(barPx) else barPx
    val progress = { (scroll.value / rangePx).coerceIn(0f, 1f) }
    if (collapsible) SnapCollapse(scroll, rangePx)
    // 顶栏的玻璃采这一页自己的滚动层：顶栏本身在壳的采样层里，采壳的层会采到自己
    val pageBackdrop = rememberLayerBackdrop()
    val glass = LocalGlassStyle.current && collapsible

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .then(if (glass) Modifier.layerBackdrop(pageBackdrop) else Modifier)
                .then(if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier)
                .overScrollVertical()
                .verticalScroll(scroll)
        ) {
            Spacer(Modifier.height(contentTopPadding))
            if (masthead) {
                EditorialMasthead(
                    greeting = Greeting.of(),
                    name = host.greetingName,
                    dateLabel = host.dateLabel,
                    weekNumber = if (HomeCard.WEEK_NUMBER in honored) host.weekNumber else 0,
                    status = host.statusLine,
                    onStatus = host::onOpenStatus,
                    underBar = collapsible,
                    progress = progress,
                    modifier = Modifier.enterOnce(0).onSizeChanged { mastheadPx = it.height },
                )
            } else if (collapsible) {
                Spacer(Modifier.height(BAR_HEIGHT))
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = if (masthead) 6.dp else 8.dp)
            ) {
                if (HomeCard.BULLETIN_BAR in honored && host.bulletins.isNotEmpty()) {
                    BulletinNoticePanel(
                        bulletins = host.bulletins,
                        onTap = host::onBulletinTap,
                        onDismiss = host::onBulletinDismiss,
                    )
                    Spacer(Modifier.height(SECTION_GAP))
                }
                if (isIconTheme) {
                    HomeHeroCard(
                        modifier = Modifier.enterOnce(0),
                        host = host,
                        dateLabel = host.dateLabel,
                        weekNumber = if (HomeCard.WEEK_NUMBER in honored) host.weekNumber else 0,
                        quick = quick?.let { { it(44.dp, true) } },
                    )
                } else {
                    EditorialFrontPage(
                        modifier = Modifier.enterOnce(0),
                        host = host,
                        inbox = frontInbox,
                        quick = quick?.let { { it(40.dp, false) } },
                    )
                }
            }

            host.inbox?.let { summary ->
                if (HomeCard.INBOX in honored && isIconTheme) {
                    Spacer(Modifier.height(SECTION_GAP))
                    HomeInboxCard(summary) { host.onRoute(AppRoute.Inbox) }
                }
            }

            if (HomeCard.SERVICE_GRID in honored && groups.isNotEmpty()) {
                Spacer(Modifier.height(if (isIconTheme) SECTION_GAP else 20.dp))
                if (isIconTheme) {
                    CategoryCards(
                        count = groups.size,
                        spacing = SECTION_GAP,
                        weight = { 1 + (groups[it].rows.size + 3) / 4 },
                    ) { index ->
                        val group = groups[index]
                        HomeCategoryCard(
                            title = group.category.title,
                            subtitle = group.category.subtitle,
                            accent = legacyColor(HomeIcons.accentKey(group.category)),
                            rows = group.rows.map { rowOf(it.service, null) },
                        )
                    }
                } else {
                    HomeIndexCard(
                        sections = groups.map { group ->
                            IndexCategorySection(
                                title = group.category.title,
                                subtitle = group.category.subtitle,
                                accent = legacyColor(HomeIcons.accentKey(group.category)),
                                rows = group.rows.map { indexRow(it.service, it.stat, it.statDetail) },
                            )
                        },
                        modifier = Modifier.enterOnce(1),
                    )
                }
            }
            host.gapNote?.let { note ->
                Spacer(Modifier.height(SECTION_GAP))
                GapNote(note)
            }
            Spacer(Modifier.height(SECTION_GAP + extraBottomPadding + 8.dp))
        }
        if (topActions != null) {
            val scope = rememberCoroutineScope()
            HomeCollapsingBar(
                progress = progress,
                topInset = contentTopPadding,
                backdrop = if (glass) pageBackdrop else null,
                onTapCollapsed = { scope.launch { scroll.animateScrollTo(0) } },
                actions = topActions,
                alignWithGreeting = masthead,
            )
        }
    }
}

/** 停手时折叠到一半的顶栏吸到最近的一端，和 MIUIX 大标题的手感一致。 */
@Composable
private fun SnapCollapse(scroll: ScrollState, rangePx: Float) {
    LaunchedEffect(scroll, rangePx) {
        snapshotFlow { scroll.isScrollInProgress }.collect { moving ->
            val v = scroll.value
            if (moving || v <= 0 || v >= rangePx || rangePx > scroll.maxValue) return@collect
            scroll.animateScrollTo(if (v < rangePx / 2) 0 else rangePx.roundToInt())
        }
    }
}

/**
 * 首页的折叠顶栏，叠在滚动内容上面。三个圆钮一直在右上角；往上滑过刊头后，
 * 玻璃底色、底边发丝线和左边的方印一起浮出来，内容从它后面模糊着滑过。折叠后点空白处回到顶部。
 */
@Composable
private fun HomeCollapsingBar(
    progress: () -> Float,
    topInset: Dp,
    backdrop: LayerBackdrop?,
    onTapCollapsed: () -> Unit,
    actions: @Composable () -> Unit,
    alignWithGreeting: Boolean = false,
) {
    val collapsed by remember { derivedStateOf { progress() > 0.5f } }
    val tint = glassBarTint()
    val surface = MiuixTheme.colorScheme.surface
    Box(Modifier.fillMaxWidth().height(topInset + BAR_HEIGHT)) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = ramp(progress(), 0.55f) }
                .then(if (backdrop != null) Modifier.glassBarSurface(backdrop, tint) else Modifier.background(surface))
                .then(
                    if (collapsed) Modifier.clickable(interactionSource = null, indication = null, onClick = onTapCollapsed)
                    else Modifier
                ),
        )
        EditorialRule(Modifier.align(Alignment.BottomStart).graphicsLayer { alpha = ramp(progress(), 0.7f) })
        Row(
            Modifier.fillMaxWidth().padding(top = topInset).height(BAR_HEIGHT).padding(start = 20.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                HomeSeal.Square,
                contentDescription = "岱宗盒子",
                modifier = Modifier.size(26.dp).graphicsLayer {
                    val f = ramp(progress(), 0.6f)
                    alpha = f
                    scaleX = 0.78f + 0.22f * f
                    scaleY = scaleX
                },
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.graphicsLayer {
                translationY = if (alignWithGreeting) 0f else -BUTTON_LIFT.toPx() * (1f - progress())
            }) { actions() }
        }
    }
}

/** 展开时圆钮比顶栏正中上移的距离：顶栏里圆钮上方正好留这么多，不会顶进状态栏。 */
private val BUTTON_LIFT get() = (BAR_HEIGHT - 36.dp) / 2 - 2.dp

/** [p] 从 [start] 走到 1 时线性地从 0 升到 1。 */
private fun ramp(p: Float, start: Float): Float = ((p - start) / (1f - start)).coerceIn(0f, 1f)

// ── 杂志版式（THEME_CARD）──

/** 分类面板：白色卡面、细描边与柔和投影；今日安排使用暖色底。 */
@Composable
private fun Modifier.editorialPaper(
    radius: Dp = EDITORIAL_RADIUS,
    tinted: Boolean = false,
): Modifier {
    val dark = LocalIsDarkTheme.current
    val base = AppCardColor
    val fill = when {
        dark && tinted -> lerp(base, HomeSeal.Red, 0.045f)
        dark -> base
        tinted -> lerp(base, Color(0xFFF6EFE4), 0.55f)
        else -> base
    }
    val border = MiuixTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.09f else 0.065f)
    return this
        .then(if (dark) Modifier else Modifier.appCardShadow(shape = RoundedCornerShape(radius)))
        .squircleClip(radius)
        .background(fill)
        .squircleBorder(0.6.dp, border, radius)
}

/** 刊头：印章跨两行；右边上行问候（与操作钮同高），下行日期、教学周与连接状态。 */
@Composable
private fun EditorialMasthead(
    greeting: String,
    name: String,
    dateLabel: String,
    weekNumber: Int,
    status: HomeStatusLine?,
    onStatus: () -> Unit,
    underBar: Boolean,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val ink = MiuixTheme.colorScheme.onSurface
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(
        modifier.fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 20.dp)
            .graphicsLayer {
                val p = progress()
                alpha = (1f - p * 1.35f).coerceIn(0f, 1f)
                scaleX = 1f - 0.03f * p
                scaleY = scaleX
                transformOrigin = TransformOrigin(0.5f, 0f)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(HomeSeal.Square, contentDescription = "岱宗盒子", modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth().height(36.dp)
                    .padding(end = if (underBar) 132.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    greeting,
                    style = Editorial.masthead.copy(fontSize = 26.sp, lineHeight = 34.sp),
                    color = ink,
                    maxLines = 1,
                )
                if (name.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(name, fontSize = 13.sp, color = muted, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dateLabel, fontSize = 12.sp, color = muted, maxLines = 1)
                if (weekNumber in 1..MAX_TEACHING_WEEK) {
                    Text(" · ", fontSize = 12.sp, color = muted.copy(alpha = 0.5f))
                    Text("第 $weekNumber 周", fontSize = 12.sp,
                        color = if (LocalIsDarkTheme.current) lerp(HomeSeal.Red, ink, 0.35f) else HomeSeal.Red,
                        maxLines = 1)
                }
                if (status != null) {
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        HeroStatusLine(status, onStatus, compact = true)
                    }
                }
            }
        }
    }
}

/** 今日安排只放时间相关的信息；所有功能及其状态统一放在下面的服务区。 */
@Composable
private fun EditorialFrontPage(
    modifier: Modifier = Modifier,
    host: HomeHost,
    inbox: HomeInboxSummary?,
    quick: (@Composable () -> Unit)?,
) {
    val nextUp = host.nextUp.takeIf { HomeCard.NEXT_UP in host.honoredCards }
    val exam = host.glances.takeIf { HomeCard.GLANCE in host.honoredCards }
        ?.firstOrNull { it.route == AppRoute.Schedule }
    if (nextUp == null && inbox == null && exam == null && quick == null) return

    Column(modifier.fillMaxWidth().editorialPaper(tinted = true)) {
        nextUp?.let {
            EditorialNextUpLead(it) { it.route?.let(host::onRoute) ?: host.onProfile() }
        }
        if (exam != null) {
            if (nextUp != null) EditorialRule(Modifier.padding(horizontal = 18.dp))
            Row(
                Modifier.fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = SinkFeedback(),
                        onClick = { host.onRoute(AppRoute.Schedule) },
                    )
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(exam.icon, null, tint = HomeSeal.Red, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(exam.label, fontSize = 12.sp, color = HomeSeal.Red)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(exam.value, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    exam.caption?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.size(15.dp))
            }
        }
        if (inbox != null) {
            if (nextUp != null || exam != null) EditorialRule(Modifier.padding(horizontal = 18.dp))
            InboxBrief(inbox) { host.onRoute(AppRoute.Inbox) }
        }
        if (quick != null) {
            if (nextUp != null || exam != null || inbox != null) EditorialRule(Modifier.padding(horizontal = 18.dp))
            Box(Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) { quick() }
        }
    }
}

@Composable
private fun EditorialNextUpLead(
    nextUp: HomeNextUp,
    onClick: () -> Unit,
) {
    val ink = MiuixTheme.colorScheme.onSurface
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val timeLabel = nextUp.timeLabel

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .background(
                        if (timeLabel != null) HomeSeal.Red else muted.copy(alpha = 0.45f),
                        CircleShape,
                    )
            )
            Spacer(Modifier.width(7.dp))
            Text(
                if (timeLabel != null) "接下来" else "我的日程",
                style = Editorial.kicker.copy(fontSize = 11.sp, letterSpacing = 1.6.sp),
                color = muted,
                modifier = Modifier.weight(1f),
            )
            if (!nextUp.eta.isNullOrBlank()) {
                Text(
                    nextUp.eta,
                    style = TextStyle(
                        fontFamily = Editorial.Serif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        letterSpacing = 0.5.sp,
                    ),
                    color = HomeSeal.Red,
                    modifier = Modifier
                        .background(HomeSeal.Red.copy(alpha = 0.09f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = muted.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        if (timeLabel != null) {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        timeLabel,
                        style = Editorial.figure.copy(
                            fontSize = 28.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = ink,
                    )
                    if (!nextUp.timeCaption.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            nextUp.timeCaption,
                            style = MiuixTheme.textStyles.footnote2,
                            color = muted,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Box(
                    Modifier
                        .width(0.6.dp)
                        .fillMaxHeight()
                        .padding(vertical = 2.dp)
                        .background(ink.copy(alpha = 0.12f))
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        nextUp.title,
                        style = TextStyle(
                            fontFamily = Editorial.Serif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                            letterSpacing = 0.4.sp,
                        ),
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!nextUp.detail.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            nextUp.detail,
                            style = MiuixTheme.textStyles.footnote1,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    nextUp.icon,
                    contentDescription = null,
                    tint = muted.copy(alpha = 0.75f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        nextUp.title,
                        style = TextStyle(
                            fontFamily = Editorial.Serif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            letterSpacing = 0.4.sp,
                        ),
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!nextUp.detail.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            nextUp.detail,
                            style = MiuixTheme.textStyles.footnote2,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxBrief(summary: HomeInboxSummary, onClick: () -> Unit) {
    val ink = MiuixTheme.colorScheme.onSurface
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "待办",
            style = Editorial.kicker.copy(fontSize = 11.sp, letterSpacing = 1.5.sp),
            color = HomeSeal.Red,
            modifier = Modifier
                .border(0.6.dp, HomeSeal.Red.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.5.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            summary.nextTodo ?: "去收纳里看看",
            style = TextStyle(
                fontFamily = Editorial.Serif,
                fontWeight = FontWeight.Medium,
                fontSize = 14.5.sp,
                lineHeight = 19.sp,
                letterSpacing = 0.3.sp,
            ),
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            listOfNotNull(
                summary.todoCount.takeIf { it > 0 }?.let { "$it 件没办" },
                summary.unreadCount.takeIf { it > 0 }?.let { "$it 组未读" },
            ).joinToString(" · "),
            style = MiuixTheme.textStyles.footnote2,
            color = muted,
            maxLines = 1,
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = muted.copy(alpha = 0.6f),
            modifier = Modifier.size(15.dp),
        )
    }
}

private data class IndexCategorySection(
    val title: String,
    val subtitle: String,
    val accent: Color,
    val rows: List<HomeServiceRow>,
)

/** 分类容器承载完整入口；状态直接附在入口下方，避免与展示卡重复。 */
@Composable
private fun HomeIndexCard(
    sections: List<IndexCategorySection>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("校园服务", fontSize = 19.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
        if (isWideLayout()) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                balancedColumns(sections.size) { 2 + (sections[it].rows.size + 1) / 2 }.forEach { column ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        column.forEach { ServiceSection(sections[it]) }
                    }
                }
            }
        } else {
            sections.forEachIndexed { index, section ->
                ServiceSection(section)
                if (index < sections.lastIndex) Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun ServiceSection(section: IndexCategorySection) {
    val dark = LocalIsDarkTheme.current
    val accent = if (dark) lerp(section.accent, Color.White, 0.3f) else section.accent
    val ink = MiuixTheme.colorScheme.onSurface
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(
        Modifier.fillMaxWidth().editorialPaper()
            .drawBehind {
                drawRect(Brush.radialGradient(
                    listOf(accent.copy(alpha = if (dark) 0.09f else 0.055f), Color.Transparent),
                    center = Offset(size.width, 0f), radius = size.width * 0.9f,
                ))
            }.padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(28.dp).background(accent, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(section.title, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    fontFamily = Editorial.Serif, color = ink)
                Spacer(Modifier.height(3.dp))
                Text(section.subtitle, fontSize = 11.sp, color = muted, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        section.rows.chunked(2).forEachIndexed { lineIndex, line ->
            if (lineIndex > 0) Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                line.forEach { IndexEntry(it, Modifier.weight(1f).fillMaxHeight()) }
                if (line.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun IndexEntry(row: HomeServiceRow, modifier: Modifier = Modifier) {
    val dark = LocalIsDarkTheme.current
    val ink = MiuixTheme.colorScheme.onSurface
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val tile = if (dark) lerp(AppCardColor, Color.White, 0.045f)
        else lerp(AppCardColor, MiuixTheme.colorScheme.surface, 0.65f)
    val iconColor = if (dark) lerp(row.color, Color.White, 0.4f) else row.color
    Row(
        modifier.background(tile, RoundedCornerShape(15.dp))
            .opensRow(row, 15.dp).padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).background(row.color.copy(alpha = if (dark) 0.18f else 0.10f),
            RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(row.icon, null, tint = iconColor, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, color = ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(row.stat ?: row.subtitle,
                fontSize = 11.sp, lineHeight = 15.sp,
                fontWeight = if (row.stat != null) FontWeight.Medium else FontWeight.Normal,
                color = when {
                    row.stat == null || row.tone == HomeTone.MUTED -> muted
                    row.tone == HomeTone.ALERT -> MiuixTheme.colorScheme.error
                    row.tone == HomeTone.ACCENT -> iconColor
                    else -> lerp(row.color, ink, if (dark) 0.55f else 0.25f)
                },
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            row.statDetail?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, fontSize = 10.5.sp, lineHeight = 14.sp, color = muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ── 彩虹图标主题（THEME_ICON）──

/** 彩虹主题的「今日」卡。 */
@Composable
private fun HomeHeroCard(
    modifier: Modifier = Modifier,
    host: HomeHost,
    dateLabel: String,
    weekNumber: Int,
    quick: (@Composable () -> Unit)?,
) {
    val honored = host.honoredCards
    val greeting = Greeting.of()
    val headline = if (host.greetingName.isBlank()) greeting else "$greeting，${host.greetingName}"
    val meta = buildString {
        append(dateLabel)
        if (HomeCard.WEEK_NUMBER in honored && weekNumber in 1..MAX_TEACHING_WEEK) append(" · 第${weekNumber}周")
    }
    val primary = MiuixTheme.colorScheme.primary
    val status = host.statusLine.takeIf { HomeCard.GREETING in honored }
    val nextUp = host.nextUp.takeIf { HomeCard.NEXT_UP in honored }
    val glances = host.glances.takeIf { HomeCard.GLANCE in honored }.orEmpty()

    Box(
        modifier
            .fillMaxWidth()
            .appCardShadow(shape = RoundedCornerShape(CARD_RADIUS), strong = true)
            .squircleClip(CARD_RADIUS)
            .background(AppCardColor),
    ) {
        Box(Modifier.matchParentSize()) {
            val cardBase = AppCardColor
            val heroMesh = remember(primary, cardBase) {
                HeroMeshWeights.map { line -> line.map { t -> lerp(cardBase, primary, t) } }
            }
            MeshBackground(
                modifier = Modifier.matchParentSize(),
                lightVertexColors = heroMesh,
                darkVertexColors = heroMesh,
                runForMillis = 6_000L,
            )
        }
        host.heroArt?.invoke(this)
        Column(Modifier.fillMaxWidth().animateContentSize().padding(14.dp)) {
            if (HomeCard.GREETING in honored) {
                Column(Modifier.padding(start = 6.dp, top = 8.dp, end = HERO_ART_SIZE)) {
                    Text(
                        meta,
                        style = MiuixTheme.textStyles.footnote1,
                        color = primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        headline,
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (status != null) {
                        Spacer(Modifier.height(8.dp))
                        HeroStatusLine(status, host::onOpenStatus)
                    }
                }
                Spacer(Modifier.height(if (status != null) 18.dp else 30.dp))
            }
            if (HomeCard.NEXT_UP in honored && nextUp != null) {
                HeroNextUp(
                    nextUp = nextUp,
                    onClick = { nextUp.route?.let { route -> host.onRoute(route) } ?: host.onProfile() },
                )
            }
            if (glances.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    glances.forEach { glance ->
                        HeroGlance(
                            icon = glance.icon,
                            label = glance.label,
                            value = glance.value,
                            caption = glance.caption,
                            number = glance.number,
                            valueColor = when (glance.tone) {
                                HomeTone.ACCENT -> primary
                                HomeTone.ALERT -> MiuixTheme.colorScheme.error
                                HomeTone.NORMAL -> MiuixTheme.colorScheme.onSurface
                                HomeTone.MUTED -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                            },
                            onClick = { glance.route?.let { host.onRoute(it) } },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
            if (quick != null) {
                Spacer(Modifier.height(12.dp))
                quick()
            }
        }
    }
}

/** Hero 里半透明的内嵌面板：叠在 Mesh 上像一层磨砂，比再套一张实心卡轻。 */
@Composable
private fun Modifier.heroInset(radius: Dp = 18.dp): Modifier {
    val dark = LocalIsDarkTheme.current
    return this
        .squircleClip(radius)
        .background(AppCardColor.copy(alpha = if (dark) 0.55f else 0.72f))
        .squircleBorder(1.dp, Color.White.copy(alpha = if (dark) 0.06f else 0.6f), radius)
}

@Composable
private fun HeroStatusLine(
    status: HomeStatusLine,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val tint = when (status.tone) {
        HomeTone.ACCENT -> STATUS_BLUE
        HomeTone.ALERT -> MiuixTheme.colorScheme.error
        HomeTone.NORMAL -> STATUS_GREEN
        HomeTone.MUTED -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val dot = @Composable {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tint)
        )
    }
    val click = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = SinkFeedback(),
        onClick = onClick,
    )
    // 刊头里只给结论，明细点开看
    if (compact) {
        Row(click, verticalAlignment = Alignment.CenterVertically) {
            dot()
            Spacer(Modifier.width(5.dp))
            Text(
                status.label,
                style = MiuixTheme.textStyles.footnote2,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Default.ChevronRight, contentDescription = "查看子系统连接状态", tint = muted, modifier = Modifier.size(13.dp))
        }
        return
    }
    Row(click, verticalAlignment = Alignment.CenterVertically) {
        dot()
        Spacer(Modifier.width(6.dp))
        Text(
            "${status.label} · ${status.detail}",
            style = MiuixTheme.textStyles.footnote1,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = "查看子系统连接状态",
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** 彩虹主题的下一项安排：左边钟点，右边名称与倒计时，整条点进日程。 */
@Composable
private fun HeroNextUp(
    nextUp: HomeNextUp?,
    onClick: () -> Unit,
) {
    val primary = MiuixTheme.colorScheme.primary
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(
        Modifier
            .fillMaxWidth()
            .heroInset()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(),
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val item = nextUp ?: return
        val timeLabel = item.timeLabel
        var detail: AnnotatedString? = null
        if (timeLabel != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    timeLabel,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                    color = primary,
                )
                Text(
                    item.timeCaption.orEmpty(),
                    style = MiuixTheme.textStyles.footnote2,
                    color = muted,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.18f))
            )
            Spacer(Modifier.width(12.dp))
            detail = buildAnnotatedString {
                val eta = item.eta
                if (!eta.isNullOrBlank()) {
                    pushStyle(SpanStyle(color = primary, fontWeight = FontWeight.Medium))
                    append(eta)
                    pop()
                }
                val tail = item.detail
                if (!tail.isNullOrBlank()) append((if (eta.isNullOrBlank()) "" else " · ") + tail)
            }
        } else {
            ExpressiveIcon(icon = item.icon, color = primary, size = 38.dp, iconSize = 20.dp)
            Spacer(Modifier.width(12.dp))
            item.detail?.takeIf { it.isNotBlank() }?.let { detail = AnnotatedString(it) }
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            detail?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = MiuixTheme.textStyles.footnote1,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = muted, modifier = Modifier.size(18.dp))
    }
}

/** 彩虹主题的速览格：小标签在上、数值在下。 */
@Composable
private fun HeroGlance(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueColor: Color = MiuixTheme.colorScheme.onSurface,
    number: Double? = null,
    caption: String? = null,
) {
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val valueStyle = MiuixTheme.textStyles.body1
    Column(
        modifier
            .heroInset(16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(),
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = muted, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MiuixTheme.textStyles.footnote2, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        if (number != null) {
            RollingNumberText(
                value = number,
                format = { yuanText(it) },
                style = valueStyle,
                fontWeight = FontWeight.Bold,
                color = valueColor,
            )
        } else {
            Text(
                value,
                style = valueStyle,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (caption != null) {
            Text(caption, style = MiuixTheme.textStyles.footnote2, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 「待办与消息」那一格（彩虹主题）。 */
@Composable
private fun HomeInboxCard(summary: HomeInboxSummary, onClick: () -> Unit) {
    val accent = MiuixTheme.colorScheme.primary
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .appCardShadow(shape = RoundedCornerShape(CARD_RADIUS))
            .squircleClip(CARD_RADIUS)
            .background(AppCardColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(),
                onClick = onClick,
            )
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dark = LocalIsDarkTheme.current
            Box(
                Modifier
                    .size(32.dp)
                    .squircleBackground(accent.copy(alpha = if (dark) 0.24f else 0.12f), 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("待办与消息", style = MiuixTheme.textStyles.headline1, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        summary.isEmpty -> "没有要办的事，也没有未读"
                        summary.todoCount > 0 && summary.unreadCount > 0 ->
                            "还有 ${summary.todoCount} 件没办 · ${summary.unreadCount} 组没读"
                        summary.todoCount > 0 -> "还有 ${summary.todoCount} 件没办"
                        else -> "${summary.unreadCount} 组消息没读"
                    },
                    style = MiuixTheme.textStyles.footnote2,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = muted, modifier = Modifier.size(18.dp))
        }
        summary.nextTodo?.let { todo ->
            Spacer(Modifier.height(12.dp))
            Text("最要紧的一件", style = MiuixTheme.textStyles.footnote2, color = muted, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                todo,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 分类卡的排布（彩虹主题）。 */
@Composable
private fun CategoryCards(
    count: Int,
    spacing: Dp,
    weight: (Int) -> Int,
    card: @Composable (Int) -> Unit,
) {
    if (!isWideLayout()) {
        for (i in 0 until count) {
            Box(Modifier.enterOnce(i + 1)) { card(i) }
            if (i != count - 1) Spacer(Modifier.height(spacing))
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
        balancedColumns(count, weight).forEach { column ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing)) {
                column.forEach { i -> Box(Modifier.enterOnce(i + 1)) { card(i) } }
            }
        }
    }
}

/**
 * 宽屏两列：分类只有几个，穷举分法取两列高度差最小的；第一块固定在左列，列内保持原顺序。
 * [weight] 是各块的大致高度（行数）。
 */
private fun balancedColumns(count: Int, weight: (Int) -> Int): List<List<Int>> {
    val w = List(count) { weight(it) }
    fun inRight(mask: Int, i: Int) = (mask shr i) and 1 == 1
    val best = (0 until (1 shl count) step 2).minByOrNull { mask ->
        abs(w.indices.sumOf { if (inRight(mask, it)) w[it] else -w[it] })
    } ?: 0
    return w.indices.partition { !inRight(best, it) }.toList()
}

/** 两个主题共用的服务条目数据。 */
internal data class HomeServiceRow(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String = "",
    val color: Color,
    val onClick: () -> Unit,
    val stat: String? = null,
    val statDetail: String? = null,
    val tone: HomeTone = HomeTone.NORMAL,
)

/** 彩虹主题的分类卡。 */
@Composable
private fun HomeCategoryCard(
    title: String,
    subtitle: String,
    accent: Color,
    rows: List<HomeServiceRow>,
) {
    val dark = LocalIsDarkTheme.current
    val glow = accent.copy(alpha = if (dark) 0.16f else 0.10f)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .appCardShadow(shape = RoundedCornerShape(CARD_RADIUS))
            .squircleClip(CARD_RADIUS)
            .background(AppCardColor)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(glow, Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = size.maxDimension * 0.75f,
                    )
                )
            }
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                subtitle,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(bottom = 2.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        rows.chunked(4).forEach { group ->
            Row(Modifier.fillMaxWidth()) {
                group.forEach { row ->
                    HomeServiceTile(row, Modifier.weight(1f), solidIcon = true)
                }
                repeat(4 - group.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** 宫格里的一个入口：图标 + 名称，没有独立底色，卡片是唯一容器。 */
@Composable
private fun HomeServiceTile(
    row: HomeServiceRow,
    modifier: Modifier = Modifier,
    solidIcon: Boolean = false,
) {
    val origin = rememberExpandOriginSource()
    val density = LocalDensity.current
    Column(
        modifier = modifier
            .expandOriginSource(origin)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(),
                onClick = {
                    origin.arm(row.key, 14.dp, density)
                    row.onClick()
                }
            )
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (solidIcon) {
            GradientAppIcon(row.icon, row.color)
        } else {
            ExpressiveIcon(icon = row.icon, color = row.color, size = 42.dp, iconSize = 21.dp)
        }
        Spacer(Modifier.height(7.dp))
        Text(
            row.title,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 点开功能页，并让功能页从这一块放大出来。 */
@Composable
private fun Modifier.opensRow(row: HomeServiceRow, radius: Dp = 12.dp): Modifier {
    val origin = rememberExpandOriginSource()
    val density = LocalDensity.current
    return this
        .expandOriginSource(origin)
        .clip(RoundedCornerShape(radius))
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = SinkFeedback(),
            onClick = {
                origin.arm(row.key, radius, density)
                row.onClick()
            },
        )
}

/** 首页各卡之间统一的间距。 */
private val SECTION_GAP = 14.dp

/** 首页折叠顶栏（状态栏以下）的高度，三个 36dp 圆钮在里面居中。 */
private val BAR_HEIGHT = 52.dp

/** 杂志版式的卡片圆角。 */
private val EDITORIAL_RADIUS = 24.dp

/** 教学周超过这个数就不显示（假期或学期没对上）。 */
private const val MAX_TEACHING_WEEK = 25

/** 彩虹主题分类卡圆角。 */
private val CARD_RADIUS = 26.dp

/** Hero 右上角插画占的宽度（`:app` 那张校区图是 104dp，文字列得给它让开）。 */
private val HERO_ART_SIZE = 104.dp

private val STATUS_GREEN = Color(0xFF2E7D32)
private val STATUS_BLUE = Color(0xFF1565C0)

/** `¥42.50` —— 自己拼两位小数。 */
private fun yuanText(value: Float): String {
    val cents = (value * 100 + 0.5).toLong()
    return "¥${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

/** 首页 Hero 卡 Mesh 渐变的 3x3 顶点：每格是「主题色混进卡片底色的比例」。 */
private val HeroMeshWeights = listOf(
    listOf(0.15f, 0.08f, 0.22f),
    listOf(0.07f, 0.11f, 0.08f),
    listOf(0.03f, 0.02f, 0.05f),
)
