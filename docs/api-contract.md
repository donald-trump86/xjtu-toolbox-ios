# serve 模式的 `/api/*` 契约（D5）

> **状态**：草案 v0（2026-10-09）。骨架、通用形状、安全口径、端点清单已经钉下；**每条端点的响应字段由"搬它的那次提交"补齐**
> （谁把某个 `*Api` 搬进 `:data`，谁就顺手把那一行从 `TODO` 改成实测形状）。
> **本文件是 `:server` 的对外接口真源**（D5：文档 + 夹具契约测试都在本仓；campus-api 只作参考，不再是依赖）。

## 1. 这是什么、谁消费

- **接口本体**：`serve` 模式的 `:server`（同一份 `:data` 数据层，无窗口）托管的 `/api/*`。
- **唯一消费者**：`:web`（Kotlin/Wasm，浏览器里）。窗口模式**没有契约**（UI 与数据层同进程 ⇒ 直接函数调用）。
- **为什么必须重新设计而不是照抄 campus-api**（D5 原文）：以**端口/模型的自然形状**为准，逐条与旧形状对照 ——
  旧的合理就沿用（这样 `:web` 对应那些实现可以留着），不合理就改。
- **数据层是同一份**：`:server` 的每个端点都是 `:data` 里某个 `*Api` / `*Source` 的**薄包装**，
  不做二次解析（解析与口径在 `:data`，有测试钉着）。

## 2. 与 campus-api 的三条本质差别（这才是"重新设计"的理由）

| | campus-api（旧） | `/api/*`（新） |
|---|---|---|
| **能力** | **只读**：图书馆/场馆/评教都在自己的 status 里写 `readOnly:true` | **含写路径**（已落地的两种）：图书馆约座 / 换座 / 签到 / 退座（§5.3.1）与场馆下单 / 取消（§5.3.2）。⚠️ **付款码与加餐券不在其中**：`:data` 侧两份实现都有（`data/src/commonMain/kotlin/com/xjtu/toolbox/pay/PaymentCodeApi.kt`、`data/src/commonMain/kotlin/com/xjtu/toolbox/coupon/CouponApi.kt`），Android 与桌面的窗口模式已在用 ⇒ 2026-10-13 起加餐券的**只读**两发（`/api/coupon/{list,detail}`）已落地（§5.5），**领券**（`activateCoupon`，真改上游状态）与付款码仍**没有对应端点**（§5 表里付款码那一行仍是 P2 未实现） |
| **谁能做** | 一刀切只读 | **能力开关随部署与账号变**：`canBook` / `canCancel` / `canSubmit` / `hasSeatPlan` / `availableSources` 必须**如实**出现在响应里，屏按它决定画不画按钮（"点了会失败的按钮一个都不画"） |
| **会话** | 单账号、回环、零鉴权（凭据托管在 campus-api 进程里） | **浏览器不带任何凭据**：登录/登出/短信二验都经 `/api/session*`，会话在 `:server` 进程里（默认只监听 `127.0.0.1` + 访问令牌）。令牌先换成 cookie `serve_token`（只有它能被浏览器带着走），之后的请求就只靠它 |

> 推论（写进 `:web` 的改造）：浏览器端原来那 13 个「只读投影」实现在 serve 模式下**要退休**——
> 它们把能力开关硬编成 `false`（`canBook=false` 那种）。新契约里开关是**响应的一部分**，
> 于是同一份 `:web` 屏在 serve 模式下能看到真实能力（能约座就是能约座）。

## 3. 默认安全口径（D3 定稿，实现时逐条守住）

1. 默认**只监听 `127.0.0.1`**；
2. 首次启动生成并**打印访问令牌**；浏览器侧必须带（`Authorization: Bearer <token>`，或令牌换 cookie 后随请求带）。
    cookie 名固定 **`serve_token`**，由 `GET /api/session` 发出（`Path=/; HttpOnly; SameSite=Lax`，见 §5.1）；
    浏览器在地址栏里贴不了 `Authorization`，所以实际形态就是「先用 Bearer 换 cookie，之后只带 cookie」；
    不带 `Secure` 是因为默认是明文 HTTP（浏览器会直接丢掉带 `Secure` 的 cookie）。
3. 对外暴露必须**显式** `--host 0.0.0.0`，文档写明"自己加反代 + 鉴权，风险自负"；
4. `/api/status` 是**唯一免令牌**端点（只报"活着 + 有没有会话"），且**不含身份信息**（不返回学号/姓名）。
5. **`--host 0.0.0.0` + HTTP ⇒ 非安全上下文**（2026-10-10 实测，Web 外壳那边撞到的）：
   `navigator.clipboard` 在非安全上下文里**根本不存在** ⇒ 页面里任何「复制」按钮会**静默失败**
   （点下去没反应，也不报错）。默认的 `127.0.0.1` 不受影响（localhost 属安全上下文）。
   ⇒ 对外暴露时要么自己上 HTTPS（反代 + 证书），要么给复制路径留一条 `document.execCommand('copy')`
   的降级；**不要**默认就走降级 —— 那是把「不安全」常态化。同类受影响的还有 `crypto.subtle`、
   地理位置、Service Worker（`serve` 模式若打算做 PWA，先把这一条想清楚）。

## 4. 通用形状

- **信封**：`{ "code": 0, "data": <T|null>, "message": "<错误文案>" }`（`code != 0` 时 `message` 必须是能直接给用户看的中文短句，口径与 `:core` 的 `FriendlyError` 一致）。
- **`code` 就是 HTTP 状态码**（2026-10-11 落地 `/api/session*` 时定下，`:server` 的 `ApiErrors` 是这一条的实现）：
  成功 `code == 0` 且 HTTP 200；失败时 `code` 与 HTTP 状态码**逐位相同** —— 一条信息不出现两种写法，
  客户端读哪一个都一样。已用的码：`400` 请求体形状不对 · `401` 无令牌 / 登录被拒 · `404` 端点不存在 ·
  `409` 另一次登录正在进行 / 上游明确拒绝一次写动作（§5.3.1） · `502` 上游或网络的故障（稍后重试可能就成）。
- **例外**：`/api/status` 是**裸对象**（历史遗留）。要么在实现时统一成信封，要么在本文档里明确标注为例外 —— **只能选一个**。
- ⚠️ **时间一律 ISO-8601 带时区的字符串**（`2026-10-09T23:52:00+08:00`）。
  **不要用 epoch 秒/毫秒**：这是"静默漂移"的头号来源（秒 vs 毫秒、本地时区 vs UTC 在两端的表现不同，
  而两端的 UI 都会"看起来对"）。
- **分页**：`?page=<1-based>&size=<n>`，响应带 `{ "page": n, "size": n, "total": n }`；
  **过滤与排序由服务端定，并在本文档写死**（客户端的顺序假设必须能被夹具测试钉住）。
- **空值与缺失**：`null` = 上游明确说"没有"；字段缺失 = 本端不投影。两者**不能混用**（`MyBookingInfo` 的 `total:null ≠ 0` 就是这条的实例）。
- **能力开关**：每条涉及写操作的端点/对象里带上开关字段（见 §2），客户端**不得**靠猜。

## 5. 端点清单

优先级：**P0 = Stage A 只读屏**（验收要求 23~27 条路由可用）· **P1 = 写路径**（Stage B）· **P2 = 宿主能力**（Stage C）。

