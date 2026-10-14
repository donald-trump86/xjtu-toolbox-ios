package com.xjtu.toolbox.archive

/**
 * 端内名 ↔ 档案路径的**唯一映射规则**（§2.0，A13 的可断言形式）。
 *
 * 端内名 = `<store>` + （账号档 ? `<suffix>` : `""`）：
 *  `accounts/_2253415556/stores/schedule_cache.json` ⇄ `schedule_cache_2253415556`
 *  `global/stores/app_settings.json`                 ⇄ `app_settings`
 *
 * suffix 为 `""` ⇒ `global/`；否则 ⇒ `accounts/<suffix>/`。目录名用 suffix 逐字，
 * 保证映射是**纯字符串拼接**（§2.0 / A13）。
 */
object ArchivePaths {

    /** 端内 base + suffix → 档案里 `stores/` 下的相对路径。 */
    fun storePath(base: String, suffix: String): String =
        if (suffix.isEmpty()) "global/stores/$base.json"
        else "accounts/$suffix/stores/$base.json"

    /** 档案路径的反向：base + suffix = 端内 store 名（§2.0）。 */
    fun rawStoreName(base: String, suffix: String): String = base + suffix

    /** `files/` 的相对路径 → 档案里的完整相对路径（§2.0 / §2.3）。 */
    fun filePath(suffix: String, relPath: String): String =
        if (suffix.isEmpty()) "global/files/$relPath"
        else "accounts/$suffix/files/$relPath"
}
