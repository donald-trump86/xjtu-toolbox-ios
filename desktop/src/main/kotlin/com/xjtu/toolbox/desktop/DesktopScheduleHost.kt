package com.xjtu.toolbox.desktop

import com.xjtu.toolbox.account.AccountContext
import com.xjtu.toolbox.emptyroom.EmptyRoomApi
import com.xjtu.toolbox.inbox.InboxStore
import com.xjtu.toolbox.inbox.OwnInbox
import com.xjtu.toolbox.platform.secureKeyValueStore
import com.xjtu.toolbox.platform.showBriefMessage
import com.xjtu.toolbox.schedule.CourseLinkAccess
import com.xjtu.toolbox.schedule.CustomCourseStore
import com.xjtu.toolbox.schedule.DataCourseLinkAccess
import com.xjtu.toolbox.schedule.DataHolidaySource
import com.xjtu.toolbox.schedule.DataScheduleSession
import com.xjtu.toolbox.schedule.HolidaySource
import com.xjtu.toolbox.schedule.KeyValueCustomCourseStore
import com.xjtu.toolbox.schedule.KeyValueScheduleCacheStore
import com.xjtu.toolbox.schedule.ScheduleCacheStore
import com.xjtu.toolbox.schedule.ScheduleCapability
import com.xjtu.toolbox.schedule.ScheduleHost
import com.xjtu.toolbox.schedule.ScheduleLogin
import com.xjtu.toolbox.schedule.ScheduleSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** 桌面端的日程页宿主面（见 `ScheduleHost` 与 `ScheduleCapability`）。 */
internal class DesktopScheduleHost(private val auth: DesktopAuth) : ScheduleHost {

    override val honored: Set<ScheduleCapability> = setOf(
        ScheduleCapability.CUSTOM_COURSE_EDIT,
        ScheduleCapability.LMS_LINK,
        // 调课/停课/换教室冒一条气泡：与 Web 同一份写法（`WebScheduleHost.honored` 那一条），
        // 收纳也走同一个 `InboxStore`（桌面首页的收纳卡读的就是它，见 DesktopHome.kt）。
        ScheduleCapability.SCHEDULE_CHANGE_BUBBLE,
        // 详情里「N座」那一格：`EmptyRoomApi` 直取 CDN 上每日预生成的全校教室表（无需登录）。
        ScheduleCapability.SEAT_COUNT,
        // 「导出日历 (ICS)」：桌面没有分享面板，但**有系统保存框**（`FileDialog`）⇒
        // 那一行菜单真能落到一份 .ics 上（见 shareFile）。不是「等同没有」。
        ScheduleCapability.SHARE_FILE,
        // 课表落地后给首页发信号：桌面现在有首页了（`AppRoute.Main` → `DesktopHomeScreen`），
        // 它盯的版本号就是 `DesktopHomeSignals.scheduleVersion`（见 onScheduleSynced）。
        // WIDGET_REFRESH 仍旧不列 —— 这一端没有桌面小组件（设计文档 §6 Stage C 说
        // 「小组件用系统通知替代」，那条路还没做，见 `DesktopHomeScreen` 的 TODO #1）。
        ScheduleCapability.HOME_SIGNALS,
    )

    /**
     * 桌面没接的那两档各一句（见 [ScheduleHost.gapNotes]，共享日程屏把它们画在本该有那一块的位置）。
     *
     * 每句都说清三件事：桌面没有什么 / 为什么 / 去哪儿能用。不列 WIDGET_REFRESH：日程屏上没有
     * 「小组件」那一格，说了也无处落点（那件事归桌面外壳那一侧）。
     * SHARE_FILE 也不列了：这一端已经能把 ICS 存成文件（见 [shareFile]）。
     */
    override val gapNotes: Map<ScheduleCapability, String> = mapOf(
        ScheduleCapability.ATTENDANCE to
            "桌面版没有课表考勤角标，详情里也没有「本次考勤」：考勤要单独登录考勤站点拉流水，" +
            "这一路还没接过来。看考勤请用 App。",
        ScheduleCapability.DUE_HOMEWORK to
            "桌面版「接下来」里没有作业截止：作业截止读的是思源学堂落盘的那份缓存，" +
            "桌面还没接思源。作业请到思源学堂看（App 里也有一份）。",
    )

