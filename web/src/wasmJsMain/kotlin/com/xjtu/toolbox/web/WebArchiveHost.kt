package com.xjtu.toolbox.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xjtu.toolbox.archive.ArchiveFile
import com.xjtu.toolbox.archive.ArchiveScoreReport
import com.xjtu.toolbox.archive.ArchiveImportTarget
import com.xjtu.toolbox.archive.ArchiveInspection
import com.xjtu.toolbox.archive.ArchiveStoreBackend
import com.xjtu.toolbox.archive.ExportOptions
import com.xjtu.toolbox.archive.ImportResult
import com.xjtu.toolbox.archive.StoreArchive
import com.xjtu.toolbox.archive.StoreSnapshot
import com.xjtu.toolbox.archive.StoreValue
import com.xjtu.toolbox.archive.archiveSourceOf
import com.xjtu.toolbox.archive.archiveStoreBackend
import kotlin.time.Clock

/** Web 这一端的档案宿主装配；没有文件系统，`files/` 里只有自建日程（[WebArchiveRows]）与成绩缓存那一族（[ArchiveScoreReport]）。 */
internal object WebArchiveHost {

    /** §2.1 `createdBy`：本端固定写 `web`。 */
    const val CREATED_BY = "web"

    /** §2.1 `appVersion`：与 `WebSettingsHost.versionText` 同一个字面。 */
    const val APP_VERSION = "Web 构建（wasm）"

    /** 读端内 store 投影（`localStorage` 的 `xjtu-toolbox.` 前缀）。 */
    fun readStores(backend: ArchiveStoreBackend = archiveStoreBackend()): List<StoreSnapshot> =
        backend.storeNames().mapNotNull { backend.readStore(it) }

    /** 纯组装：已投影的 stores 与行数组 → [StoreArchive.export] 的档案字节映射（本端没有账号，全在 `global/`）。 */
    fun buildArchive(
        stores: List<StoreSnapshot>,
        files: List<ArchiveFile> = WebArchiveRows.filesOf(),
        now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    ): Map<String, String> =
        StoreArchive.export(
            accounts = emptyList(),
            createdBy = CREATED_BY,
            appVersion = APP_VERSION,
            stores = stores,
            files = files,
            options = ExportOptions(now = now),
        )

    /** 端内 → 完整档案字节映射（只读端内，不写任何 store）。 */
    fun exportArchive(now: () -> Long = { Clock.System.now().toEpochMilliseconds() }): Map<String, String> {
        val stores = readStores()
        // §6.3 逐族迁移（「成绩缓存」那一族）：端内是店、档案里是文件 ⇒ 这一族的店**不进 `stores/`**
        // （否则同一份缓存会在档案里出现两份；Android 那边还会把它当 prefs 店落下来）。
        return buildArchive(
            stores = stores.filterNot { it.rawName == WebScoreReportCache.STORE_NAME },
            files = WebArchiveRows.filesOf() + listOfNotNull(scoreFileOf(stores)),
            now = now,
        )
    }

    /**
     * §6.3 逐族迁移（「成绩缓存」那一族）：端内是店 `score_report_cache` 的 `all` 键（[WebScoreReportCache]），
     * 档案里是**文件** `schedule/score_report.json`，值是 `{"at":<ms>,"grades":[…]}`，名字里不带学号。
     *
     * 本端没有账号维度（全在 `global/`）。端内值已经带着 `at`；旧形状（裸数组）用导出时刻补上。
     * 没这份缓存 / 转不动 ⇒ null（不出这一条）。
     */
    private fun scoreFileOf(
        stores: List<StoreSnapshot>,
        now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    ): ArchiveFile? {
        val raw = stores.firstOrNull { it.rawName == WebScoreReportCache.STORE_NAME }
            ?.keys?.get(WebScoreReportCache.KEY) as? StoreValue.S ?: return null
        val canonical = ArchiveScoreReport.toCanonical(raw.v, now()) ?: return null
        return ArchiveFile("", ArchiveScoreReport.CANONICAL_REL, canonical)
    }

