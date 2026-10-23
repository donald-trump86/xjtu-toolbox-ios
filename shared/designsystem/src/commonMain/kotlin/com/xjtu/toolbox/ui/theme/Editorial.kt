package com.xjtu.toolbox.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 杂志风排版的字号与字距，字体用系统默认。只给大标题、刊头、期号、大号数字用。 */
object Editorial {
    /** 刊头大字，如问候语。 */
    val masthead = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = 0.5.sp)

    /** 头条：首页的下一节课、海报的主数字说明。 */
    val headline = TextStyle(fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = 0.3.sp)

    /** 栏目标题，如「01 上课」里的「上课」。 */
    val section = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = 1.5.sp)

    /** 大号数字，等高数字对齐。 */
    val figure = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, fontFeatureSettings = "lnum")

    /** 期号、栏目序号这类西文小字：大写、拉开字距。 */
    val kicker = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 2.sp)

    /** 发丝线：分区靠线和留白，不靠框。 */
    val hairline: Dp = 0.6.dp
}

/** 一道发丝线，颜色取描边色的淡版，深浅色下都只是轻轻一划；深色卡面上传 [color] 换成白线。 */
@Composable
fun EditorialRule(modifier: Modifier = Modifier, color: Color? = null) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Editorial.hairline)
            .background(color ?: MiuixTheme.colorScheme.outline.copy(alpha = 0.22f))
    )
}

/**
 * 栏目标题：左边两位序号（主题色），中间栏目名，右边一句说明，底下一道发丝线。
 * 序号补零写成「01」，和刊物目录一个样子。
 */
@Composable
fun EditorialSectionHeader(
    index: Int,
    title: String,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                index.toString().padStart(2, '0'),
                style = Editorial.kicker.copy(fontSize = 13.sp, letterSpacing = 1.sp),
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 3.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(title, style = Editorial.section, color = MiuixTheme.colorScheme.onSurface)
            if (note != null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    note,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(bottom = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        EditorialRule()
    }
}