    override val io: CoroutineDispatcher = Dispatchers.IO
    /** 课表落地之后要通知的事：桌面只有一件 —— 让首页重读一次缓存（`:app` 那一份还挂着桌面小组件刷新与提醒评估，桌面上没有那两样）。 */
    override fun onScheduleSynced() = DesktopHomeSignals.onScheduleSynced()

    /**
     * 把一份文件交给用户（[ScheduleCapability.SHARE_FILE] 的那一格）：桌面上没有「分享面板」，
     * 但**有系统保存框** —— 所以这一格做的不是「分享」而是「另存为」，结果一样：用户拿到了那份 .ics，
     * 拖进任何日历应用都能导入。取消 = 不算成功（那一行菜单不声称「已导出」）。
     */
    override fun shareFile(content: String, fileName: String, mimeType: String): Boolean {
        val ext = if (mimeType == "text/calendar") "ics" else "txt"
        val name = if (fileName.endsWith(".$ext", ignoreCase = true)) fileName else "$fileName.$ext"
        val saved = DesktopFileDialogs.saveBytes("导出日历", name, content.toByteArray(Charsets.UTF_8))
        showBriefMessage(if (saved != null) "已导出到 ${saved.path}" else "没有导出：保存框取消了")
        return saved != null
    }

    /**
     * 教室座位数的取数（见 [ScheduleCapability.SEAT_COUNT]）。
     *
     * 留着**一份实例**：CDN 当天那份全校 JSON 与「教室名 → 座位数」都只活在实例里，
     * 每次新建就等于每点开一次详情都重拉一遍全校数据（Android 那侧每次 `EmptyRoomApi()` 正是这个代价）。
     * 显式不走 CDN 以外的路：这一档在桌面只有 CDN 这一条（直查教务那条要站点会话，见 `EmptyRoomDirectQuery`）。
     */
    private val emptyRoom = EmptyRoomApi()

    override suspend fun roomSeatCount(location: String): Int? = emptyRoom.getRoomSeatCount(location)

    /** 调课/停课/换教室冒一条气泡（与 Web 的 `WebScheduleHost.postScheduleChange` 同一条链）。 */
    override fun postScheduleChange(message: String) {
        InboxStore.post(OwnInbox.scheduleChange(message))
    }

    override fun cache(accountId: String?): ScheduleCacheStore =
        KeyValueScheduleCacheStore(
            secureKeyValueStore(accountScopedStoreName(SCHEDULE_CACHE_STORE, accountId)),
        )

    override val customCourses: CustomCourseStore =
        KeyValueCustomCourseStore(secureKeyValueStore(CUSTOM_COURSE_STORE))

    /** 桌面把两个可发现的事实接到 `AccountContext` 与登录状态上。 */
    override val login: ScheduleLogin = object : ScheduleLogin {
        override val accountId: String get() = AccountContext.activeAccountId.orEmpty()
        override val studentId: String get() = AccountContext.activeAccountId.orEmpty()
        override val hasCredentials: Boolean get() = auth.loggedIn
    }

    override val session: ScheduleSession = DataScheduleSession(
        manager = { auth.sessionManager },
        credentials = { auth.sessionManager.credentials },
    )

    override val holidays: HolidaySource = DataHolidaySource()

    override val courseLinks: CourseLinkAccess = DataCourseLinkAccess(
        manager = auth.sessionManager,
        accountType = AccountContext.activeAccountType,
    )

    companion object {
        /** 课表族缓存的落盘文件（`schedule_cache<账号后缀>`）。 */
        const val SCHEDULE_CACHE_STORE = "schedule_cache"

        /** 自建日程的落盘文件（里面只有 `custom_courses` 这一个键）。 */
        const val CUSTOM_COURSE_STORE = "custom_courses"
    }
}
