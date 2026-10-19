package com.xjtu.toolbox.desktop.archive

import com.xjtu.toolbox.account.AccountContext
import com.xjtu.toolbox.archive.ArchiveCodec
import com.xjtu.toolbox.archive.ArchiveFileBackend
import com.xjtu.toolbox.archive.ArchiveImportTarget
import com.xjtu.toolbox.archive.ArchiveInspection
import com.xjtu.toolbox.archive.ArchiveManifest
import com.xjtu.toolbox.archive.ArchivePaths
import com.xjtu.toolbox.archive.ArchiveScoreReport
import com.xjtu.toolbox.archive.ArchiveSource
import com.xjtu.toolbox.archive.ArchiveStoreBackend
import com.xjtu.toolbox.archive.ImportResult
import com.xjtu.toolbox.archive.StoreArchive
import com.xjtu.toolbox.archive.StoreSnapshot
import com.xjtu.toolbox.archive.StoreValue
import com.xjtu.toolbox.archive.archiveFileBackend
import com.xjtu.toolbox.archive.archiveSourceOf
import com.xjtu.toolbox.archive.archiveStoreBackend
import com.xjtu.toolbox.desktop.score.DesktopScoreReportCache
import com.xjtu.toolbox.platform.KeyValueStore
import com.xjtu.toolbox.platform.Log
import com.xjtu.toolbox.platform.secureKeyValueStore
import java.io.File

/**
 * 桌面端的导入写侧：`:core` 的 [ArchiveImportTarget] 在 jvm 上的落点。
 * `files/` 里 `agent_sessions<suffix>/` 写成文件，三族行数组并进键值店（[DesktopArchiveRows]），
 * 成绩缓存那一族（[ArchiveScoreReport]）并回它自己的店（`score_report<suffix>` 的 `score_report_<学号>`），
 * 其余不落地。
 *
 * 已知问题：三族的店名按当前账号解析，启动时那次导入还没登录，带后缀的两族会落到 `<店名>default`。
 */