| 路径 | 对应端口（`:core`） | 屏 | 优先级 | 沿用旧形状？ |
|---|---|---|---|---|
| `/api/status` | ——（探活，裸对象） | （外壳） | P0 | 沿用（但见 §3.4：不含身份） |
| `/api/session` `/api/session/login` `/api/session/logout` `/api/session/mfa` | 新（`:data` 的 `SessionManager`） | 登录屏 / MFA 弹窗 | **P0（新）** | 无旧形状可沿用（逐条形状见 §5.1） |
| `/api/calendar/school` | `SchoolCalendarSource` | 校历 | P0 | 沿用（`UpstreamSchoolCalendar` 已有解析） |
| `/api/jwxt/terms` `/api/jwxt/term` `/api/jwxt/term-start` `/api/jwxt/schedule` | `ScheduleSource` 一族（`:data` 侧：`AppSchoolCourseSource` + `JwxtScheduleApi`） | 课表 | P0 | 沿用 |
| `/api/jwxt/grades` | `ScoreReportSource` | 成绩报表 | P0 | 沿用 |
| `/api/jwxt/school-courses` | `SchoolCourseSource` | 全校课表 | P0 | **要改**：campus-api 的投影少一批字段（人数/学时、`YPSJDD`、开课单位、公选筛选）⇒ 屏把筛不了的控件隐藏了。服务端自己直连教务后**能补齐**，补齐后 `:web` 可以把那两档控件放回来 |
| `/api/jwxt/evaluations` `/api/jwxt/evaluations/status` | `JudgeSource` | 评教 | P0 | **要改**：`canSubmit` 在新契约里取决于实现（只看不提交仍是默认），但**字段必须出现在响应里** |
| `/api/fitness/years` `/api/fitness/score` | `FitnessSource` | 体测 | P0 | 沿用（姓名/学号旧版按隐私口径不投影；新契约里**是本人的数据**，应带上） |
| `/api/info/yellowpage` | `YellowPageSource` | 黄页 | P0 | 沿用 |
| `/api/info/faculty` | `FacultySource` | 教师检索 | P0 | **要改**：旧版刻意不传 `?contacts=1`、不解析主页 ⇒ 新契约按能力开关如实报（`contactsAvailable`） |
| `/api/notification/list` `/api/notification/sources` | `NoticeSource` | 通知 | P0 | 沿用（29 个源） |
| `/api/inbox` | `InboxSource` | 消息收纳 | P0 | 沿用（四路聚合） |
| `/api/emptyroom/cdn` `/api/emptyroom/rooms` | `EmptyRoomSource` | 空闲教室 | P0 | **要改**：旧版只有 CDN 一档（`availableSources` 只报它）。直连教务后"实时状态/直查教务"两档能回来 ⇒ 开关如实报 |
| `/api/library/campus` `/api/library/areas` `/api/library/seats` `/api/library/my` `/api/library/action` | `LibrarySource` | 图书馆座位 | P0 | **部分沿用 + 补**：`/campus` `/areas` `/seats` `/my` 形状可沿用（逐字段映射见 `CampusLibraryApi` 的 KDoc 表）；**写端点 `/api/library/action` 已落地**（§5.3.1），`canBook=true` 时屏上那些按钮才画 |
| `/api/card/balance` `/api/card/transactions` | `CampusCardSource` | 校园卡 | P0 | 沿用（`amount ← signed`、`description ← channel`、`cardType ← cardName`；⚠️ 挂失口径 `lostflag` vs `barflag` 要在本文档里明确选一个） |
| `/api/venue/products` `/api/venue/slots` `/api/venue/orders` `/api/venue/status` | `VenueSource` | 体育场馆 | P0 | **部分沿用 + 补**：`slots` 的 `surplus` **不要用旧字段**（旧口径在 `all == used` 时给 0，而两端一致的读法是 `status == 1` ⇒ 可订） |
| `/api/venue/captcha` `/api/venue/book` `/api/venue/cancel` | `VenueSource` | 体育场馆（写） | **P1（写路径）· 已落地 2026-10-12** | 无旧形状：serve 侧新开的两条写路径（契约 §5.3.2 逐字段；落地提交 `45a0e50`） |
| `/api/dzpz/transcript` | `TranscriptSource`（`:data` 侧 `AppTranscriptSource` + `TranscriptApi`） | 电子成绩单 | **P0 · 已落地 2026-10-13** | **新形状**：只端七步里的**第一步**（建单表单）—— 出 PDF 那半条链没有端点，逐字段见 §5.5 |
| `/api/attendance/terms` `/api/attendance/records` | `AttendanceSource`（`AppAttendanceSource` + `AttendanceApi`） | 考勤 | **P0 · 已落地 2026-10-13** | **要改**：`startSection ← startTime`、`endSection ← endTime`、`term ← termString` 三处改名（`:core` 那两个 `Time` 字段其实是节次），`teacher` 按红线不端，见 §5.5 |
| `/api/coupon/list` `/api/coupon/detail` | `CouponSource`（`AppCouponSource` + `CouponApi`） | 加餐券（**只读**） | **P0 · 已落地 2026-10-13** | **新形状**（原来这一行是 P2 = Stage C 宿主能力；本轮只端**只读**半，领券不端）。金额单位是**分**，见 §5.5 |
| `/api/dorm/rooms` `/api/dorm/reading` | `DormPowerSource`（`:data` 的 `DormPowerApi` 本身就是端口实现） | 宿舍电费 | **P0 · 已落地 2026-10-13** | **要改**：旧的 `GET /api/dorm/kwh?roomId=` 一个端点把列表与读数混在一处 ⇒ 拆成两个；`reading` 不带时间戳，见 §5.5 |
| `/api/payment-code` | 新（`:data` 侧 `PaymentCodeApi`） | 付款码 | P2 | 无旧形状（⚠️ **仍未实现**：`:server` 里没有这个端点） |
| `/api/v1/personal/me/user` | —— | （身份） | P2 | ⚠️ 待确认：这条出现在 `:core` 现有代码里，实现时先查清它是谁的投影 |

> **状态收口（2026-10-13 核账，纯文档轮次）**：`:server` 里**已实现**的端点族 =
> `status` · `session{,/login,/logout,/mfa}` · `calendar/school` · `info/{yellowpage,faculty}` ·
> `fitness/{years,score}` · `notification/{sources,list}` · `inbox` · `emptyroom/{cdn,rooms}` ·
> `jwxt/{terms,term,term-start,schedule,grades,school-courses,evaluations,evaluations/status}` ·
> `library/{campus,areas,seats,my,action}` · `venue/{products,slots,orders,status,captcha,book,cancel}` ·
> `card/{balance,transactions}` ·
> **只读四域（2026-10-13 收尾轮新增）**：`dzpz/transcript` ·
> `attendance/{terms,records}` · `coupon/{list,detail}` · `dorm/{rooms,reading}` ——
> 与本文档 §5.1–§5.3.2 与 §5.5 逐条对得上（核法：`:server` 源码里那些
> `*_SEGMENT` 常量与 `get()`/`post()` 注册点逐个列过一遍）。
> **仍未实现**的只剩两行：`/api/payment-code`（P2：`:data` 侧有 `PaymentCodeApi`，`:server` 里没有端点）、
> `/api/v1/personal/me/user`（P2：来源仍待确认）。
> （加餐券那一行已于 2026-10-13 从「未实现」里出来 —— 只读两发落地，见 §5.5；**领券**仍未端。）
> ⚠️ **表里那个 `P0`/`P1`/`P2` 是优先级，不是状态** —— `P1` 那两行（写路径）都已落地（见各自那一格）。
### 5.1 `/api/session*`（P0「新」那一行的实测形状，2026-10-11 落地）

会话本体在 `:server` 进程里（`ServeSession`：`:data` 的 `SessionManager` + 落盘凭据），
浏览器**不带任何凭据**地登进来。四个端点全部挂在令牌闸门**里面**（`/api/status` 的免令牌是唯一例外，见 §3.4）。

| 端点 | 请求 | 响应 `data` |
|---|---|---|
| `GET /api/session` | —— | `{ "authenticated": true\|false }`，并带 `Set-Cookie`（见下） |
| `POST /api/session/login` | `{"username":"…","password":"…"}` | `{ "authenticated": true }` |
| `POST /api/session/logout` | ——（不读 body，`{}` 也行） | `{ "authenticated": false }` |
| `GET /api/session/mfa` | ——（轮询） | `{ "pending": …, "siteName": …, "rejections": …, "attemptsLeft": … }` |
| `POST /api/session/mfa` | `{"code":"…"}` 或 `{"cancel":true}` | 同上（**动作之后那一刻**的快照） |

四条口径（实现见 `server/src/main/kotlin/com/xjtu/toolbox/server/SessionRoutes.kt` / `ServeSession.kt`）：

1. **令牌换 cookie 只在 `GET /api/session` 发**：响应带
   `Set-Cookie: serve_token=<令牌>; Path=/; HttpOnly; SameSite=Lax`。浏览器先用
   `Authorization: Bearer` 打这一枪，之后**只带 cookie**（闸门两种形态都认，名字固定 `serve_token`）。
   `HttpOnly` 是重点：页面脚本读不到令牌。其余三个端点**不发**这行 —— 客户端启动时必然要问一次
   「有没有会话」，cookie 顺手就拿到了。
2. **`authenticated` 的含义是「登录跑完了」**（或冷启动从落盘凭据静默恢复过），**不是**
   「内核里有凭据」：登录挂在短信二验上时它仍是 `false`（那一发随时可能失败），`/api/status` 也照此报。
   恢复**不联网验证**（第一次真取数时站点会话自己探活）—— 打开页面这个最该快的动作不该等一次网络。
