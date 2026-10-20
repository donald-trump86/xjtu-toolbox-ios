package com.xjtu.toolbox.web

import com.xjtu.toolbox.core.net.ApiMode
import com.xjtu.toolbox.core.net.CampusEmptyRoomApi
import com.xjtu.toolbox.inbox.InboxStore
import com.xjtu.toolbox.inbox.OwnInbox
import com.xjtu.toolbox.platform.keyValueStore
import com.xjtu.toolbox.schedule.CourseLinkAccess
import com.xjtu.toolbox.schedule.CustomCourseStore
import com.xjtu.toolbox.schedule.HolidaySource
import com.xjtu.toolbox.schedule.JwxtScheduleSource
import com.xjtu.toolbox.schedule.KeyValueCustomCourseStore
import com.xjtu.toolbox.schedule.KeyValueScheduleCacheStore
import com.xjtu.toolbox.schedule.ScheduleCacheStore
import com.xjtu.toolbox.schedule.ScheduleCapability
import com.xjtu.toolbox.schedule.ScheduleHost
import com.xjtu.toolbox.schedule.ScheduleLogin
import com.xjtu.toolbox.schedule.ScheduleSession
import com.xjtu.toolbox.schedule.ServedSchedule
import com.xjtu.toolbox.settings.SettingsStore
import com.xjtu.toolbox.util.safeParseJsonObject
import com.xjtu.toolbox.util.safeString
import io.ktor.client.HttpClient
import kotlinx.datetime.LocalDate

/**
 * Web 端的日程页宿主面（`ScheduleHost`）。
 *
 * 座位数两种形态都有真取数（[CampusEmptyRoomApi.seatCount]：serve 打 `seat-count`，
 * CAMPUS_API 则在当天已拉过的教室行里查），所以 [honored] 里不再按形态分叉。
 */
internal class WebScheduleHost(
    private val client: HttpClient,
    private val mode: ApiMode,
) : ScheduleHost {

    /**
     * 见类 KDoc：自建日程 / 课表变更气泡 / 座位数三样，两种形态都真会照着办。
     * 座位数在 CAMPUS_API 形态下也不是空实现：算的是 [CampusEmptyRoomApi.seatCount] 的 `cachedRooms` 那一档。
     */
    override val honored: Set<ScheduleCapability> = buildSet {
        add(ScheduleCapability.CUSTOM_COURSE_EDIT)
        // 调课/停课的气泡：一条自己的提醒写进 `:core` 的收纳（见类 KDoc 与 [postScheduleChange]）
        add(ScheduleCapability.SCHEDULE_CHANGE_BUBBLE)
        add(ScheduleCapability.SEAT_COUNT)
    }

    /**
     * 这一端没接的那一档一句（见 [ScheduleHost.gapNotes]，共享日程屏把它画在**本该有那一块的位置**）。
     *
     * 只写 [ScheduleCapability.SHARE_FILE]：日程页顶那条总说明（`WebScheduleGapNote`）讲的是**取数**
     * 与在屏上没有落点的能力级缺口，而「导出日历 (ICS)」这一行落在导出菜单里 —— 那一格空着最像
     * 「坏了」，所以缺口说明就放在它本该出现的地方。文案：这一端没有什么 / 为什么 / 去哪儿能用。
     */
    override val gapNotes: Map<ScheduleCapability, String> = mapOf(
        ScheduleCapability.SHARE_FILE to
            "Web 版没有「导出日历 (ICS)」：这一步要落一个文件给系统，本端没接（不是浏览器做不到 ——" +
                "只是这一端还没接上下载那一步）。要 .ics 请用 App 或桌面版：日程 → 右上「导出」，" +
                "桌面会弹系统保存框。",
    )

    /** 课表变更（调课/停课/换教室）冒一条气泡。 */
    override fun postScheduleChange(message: String) {
        InboxStore.post(OwnInbox.scheduleChange(message))
    }

    override fun cache(accountId: String?): ScheduleCacheStore =
        KeyValueScheduleCacheStore(keyValueStore(SCHEDULE_CACHE_STORE))

    override val customCourses: CustomCourseStore =
        KeyValueCustomCourseStore(keyValueStore(CUSTOM_COURSE_STORE))

    /** 这一端没有账号：`accountId` / `studentId` 都给空串（`:app` 那边是登录态里的学号）。 */
    override val login: ScheduleLogin = object : ScheduleLogin {
        override val accountId: String = ""
        override val studentId: String = ""
        /** `true`：本端能不能取数由服务端答（campus-api 的会话 / serve 的令牌）。 */
        override val hasCredentials: Boolean = true
    }

    override val session: ScheduleSession = WebScheduleSession(WebScheduleSource(client, mode))

    override val holidays: HolidaySource = WebHolidaySource

    /** 课程详情的两项下钻本端没有 ⇒ 不画。 */
    override val courseLinks: CourseLinkAccess? = null

    /**
     * 详情里「N座」那一格的取数：serve 形态是 §5.8 的 `seat-count`；CAMPUS_API 形态从
     * 本次会话已经拉到的那一天教室行（`cachedRooms`）里按教室名查，查不到就是 null（那一格不画数字）。
     */
    private val emptyRoom = CampusEmptyRoomApi(client, API_BASE, mode)

    override suspend fun roomSeatCount(location: String): Int? = emptyRoom.seatCount(location)

    companion object {
        /** 课表族缓存的键前缀（wasm 上落在 `xjtu-toolbox.schedule_cache.*`）。 */
        const val SCHEDULE_CACHE_STORE = "schedule_cache"

        /** 自建日程那张表（`xjtu-toolbox.custom_courses.*`）。 */
        const val CUSTOM_COURSE_STORE = "custom_courses"
    }
}

/** 教务会话那一格：本端没有登录这回事，会话在服务端进程里。 */
internal class WebScheduleSession(private val webSource: WebScheduleSource) : ScheduleSession {

    override val source: JwxtScheduleSource get() = webSource

    override val isLoggedIn: Boolean get() = true

    override suspend fun ensureLogin(userInitiated: Boolean): JwxtScheduleSource = webSource

    override suspend fun reAuthenticate(userInitiated: Boolean): JwxtScheduleSource = webSource

    override suspend fun fetchSchedule(
        source: JwxtScheduleSource,
        termCode: String,
        userInitiated: Boolean,
    ): ServedSchedule = ServedSchedule(
        courses = source.schedule(termCode).courses,
        sourceKey = SettingsStore.SCHEDULE_SOURCE_JWXT,
    )
}

/** 节假日那一格：只读落盘的那一份，不联网。 */
private object WebHolidaySource : HolidaySource {

    private const val CACHE_KEY = "holiday_dates"

    override fun peekCached(cache: ScheduleCacheStore): Map<LocalDate, String> =
        parseHolidays(cache.readJson(CACHE_KEY, Long.MAX_VALUE))

    /** 见本对象 KDoc：本端不联网取节假日 ⇒ 刷新就是再读一次缓存。 */
    override suspend fun refresh(cache: ScheduleCacheStore, force: Boolean): Map<LocalDate, String> =
        peekCached(cache)

    private fun parseHolidays(json: String?): Map<LocalDate, String> {
        if (json.isNullOrBlank()) return emptyMap()
        val root = json.safeParseJsonObject()
        return root.entries.mapNotNull { (date, name) ->
            runCatching { LocalDate.parse(date) to name.safeString() }.getOrNull()
        }.toMap()
    }
}
