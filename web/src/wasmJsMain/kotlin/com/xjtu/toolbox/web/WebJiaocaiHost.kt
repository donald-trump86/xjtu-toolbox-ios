package com.xjtu.toolbox.web

import com.xjtu.toolbox.jiaocai.JiaocaiTermTextbook
import com.xjtu.toolbox.jiaocai.JiaocaiTermTextbooks

/**
 * 「本学期我的教材」那一段在 **serve 形态**下的宿主填空：**如实回空表**（那一段整段不显示）。
 *
 * ## 为什么是空表，而不是「查不到」
 *
 * 那一段的数据**不在教材中心**，在**教务**的报表里（`:data` 的 `JwxtScheduleApi.textbooks(studentId, term)`，
 * 帆软报表，**要学号**）—— 学号是契约 §5.2 的红线，serve 那一层刻意不端它（`docs/api-contract.md`
 * §5.6 的 jiaocai 那一节写明了这一条）。本端也没有第二个来源：浏览器手里没有课表缓存。
 *
 * `JiaocaiTermTextbooks.currentTerm()` 的约定本来就是「读不到（没有缓存 / 没有学期）返回空表」，
 * `JiaocaiTermTextbooks.currentTerm()` 的约定本来就是「读不到（没有缓存 / 没有学期）返回空表」，
 * 而 `:core` 的 `TextbookScreen` 拿到空表就**整段不显示**（那一屏内部把「还没读」与「读完了
 * 但没有」分得很清楚）—— 所以这里给空表正好落在「这一段不画」上，**不会**画出「本学期没有
 * 教材」这种把缺口说成事实的话。这一条与桌面端的 `DesktopJiaocaiTermTextbooks`（那边没有
 * 课表缓存）同一条口径。
 *
 * ⚠️ 与它相对的另一半（全文库的取数与书架）在 `:web` 上连端口都没有：那一条链是另一个域名、
 * 另一套会话（`jiaocai1`），本轮没有端点 ⇒ `TextbookScreen` 那边传 null（见那个参数的 KDoc），
 * 屏上只留「查教材」一栏。
 */
internal object WebJiaocaiTermTextbooks : JiaocaiTermTextbooks {

    /** 见类 KDoc：本端没有那份教务缓存（它要学号）⇒ 如实回空表，那一段整段不显示。 */
    override suspend fun currentTerm(): List<JiaocaiTermTextbook> = emptyList()
}