    /** 导出：组装 + 交给浏览器下载，返回结论文本。 */
    fun exportToDownload(now: () -> Long = { Clock.System.now().toEpochMilliseconds() }): String {
        val archive = exportArchive(now)
        WebArchiveIo.download(archive)
        return "已导出 ${archive.size} 个条目（${archive.keys.count { it.contains("/stores/") }} 个 store），" +
            "浏览器把它存到默认下载位置"
    }

    /** 自校验一份已解出来的档案（§6.2 第 3 条的前半，纯读） */
    fun checkArchive(entries: Map<String, String>): WebArchiveCheck {
        return when (val inspection = StoreArchive.inspect(archiveSourceOf(entries))) {
            is ArchiveInspection.Failed -> WebArchiveCheck.Failed(inspection.reason)
            is ArchiveInspection.Ok -> WebArchiveCheck.Ok(
                version = inspection.manifest.version,
                stores = inspection.stores,
                files = inspection.files,
                redacted = inspection.manifest.redacted.size,
            )
        }
    }

    /** 导入那一行的结论文本（纯函数，供屏与「手工点一次」共用同一份措辞）。 */
    fun importStatusText(picked: PickedArchive): String = when (picked) {
        is PickedArchive.Failed -> "导入失败：${picked.reason}"
        is PickedArchive.Entries -> importAndReport(picked)
    }

    /** 真导 + 结论文本。 */
    fun importArchive(
        entries: Map<String, String>,
        target: ArchiveImportTarget = WebArchiveImportTarget(),
    ): ImportResult = StoreArchive.import(archiveSourceOf(withoutUnlandedFiles(entries)), target)

    private fun importAndReport(picked: PickedArchive.Entries): String {
        val unlandedFiles = unlandedFilesCount(picked.entries)
        return when (val result = importArchive(picked.entries)) {
            is ImportResult.Failed -> "「${picked.fileName}」不可用：${result.reason}（本机数据未改动）"
            is ImportResult.Ok -> buildString {
                append("「${picked.fileName}」已导入：:core 的 StoreArchive.import 按 §6.4 写入 ")
                append(result.keysWritten)
                append(" 个键 / 保持 ")
                append(result.keysKept)
                append(" 个")
                if (result.filesWritten > 0) {
                    append("；自建日程落了 ${result.filesWritten} 条")
                }
                if (unlandedFiles > 0) {
                    append("；本端没有文件系统，$unlandedFiles 个其他 files/ 条目未落地")
                }
            }
        }
    }
}

/** 一份档案自校验的结果。 */
internal sealed interface WebArchiveCheck {

    /** 通过。 */
    data class Ok(
        val version: Int,
        /** 档案里 `stores/` 那一层的相对路径（码点升序）。 */
        val stores: List<String>,
        /** `files/` 那一层里的文件数（本端导出时最多 1 条：自建日程）。 */
        val files: Int,
        /** `manifest.redacted` 的条目数（凭据族没跟过来的那些，UI 只说数量）。 */
        val redacted: Int,
    ) : WebArchiveCheck

    /** 明确失败。 */
    data class Failed(val reason: String) : WebArchiveCheck
}

/** 设置页那两个归档入口动作 + 它的一行结果文本。 */
internal class WebArchiveActions {

    /** 上一次动作的结论（`null` = 还没点过）。 */
    var status: String? by mutableStateOf(null)
        private set

    /** 导出：组装（`localStorage` 投影 → 档案）→ 浏览器下载（同步，落在手势里）。 */
    fun export() {
        status = runCatching { WebArchiveHost.exportToDownload() }
            .getOrElse { "导出失败：${it.message ?: it::class.simpleName}" }
    }

    /** 导入：选 zip → 内存里解 → 自校验 → `StoreArchive.import`（§6.4）→ 结论文本。 */
    fun import() {
        WebArchiveIo.pick { picked -> status = WebArchiveHost.importStatusText(picked) }
    }
}
