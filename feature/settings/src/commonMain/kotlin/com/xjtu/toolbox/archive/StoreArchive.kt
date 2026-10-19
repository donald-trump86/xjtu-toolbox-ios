package com.xjtu.toolbox.archive

import kotlin.time.Clock

/** 统一存档层的纯函数门面。 */
object StoreArchive {

    /** 纯函数导出：把 host 已投影的 store 快照 + `files/` 组装成档案，返回 `档案内相对路径 → 字节` 的映射（含 `manifest.json`）。 */
    fun export(
        accounts: List<ArchiveAccount>,
        createdBy: String,
        appVersion: String,
        stores: List<StoreSnapshot>,
        files: List<ArchiveFile>,
        options: ExportOptions = ExportOptions(),
    ): Map<String, String> {
        val suffixes = accounts.map { it.suffix } + listOf("default", "_")
        val ids = ArchiveNames.split(stores.map { it.rawName }, suffixes)

        val out = linkedMapOf<String, String>()
        val storeNames = mutableSetOf<String>()
        val redacted = mutableListOf<String>()

        stores.zip(ids).forEach { (snapshot, id) ->
            val base = id.base
            if (ArchiveRegistry.isExcluded(base)) return@forEach
            val path = ArchivePaths.storePath(base, id.suffix)
            if (ArchiveRegistry.isCredential(base)) {
                if (!options.includeCredentials) {
                    redacted += path
                    return@forEach
                }
            }
            storeNames += base
            out[path] = ArchiveCodec.encodeStore(declaredTypes(snapshot, base), base, id.suffix)
        }

        files.forEach { f ->
            out[ArchivePaths.filePath(f.suffix, f.relPath)] = f.content
        }

        val manifest = ArchiveManifest(
            createdAt = ArchiveCodec.formatTimestamp(options.now()),
            createdBy = createdBy,
            appVersion = appVersion,
            noAccount = accounts.isEmpty(),
            accounts = accounts,
            stores = storeNames.sorted(),
            files = files.map { it.relPath }.distinct().sorted(),
            redacted = redacted.sorted(),
        )
        out["manifest.json"] = ArchiveCodec.encodeManifest(manifest)
        return out
    }

    /**
     * 按【声明类型】写值（`ArchiveRegistry.isStringSet`）：声明的键强制成 `SetValue` ⇒ 档案里是 `t="set"` + 数组。
     *
     * 为什么靠声明而不是靠猜：端内存储只有字符串（web 逗号 join、桌面 `\u0000` join、Android 真集合），
     * “只看字符串”反推不出集合语义——单元素集合 `42` 看着像 int、空集合就是空字符串，
     * 导出成 `t="i"` / `t="s"` 后到 Android 那边用 `getStringSet` 一读就是 `ClassCastException`。
     * 没声明的键原样放过（不在此处做档案形状统一 / 键名归一）。
     */
    private fun declaredTypes(snapshot: StoreSnapshot, base: String): StoreSnapshot {
        val keys = snapshot.keys.entries.associate { (key, value) -> key to declaredValue(base, key, value) }
        return if (keys == snapshot.keys) snapshot else snapshot.copy(keys = keys)
    }

    /**
     * 一个键的【声明类型】归一：声明的键（[ArchiveRegistry.isStringSet]）⇒ 摊成 [StoreValue.SetValue]；
     * 没声明的键原样返回（不在此处做档案形状统一 / 键名归一）。
     *
     * 导出侧（[export]）与导入侧（[import]）共用这一条，方向相反但口径相同：导出侧据此**写** `t="set"`，
     * 导入侧据此**读**——老档案里同一个键可能是 `t="i"`（单元素集合被猜成 int）或 `t="s"` + `""`（空集合），
     * Android 落它用 `getStringSet`，读到一个非集合值就是 `ClassCastException`。
     */
    private fun declaredValue(base: String, key: String, value: StoreValue): StoreValue =
        if (ArchiveRegistry.isStringSet(base, key)) asStringSet(value) else value

    /**
     * 把反推出来的值摊成字符串集合：已是集合的原样返回；字符串按分隔符拆（web 逗号 / 桌面 `\u0000` 都支持）；
     * 被猜成 `b/i/l/f` 的标量就是一个元素（如单收藏 `42`）；空串 = 空集合（不是不存在的键）。
     */
    private fun asStringSet(value: StoreValue): StoreValue.SetValue = when (value) {
        is StoreValue.SetValue -> value
        is StoreValue.S -> StoreValue.SetValue(
            value.v.split(',', '\u0000').map { it.trim() }.filter { it.isNotEmpty() }
        )
        is StoreValue.B -> StoreValue.SetValue(listOf(value.v.toString()))
        is StoreValue.I -> StoreValue.SetValue(listOf(value.v.toString()))
        is StoreValue.L -> StoreValue.SetValue(listOf(value.v.toString()))
        is StoreValue.F -> StoreValue.SetValue(listOf(value.v.toString()))
    }

