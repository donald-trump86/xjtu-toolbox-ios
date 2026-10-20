package com.xjtu.toolbox.settings

import com.xjtu.toolbox.platform.KeyValueStore
import com.xjtu.toolbox.platform.keyValueStore

/** 设置页那些偏好的读写。 */
class SettingsStore(private val store: KeyValueStore = keyValueStore(APP_SETTINGS_FILE)) {

    /** 界面风格：[NAV_STYLE_FLOATING] 玻璃（默认），[NAV_STYLE_CLASSIC] 经典。 */
    var navBarStyle: String
        get() = store.getString(KEY_NAV_BAR_STYLE) ?: NAV_STYLE_FLOATING
        set(value) = store.putString(KEY_NAV_BAR_STYLE, value)

    var darkMode: String
        get() = store.getString(KEY_DARK_MODE) ?: DARK_MODE_SYSTEM
        set(value) = store.putString(KEY_DARK_MODE, value)

    /** 跟随系统壁纸 / 调色盘动态取色（Material You / Monet）。默认关闭。 */
    var dynamicColor: Boolean
        get() = store.getBoolean(KEY_DYNAMIC_COLOR, false)
        set(value) = store.putBoolean(KEY_DYNAMIC_COLOR, value)

    /** 自定义主题种子色；空串表示未选择。保持独立，切换取色来源时保留上次选择。 */
    var customColorHex: String
        get() = normalizeThemeColor(store.getString("custom_color_hex").orEmpty())
        set(value) = store.putString("custom_color_hex", normalizeThemeColor(value))

    var customColorEnabled: Boolean
        get() = store.getBoolean("custom_color_enabled", false)
        set(value) = store.putBoolean("custom_color_enabled", value)

    /** 主题实际采用的种子色：未启用自定义时为空串。 */
    val themeSeedHex: String get() = if (customColorEnabled) customColorHex else ""

    /**
     * 默认启动 tab：**存规范化值**（[TAB_KEY_HOME] 那五个，见 [normalizeDefaultTab]）。
     *
     * 为什么不直接存某一端的 tab id：三端的 tab 集合本就不一样（`:app` 五格 HOME/COURSES/PIDAI/TOOLS/PROFILE、
     * web 同构五格、桌面六格 main/school_calendar/library/yellow_page/games/all）。存端 id 就是把某一端的
     * 私有名字写进公共设置。规范化之后三端各有一张「规范值 → 本端 tab」的映射表：
     * `:app` `bottomTabOf()`、`:desktop` `desktopStartTarget()`、`:web` `webStartTarget()`。
     *
     * 老值（老版本存的是 `BottomTab` 的**名字**：`HOME/COURSES/…`）读进来时就翻成规范值 ⇒ 升级上来的用户
     * 不用重设，也不需要迁移脚本；写下去的一律是规范值。
     */
    var defaultTab: String
        get() = normalizeDefaultTab(store.getString(KEY_DEFAULT_TAB))
        set(value) = store.putString(KEY_DEFAULT_TAB, normalizeDefaultTab(value))
    var networkMode: String
        get() = store.getString(KEY_NETWORK_MODE) ?: NETWORK_AUTO
        set(value) = store.putString(KEY_NETWORK_MODE, value)

    var homeTheme: String
        get() = store.getString(KEY_HOME_THEME) ?: THEME_CARD
        set(value) = store.putString(KEY_HOME_THEME, value)

    var showQuickActions: Boolean
        get() = store.getBoolean(KEY_SHOW_QUICK_ACTIONS, true)
        set(value) = store.putBoolean(KEY_SHOW_QUICK_ACTIONS, value)

    /** 用户置顶的常用功能（路由 id，按显示顺序）；null = 没编辑过，按使用频率挑。 */
    var pinnedQuickRoutes: List<String>?
        get() = store.getString("pinned_quick_routes")?.split('\n')?.filter { it.isNotBlank() }
        set(value) = store.putString("pinned_quick_routes", value?.joinToString("\n"))

    /** 场馆滑块验证码自动识别开关。 */
    var venueAutoSolveCaptchaEnabled: Boolean
        get() = store.getBoolean(KEY_VENUE_AUTO_SOLVE_CAPTCHA, true)
        set(value) = store.putBoolean(KEY_VENUE_AUTO_SOLVE_CAPTCHA, value)

    /** 课表格子上叠考勤角标。 */
    var scheduleAttendanceBadge: Boolean
        get() = store.getBoolean(KEY_SCHEDULE_ATTENDANCE_BADGE, true)
        set(value) = store.putBoolean(KEY_SCHEDULE_ATTENDANCE_BADGE, value)