3. **MFA 是轮询**（不是 SSE）：`GET` 报挂起的询问，`POST` 交验证码或取消。
   - 没有挂起时 `{"pending":false}`（其余三个字段是 `null`，不是字段缺失）；有挂起时 `pending:true` +
     `siteName`（站点显示名，如「图书馆」）+ `rejections`（服务端拒绝了几次）+ `attemptsLeft`
     （= `3 - rejections`；3 是 `:data` 的 `SessionManager.MFA_MAX_ATTEMPTS`）。
   - `POST` 是**动作**不是结果：验证码交给内核那条登录流程去校验，结论从下一次轮询（`rejections` 涨没涨）
     或那一发登录请求的响应读；取消之后那一发登录以 4xx 收尾（这就是 `cancel` 的语义）。
   - 拿手机号是一个**副作用**：轮询到 `pending:true` 那一刻 `:server` 会调 `MFAContext.getPhoneNumber()`
     —— 不只是为了显示，`verifyCode` 要拿它设下的 `gid` 才能校验验证码（两个窗口端在弹窗里做的是同一件事）。
4. **`POST /api/session/login` 同时只允许一个流程**：第二发并发请求拿 `409`（中文短句），
   不排队等一个可能 150 秒的 MFA（并存的两个登录会互相 `reconfigureForAccount`，把命名空间搅成半新半旧）。
   失败码：`401` = 凭据/验证被拒（重发同样的请求没有意义）· `502` = 上游或网络的故障（稍后重试可能就成）·
   `400` = 请求体形状不对。登录成功后凭据落盘（与桌面端同一份存储，`0600`），登出时凭据 / cookie /
   站点快照**一起删**。

> **未投影的东西（TODO）**：① MFA 的绑定手机号（弹窗里那句「验证码已发送到 138\*\*\*\*0000」要它；
> 多一个字段 = 多一处口径，留到 `:web` 那个弹窗真开始画时一起加 —— 2026-10-11 弹窗**已经画了**，
> 在 `WebServeSessionScreen` 里，这句如实没写，正是差这个字段）；② `attemptsLeft` 依赖
> `SessionManager.MFA_MAX_ATTEMPTS`（那边是 `private`，`:server` 侧是同一数字的第二份写法，
> 改内核那个值要连它一起改）；③ `/api/session*` 的响应**只有**上面那些字段 —— 学号 / 姓名 /
> 手机号 / 账号类型一律**不投影**（与 `/api/status` 那条红线同一口径）；
> ④ **轮询间隔**：契约只写「轮询」不写间隔，消费方现在取 **2 秒**（`WebServeSessionScreen` 的
> `MFA_POLL_INTERVAL_MS` 与它的 TODO）—— 要写死就两处一起写死，并考虑退避。

> **TODO（每条一行，由搬它的提交补齐）**：请求参数的确切名字与默认值 · 响应字段的逐条形状 ·
> 与 `:app` 同一条上游样本的逐字段一致性证据（夹具）· 该端点的能力开关取值表。
> **已补齐的那一行**：`/api/session*` —— 逐条请求/响应形状、错误码、Set-Cookie 归属与两条 TODO
> 都写在 §5.1（由 `2026-10-11` 那次「serve 第二步」的提交补齐）。

### 5.2 P0 取数端点（2026-10-11 落地，`:server` 实现，测试 35 例钉住）

端点全部挂在令牌闸门内（`/api/status` 是唯一免令牌端点）。统一口径：信封 `{code,data,message}`；
`code == HTTP 状态码`（成功 `0` + 200）；时间一律 ISO-8601 **服务端系统时区**（跨界部署时区差异由部署方负责）；
错误码：`400` 参数形状 · `401` 无令牌/错了，或令牌对但**没登录**（「请先登录：POST /api/session/login」）·
`404` 未实现 · `502` 上游/网络故障（`FriendlyError` 中文短句，含会话过期）。
能力开关一律**如实**：`canBook` / `canCancel` / `canSubmit` / `availableSources` / `contactsAvailable` 出现在各自响应里。

**红线（三处刻意例外，均为公开数据或登录者本人数据，测试用编造夹具值钉住）**：
「响应不含学号/姓名/手机号」对登录者本人成立；例外的三处是 ① `fitness/score` 的姓名/学号（本人数据），
② `yellowpage`/`faculty` 的教师名与部门电话（公开通讯录），③ `emptyroom/rooms` 实时档的上课教师名。

| 端点 | 请求 | 响应 `data`（逐字段） | 备注 |
|---|---|---|---|
| `GET /api/calendar/school` | —— | `{terms:[{id,startDate,endDate,termName,yearName,totalWeeks,workDays,events:[{id,startDate,endDate,name,remark,days,colorHex}]}]}` | 开学日起序；`:web` 消费方 2026-10-11 落地（§5.4） |
| `GET /api/info/yellowpage` | —— | `{updateTime,categories:[{id,name}],departments:[{id,categoryId,name,phone}]}` | 已滤 `status!=1`、不投影 `sort` |
| `GET /api/info/faculty` | `?name=&dept=&page=&size=` | `{contactsAvailable:false,total,totalPage,pageIndex,members:[…]}` | 不解析主页、不传 `contacts=1`（serve 只读）；`contactsAvailable:false` 固定 |
| `GET /api/fitness/years` | —— | `{years:[{yearNum,name,checked}]}` | `FitnessSource` |
| `GET /api/fitness/score` | `?year=` | `{studentNumber,studentName,totalScore,totalGrade,reportType,reportStatus,sex,grade,items:[…]}` | **本人数据**（例外①）：姓名/学号可投影，消费方 2026-10-11 带上 |
| `GET /api/notification/list` | `?page=&size=&source=&all=1` | `{page,size,total,items:[…],skipped:[…],hasMore}` | `size`=本次响应条数（`NoticeSource` 无页大小概念，非服务端分页窗） | 
| `GET /api/notification/sources` | —— | `{total,sources:[{code,displayName,category}]}` | 29 个源 |
| `GET /api/inbox` | —— | `{messages,todos:{分类:[…]},finished,readAt,seenTodos,ignored,off,schoolFetchedAt,bubbled}` | `:data` 的 `InboxData` 投影（读侧；`markRead` 等写路径属 Stage B） |
| `GET /api/emptyroom/cdn` | `?campus=&date=&from=&to=` | `{availableSources:[{key,name}],source,campus,buildings,date,noData,note,rooms:[…]}` | CDN 档（`noData:true` + `note` = 那天没数据，不是错误） |
| `GET /api/emptyroom/rooms` | `?campus=&date=&source=direct\|live` | 直查：同 CDN 档；实时：`{availableSources,…,fetchedAt,buildings,rooms:[{name,building,status,people,seats,course?,teacher?}]}` | `availableSources`：未登录 `[cdn]`；登录后 `[live,cdn,direct]` |

**TODO（拍板过，未做）**：`emptyroom/rooms` 的 `from/to`（节次过滤）——`:data` `EmptyRoomDirectQuery` 只给全天，留待上游加维度；
`faculty` 的 `?filters=1`（筛选项表四张，`:data` 能解析但契约未定）与 `fitness/score` 的 `?year=all`（全学期聚合）同理；
时间戳一律服务端时区。

### 5.3 P0 需登录六域（2026-10-11 落地）

