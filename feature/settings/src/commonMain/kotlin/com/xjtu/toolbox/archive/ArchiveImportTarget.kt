package com.xjtu.toolbox.archive

/** 导入侧的端内写入缝（§1.4 缝 A 的写半边）。 */
interface ArchiveImportTarget {

    /** 本端某个端内 store 的现状（含旁挂时间戳）；端上没有它 ⇒ null。 */
    fun readStore(rawName: String): StoreSnapshot?

    /** 把一个键写进端内 store（[rawName] = 端内名，含后缀）。 */
    fun putKey(rawName: String, key: String, value: StoreValue, at: Long?)

    /** 本端 `files/` 里这个相对路径的现状；不存在 ⇒ null。 */
    fun readFile(suffix: String, relPath: String): String?

    /** 写一个 `files/` 条目（相对路径 = `files/` 之下的路径，目录名不带 suffix，§2.3）。 */
    fun putFile(suffix: String, relPath: String, content: String)

    /**
     * **键名归一**（§6.3 逐族迁移）：档案里这条 `files/` 相对路径在本端要落成的端内相对路径；默认原样。
     *
     * [accountId] = `accounts/<suffix>` 那一档在 `manifest.accounts` 里的账号（`global/` 下 ⇒ null）：
     * 端内名要学号的那一族（[ArchiveScoreReport]）靠它把学号拼回去，而学号**不进档案路径**。
     *
     * 返回 null = 本端拼不出这个端内名（档案里没那一档账号、本端也没登录）⇒ 这一条跳过并计入
     * [ImportResult.Ok.filesSkipped]，整次导入照常跑完（§8.6：绝不抛）。
     */
    fun fileRelPath(suffix: String, accountId: String?, relPath: String): String? = relPath

    /**
     * **形状归一**（§6.3 逐族迁移）：档案里的字节 → 本端要写的字节；默认原样。null = 转不动 ⇒ 跳过并计数。
     *
     * [relPath] 是**档案里**那条路径（族按它认出来），[content] 是档案里那份字节；
     * 落地路径用 [fileRelPath] 算出来的那个。
     */
    fun fileContent(suffix: String, accountId: String?, relPath: String, content: String): String? = content
}
