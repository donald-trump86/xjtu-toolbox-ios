package com.xjtu.toolbox.legal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The privacy text shown by the iOS settings page. */
object Eula {
    data class Section(val title: String, val body: AnnotatedString)

    @Composable
    fun sections(): List<Section> = listOf(
        section(
            "一、应用性质",
            "岱宗盒子 iOS 是 XJTU Toolbox 的非官方 Fork，复用上游界面与校园协议，为 iPhone 和 iPad 提供课表、成绩与账号功能。本应用不隶属于、不代表西安交通大学或其任何部门。",
        ),
        section(
            "二、校园数据请求",
            "学校系统请求由设备直接发起，通过 HTTPS 和系统 TLS 验证连接相关学校服务。应用不通过开发者服务器转发校园账号或个人课表、成绩；本版本未接入自动崩溃上报或分析服务。学校 WebVPN 是可选访问方式，其认证与各业务系统的会话分别管理。",
        ),
        section(
            "三、设备内存储",
            "账号凭据、会话和个人课表、成绩缓存保存在设备 Keychain 中，外观与触感偏好保存在本地。退出账号时清除对应会话与个人缓存，并拒绝旧请求继续写入缓存。设备锁定、签名身份变化或系统策略可能影响 Keychain 访问，届时需要解决访问条件或重新登录。",
        ),
        section(
            "四、登录与短信验证",
            "校园密码和短信验证码在应用内输入，并发送给相应的学校认证接口。后台恢复不会主动发送短信。请只使用自己的账号，不要在反馈、聊天或公开截图中提供密码、验证码、Cookie 或令牌。当前基本信息仅显示 NetID。",
        ),
        section(
            "五、外部工具与链接",
            "SideStore 和 LocalDevVPN 用于个人设备安装及刷新签名，由各自工具管理 Apple Account 与设备配对。它们与校园登录独立。点击项目主页、源码或许可链接时，系统会打开相应外部页面。",
        ),
        section(
            "六、数据准确性与使用",
            "学校接口、网络和会话状态可能变化，离线缓存也可能过期。课程、考试与成绩等重要信息请以学校正式系统为准。应用按现状提供；使用时应遵守学校系统的使用规定，避免恶意请求或接口滥用。",
        ),
        section(
            "七、来源与许可",
            "本 Fork 基于 yeliqin666/xjtu-toolbox-android，保留上游版权，以 GPL-3.0 发布。第三方组件、地图数据和资源遵循各自许可，完整声明见项目仓库。学校名称、业务数据和相关标识归对应权利方所有；本 Fork 的新图标不是学校官方标识。",
        ),
    )

    private fun section(title: String, text: String) = Section(title, buildAnnotatedString { append(text) })

    @Composable
    fun Body(modifier: Modifier = Modifier) {
        val items = sections()
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(16.dp)) {
                items.forEachIndexed { idx, section ->
                    if (idx > 0) Spacer(Modifier.height(12.dp))
                    Text(
                        section.title,
                        style = MiuixTheme.textStyles.subtitle,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        section.body,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}
