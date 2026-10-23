package com.xjtu.toolbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 通知页「屁岱精选」和对话结果共用一条通知，来源信息始终从原始通知取得。 */
@Composable
fun NoticePickItem(
    title: String, metadata: String, why: String = "", level: Int = 1,
    enabled: Boolean = true, onOpen: () -> Unit, modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val color = when (level) {
        3 -> MiuixTheme.colorScheme.error
        2 -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppInsetColor)
            .clickable(enabled = enabled, onClickLabel = "查看通知原文", onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(color))
            Text(title, style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Medium,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 9.dp).weight(1f))
            if (enabled) Icon(Icons.AutoMirrored.Filled.ArrowForward, null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = .55f),
                modifier = Modifier.padding(start = 8.dp, top = 3.dp).size(14.dp))
        }
        if (why.isNotBlank()) Text(why, style = MiuixTheme.textStyles.footnote1, color = color,
            modifier = Modifier.padding(start = 15.dp))
        if (metadata.isNotBlank() || trailing != null) Row(Modifier.padding(start = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(metadata, style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
    }
}
