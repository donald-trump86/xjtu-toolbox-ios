package com.xjtu.toolbox.schedule

/**
 * `:data` 里那几处「java.time ↔ kotlinx-datetime」的边界转换。
 *
 * 为什么需要：课表这一族的**存储口径**（`ScheduleCache` 的 `start_date_<学期>`）与 Android 侧
 * 的调用方收的都是 `java.time.LocalDate`，而 `:core` 的 `TermWeeks` 收 kotlinx 的。
 * 逐字段搬、两个方向都要，与 `:app` 的 `util/TimeBridge.kt` 那两条同一个写法
 * （那边是 `internal`，跨不了模块边界）。
 *
 * 名字起成 `kxDate` / `javaDate` 而不是 `toKx` / `toJavaTime`：`:app` 的 `com.xjtu.toolbox.schedule`
 * 包里还有代码在调那两条同名扩展，同名而不可见的候选只会让编译器报「private in file」这类
 * 让人看不懂的错（实测踩过），换一个名字最省事。
 */
internal fun kxDate(date: java.time.LocalDate): kotlinx.datetime.LocalDate =
    kotlinx.datetime.LocalDate(date.year, date.monthValue, date.dayOfMonth)

internal fun javaDate(date: kotlinx.datetime.LocalDate): java.time.LocalDate =
    java.time.LocalDate.of(date.year, date.month.ordinal + 1, date.day)
