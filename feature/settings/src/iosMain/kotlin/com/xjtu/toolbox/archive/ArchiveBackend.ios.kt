package com.xjtu.toolbox.archive

/** iOS archive export is P2; callers must hide its entry until secure projection is available. */
actual fun archiveFileBackend(): ArchiveFileBackend? = null
actual fun archiveCacheRoot(): String? = null

actual fun archiveStoreBackend(): ArchiveStoreBackend = UnavailableIosArchiveStore
actual fun archiveDataRoot(): String? = null

private object UnavailableIosArchiveStore : ArchiveStoreBackend {
    private fun unavailable(): Nothing = throw UnsupportedOperationException("Archive export is not available on iOS")
    override fun storeNames(): List<String> = unavailable()
    override fun readStore(rawName: String): StoreSnapshot? = unavailable()
    override fun excludedNames(): List<String> = unavailable()
}
