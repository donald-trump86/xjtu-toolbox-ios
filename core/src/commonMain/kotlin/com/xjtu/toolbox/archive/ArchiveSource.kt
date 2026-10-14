package com.xjtu.toolbox.archive

/**
 * 档案的**传输缝**（§1.4）：目录 / zip / SAF / 下载 都只露这两个窄接口，
 * 具体实现各端自定（S4 Android、S5 桌面、S6 web）—— **不在 `:core`**。
 *
 * `name` / `rel` 都是**档案内相对路径**（`manifest.json` 除外）：
 *  - store：`global/stores/<base>.json` 或 `accounts/<suffix>/stores/<base>.json`；
 *  - file：`global/files/<rel>` 或 `accounts/<suffix>/files/<rel>`（`rel` = `files/` 之下的路径）。
 *
 * 这样 A3（字节稳定）与 A7（坏档案不动端）能在**没有真文件系统**的环境里跑
 * （`:core:jvmTest` 用内存实现），而 SAF / zip / 目录这些端内细节不进共享层。
 */
interface ArchiveSource {
    fun readManifest(): String?
    fun storeNames(): List<String>
    fun readStore(name: String): String?
    fun filePaths(): List<String>
    fun readFile(rel: String): String?
}

interface ArchiveSink {
    fun writeManifest(json: String)
    fun writeStore(name: String, json: String)
    fun writeFile(rel: String, content: String)
}
