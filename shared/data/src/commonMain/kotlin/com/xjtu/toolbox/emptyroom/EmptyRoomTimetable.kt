package com.xjtu.toolbox.emptyroom

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class RoomTimetable(val rooms: List<RoomInfo>, val direct: Boolean, val skippedBuildings: List<String> = emptyList())

/** CDN 失败自动直连，教学楼查询最多同时两路。 */
suspend fun queryRoomTimetable(
    campus: String, buildings: Set<String>, date: String, api: EmptyRoomApi,
    preferDirect: Boolean = false, directQuery: suspend () -> EmptyRoomDirectQuery,
): RoomTimetable = withContext(Dispatchers.IO) {
    if (!preferDirect) {
        try {
            val rooms = api.getEmptyRoomsMulti(campus, buildings, date)
            if (rooms.isNotEmpty()) return@withContext RoomTimetable(rooms, direct = false)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { }
    }
    val query = directQuery()
    query.ensureRoleStudent()
    query.getCampusCodes()
    val codes = query.getBuildingCodes()
    val targets = buildings.filter { it in codes }
    require(targets.isNotEmpty()) { "教务暂无这些楼栋：${buildings.joinToString("、")}" }
    val gate = Semaphore(2)
    val context = currentCoroutineContext()
    val results = targets.map { building -> async {
        gate.withPermit {
            runCatching { query.queryDay(campus, building, date) { _, _ -> context.ensureActive() } }
                .onFailure { if (it is CancellationException) throw it }
        }
    } }.awaitAll()
    if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
    val skipped = buildings.filter { it !in codes } + targets.filterIndexed { i, _ -> results[i].isFailure }
    RoomTimetable(results.flatMap { it.getOrDefault(emptyList()) }.sortedBy { it.name }, direct = true, skipped)
}