    /** 自校验一份档案（纯读）：`manifest.json` 在不在、`format` 对不对、`version` 认不认识（不认识就明确失败）、每个 `stores/` 下的 `.json` 解不解得开、账号后缀有没有碰撞（§4）。 */
    fun inspect(source: ArchiveSource): ArchiveInspection = when (val validated = validate(source)) {
        is Validated.Failed -> ArchiveInspection.Failed(validated.reason)
        is Validated.Ok -> ArchiveInspection.Ok(
            manifest = validated.manifest,
            stores = validated.storePaths,
            files = source.filePaths().size,
        )
    }

    // ── 导入（§6.4 的冲突与合并） ─────────────────────────────────────────────

    /** 把一份档案合并进端内（§6.4），返回写了几笔。 */
    fun import(source: ArchiveSource, target: ArchiveImportTarget): ImportResult {
        val validated = when (val v = validate(source)) {
            is Validated.Failed -> return ImportResult.Failed(v.reason)
            is Validated.Ok -> v
        }

        var keysWritten = 0
        var keysKept = 0
        validated.records.forEach { record ->
            val snapshot = record.snapshot
            if (ArchiveRegistry.isExcluded(record.base)) return@forEach
            val local = target.readStore(snapshot.rawName)
            snapshot.keys.forEach { (key, value) ->
                val at = snapshot.writtenAt[key]
                if (archiveWins(key, at, local)) {
                    // 导入侧的容错在落地那一刻（§8.6）：值按【目标端读法】归一后再写；归一与落地整条都不许
                    // 往上抛 —— 坏一条只丢这一条，别的键照常写（端内没动，计数上等于这一条没落地）。
                    val landed = runCatching {
                        target.putKey(snapshot.rawName, key, declaredValue(record.base, key, value), at)
                    }.isSuccess
                    if (landed) keysWritten++ else keysKept++
                } else {
                    keysKept++
                }
            }
        }

        // `accounts/<suffix>` → 学号（§6.3 键名归一时把端内名拼回去要用它）
        val accountIds = validated.manifest.accounts.associate { it.suffix to it.accountId }
        var filesWritten = 0
        var filesSkipped = 0
        source.filePaths().forEach { path ->
            val (relPath, suffix) = ArchivePaths.parseFilePath(path) ?: return@forEach
            // 落地整条都不许往上抛：拼不出端内名 / 转不动形状 / 读不出来 ⇒ 只丢这一条并计数（§8.6）。
            val landed = runCatching {
                landFile(suffix, accountIds[suffix], relPath, path, source, target)
            }.getOrElse { FileLanding.Skipped }
            when (landed) {
                FileLanding.Written -> filesWritten++
                FileLanding.Present -> Unit // §6.4 文件层：本端有 ⇒ 保持不变（与以前一样不计数）
                FileLanding.Skipped -> filesSkipped++
            }
        }


        return ImportResult.Ok(
            keysWritten = keysWritten,
            keysKept = keysKept,
            filesWritten = filesWritten,
            filesSkipped = filesSkipped,
        )
    }

    /**
     * 一条 `files/` 条目的落地：① 键名归一（档案名 → 本端名）→ ② §6.4 文件层的「本端有 ⇒ 保持不变」
     * → ③ 形状归一（档案形状 → 本端形状）→ ④ 写。
     *
     * ①②③ 任一步拼不出来 ⇒ [FileLanding.Skipped]（调用方计数，不抛）；
     * 本端已经有这一条 ⇒ [FileLanding.Present]（§6.4：文件层没有逐键仲裁，本端胜）。
     */
    private fun landFile(
        suffix: String,
        accountId: String?,
        relPath: String,
        archivePath: String,
        source: ArchiveSource,
        target: ArchiveImportTarget,
    ): FileLanding {
        val localRelPath = target.fileRelPath(suffix, accountId, relPath) ?: return FileLanding.Skipped
        if (target.readFile(suffix, localRelPath) != null) return FileLanding.Present
        val content = source.readFile(archivePath) ?: return FileLanding.Skipped
        val localContent = target.fileContent(suffix, accountId, relPath, content) ?: return FileLanding.Skipped
        target.putFile(suffix, localRelPath, localContent)
        return FileLanding.Written
    }