| 端点 | 请求 | 响应 `data`（逐字段，节选） | 备注 |
|---|---|---|---|
| `GET /api/jwxt/terms` | —— | `[{name,startDate,endDate,current?}]` | 学期名兜底 = `DM` 字段 |
| `GET /api/jwxt/term` | —— | 当前学期对象（形状同上） | `ScheduleSource` 一族 |
| `GET /api/jwxt/term-start` | `?term=`（省略 = 当前学期） | `{term,startDate,totalWeeks}` | 2026-10-11 落地：`startDate` = 上游 `XQKSRQ` 的**日期那一段**（第 1 周周一）、`totalWeeks` = `ZZC`（只在 `1..30` 里算数，否则 `0`）。`:data` 侧 `JwxtScheduleApi.termStart()`。**没有**「今天是第几周」这种服务端算出来的字段（那是消费方拿 `startDate` 自己算的）；`?term=` 形状不对 ⇒ `400` |
| `GET /api/jwxt/schedule` | `?term=`（省略 = 当前学期） | `{term,count,rows:[…]}`；行的键名 = 上游列名，只投影 `KCM`/`SKJS`/`JASMC`/`SKXQ`/`KSJC`/`JSJC`/`ZCMC`/`JXBID` 八列，值一律是**字符串**（上游给数字的三格也按文本给）、缺键是**空串** | 2026-10-11 落地：`:data` 侧 `JwxtScheduleApi.rows()`（`wdkb` 的 `xskcb.do`）。周次看 `ZCMC` 文本、**不看** `SKZC` 位串；`?term=` 形状不对 ⇒ `400`。⚠️ **调停补课没有合进这些行**，而且这已经**是有意为之、不是缺口**（2026-10-13 核）：`:data` 的 `JwxtScheduleApi` 现在两条都在 —— `rows()` = 排课原样（本端点端出去的就是它，契约面逐字冻结），`schedule()` = **已合并**调停补课（合并那一层由 `7a5590da` 落地，Android 与桌面端吃的就是它）；两条路「别混用」写在 `rows` 的 KDoc 里。要不要给本端点再加一档（把合并后的那一份也端出去）是**下一个决定**（TODO：待用户拍板） |
| `GET /api/jwxt/grades` | `?term=&all=1` | `{page,size,total,grades:[…]}` | 默认全量；`:data` `ScoreReportSource` |
| `GET /api/jwxt/school-courses` | `?course=&code=&teacher=&campus=&weekday=&from=&to=&page=&size=` | 见下节 | **要改**（契约§5）：补人数/学时、`YPSJDD`、开课单位、公选筛选（`:data` `AppSchoolCourseSource` 已能取的投影，取不到标 TODO）。消费方 2026-10-11 把 `supportsDepartmentFilter` / `supportsElectiveFilter` 读进去了（§5.4 的两个「不画」） |
| `GET /api/jwxt/evaluations` | `?terms=&type=&finished=` | `{canSubmit:false,items:[…]}` | `canSubmit` 必须出现、如实 `false`（本实现只看不认不投） |
| `GET /api/jwxt/evaluations/status` | —— | `{canSubmit:false}` | 提交/撤销（P1）、研究生 gste/gmis 路（P1） |
| `GET /api/library/campus` | —— | `{current:{code,name}|null, campuses:[{code,name}], queryableFloors:[…]|null, canBook:true, hasSeatPlan:true}` | `current:null`=认不出不猜；`canBook:true` 如实（:data 写路径真） |
| `GET /api/library/areas` | `?campus=&floor=` | `{floor,areaCount,areas:[{code,name,floor,available,total}],totals:{…}}` | `available/total:null`=scount 无此区域（不拿 0 冒充「满了」） |
| `GET /api/library/seats` | `?area=&time=` | `{area,total,available,seats:[{seatId,available}]}` | 排序与 `:app` 同；**`time` 收了但上游无时间维**（TODO） |
| `GET /api/library/my` | —— | `{my:{seatId,area,statusText,actionUrls}|null}` | `my:null`=页面明确没预约（§4 null≠空对象）；`actionUrls` = 页面上那几个动作 **→ 上游地址**（label→url，见 §5.3.1 —— 客户端只当不透明句柄） |
| `POST /api/library/action` | body `{action,…}`（动作表见 §5.3.1） | `{ok:true,message}` | 约座/换座/签到/中途离开/中途返回/退座/切校区···一个端点一条动作轴；失败码（400/401/409/502）与例子见 §5.3.1 |
| `GET /api/card/balance` | —— | `{balance,pendingAmount,lostFlag,frozenFlag,expireDate,cardType,department}` | 红线：`account/name/studentNo` **不投影**；挂失口径选 **`barflag`**（`:data`/`:app` 读法，不选 campus-api 的 `lostflag`） |
| `GET /api/card/transactions` | `?from=&to=`（必填 YYYY-MM-DD，反序 400）`&page=&size=` | `{page,size,total,transactions:[{time,merchant,amount,balance,type,description}]}` | `amount` 带符号（负=支出）、`description`←resume |
| `GET /api/venue/products` | —— | `{venues:[{id,name,address?,iconType?,advanceDay,advanceNum}]}` | 拉页到不足为止 |
| `GET /api/venue/slots` | `?serviceId=&date=`（必填 400） | `{serviceId,date,slots:[{areaDetailId,areaName,stockId,timeSlot,price,date,allCount,usingNum,surplus,serviceid,isAvailable}]}` | **`surplus` 读法 = `(all-used).coerceAtLeast(1)`、`all==used` 给 1 不给 0**（旧字段废弃） |
| `GET /api/venue/orders` | `?page=&size=` | `{page,size,total|null,orders:[{orderId,status,statusText,createdAt,price,venueName,canPay,canCancel,details:[…]}],hasMore}` | |
| `GET /api/venue/status` | —— | `{canBook:true, canCancel:true, browserLoginUrl}` | `canBook:true` 如实（写端点 `/captcha` `/book` 已落地，滑块由浏览器端宿主画，见 §5.3.2）；`canCancel:true` 不需要滑块 |
| `GET /api/venue/captcha` | `?serviceId=`（必填 400） | `{captchaId,backgroundImage,sliderImage,bgWidth,bgHeight,sliderWidth,sliderHeight}` | 写路径的第一步：把上游那张滑块图与它发的 `captchaId` **按值**端出来（§5.3.2 第 1 条） |
| `POST /api/venue/book` | body `{serviceId,selections:[{stockId,areaDetailId}],captchaId,sliderTrack}` | `{ok:true,orderId|null,price,message}` | 下单：轨迹（**字符串**原文）带回来交给 `:data`；失败码（400/401/409/502）见 §5.3.2 |
| `POST /api/venue/cancel` | body `{orderId}` | `{ok:true,message}` | 取消：一次普通 POST，不需要滑块 |

**TODO（拍板过，未做）**：`library/seats?time`（上游无时间维）、
`card` 挂失（P1 写）、`evaluations` 提交/撤销（P1）。
（`venue/book` `/cancel` 与 `canBook:true` 已于 2026-10-12 落地，见 §5.3.2。）
⚠️ 上面那三件事**仍未实现**（2026-10-13 核：`:server` 的 `CardRoutes` 只有 `balance`/`transactions`，`JwxtRoutes` 的评教只有只读那两发 —— 没有挂失与提交端点）。

### 5.3.1 library 写路径（2026-10-11 落地）：`POST /api/library/action`

一个端点、一条动作轴（`data.action`），不是七个路径：约座 / 换座 / 签到 / 中途离开 / 中途返回 / 退座 / 切校区
共用同一条会话、同一套失败码、同一份信封，拆开只会多七处要同步的形状。

**`actionUrls` 现在投影了**：`GET /api/library/my` 的 `my.actionUrls` = `` {label: 上游地址} ``
（例如 `{"取消预约":"http://rg.lib.xjtu.edu.cn:8086/my/?cancel=1&ri=88"}`）—— 客户端只把它当
**不透明句柄**（认得出自己刚拿到的那一条）；「要执行哪个动作」说 `action` 码，**地址由服务端自己从当前
预约页上取**，本契约不允许客户端指定「拿我的会话去打哪个地址」。

| 动作 | 中文 | 请求里的字段 | `:data` 的调用 |
|---|---|---|---|
| `seat` | 约座 | `seatId` + `area`（区域码）必填；`autoSwap` 可选（默认 `true` = 已有预约时自动改走换座） | `bookSeat(seatId, area, autoSwap)` |
| `change` | 换座 | `seatId` + `area` 必填 | `swapSeat(seatId, area)` |
| `signin` | 入馆签到 | ——（地址服务端从当前预约页取） | `executeAction(「入馆签到」的地址)` |
| `leave` | 中途离开 | 同 `signin` | `executeAction(「中途离开」的地址)` |
| `back` | 中途返回 | 同 `signin` | `executeAction(「中途返回」的地址)` |
| `cancel` | 退座（取消预约） | 同 `signin` | `executeAction(「取消预约」的地址)` |
| `switch_campus` | 切换账号校区 | `campus` 必填（`east`/`west`/`inno`） | `switchCampus(LibraryCampus.byId(campus))` |

请求例子（约座）：

```json
POST /api/library/action
{"action":"seat","seatId":"A01","area":"north2east","autoSwap":true}
```

签到/中途离开/中途返回/退座只发动作码（`{"action":"signin"}`）—— 地址不在请求里（见上）。

响应 `data`（成功）：`{ "ok": true, "message": "✓ 座位 A01 预约成功！" }` —— `ok` 恒为 `true`（失败走信封，
见下），`message` 是 `:data` 的 `BookResult.message` **原样转发**（已是中文短句）。失败时 `data` 为 `null`、
`code` 与 HTTP 码逐位相同（§4）。

