package com.xjtu.toolbox.agent

import kotlinx.serialization.json.*

/** 查询条件与展示条件共用同一口径，避免模型传了节次却查询实时状态。 */
data class AgentRoomRequest(val view: String, val sections: List<Int>, val minimum: Int)

fun agentRoomRequest(args: JsonObject): AgentRoomRequest {
    val display = args["display"] as? JsonObject
    fun value(key: String) = args[key] ?: display?.get(key)
    val sections = value("sections")?.let { raw ->
        require(raw is JsonArray) { "sections 须为节次数组" }
        raw.map { (it as? JsonPrimitive)?.intOrNull?.also { n -> require(n in 1..11) { "节次须为 1–11" } }
            ?: error("sections 须为节次数组") }.distinct()
    }.orEmpty()
    val minimum = value("min_free_sections")?.let {
        (it as? JsonPrimitive)?.intOrNull?.also { n -> require(n in 1..11) { "min_free_sections 须为 1–11" } }
            ?: error("min_free_sections 须为整数")
    } ?: 1
    val fields = display?.get("fields") as? JsonArray
    args["section"]?.let { raw -> require(raw.jsonPrimitive.intOrNull in 1..11) { "section 须为 1–11" } }
    val needsSlots = sections.isNotEmpty() || args["section"] != null || value("min_free_sections") != null || fields?.any { it.jsonPrimitive.content == "slots" } == true
    val needsLive = display?.get("statuses") != null || fields?.any { it.jsonPrimitive.content == "status" } == true
    val view = value("view")?.jsonPrimitive?.content ?: "auto"
    require(view in setOf("auto", "slots", "status", "both")) { "view 可选 auto/slots/status/both" }
    require(view != "status" || !needsSlots) { "节次条件请使用 view=slots/both" }
    return AgentRoomRequest(if (view == "auto" && needsSlots) { if (needsLive) "both" else "slots" } else view, sections, minimum)
}
