package com.xjtu.toolbox.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xjtu.toolbox.ui.components.AppCardColor
import com.xjtu.toolbox.ui.components.AppSearchBar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun NoticeSourceBar(sources: Set<NotificationSource>, onOpen: () -> Unit) {
    val primary = MiuixTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp)).background(AppCardColor)
            .semantics { contentDescription = "选择通知来源" }.clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(primary.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Newspaper, null, Modifier.size(20.dp), tint = primary)
        }
        Text(sources.joinToString("、") { it.displayName }, modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${sources.size} 个来源", style = MiuixTheme.textStyles.footnote1, color = primary)
        Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(20.dp), tint = primary)
    }
}

/** 多选先保存在面板内，确认后只重新加载一次。搜索不受分类限制。 */
@Composable
internal fun NoticeSourceSheet(
    show: Boolean,
    sources: Set<NotificationSource>,
    onDismiss: () -> Unit,
    onConfirm: (Set<NotificationSource>) -> Unit,
) {
    var selected by remember { mutableStateOf(sources) }
    var category by remember { mutableStateOf(sources.firstOrNull()?.category ?: SourceCategory.GENERAL) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(show) {
        if (show) {
            selected = sources
            category = sources.firstOrNull()?.category ?: SourceCategory.GENERAL
            query = ""
        }
    }
    val primary = MiuixTheme.colorScheme.primary
    val summary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val shape = RoundedCornerShape(12.dp)
    val matching = if (query.isBlank()) NotificationSource.byCategory(category)
        else NotificationSource.entries.filter { it.displayName.contains(query.trim(), ignoreCase = true) }
    OverlayBottomSheet(show = show, title = "通知来源", onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
            AppSearchBar(query = query, onQueryChange = { query = it }, label = "搜索学院或部门", modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.weight(1f, fill = false).heightIn(min = 280.dp)) {
                if (query.isBlank()) {
                    Column(Modifier.width(82.dp).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SourceCategory.entries.forEach { item ->
                            val active = category == item
                            Row(
                                Modifier.fillMaxWidth().height(54.dp).clip(shape)
                                    .background(if (active) primary.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { category = item },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.width(3.dp).height(20.dp).clip(shape).background(if (active) primary else androidx.compose.ui.graphics.Color.Transparent))
                                Text(item.displayName, modifier = Modifier.padding(start = 10.dp), fontSize = 13.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, color = if (active) primary else summary)
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    matching.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { item ->
                                val checked = item in selected
                                Row(
                                    Modifier.weight(1f).heightIn(min = 58.dp).clip(shape)
                                        .background(if (checked) primary.copy(alpha = 0.10f) else summary.copy(alpha = 0.055f))
                                        .border(1.dp, if (checked) primary.copy(alpha = 0.5f) else summary.copy(alpha = 0.08f), shape)
                                        .toggleable(value = checked, role = Role.Checkbox) {
                                            selected = if (item in selected) selected - item else selected + item
                                        }.padding(horizontal = 10.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(item.displayName, modifier = Modifier.weight(1f), fontSize = 13.sp, maxLines = 2,
                                        fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (checked) primary else MiuixTheme.colorScheme.onSurface)
                                    if (checked) Icon(Icons.Default.Check, "已选", Modifier.size(15.dp), tint = primary)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (matching.isEmpty()) Text("没有匹配的来源", modifier = Modifier.padding(20.dp), color = summary)
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp))
                .background(if (selected.isNotEmpty()) primary else summary.copy(alpha = 0.12f))
                .clickable(enabled = selected.isNotEmpty()) { onConfirm(selected) }, contentAlignment = Alignment.Center) {
                Text(if (selected.isEmpty()) "请至少选择一个来源" else "查看 ${selected.size} 个来源",
                    color = if (selected.isNotEmpty()) MiuixTheme.colorScheme.onPrimary else summary,
                    fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