错误码（§4 那一套的延伸）：

- **400** 未知的 `action` / 缺了这个动作要的字段 / `campus` 不认 —— 文案把合法取值列出来；
- **401** 未登录（与会话闸门同一个码，`message` 指路先登录）；
- **409** 上游**明确说这次动作不成**（座位已被他人预约、已有别的预约、当前预约上没有这个操作、不在开放
  时间…）—— 与「另一次登录在进行」同一个码、同一种意思（「现在不能」），区别只在谁在说不行；
  重发同样的请求没有意义，所以**不是** 502；会话失效的 `message`（「登录状态已失效…」）也在这档上，但
  会先被翻成 401（同一句话的语义就是要你去重新登录）；
- **502** 上游/网络的故障（稍后重试可能就成），或「我的预约」页认不出来、取不到按钮地址。

⚠️ 切校区是**改账号资料**的写（`rplace`）：浏览器端离开本页时会把校区**真切回去**（`restoreCampus` 走
同一个端点，`:core` 的 `CampusLibraryApi` 带了页面之外的作用域）—— 与 `:app` / 桌面端同一条约定。

### 5.3.2 venue 写路径（2026-10-12 落地）：`POST /api/venue/book` / `POST /api/venue/cancel`

场馆的下单比图书馆多一样东西：**一张滑块图**。这一节把「挑战怎么暴露」「两个端点逐字段」
「失败码」写死。

#### 1. 滑块挑战的暴露方式：**服务端按值端出，浏览器端自己画**（本轮拍板）

这两条路里选的是**后者**：

| 方案 | 为什么不选 |
|---|---|
| ① 服务端自己解（`:server` 用 `:data` 的 `VenueCaptchaSolver` 在进程内识别，浏览器不用画滑块） | 它能把「下单」做成一个原子调用，但**把滑块这一步变成了黑盒**：`:app` / 桌面都是宿主画的，serve 选它就意味着这条路在三种端上是三套不同的东西；而且它把一个「人机验证」变成服务端自动过，与各端已经存在的手动那条路也不一致 |
| ② **按值端出去**（`GET /api/venue/captcha` + `POST /api/venue/book` 带轨迹） | —— **选它**：与 Android/桌面**同一条协议**（轨迹的 JSON 原文一字不改地往上传），且浏览器里真的能画、能拖、能提交（`:web` 的 `WebSlideCaptchaHost`） |

为什么不能「浏览器直接用 `:data` 的求解器」：**`:data` 只有 jvm/android 两个目标**，
`SliderCaptchaView` 与 `VenueCaptchaSolver` 都依赖 okhttp/jsoup 那一档（wasm 上引用不到）⇒
浏览器里能用的只有 `:core` 的图片缝（skiko 解码）+ 自己写的拖动采集。

**生命周期**：本层**不发行自己的挑战号、也不常驻状态** —— `captchaId` 就是上游发的那个
（`CaptchaData.id` 原名改名，与 `orderId` 一样是**要原样带回来的句柄**），「用掉了没 / 过期没」
由上游判。所以没有需要清理的内存表。TODO（真站未验）：若实测发现上游还要求「服务端必须记住
图与 id 的对应关系」之类的事，再在这一层加一张一次性挑战表（内存 + TTL，写在文件头）。

#### 2. `GET /api/venue/captcha`

```json
GET /api/venue/captcha?serviceId=101
{"code":0,"data":{"captchaId":"cap-1",
  "backgroundImage":"data:image/jpeg;base64,…","sliderImage":"data:image/png;base64,…",
  "bgWidth":260,"bgHeight":160,"sliderWidth":50,"sliderHeight":50}}
```

七格就是全部（`captchaId ← CaptchaData.id`，其余原样转发）：两张图都是 data URI 原文，
服务端不重编码、不验尺寸。`?serviceId=` 缺或不是数字 ⇒ **400**。

#### 3. `POST /api/venue/book`

