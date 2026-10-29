package com.xjtu.toolbox.campus

import com.xjtu.toolbox.jwapp.*
import com.xjtu.toolbox.score.ScoreCalculator
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.*

internal class CampusScoreSource(private val session: CampusSession) : JwappScoreSource {
    override suspend fun getGrade(termCode: String?): List<TermScore> {
        val account = session.state.value.accountId
        val cache = session.scoreCache
        val root = request("termScore", buildJsonObject { put("termCode", termCode ?: "*") })
        if (root.int("code") != 200) throw CampusProtocolException("移动教务成绩查询失败")
        val terms = root.obj("data")?.array("termScoreList") ?: throw CampusProtocolException("移动教务成绩响应缺少数据")
        val result = terms.map { item ->
            val term = item as? JsonObject ?: throw CampusProtocolException("移动教务学期格式无效")
            val scores = term.array("scoreList") ?: throw CampusProtocolException("移动教务成绩列表格式无效")
            TermScore(term.string("termCode").orEmpty(), term.string("termName").orEmpty(), scores.map { score ->
                val row = score as? JsonObject ?: throw CampusProtocolException("移动教务成绩格式无效")
                val raw = row.string("score").orEmpty()
                val name = row.string("courseName").orEmpty()
                ScoreItem(
                    id = row.string("id").orEmpty(), termCode = row.string("termCode") ?: term.string("termCode").orEmpty(),
                    courseName = name, score = raw, scoreValue = raw.toDoubleOrNull(), passFlag = row.bool("passFlag"),
                    specificReason = row.string("specificReason"), coursePoint = row.string("coursePoint")?.toDoubleOrNull() ?: 0.0,
                    examType = row.string("examType").orEmpty(), majorFlag = row.string("majorFlag"), examProp = row.string("examProp").orEmpty(),
                    replaceFlag = row.bool("replaceFlag"), gpa = row.string("gpa")?.toDoubleOrNull(),
                    courseCode = Regex("\\(([A-Z]{2,}\\d{4,}\\w*)\\)$").find(name.trim())?.groupValues?.get(1),
                )
            })
        }
        if (termCode == null && session.state.value.accountId == account) cache.write(result)
        return result
    }
    override suspend fun getScoreDetail(courseId: String): ScoreDetail {
        val root = request("scoreDetail", buildJsonObject { put("id", courseId) })
        val code = root.int("code")
        val data = root.obj("data")
        if (code != 200 || data == null) {
            if (code in setOf(200, 401, 404) || isNoScoreDetailMessage(root.string("msg"))) throw NoScoreDetailException()
            throw CampusProtocolException("移动教务成绩明细查询失败")
        }
        val score = data.string("score").orEmpty()
        val serverGpa = data.string("gpa")?.toDoubleOrNull() ?: 0.0
        return ScoreDetail(
            courseName = data.string("courseName").orEmpty(), coursePoint = data.string("coursePoint")?.toDoubleOrNull() ?: 0.0,
            examType = data.string("examType").orEmpty(), majorFlag = data.string("majorFlag"), examProp = data.string("examProp").orEmpty(),
            replaceFlag = data.bool("replaceFlag"), score = score, scoreValue = score.toDoubleOrNull(),
            gpa = serverGpa.takeIf { it > 0.0 } ?: ScoreCalculator.scoreToGpa(score) ?: 0.0,
            passFlag = data.bool("passFlag"), specificReason = data.string("specificReason"),
            itemList = data.array("itemList").orEmpty().map { element ->
                val item = element as? JsonObject ?: throw CampusProtocolException("移动教务成绩明细格式无效")
                val raw = item.string("itemScore").orEmpty()
                ScoreDetailItem(item.string("itemName").orEmpty(),
                    item.string("itemPercent")?.trim()?.trimEnd('%')?.toDoubleOrNull()?.div(100.0) ?: 0.0,
                    raw, raw.toDoubleOrNull())
            },
        )
    }
    override suspend fun reAuthenticate() {
        val state = session.reAuthenticate(userInitiated = true)
        when (state.status) {
            CampusStatus.AUTHENTICATED -> Unit
            CampusStatus.SMS_REQUIRED -> throw CampusMfaRequiredException()
            else -> throw CampusSessionExpiredException()
        }
    }
    private suspend fun request(path: String, data: JsonObject): JsonObject = parseObject(session.apiRequest(
        CampusSite.SCORES, "https://$JWAPP_HOST/api/biz/v410/score/$path", HttpMethod.Post, data.toString(), ContentType.Application.Json,
    ).body)
}
