@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.xjtu.toolbox.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.xjtu.toolbox.account.AccountContext
import com.xjtu.toolbox.auth.AccountType
import com.xjtu.toolbox.auth.AuthExpiryHandler
import com.xjtu.toolbox.auth.LocalAuthExpiry
import com.xjtu.toolbox.auth.LoginScreen
import com.xjtu.toolbox.auth.LoginUiState
import com.xjtu.toolbox.auth.MfaCodeDialog
import com.xjtu.toolbox.campus.*
import com.xjtu.toolbox.home.*
import com.xjtu.toolbox.jwapp.JwappScoreScreen
import com.xjtu.toolbox.nav.AppRoute
import com.xjtu.toolbox.schedule.*
import com.xjtu.toolbox.settings.*
import com.xjtu.toolbox.ui.LocalIsWideLayout
import com.xjtu.toolbox.ui.calculateIsWideLayout
import com.xjtu.toolbox.ui.theme.XJTUToolBoxTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The only Swift-facing entry point. Authentication and all feature state stay in Kotlin. */
fun MainViewController(): UIViewController = ComposeUIViewController { IosApp() }

private enum class IosPage(val title: String) {
    HOME("首页"), SCHEDULE("课表"), SCORES("成绩"), ACCOUNT("我的"), SETTINGS("设置"),
}