```json
POST /api/venue/book
{"serviceId":101,
 "selections":[{"stockId":9101,"areaDetailId":5101}],
 "captchaId":"cap-1",
 "sliderTrack":"{\"bgImageWidth\":260,…}"}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `serviceId` | ✓ | 场馆号（`/api/venue/products` 的 `id`），正整数 |
| `selections[]` | ✓（至少一行） | 选中的场地明细：**只要两个数**（`stockId` + `areaDetailId`，来自 `/api/venue/slots`）。上游 `stockdetail` 那个信封（`:data` 的 `VenueApi.buildBookingParam`）只用到它们 |
| `captchaId` | ✓ | `/api/venue/captcha` 给的那个，原样带回 |
| `sliderTrack` | ✓ | 滑块轨迹的 JSON **原文**（`:core` 的 `SliderResult.toJson()`，与 Android/桌面同一协议：260 坐标系 + down/move/up + 两个 ISO 时刻）。**当字符串传**，服务端不解析它 |

响应 `data`（成功）：`{ "ok": true, "orderId": "VT20261013001", "price": 20.0, "message": "预订成功" }`
—— `ok` 恒为 `true`（失败走信封），`orderId` 上游没给就是 `null`（§4 不编），`message` / `price`
是 `:data` 的 `BookingResult` 原样（即上游那一句）。服务端不再算一份 `param`（那是 `:data` 的事，
客户端也拼不了），也不走那条「先拿 `PendingOrder` 再提交」的两次请求（`:data` 的 `prepareOrder` 是纯计算）。

#### 4. `POST /api/venue/cancel`

```json
POST /api/venue/cancel
{"orderId":"VT20261013001"}
```

响应 `data`：`{ "ok": true, "message": "取消成功" }` —— 与 `/api/library/action` 同一套（`ok` 恒 `true`，
`message` 是 `:data` 的 `OrderActionResult.message` 原样）。取消不需要滑块（一次普通 POST）。

#### 5. 错误码（两个写端点共用）

- **400** 参数形状不对：请求体不是 JSON / `serviceId` 不是正整数 / `selections` 空或某行少了
  `stockId`/`areaDetailId` / 缺 `captchaId` / 缺 `sliderTrack` / 缺 `orderId`。文案一律说出缺什么；
- **401** 未登录（与会话闸门同一个码，`message` 指路先登录）——包括取数/提交时发现**会话已失效**那一档；
- **409** 上游**明确说这次动作不成**：验证码有误、场地已被抢、订单不存在……都是业务结果不是故障，
  文案是 `:data` 的结果对象里那句原样中文（与图书馆那张同一个码、同一种意思：「现在不能」）；
- **502** 上游/网络的故障（稍后重试可能就成）：挑战取不回来、提交那一枪没打通。

⚠️ **已知缺口（如实记下，不在本轮）**：下单能成之后屏上那个「去支付」**点了不会有动作** ——
支付页在 80 端口那台站点上（`VenueApi.paymentUrl` 拼的），而浏览器里没有场馆站的会话，
支付页地址也只有 `:data` 知道（`:core` 不复制一份站点知识）⇒ `CampusVenueApi.paymentUrl` 在 serve
模式下如实返回空串（不是抛、也不是编一个地址）。TODO：契约给了支付入口（或让 `/api/venue/status`
的 `browserLoginUrl` 能被客户端缓存下来）之后再把它接上。

### 5.4 `:web` 消费方（2026-10-11 落地，`ApiMode` 那一次提交）

消费方（`core/net` 的 17 个 `Campus*Api` + `:web` 外壳）按**本文件**接线，不认 campus-api 的字段名（§7.2）。
这条只记**消费方自己的行为**，不改任何端点的形状 —— 改形状仍然要走 §7 的三处同改。

- **模式开关**：`?backend=serve` 钉死 serve 模式（书签/排障用）；否则探一次 `GET /api/status` —— serve 的那份是
  **裸对象且不含身份**（§3.4），campus-api 的同名端点带 `username`，这是两条路在同一个路径上唯一稳定的差别。
  探不出结果按 campus-api 处理（默认形态一字不改）。实现：`web/…/ServeSession.kt` 的 `detectApiMode`。
- **登录**：`POST /api/session/login`；挂起时**并发轮询** `GET /api/session/mfa` —— 间隔由消费方定 **2 秒**
  （`MFA_POLL_INTERVAL_MS`，TODO 见 §5.1 的 ④）。MFA 弹窗显示 `siteName` / `rejections` / `attemptsLeft`
  （`WebServeSessionScreen`）；绑定手机号不投影（§5.1 TODO ①），弹窗如实不写「已发送到 138\*\*\*\*0000」。
- **访问令牌只在浏览器内存里**：不落 `localStorage`（XSS 拿到令牌就等于拿到这道门）；`GET /api/session`
  那一发顺手把令牌换成 `serve_token` cookie（§3.2），之后每次请求同时带 Bearer 与 cookie ——
  「Ktor 的 fetch 引擎会不会自动带 cookie」是引擎实现细节，不作为契约依赖。
- **消费方侧的「不画」**（都是「点了会失败的按钮一个都不画」这一条的实现，服务端别当成 bug）：
  1. ~~library~~（已翻转，2026-10-11）：写端点 `/api/library/action` 落地（§5.3.1）⇒ `CampusLibraryApi` 的
     `SERVE_WRITE_ENDPOINTS_READY=true`，`canBook` 随服务端如实报的 `canBook:true` 翻 true —— 座位格、
     「我的预约」按钮、校区切换全部真的能点；但 `hasSeatPlan` 仍报 `false`（§5 的 library 没有布局/底图
     端点 ⇒ 平面图那一档不画）。
  2. ~~venue~~（已翻转，2026-10-12）：写端点 `/api/venue/captcha` + `/book` + `/cancel` 落地（§5.3.2），
     滑块由 `:web` 的 `WebSlideCaptchaHost` 在浏览器里画 ⇒ `CampusVenueApi` 的 `SERVE_WRITE_ENDPOINTS_READY=true`，
     `canBook`/`canCancel` 翻 true（与 `/api/venue/status` 报的两个开关一致）。⚠️ 留下的**如实缺口**：
     「去支付」那一跳在浏览器里仍不可达（见 §5.3.2 末尾）；
  3. school-courses：响应报 `supportsDepartmentFilter` / `supportsElectiveFilter` 是 `true`，但没有返回那两张
     id 表的端点 ⇒ 消费方报 `false`（下拉出现了却没有选项，比不出现更糟）。
  两处的 TODO 都写在对应 `Campus*Api` 的 KDoc 里，端点落地的提交把它们一起翻成 true ——
  library 那一条已由 2026-10-11 的提交翻成 true，venue 那一条已由 2026-10-12 的提交翻成 true，
  school-courses 的那处照旧。
- **课表缺口已补齐**（2026-10-11）：`/api/jwxt/schedule` 与 `/api/jwxt/term-start` 两个端点落地，
  serve 模式的课表屏（含默认落地页）正常出数据 —— 消费侧是 `CampusScheduleApi` 的 `ApiMode.SERVE` 那一支
  （`CampusApi.schedule` / `.termStart`，`:web` 的 `ScheduleScreen` 把外壳探到的 mode 传下去）。
  留下的那一处**如实差异**：这些行是**排课原样**（`:data` 的 `JwxtScheduleApi.rows()`），调停补课没合进去
  （合并后的那一份是同一个类的 `schedule()`，`7a5590da` 落地，Android 与桌面端吃的就是它）——
  所以同一份课表在 serve 模式与 `:app` / 桌面端之间对调课的表现不同。**这是有意为之，不是缺口**（§5.3 那一格）。

**消费方现状收口（2026-10-13 核账）**：

- 上面那三处「不画」里：**library 已翻 true**（`4abd1ae` 那次提交）、**venue 已翻 true**（`45a0e50` 那次提交，
  `canBook`/`canCancel` 与服务端报的两个开关一致）、**school-courses 照旧**（缺那两张 id 表的端点）；
- 课表那两个端点落地的提交是 `0022296`（它同一次里改了本文档 §5.3 两行 + 这一节 + `:testkit` 夹具，
  也就是 §7 的「三处同改」）；
- 本节**只记消费方自己的行为**，端点形状的改动仍要走 §7；
- 本节**没有**覆盖 Stage C 那批宿主能力（加餐券 / 付款码）。付款码在 `:server` 侧仍是 P2 未实现（§5 表那一行），
  所以 `:web` 这边也还没有可谈的消费方行为（⚠️ 别读成「这两样没实现」：`:data` 侧的实现都在（
  `data/src/commonMain/kotlin/com/xjtu/toolbox/pay/PaymentCodeApi.kt`、`data/src/commonMain/kotlin/com/xjtu/toolbox/coupon/CouponApi.kt`），
  Android 与桌面都在用（`docs/desktop-port-plan.md` §12.2 / §12.3）；缺的只是 `:server` 把它们端出去）；
- **只读四域的消费方已落地（2026-10-13 接线）**：成绩单 / 考勤 / 加餐券 / 宿舍电费四屏在 `:web` 上
  真能打开了 —— 取数是 `:core/net` 的四个新 `Campus*Api`（`ApiMode.SERVE` 那一支，逐屏见 §5.5 末尾那张表），
  屏是 `:core` 的共享屏（宿舍电费那一屏是本端自己的读数页，理由同样在 §5.5）。每屏顶部都画一条「如实缺口」
  说明，拿不到的字段一个都不画。这一批同时把 §7 第 1 条那个「三处同改」的第三处（`:web`）补上了：
  上一次（七个端点落地那批）那一处是有意留空的，登记在 §7 第 5 条。

### 5.5 只读四域（2026-10-13 收尾轮落地）：成绩单 / 考勤 / 加餐券 / 宿舍电费

七个端点，四个 `:server` 路由文件（`DzpzRoutes.kt` / `AttendanceRoutes.kt` / `CouponRoutes.kt` /
`DormPowerRoutes.kt`），全部是「`:data` 里已有的实现在取数」的**薄包装**（解析口径一行不重做）。
统一口径照 §5.2：信封 `{code,data,message}`、`code == HTTP 状态码`、`401` =「请先登录：POST /api/session/login」、
`400` 参数形状、`404` 未实现、`502` 上游/网络故障、时间 ISO-8601（服务端时区）。

**会话**：四个域各自一个站点（`dzpz` / `coupon` / `ssn` / `new_attendance`），四个都已进
`ServeSession.SESSION_SITE_KEYS` 的登录预热清单 —— 它们的域名互相独立，没有哪个站点能替另一个建会话。
⚠️ `ssn` 不只是「预热更好」：`cid` 是**登录**时从缴费页 HTML 里取出来写进会话的（`SsnLogin.parseCid`），
没有它 `DormPowerApi` 直接抛「未登录」。

| 端点 | 请求 | 响应 `data`（逐字段） |
|---|---|---|
| `GET /api/dzpz/transcript` | ——（不收参数） | `{workflowId:int, typeOptions:[{name:string,value:int}], defaultDate:string, defaultRequestName:string}` |
| `GET /api/attendance/terms` | —— | `{terms:[{bh:string,name:string,code:string,startDate:string,endDate:string}]}` |
| `GET /api/attendance/records` | `?term=<学期号 bh>&from=<YYYY-MM-DD>&to=<YYYY-MM-DD>` | `{term:string, count:int, records:[{id,courseName,courseCode,term,date,week,startSection,endSection,location,status,statusText}]}` |
| `GET /api/coupon/list` | `?filter=<档>&page=<n>&size=<n>` | `{filter:string, page:int, size:int, total:int, records:[{sendId,showCardId,voucherName,typeName,amountFen,leftAmountFen,leftCount,startDate,endDate,imageUrl}]}` |
| `GET /api/coupon/detail` | `?showCardId=<必填>` | `{showCardId,voucherName,title,description,amountFen,leftAmountFen,startDate,endDate,batchId,imageUrl,closedPacketImageUrl,openPacketImageUrl}` |
| `GET /api/dorm/rooms` | ——（不收参数） | `{rooms:[{id:string,name:string}]}` |
| `GET /api/dorm/reading` | `?roomId=<必填>` | `{roomId:string, kwh:number\|null}` |

**`GET /api/dzpz/transcript`（成绩单，只端七步里的第一步）**

- 端的是 `TranscriptSource.loadCreateForm(workflowId)` —— 七步里**唯一一步取数**。`workflowId` **不是请求参数**：
  本端点只端成绩单，取 `DzpzDocuments.TRANSCRIPT.workflowId`（= `29`）；
- `typeOptions` ← `FormContext.typeOptions`；`cancel=1` 的选项在 `:data` 的 `parseTypeOptions` 里已经滤掉了
  ⇒ **没有 `cancelled` 字段**（投影出来恒 `false`，多一格只会多一处口径）；
- `defaultDate` / `defaultRequestName` ← `FormContext` 同名字段（上游 `field7249` / `field-1`）；
- **不投影** `params` / `submitParams` / `maindata` / `linkageUUID` / `signatureAttributesStr` / `signatureSecretKey`：
  那是提交表单体与**签章密钥**，属于写路径的原料 —— 端出去等于把密钥交给浏览器；
- ⚠️ **第 2~7 步没有端点**（联动 / 预览 PDF / 两次提交 / 取下载链接 / 下载 PDF 字节）：第 2 步的返回值里有
  **学号**（红线），第 3~7 步是写路径 ⇒ serve 模式下这张屏**现在只能画「选类型」那一步**，
  出 PDF 那半条链是**如实缺口**（不是 bug）；
- 错误码：`401` 令牌对但没会话 · `502` 上游/网络故障。**没有 `400`**：本端点不收参数（URL 写错只能是 `404`）。

**`GET /api/attendance/terms` / `GET /api/attendance/records`（考勤）**

- `?term=` 是**学期号 `bh`**（形如 `646`，取自 `/api/attendance/terms` 的 `bh`），**不是**教务风格学期码；
  省略 = 当前学期（`:data` 的 `getTermBh()`：学期列表按 `startDate` 倒序取第一个，再按今天夹一次）。
  给了但不是纯数字 ⇒ `400`；
- `?from=&to=` 是 `YYYY-MM-DD`；省略时用 `:data` 的兜底（`from` = 该学期起、`to` = 今天）。
  给了但不成形 ⇒ `400`。**响应不回这两格**（兜底口径在 `:data`，本层不复制一份；要范围就看行的 `date`）；
- `terms` 的顺序 = `:data` 的顺序（`sortedByDescending { startDate }`，当前学期在第一个）；`name` 是 `:data`
  拼好的人类可读名（`"2026-2027 第一学期"` 那一套）、`code` 是它算的教务风格学期码（`TermCodeMapper`），
  本层不再拼第二份；
- `records` 命中 `AttendanceWaterRecord` 的第一批字段，**三处刻意的改名**（与 §5 表里 `amount ← signed`
  同一种做法）：`startSection ← startTime`、`endSection ← endTime`（`:core` 那两个字段名里的 `Time`
  其实是**节次**（`Int`，`1..11`），照抄会让消费方以为是时刻）、`term ← termString`（学期码，
  取不到码时 `:data` 兜底成学期名）。行按日期降序；
- `week` = 第几周（`0` = 算不出来，`:data` 不编）；`id ← sbh`（`resultId`，取不到时由 `:data` 按明细拼一个）；
- `status` = `WaterType.value`：`1` 正常 / `2` 迟到 / `3` 缺勤 / `5` 请假 / **`-1` = 未识别的状态码**；
  `statusText` 是配套中文（`-1` ⇒「未知」）。⚠️ **`-1` 必须按 `statusText` 显示** —— `:data` 的 `WaterType`
  KDoc 把这条写死了（不许悄悄算成正常，那会把统计数字做成好看的假数字）；
- ⚠️ **`teacher` 不投影**：考勤记录里的上课教师名是**姓名**，而 §5.2 的例外清单里没有它
  （例外只有 ① `fitness/score` 本人姓名/学号 ② 黄页/教师检索的通讯录 ③ `emptyroom/rooms` 实时档的教师名）。
  `:web` 若要显示教师名，得先把它加进例外清单（那是改契约，不是改这一层）；
- ⚠️ **还没端的三块**（serve 模式下考勤屏目前只有「学期选择 + 流水表」）：`getStudentInfo`（姓名那格过不了
  红线）、统计栏（`getKqtjCurrentWeek` / `getKqtjByTime` / `computeCourseStatsFromRecords`）、
  打卡原始流水（`getStreams`）；
- **请假那一半一律不端**（`getSemesterWindow` / `getFlowPreview` / `getLeavePage` / `uploadEvidence` /
  `createLeave` / `withdrawLeave` / `cancelLeave`）：请假 / 销假是写路径；
- 错误码：`400` 上面那三格形状不对 · `401` 没会话 · `502` 上游/网络故障。

**`GET /api/coupon/list` / `GET /api/coupon/detail`（加餐券，只读）**

- `?filter=` 取四档的**小写枚举名**：`available`（可领取，status=0）· `usable`（可使用，status=1）·
  `used_up`（已用完）· `expired`（已过期）。**省略 = `usable`** —— `:core` 的 `CouponViewModel` 初始档
  就是它，本层不另定一个默认档。中文名（`CouponFilter.label` =「可领取」那些）**不当参数收**：参数只认
  一套 ASCII 写法，响应里的 `filter` 也是同一个值；
- `?page=`（省略 `1`）`&size=`（省略 `20`，照 `:core` 的 `CouponViewModel` 拉列表用的页大小）；
  不是正整数 ⇒ `400`；
- 金额一律**单位是分**（`amountFen` / `leftAmountFen`，`:core` 模型的原字段名）。`CouponRecord` 上那两个
  `amountYuan` 属性是**换算便利属性**、不是模型字段 ⇒ 不进线上形状（除 100 是消费方的事，本层不做第二次舍入）；
- `imageUrl` 是 `:data` 的 `normalizeImageUrl` 拼好的**绝对地址**（`https://egc.xjtu.edu.cn` + 相对路径；
  上游 `pic` 为空 ⇒ 空串，不编地址）。⚠️ **已知缺口**：那张图要带 `egc` 的 `Authorization` 才取得到 ⇒
  浏览器**直接打开这个 URL 会拿到登录页**；本层没有图片代理端点（要封面图就得下一批开一个）；