class DesktopArchiveImportTarget(
    /** 写侧：按端内名取那个已有句柄（默认 [secureKeyValueStore]）。 */
    private val storeOf: (String) -> KeyValueStore = { secureKeyValueStore(it) },
    /** 读侧现状（`<数据根>/<name>.properties`）。 */
    private val storeBackend: ArchiveStoreBackend = archiveStoreBackend(),
    /** `files/` 那一层的读侧现状。 */
    private val files: ArchiveFileBackend? = archiveFileBackend(),
    /** 数据根（`<数据根>/agent_sessions<suffix>/…` 的父目录）。 */
    private val dataRoot: File? = archiveDataRootOrNull(),
    /** 墙上时钟（重建旧档案里裸数组的 `at` 用）。 */
    private val now: () -> Long = { System.currentTimeMillis() },
) : ArchiveImportTarget {

    override fun readStore(rawName: String): StoreSnapshot? = storeBackend.readStore(rawName)

    override fun putKey(rawName: String, key: String, value: StoreValue, at: Long?) {
        val store = storeOf(rawName)
        when (value) {
            is StoreValue.S -> store.putString(key, value.v)
            is StoreValue.B -> store.putBoolean(key, value.v)
            is StoreValue.I -> store.putInt(key, value.v)
            is StoreValue.L -> store.putString(key, value.v.toString())
            is StoreValue.F -> store.putString(key, value.v.toString())
            is StoreValue.SetValue -> store.putStringSet(key, value.v.toSet())
        }
        // §5.2 第 5 条：`at` 落在旁挂键上（档案里那份时间戳就是从这里来的）
        if (at != null) store.putString(key + ArchiveCodec.WRITTEN_AT_SUFFIX, at.toString())
    }

    override fun readFile(suffix: String, relPath: String): String? {
        // 成绩缓存那一族在桌面端内是 KV 店（`score_report<suffix>` 的 `score_report_<学号>`）⇒ 现状从店里读：
        // §6.4 文件层那条「本端有 ⇒ 保持不变」对 KV 载体一样成立。
        scoreReportValue(suffix, relPath)?.let { return it }
        return files?.read(suffix, relPath)
    }

    /**
     * 键名归一（§6.3）：档案里那一族的名字**不含学号**，桌面端内名要它（键 `score_report_<学号>`）。
     * 档案里那一档账号先查，没有时用本机当前账号；两边都拼不出 ⇒ null（这一条跳过并计数）。
     */
    override fun fileRelPath(suffix: String, accountId: String?, relPath: String): String? {
        if (!ArchiveScoreReport.isFamily(relPath)) return relPath
        val studentId = ArchiveScoreReport.studentIdOf(suffix, accountId ?: activeAccountId()) ?: return null
        return ArchiveScoreReport.endRelPath(studentId)
    }

    /**
     * 形状归一（§6.3）：桌面端内本来就是 `{"at","grades"}`，旧档案里的裸数组用导入时刻重建 `at`。
     * 转不动 ⇒ null（跳过并计数）。
     */
    override fun fileContent(suffix: String, accountId: String?, relPath: String, content: String): String? =
        if (!ArchiveScoreReport.isFamily(relPath)) content else ArchiveScoreReport.toCanonical(content, now())

    override fun putFile(suffix: String, relPath: String, content: String) {
        // 成绩缓存那一族并回它自己的店（不归键值店 / 也不落成文件）
        if (landScoreReport(suffix, relPath, content)) return
        if (DesktopArchiveRows.land(suffix, relPath, content, storeOf = storeOf) != null) return
        val root = dataRoot ?: return
        if (!relPath.startsWith(AGENT_SESSIONS_PREFIX)) return
        val target = File(File(root, "agent_sessions$suffix"), relPath.removePrefix(AGENT_SESSIONS_PREFIX))
        DesktopArchiveIo.writeFile(target, content.toByteArray(Charsets.UTF_8))
    }

    /** 本机当前账号（没登录 / 取不到 ⇒ null）。 */
    private fun activeAccountId(): String? =
        runCatching { AccountContext.activeAccountId }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * 成绩缓存那一族在桌面端内所在的店名：`score_report` + 账号后缀（`DesktopScoreReportCache.storeName(accountId)`
     * 的同一个口径；档案里的 suffix 与 `AccountContext.suffixFor` 同一种写法）。
     */
    private fun scoreReportStoreName(suffix: String): String = DesktopScoreReportCache.STORE_NAME + suffix

    /** 这一条是成绩缓存那一族时给出端内键（`score_report_<学号>`）；不是 / 名字里没有学号（规范名）⇒ null。 */
    private fun scoreReportKey(relPath: String): String? =
        if (ArchiveScoreReport.isFamily(relPath)) {
            ArchiveScoreReport.legacyStudentId(relPath)?.let { DesktopScoreReportCache.keyFor(it) }
        } else {
            null
        }

    /** 端内店里那一族的现状（§6.4：本端有 ⇒ 保持不变）。 */
    private fun scoreReportValue(suffix: String, relPath: String): String? {
        val key = scoreReportKey(relPath) ?: return null
        return runCatching { storeOf(scoreReportStoreName(suffix)).getString(key) }.getOrNull()
    }

    /** 把档案里那一族写回它自己的店；不是这一族 ⇒ false（交给别的落点）。 */
    private fun landScoreReport(suffix: String, relPath: String, content: String): Boolean {
        val key = scoreReportKey(relPath) ?: return false
        storeOf(scoreReportStoreName(suffix)).putString(key, content)
        return true
    }


    private companion object {
        /** 桌面 `files/` 里唯一落成文件的一族。 */
        const val AGENT_SESSIONS_PREFIX = "agent_sessions/"
    }
}

/** 桌面端的一次性导入编排：自校验 → §6.4 合并 → 落「已导入」标记。 */
object DesktopArchiveImport {

    /** 标记文件名（档案目录里的一份点文件；[DesktopArchiveIo.readDirectory] 读档案时会跳过点文件）。 */
    const val MARKER_NAME = ".imported"