@Composable
private fun IosApp() {
    val settings = remember { SettingsStore() }
    val session = remember { CampusSession() }
    val state by session.state.collectAsState()
    val accountViewModels = remember(state.accountId) {
        if (AccountContext.activeAccountId != state.accountId) {
            AccountContext.switchEpoch++
            AccountContext.activeAccountId = state.accountId
        }
        object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    }
    val accountCaches = remember(state.accountId) { CampusCacheScope() }
    DisposableEffect(accountViewModels, accountCaches) {
        onDispose { accountCaches.revoke(); accountViewModels.viewModelStore.clear() }
    }
    val scope = rememberCoroutineScope()
    var appearance by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf(IosPage.HOME) }
    var localError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session) { session.restore() }
    DisposableEffect(session) { onDispose { session.close() } }
    val darkMode = remember(appearance) { settings.darkMode }
    val seed = remember(appearance) { settings.themeSeedHex }
    val authExpiry = remember { AuthExpiryHandler { _, _ -> page = IosPage.ACCOUNT } }
    XJTUToolBoxTheme(darkModeOverride = darkMode, customColorHex = seed) {
        val wide = calculateIsWideLayout()
        CompositionLocalProvider(
            LocalIsWideLayout provides wide,
            LocalAuthExpiry provides authExpiry,
            LocalViewModelStoreOwner provides accountViewModels,
        ) {
            val navigate: (AppRoute) -> Unit = { route ->
                page = when (route) {
                    AppRoute.Schedule -> IosPage.SCHEDULE
                    AppRoute.JwappScore -> IosPage.SCORES
                    AppRoute.Settings -> IosPage.SETTINGS
                    AppRoute.Accounts -> IosPage.ACCOUNT
                    else -> IosPage.HOME
                }
            }
            val settingsHost = remember {
                object : SettingsHost {
                    override val versionText = "iOS 0.1.0 (1)"
                    override val honoredRows = setOf(SettingsRow.DARK_MODE, SettingsRow.HAPTICS)
                    override val changelog = emptyList<ChangelogEntry>()
                    override val scheduleSourceOptions = emptyList<ScheduleSourceOption>()
                    override val noticeSection: (@Composable () -> Unit)? = null
                    override val dataPrivacySection: (@Composable () -> Unit)? = null
                    override val updateSection: (@Composable () -> Unit)? = null
                    override val onNetworkModeChanged: (() -> Unit)? = null
                    override fun onAppearanceChanged() { appearance++ }
                    override val onOpenUrl: (String) -> Unit = { url ->
                        if (url.startsWith("https://")) NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it) }
                    }
                }
            }
            val homeHost = remember(state, appearance) {
                object : HomeHost {
                    override val honoredCards = setOf(HomeCard.GREETING, HomeCard.SERVICE_GRID)
                    override val audience = AccountType.UNDERGRADUATE
                    override val greetingName = ""
                    override val dateLabel: String = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Shanghai")).date.toString()
                    override val weekNumber = 0
                    override val statusLine = HomeStatusLine(
                        label = if (state.accountId == null) "尚未登录" else "校园账号 · ${state.accountId}",
                        detail = if (state.route == CampusRoute.WEBVPN) "通过学校 WebVPN 连接" else "直接连接学校服务",
                        tone = if (state.status == CampusStatus.AUTHENTICATED) HomeTone.NORMAL else HomeTone.MUTED,
                    )
                    override val openableRouteIds = setOf(AppRoute.Schedule.id, AppRoute.JwappScore.id)
                    override fun onRoute(route: AppRoute) = navigate(route)
                    override fun onProfile() { page = IosPage.ACCOUNT }
                    override fun onOpenStatus() { page = IosPage.ACCOUNT }
                }
            }
            val accountId = state.accountId
            val scheduleHost = remember(session, accountId, accountCaches) { IosScheduleHost(session, accountId, accountCaches) }
            val content: @Composable () -> Unit = {
                when (page) {
                    IosPage.HOME -> HomeScreen(homeHost)
                    IosPage.SCHEDULE -> if (accountId == null) {
                        SignInPrompt("登录后查看课表", onSignIn = { page = IosPage.ACCOUNT })
                    } else key(accountId) {
                        IosSchedulePage(scheduleHost, navigate)
                    }
                    IosPage.SCORES -> if (accountId == null) {
                        SignInPrompt("登录后查看成绩", onSignIn = { page = IosPage.ACCOUNT })
                    } else key(accountId) {
                        JwappScoreScreen(
                            source = session.scoreSource,
                            studentId = "",
                            cache = session.scoreCache,
                            onBack = { page = IosPage.HOME },
                        )
                    }
                    IosPage.ACCOUNT -> AccountPage(session, state, localError, scope,
                        onSettings = { page = IosPage.SETTINGS },
                        onLogout = {
                            scope.launch {
                                accountCaches.revoke()
                                accountViewModels.viewModelStore.clear()
                                try {
                                    accountCaches.clear()
                                    session.logout()
                                    localError = null
                                } catch (e: CancellationException) { throw e }
                                catch (_: Exception) { localError = "本机数据清理失败，请重试" }
                            }
                        },
                    )
                    IosPage.SETTINGS -> SettingsScreen(settings, settingsHost, onBack = { page = IosPage.ACCOUNT })
                }
            }
            Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (wide) {
                    Row(Modifier.weight(1f)) {
                        Column(Modifier.width(112.dp).fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("岱宗盒子", modifier = Modifier.padding(vertical = 16.dp))
                            NavigationItems(page) { page = it }
                        }
                        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                    Row(Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.surfaceVariant).padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        NavigationItems(page) { page = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationItems(selected: IosPage, onSelect: (IosPage) -> Unit) {
    listOf(IosPage.HOME, IosPage.SCHEDULE, IosPage.SCORES, IosPage.ACCOUNT).forEach { item ->
        TextButton(text = if (selected == item || selected == IosPage.SETTINGS && item == IosPage.ACCOUNT) "● ${item.title}" else item.title, onClick = { onSelect(item) })
    }
}

@Composable
private fun SignInPrompt(title: String, onSignIn: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title)
        Spacer(Modifier.height(12.dp))
        TextButton(text = "校园账号登录", onClick = onSignIn)
    }
}

@Composable
private fun IosSchedulePage(host: ScheduleHost, navigate: (AppRoute) -> Unit) {
    var subtitle by remember { mutableStateOf("") }
    var actions by remember { mutableStateOf<(@Composable RowScope.() -> Unit)?>(null) }
    var bottom by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("课表"); if (subtitle.isNotBlank()) Text(subtitle, style = MiuixTheme.textStyles.footnote1) }
            actions?.invoke(this)
        }
        bottom?.invoke()
        Box(Modifier.weight(1f)) {
            ScheduleScreen(host, onSubtitleChange = { subtitle = it }, onActionsChange = { actions = it }, onBottomContentChange = { bottom = it }, onNavigate = navigate)
        }
    }
}