- `detail` 的十二格原样投影（`title ← destitle`、`description ← describes`、`leftAmountFen ← ltranamt`
  那些映射在 `:data` 的 `CouponJsonParser` 已经做掉）；`showCardId` 是请求给的那个原样回；
- **领券（写路径）不端**：`CouponSource.activateCoupon`（`POST /app/voucher/activate`）真会改上游状态 ⇒
  serve 模式下那个「领取」按钮**没有端点**（如实缺口）；
- 错误码：`400` `filter` 不认识 / `page`、`size` 不是正整数 / `showCardId` 缺失 · `401` 没会话 ·
  `502` 上游/网络故障（含 `:data` 认出的会话失效那一档）。

**`GET /api/dorm/rooms` / `GET /api/dorm/reading`（宿舍电费，只读）**

- 与 campus-api 的同名能力对照：旧的是 `GET /api/dorm/kwh?roomId=`（一个端点把列表与读数混在一处）；
  本契约拆成 `rooms` / `reading` 两个（列表一次、读数一间一次），**旧的路径不在本契约里**；
- `rooms` 不收参数；`id ← roomId` 是**要原样带回**给 `reading` 的句柄，`name ← roomName`
  （「园区/楼/单元/层/房间」拼好的全名）。上游缺 `roomId` 的行由 `:data` 丢掉（不是投影成空 id）；
- `?roomId=`（`reading` 必填，取自 `rooms` 的 `id`）；缺或空白 ⇒ `400`；
- `kwh` 是**剩余电量（度）**，读不到就是 **`null`**（§4：`null` = 上游明确说没有 —— 那间房没装表，
  **不是 0**）；
- ⚠️ **没有时间戳**：`:core` 的 `DormReading.at` 是 `DormPowerStore` 在**本机**记的「什么时候查到的」，
  上游不给 ⇒ 本端点不编（§4：时间一律 ISO-8601，编一个反而是第二个口径）。要「这张读数是什么时候的」
  由消费方在**自己拿到响应的那一刻**记；
- **绑定 / 解绑 / 充值 / `freshPayUrl` 都不端**：那几件事发生在缴费页里（浏览器端的事）；`freshPayUrl()`
  只是「换一张能直接登录缴费页的入口地址」，而且它**顺手记下「用户进过这一页」**（`DormPowerStore.markUsed`）
  —— 那是宿主侧状态，serve 这一层不该替浏览器记 ⇒ `:web` 的「去缴费」那一跳在 serve 模式下没有端点；
- 错误码：`400` `roomId` 缺/空白 · `401` 没会话 · `502` 上游/网络故障（含会话失效那一档 —— 上游的
  `401001` 被 `:data` 认成登录过期，本层按 `502` 报「稍后重试可能就成」）。

#### 消费方（`:web`）：**已落地**（2026-10-13 接线）