    private const val MARKER_SUFFIX = ".imported"
    private const val TAG = "DesktopArchive"

    /** 那一份档案的标记文件（目录形态写进目录里，zip 形态写它旁边）。 */
    fun markerFileFor(archive: File): File =
        if (archive.isDirectory) {
            File(archive, MARKER_NAME)
        } else {
            File(archive.parentFile, archive.name + MARKER_SUFFIX)
        }

    /** 档案的身份（§2.1）。 */
    fun identityOf(manifest: ArchiveManifest): String =
        "${manifest.createdAt}|${manifest.createdBy}|${manifest.version}"

    /** 标记里记着的身份（第一行）；没有标记 / 读不出来 ⇒ null。 */
    fun readMarkerIdentity(marker: File): String? =
        if (!marker.isFile) null
        else runCatching { marker.readText().lineSequence().firstOrNull() }.getOrNull()

    /** 跑一次导入（自校验 → §6.4 合并 → 标记）。 */
    fun run(
        archive: File,
        entries: Map<String, String>,
        target: ArchiveImportTarget = DesktopArchiveImportTarget(),
    ): DesktopImportOutcome = run(archive, archiveSourceOf(entries), target)

    /** 同上，但调用方已经有一个 [ArchiveSource]（[DesktopArchiveHost.checkArchive] 走的就是这一条）。 */
    fun run(
        archive: File,
        source: ArchiveSource,
        target: ArchiveImportTarget,
    ): DesktopImportOutcome {
        val validated = StoreArchive.inspect(source)
        if (validated is ArchiveInspection.Failed) return DesktopImportOutcome.Failed(validated.reason)
        val manifest = (validated as ArchiveInspection.Ok).manifest

        val marker = markerFileFor(archive)
        val identity = identityOf(manifest)
        if (readMarkerIdentity(marker) == identity) {
            return DesktopImportOutcome.AlreadyImported(archive, identity)
        }

        return when (val result = StoreArchive.import(source, target)) {
            is ImportResult.Failed -> DesktopImportOutcome.Failed(result.reason)
            is ImportResult.Ok -> {
                writeMarker(marker, identity, result)
                Log.i(
                    TAG,
                    "导入 ${archive.name}：:core 的 StoreArchive.import 写入 ${result.keysWritten} 个键 /" +
                        " 保持 ${result.keysKept} 个 / 写回 ${result.filesWritten} 个文件" +
                        (if (result.filesSkipped > 0) " / 跳过 ${result.filesSkipped} 条" else ""),
                )
                DesktopImportOutcome.Imported(archive, identity, result)
            }
        }
    }

    /** 标记：第一行是身份，其余是这一次落地的计数（给人看的，不是判据）。 */
    private fun writeMarker(marker: File, identity: String, result: ImportResult.Ok) {
        marker.parentFile?.mkdirs()
        marker.writeText(
            buildString {
                appendLine(identity)
                appendLine("keysWritten=${result.keysWritten}")
                appendLine("keysKept=${result.keysKept}")
                appendLine("filesWritten=${result.filesWritten}")
                appendLine("filesSkipped=${result.filesSkipped}")
            },
        )
    }
}

/** 一次导入的结论。 */
sealed interface DesktopImportOutcome {

    /** 真的导了：写了几笔见 [result]。 */
    data class Imported(val archive: File, val identity: String, val result: ImportResult.Ok) : DesktopImportOutcome

    /** 这份档案的身份与标记一致 ⇒ 什么都不做（§6.1 第 1 条：导入幂等）。 */
    data class AlreadyImported(val archive: File, val identity: String) : DesktopImportOutcome

    /** 明确失败，端内零改动。 */
    data class Failed(val reason: String) : DesktopImportOutcome
}

/** 数据根（这一端恒非 null；包一层 runCatching 只是为了不在测试环境里炸）。 */
private fun archiveDataRootOrNull(): File? = runCatching { File(DesktopArchiveHost.dataRoot().path) }.getOrNull()