    /** 当前学期课表从哪个系统拉，取值见 `ScheduleSource.key`。 */
    var scheduleSource: String
        get() = store.getString(KEY_SCHEDULE_SOURCE) ?: SCHEDULE_SOURCE_JWXT
        set(value) = store.putString(KEY_SCHEDULE_SOURCE, value)

    companion object {
        fun normalizeThemeColor(value: String): String {
            val hex = value.trim().removePrefix("#")
            if (hex.length !in listOf(3, 6) || !hex.all { it in "0123456789abcdefABCDEF" }) return ""
            return "#" + (if (hex.length == 3) hex.map { "$it$it" }.joinToString("") else hex).uppercase()
        }
        /** 非敏感的应用设置（外观、协议、更新……），普通 SharedPreferences。 */
        const val APP_SETTINGS_FILE = "app_settings"

        private const val KEY_NAV_BAR_STYLE = "nav_bar_style"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_DEFAULT_TAB = "default_tab"
        private const val KEY_NETWORK_MODE = "network_mode"
        private const val KEY_HOME_THEME = "home_theme"
        private const val KEY_SHOW_QUICK_ACTIONS = "show_quick_actions"
        private const val KEY_VENUE_AUTO_SOLVE_CAPTCHA = "venue_auto_solve_captcha"
        private const val KEY_SCHEDULE_ATTENDANCE_BADGE = "schedule_attendance_badge"
        private const val KEY_SCHEDULE_SOURCE = "schedule_source"

        const val SCHEDULE_SOURCE_JWXT = "jwxt"

        /** 取值是旧版考勤（bkkq）的写法，已写进用户设置，不能改。 */
        const val SCHEDULE_SOURCE_ATTENDANCE = "bkkq"
        const val NAV_STYLE_FLOATING = "floating"
        const val NAV_STYLE_CLASSIC = "classic"
        const val DARK_MODE_SYSTEM = "system"
        const val DARK_MODE_LIGHT = "light"
        const val DARK_MODE_DARK = "dark"
        const val THEME_CARD = "card"
        const val THEME_ICON = "icon"
        /** 老写法：`:app` 的 `BottomTab` 名字（已写进用户设置，不能改）。只用来读老值，见 [normalizeDefaultTab]。 */
        const val TAB_HOME = "HOME"
        const val TAB_COURSES = "COURSES"
        const val TAB_PIDAI = "PIDAI"
        const val TAB_TOOLS = "TOOLS"
        const val TAB_PROFILE = "PROFILE"

        /** 规范化落点：与任何一端的 tab id 都无关，各端自己映射（见 [defaultTab]）。 */
        const val TAB_KEY_HOME = "home"
        const val TAB_KEY_SCHEDULE = "schedule"
        const val TAB_KEY_AGENT = "agent"
        const val TAB_KEY_SERVICES = "services"
        const val TAB_KEY_PROFILE = "profile"

        /** 规范值全集，顺序 = 设置页「默认启动 Tab」那五格。 */
        val DEFAULT_TAB_KEYS = listOf(
            TAB_KEY_HOME,
            TAB_KEY_SCHEDULE,
            TAB_KEY_AGENT,
            TAB_KEY_SERVICES,
            TAB_KEY_PROFILE,
        )

        /** 没设过 / 认不出来时落到哪：日程（与 `:app` 一直以来的默认一致：课表是最常用的那一屏）。 */
        const val TAB_KEY_DEFAULT = TAB_KEY_SCHEDULE

        /**
         * 任何写法 → 规范值。
         *
         * 认得出：规范值本身（大小写不敏感）、老写法 `HOME/COURSES/PIDAI/TOOLS/PROFILE`；
         * 认不出（手工改过的文件、将来删掉的那一格）：退 [TAB_KEY_DEFAULT]——不抛、也不把脏值留在设置里。
         */
        fun normalizeDefaultTab(raw: String?): String = when (raw?.trim()?.lowercase()) {
            TAB_KEY_HOME -> TAB_KEY_HOME
            TAB_KEY_SCHEDULE, "courses" -> TAB_KEY_SCHEDULE
            TAB_KEY_AGENT, "pidai" -> TAB_KEY_AGENT
            TAB_KEY_SERVICES, "tools" -> TAB_KEY_SERVICES
            TAB_KEY_PROFILE -> TAB_KEY_PROFILE
            else -> TAB_KEY_DEFAULT
        }

        const val NETWORK_AUTO = "auto"
        const val NETWORK_DIRECT = "direct"
        const val NETWORK_VPN = "vpn"
    }
}