七个端点由 `:core/net` 的四个新 `Campus*Api` 消费（`CampusTranscriptApi` / `CampusAttendanceApi` /
`CampusCouponApi` / `CampusDormPowerApi`）—— 它们与既有那 13 个同一族（`ApiMode` 双形态），只有一个差别：
**默认形态（campus-api）那一支不猜也不编空数据，直接抛错**（这四个域在 campus-api 里根本没有端点：
`:app` / 桌面是走 `:data` 的 okhttp 抓的）。`:web` 因此把这四屏写成「serve 形态才画真屏，否则画一张
「这一屏要 serve 模式」的如实页」，并把拿不到的东西画在屏幕上，而不是编出来：

| 屏 | 端点 | `:web` 上真画了什么 | 如实不画（缺口） |
|---|---|---|---|
| 电子成绩单 | `GET /api/dzpz/transcript` | 类型选择 + 申请信息（「申请日期」就是 `defaultDate`） | 「一键申请」与那五步步骤预览：第 2~7 步没有端点（`TranscriptSource.canRunWorkflow = false`）；`params` / `submitParams` / `maindata` / `linkageUUID` / `signatureAttributesStr` / `signatureSecretKey` 六格**留空**（刻意不投影） |
| 考勤 | `GET /api/attendance/terms` + `/records` | 学期选择器 + 流水表（节次 / 状态 / 地点 / 日期） | 上课教师名（记录里有，但不投影 —— 红线）、姓名那一格（`getStudentInfo` 不端）、统计那一栏、打卡原始流水、请假那一栏（写路径）。那三栏在屏上写的是「本端拿不到 …」而不是「暂无 …」（`AttendanceCapabilities` 三档 `false`）；统计也不拿流水在本机重算 |
| 加餐券 | `GET /api/coupon/list` + `/detail` | 四档 + 翻页 + 金额（分）+ 有效期 | 封面图（要 egc 会话 ⇒ `loadImage` 给 `null`、券卡退化成图标）；「领取」按钮（写路径不端，`CouponSource.canActivate = false`）；金额不换算成元（除 100 是屏的事） |
| 宿舍电费 | `GET /api/dorm/rooms` + `/reading` | 房间列表 + 剩余电量（`kwh: null` 单独说「没有电表」）+ **本机**查询时刻 | 「去缴费」/ 绑定 / 充值那一跳（`freshPayUrl` 不端）：本端画的是 `:web` 自己的读数页（`WebDormPowerScreen`），**不是** `:core` 那张只做缴费页浏览器的 `DormPowerScreen` |

三处要点（写在这里，免得下一个读的人当成 bug 或当成漏）：

1. **入口**：这四条路由同时进了 `:web` 的 `WebSupport.openableRouteIds`（首页宫格 / 学辅目录的唯一真源）
   与 `AppPage` 的 `when (route)` —— 缺任一处就是「屏在、门不在」那一类漏（上一轮踩过的就是它）；
2. **campus-api 形态**：那四个 `Campus*Api` 的非 serve 分支一律 `serveOnly(...)` 抛错，`:web` 在那形态下
   画的是「这一屏要 serve 模式」那张页 —— 不是「还没搬到 Web」（屏就在 `:core`）；
3. **本机时刻与 `null` 的区别**：宿舍电费的读数时间戳由消费方在**自己拿到响应的那一刻**记（端点不端
   时间戳，见上）；`kwh` 的 `null`（上游明确说「没有表」）与**字段缺失**（形状没对上）在客户端是两件事，
   后者抛错（不拿 `null` 冒充「没有表」）。

夹具与用例：`:server:test` 的 `ServeReadonlyFourDomainsTest` 8 例（形状那一侧）之外，本轮补了
`:core:jvmTest` 的 `ServeReadonlyFourDomainsApiTest` **6** 例 —— 逐字段、错误信封、`status=-1` → 「未知」、
缺字段不编造、`kwh` 缺失 vs `null`、以及「campus-api 分支抛错而不是返回空数据」。


## 6. 夹具契约测试（D5 要求，不能只写文档）

- 位置：**`:server:test`**（`kotlin("jvm")` 模块的测试任务就叫这个名；本轮已经有 13 例：6 例是 serve 第一步的
  外壳验收，7 例是 `/api/session*` 的契约验收）——起**真** `:server`（端口 `0`，系统挑），用**同一批上游样本**
  （已抽成 `:testkit`，`testImplementation(project(":testkit"))`）灌进 `:data`，对 `/api/*` 的响应**逐字段断言**；
- 与既有测试的关系：`:data:jvmTest` 的夹具测的是“数据层”；契约测的是“**同一份数据层 × HTTP 形状**”，
  两者都要在：前者保证解析没漂，后者保证形状没漂；
- 断言写在代码里（`ServeSessionTest` / `ServeServerTest`），与仓库其余契约测试同一形态；
  “上游样本 → 期望 JSON”的成对资源文件**这一轮没采用**（样本已经在 `:testkit` 的夹具常量里，
  再措一份 JSON 只会多一份要同步的副本）—— 若后续要上，放在 `:server/src/test/resources/`。
- MFA（短信二验）那条链需要一份**真的能走完**的上游剧本：二次认证页 + 取手机号 + 校验验证码 +
  隐藏表单回提，四步都在 `:testkit`（`CasSafetyVerifyPage` / `LibraryFakeUpstream` / `JwxtFakeUpstream`），
  默认关着开关，契约测试开它。
- 计数（2026-10-13 核）：`:server:test` 共 **52** 例 —— 上面那 13 例之外是按域分批补的契约验收，
  最近一批是只读四域的 `ServeReadonlyFourDomainsTest`（**8** 例，§5.5：每域两例 —— 成功逐字段一例、
  闸门/参数形状一例），它用的就是 `:testkit` 那四份早已存在的假上游（`DzpzFakeUpstream` /
  `AttendanceFakeUpstream` / `CouponFakeUpstream` / `DormPowerFakeUpstream`）—— 本轮只往
  `AttendanceFakeUpstream` 里补了一个 `TEACHER_NAME` 常量（把原本内联在 `recordsPageJson` 里的教师名
  提升为常量，供「教师名不投影」那条红线断言引用；值没变）。

## 7. 变更纪律

1. 改契约 = **同时改**：本文档 + 夹具 + `:web` 对应实现（三处都不动就是漂移的开始）；
2. `:web` 侧只认**本文档**，不认 campus-api 的字段名（那条路在 serve 模式下退休）；
3. 与 `:data` 的分工：**`:data` 定义模型与口径，本文档只定义"怎么把它端出去"**——同一个字段名不在这两处各起一个。
4. **这条纪律最近三次契约变更都照做了**：`0022296`（§5.3 两行 + §5.4）、`4abd1ae`（§5.3.1 写路径）、`45a0e50`（§5.3.2 写路径）—— 本文档、`:testkit` 夹具、`:web` 实现三处都在**同一次提交**里（见各自的提交信息）。反过来，不碰 `/api/*` 形状的那些搬迁提交（`:core`/`:data`/`:desktop`）**不动本文档**。
5. ✅ **那次例外已补位（2026-10-13 接线）**：只读四域的七个端点（§5.5）最初**只改了本文档 + `:testkit`
   夹具两处，`:web` 那一处有意留空**，当时在 §5.5 末尾**显式标注为「消费方待接」**（按第 1 条那本是漂移，
   本条纪律允许的唯一形态是「把缺的那一处指名点姓地标出来」）。后续那一次接线把第三处补上了：
   四个新 `Campus*Api`（`CampusTranscriptApi` / `CampusAttendanceApi` / `CampusCouponApi` /
   `CampusDormPowerApi`）+ 四屏接线 + `WebSupport` 入口 + §5.5 的消费方表 ⇒ 三处同改齐了。
   **教训照旧**：「端点在 `:server` 上能跑」不等于「这屏在该端上能用」，两者之间差的就是这一处。

---

## 8. 这份草案的依据（谁写的、读了什么）

- 读者：`serve` 模式的实现者（`:server` + `:web` 改造），以及每次把某个 `*Api` 搬进 `:data` 的那次提交。
- 依据（全部是只读核对）：`docs/desktop-port-plan.md` 的 D3/D5/D6 与 §3/§5/§6；
  `:core/core/net/` 那 17 个 campus-api 实现（它们的类 KDoc 里有逐条字段映射与"如实降级"说明；
  只读四域的四个是 2026-10-13 接线时补的，本文件草拟时那一族还是 13 个）；
  `:core` 里 12 个取数端口的接口 KDoc；`web/tools/serve-same-origin.py`（serve 模式的雏形）。
- 未核实的部分已标 `TODO`/`待确认`，**不要当成结论用**。