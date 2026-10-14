package com.xjtu.toolbox.archive

import kotlin.time.Clock

/**
 * 统一存档层的**纯函数门面**（S1，零端接线）。
 *
 * 这一版只做「把已投影的数据组装成档案」这一件纯事（A3 字节稳定的落点），
 * 不读不写任何端内 I/O、不做迁移、不碰导入/导出入口 —— 那些由
 * `ArchiveStoreBackend` / `ArchiveFileBackend` / 各端宿主（S2–S6）负责。
 * 带后端投影的 `export(host, sink, options)` 门面在后续工单补，本对象是它要调的核心。
 */
object StoreArchive {

    /**
     * 纯函数导出：把 host 已投影的 store 快照 + `files/` 组装成档案，
     * 返回 `档案内相对路径 → 字节` 的映射（含 `manifest.json`）。
     *
     * 组装时做的事（全部是纯函数）：
     *  - §2.0：端内名拆 base+suffix → `global/` 或 `accounts/<suffix>/`；
     *  - §8.10 / A8：凭据族默认不写、点名列进 `manifest.redacted`；端内私有（§2.4）永不写；
     *  - §2.1 / A3：`stores[]`/`files[]` 按码点升序、`keys`/`set` 的排序在 codec 里做；
     *  - `createdAt` 来自 [ExportOptions.now] —— 注入固定时钟后两次导出字节相同（A3）。
     *
     * @param accounts 已记录的账号（`accountId` 原值 + `suffix`）。suffix 列表另补 `default`/`_`
     *   两个匿名档（§4：未登录/登出中间态也占目录）。
     */
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
            out[path] = ArchiveCodec.encodeStore(snapshot, base, id.suffix)
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
}

/** §8.10 / D17：默认不导出凭据族；显式开关时凭据族照写，`redacted` 为空。 */
data class ExportOptions(
    val includeCredentials: Boolean = false,
    /** 固定时钟注入（A3 字节稳定）；默认墙上时钟。 */
    val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
)
