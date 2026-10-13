package com.xjtu.toolbox.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.Text

/**
 * 底栏 / 侧栏共用的状态：**选中的是哪一格、有没有账号角标、点了怎么办**。
 *
 * 这三样是壳的语义，不是端的能力：端上只决定「选中变谁」（写自己的设置 / 切自己的页），
 * 以及「点这一格要顺带干什么」（Android 点屁岱还要冒一句闲话，见那一段的 `onSelect`）。
 *
 * @param tabs 这一端的 tab 集合（[ShellTabs]，裁剪是各端的权利）。
 * @param selectedId 选中的那一格的 [ShellTab.id]。给的是 id 而不是序号：序号会在集合变化时错位。
 * @param onSelect 点了某一格。**程序性切页不要走这里**（那是端上自己的状态写入）——
 *   这一条回调只从底栏/侧栏来，所以「点 tab 才震一下」「点屁岱冒气泡」这类副作用也挂在这里。
 * @param accountBadge 「我的」那一格上的角标内容；null = 不画。见 [ShellAccountBadge.of]。
 */
@Stable
class ShellNavState(
    val tabs: ShellTabs,
    val selectedId: String,
    val onSelect: (ShellTab) -> Unit,
    val accountBadge: ShellAccountBadge? = null,
) {

    /** 当前选中的那一格。id 不在集合里（上一版存盘的、被裁掉的）就退到第一格。 */
    val selected: ShellTab get() = tabs.byId(selectedId) ?: tabs.first

    /**
     * [tab] 这一格上的角标；null = 不画。
     *
     * 「哪一格有角标」由 [ShellTab.showsAccountBadge] 说，「画什么」由 [accountBadge] 说 ——
     * 这一条判据（只有那一格有、其余格一个都不画）是共享的，端上不许自己再写一份。
     */
    fun badge(tab: ShellTab): (@Composable () -> Unit)? {
        val badge = accountBadge ?: return null
        if (!tab.showsAccountBadge) return null
        val count = badge.count
        return if (count != null) {
            { Badge { Text(count.coerceAtMost(99).toString()) } }
        } else {
            { Badge() }
        }
    }
}

/**
 * 账号角标的内容：数字（多账号，最多画到 99）或一个红点（未登录）。
 *
 * 判据本身也共享，因为三端都要回答同一个问题：这台设备上有几个账号。
 * `:app` 传的是 `accountManager.accountList().size` 与 `loginState.isLoggedIn`。
 */
class ShellAccountBadge private constructor(val count: Int?) {

    companion object {

        /** 多账号 → 数字；未登录 → 红点；其余（单账号且已登录）→ null（**不画角标**）。 */
        fun of(accountCount: Int, isLoggedIn: Boolean): ShellAccountBadge? = when {
            accountCount > 1 -> ShellAccountBadge(accountCount)
            !isLoggedIn -> ShellAccountBadge(null)
            else -> null
        }
    }
}