@Composable
private fun AccountPage(
    session: CampusSession,
    state: CampusSessionState,
    localError: String?,
    scope: CoroutineScope,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var route by remember { mutableStateOf(state.route) }
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val busy = state.status in setOf(CampusStatus.RESTORING, CampusStatus.AUTHENTICATING)
    suspend fun perform(action: suspend () -> Unit) {
        try { error = null; action() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "连接未完成，请检查校园网络或切换 WebVPN 后重试" }
    }
    LaunchedEffect(state.status, state.smsAlreadySent) {
        if (state.status == CampusStatus.SMS_REQUIRED && !state.smsAlreadySent) scope.launch { perform { session.requestSms() } }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("校园账号")
            TextButton(text = "设置", onClick = onSettings)
        }
        localError?.let { Text(it, modifier = Modifier.padding(horizontal = 24.dp), color = MiuixTheme.colorScheme.error) }
        if (state.accountId != null && state.status != CampusStatus.AUTHENTICATED) {
            TextButton(text = "退出并清除本机数据", enabled = !busy, onClick = onLogout)
        }
        if (state.status == CampusStatus.AUTHENTICATED) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("已登录 · ${state.accountId.orEmpty()}")
                Text(if (state.route == CampusRoute.WEBVPN) "连接方式：学校 WebVPN" else "连接方式：校园直连")
                Text("凭据、会话与课程缓存保存在本机受保护存储。退出会清除本账号的本机数据。", style = MiuixTheme.textStyles.body2)
                TextButton(text = "退出并清除本机数据", onClick = onLogout)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(text = if (route == CampusRoute.DIRECT) "● 校园直连" else "校园直连", enabled = !busy, onClick = { route = CampusRoute.DIRECT })
                TextButton(text = if (route == CampusRoute.WEBVPN) "● 学校 WebVPN" else "学校 WebVPN", enabled = !busy, onClick = { route = CampusRoute.WEBVPN })
            }
            if (state.status == CampusStatus.RESTORING) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            Box(Modifier.weight(1f)) {
                LoginScreen(
                    title = "岱宗盒子",
                    subtitle = "使用校园统一身份认证登录",
                    username = username,
                    onUsernameChange = { username = it; error = null },
                    password = password,
                    onPasswordChange = { password = it; error = null },
                    state = when {
                        busy || state.status == CampusStatus.SMS_REQUIRED -> LoginUiState.Submitting
                        error != null -> LoginUiState.Failed(error!!)
                        state.status in setOf(CampusStatus.ERROR, CampusStatus.EXPIRED) -> LoginUiState.Failed(state.message.ifBlank { "登录已过期，请重新登录" })
                        else -> LoginUiState.Idle
                    },
                    onSubmit = {
                        val submittedPassword = password
                        password = ""
                        scope.launch { perform { session.login(username.trim(), submittedPassword, route) } }
                    },
                    footer = "凭据仅存于本机 Keychain。需要短信验证时，请在应用内完成。",
                )
            }
        }
    }
    if (state.status == CampusStatus.SMS_REQUIRED) {
        MfaCodeDialog(
            siteName = "校园统一身份认证",
            phone = state.maskedPhone,
            error = error ?: state.message.takeIf { it.isNotBlank() },
            attempt = attempt,
            onSubmit = { code -> scope.launch { perform { session.verifySms(code); attempt++ } } },
            onCancel = onLogout,
        )
    }
}

private class IosScheduleHost(private val campus: CampusSession, private val account: String?, caches: CampusCacheScope) : ScheduleHost {
    private val scheduleStore = caches.store("schedule:${account.orEmpty()}")
    override val honored = setOf(ScheduleCapability.CUSTOM_COURSE_EDIT)
    override val supportsTextbooks = false
    override val changeStore = caches.store("changes:${account.orEmpty()}")
    override fun cache(accountId: String?): ScheduleCacheStore = KeyValueScheduleCacheStore(scheduleStore)
    override val customCourses: CustomCourseStore = KeyValueCustomCourseStore(caches.store("custom:${account.orEmpty()}"))
    override val login = object : ScheduleLogin {
        override val accountId get() = account.orEmpty()
        override val studentId get() = ""
        override val hasCredentials get() = account != null
    }
    override val session = object : ScheduleSession {
        override val source get() = campus.scheduleSource.takeIf { account != null }
        override val isLoggedIn get() = campus.state.value.status == CampusStatus.AUTHENTICATED
        override suspend fun ensureLogin(userInitiated: Boolean): JwxtScheduleSource = campus.ensureSchedule(userInitiated)
        override suspend fun reAuthenticate(userInitiated: Boolean): JwxtScheduleSource {
            campus.reAuthenticate(userInitiated)
            return campus.ensureSchedule(userInitiated)
        }
        override suspend fun fetchSchedule(source: JwxtScheduleSource, termCode: String, userInitiated: Boolean): ServedSchedule = ServedSchedule(source.schedule(termCode).courses, "jwxt")
    }
    override val holidays = object : HolidaySource {
        override fun peekCached(cache: ScheduleCacheStore): Map<LocalDate, String> = emptyMap()
        override suspend fun refresh(cache: ScheduleCacheStore, force: Boolean): Map<LocalDate, String> = emptyMap()
    }
    override val courseLinks: CourseLinkAccess? = null
}