    /** 一条 `files/` 条目的落地结果（只影响计数）。 */
    private enum class FileLanding { Written, Present, Skipped }

    /** §6.4：本端没有这个键 ⇒ 写入；有 ⇒ 双方都有 `at` 时新者胜（相同也算本端胜），缺一方 ⇒ 本端胜。 */
    private fun archiveWins(key: String, archiveAt: Long?, local: StoreSnapshot?): Boolean {
        if (local == null || !local.keys.containsKey(key)) return true
        val localAt = local.writtenAt[key] ?: return false
        if (archiveAt == null) return false
        return archiveAt > localAt
    }

    /** 校验 + 解码（[inspect] 与 [import] 的共同前置）；任何一步不对就是一句明确的原因。 */
    private fun validate(source: ArchiveSource): Validated {
        val storePaths = source.storeNames()
        val manifestJson = source.readManifest() ?: return Validated.Failed("缺 manifest.json")
        val manifest = runCatching { ArchiveCodec.decodeManifest(manifestJson) }.getOrElse {
            return Validated.Failed("manifest.json 解不开：${it.message ?: it::class.simpleName}")
        }
        if (manifest.format != ARCHIVE_FORMAT) return Validated.Failed("format 不是 $ARCHIVE_FORMAT")
        if (manifest.version != ARCHIVE_VERSION) return Validated.Failed("不认识的版本 ${manifest.version}")

        val collisions = ArchiveNames.detectCollision(manifest.accounts)
        if (collisions.isNotEmpty()) return Validated.Failed("账号后缀碰撞：${collisions.joinToString()}")

        val records = mutableListOf<ArchiveCodec.StoreRecord>()
        storePaths.forEach { path ->
            val json = source.readStore(path) ?: return Validated.Failed("$path 读不出来")
            records += runCatching { ArchiveCodec.decodeStore(json) }.getOrElse {
                return Validated.Failed("$path 解不开：${it.message ?: it::class.simpleName}")
            }
        }
        return Validated.Ok(manifest, storePaths.sorted(), records)
    }

    private sealed interface Validated {
        data class Ok(
            val manifest: ArchiveManifest,
            /** 档案里 `stores/` 那一层的相对路径（码点升序）。 */
            val storePaths: List<String>,
            val records: List<ArchiveCodec.StoreRecord>,
        ) : Validated

        data class Failed(val reason: String) : Validated
    }
}

/** 默认不导出凭据族（§8.10）；显式开关时照写，`redacted` 为空。 */
data class ExportOptions(
    val includeCredentials: Boolean = false,
    /** 固定时钟注入；默认墙上时钟。 */
    val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
)

/** 一份档案的自校验结果（§6.2 第 3 条的前半）。 */
sealed interface ArchiveInspection {

    /** 校验通过。 */
    data class Ok(
        /** 解出来的 `manifest.json`（§2.1）。 */
        val manifest: ArchiveManifest,
        /** 档案里 `stores/` 那一层的相对路径（码点升序）。 */
        val stores: List<String>,
        /** `files/` 那一层的条目数。 */
        val files: Int,
    ) : ArchiveInspection

    /** 明确失败：缺 `manifest.json` / `format` 不对 / `version` 不认识 / JSON 解不开 / 后缀碰撞。 */
    data class Failed(val reason: String) : ArchiveInspection
}

/** 导入的结论（§6.4）。[Failed] ⇒ 端内零改动。 */
sealed interface ImportResult {

    /** 导入跑完了。 */
    data class Ok(
        /** 真正写进端内的键数（档案有本端没有 + 档案更新）。 */
        val keysWritten: Int,
        /** 本端胜 / 档案里没有的键数；落地时被吞掉的那条也算在这里 —— 端内没动。 */
        val keysKept: Int,
        /** 写进端内的 `files/` 条目数。 */
        val filesWritten: Int,
        /**
         * 拼不出端内名 / 转不动形状 / 读不出来的 `files/` 条目数（§6.3 逐族迁移的残尾）。
         *
         * 它们**一条一笔都没写**，整次导入照旧跑完（§8.6：绝不抛）；本端已经有那条的不算在这里（那是本端胜）。
         */
        val filesSkipped: Int = 0,
    ) : ImportResult

    /** 明确失败；端内一笔都没写。 */
    data class Failed(val reason: String) : ImportResult
}
