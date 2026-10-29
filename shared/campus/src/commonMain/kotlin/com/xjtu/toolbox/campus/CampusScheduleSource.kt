package com.xjtu.toolbox.campus

import com.xjtu.toolbox.core.net.TermStartData
import com.xjtu.toolbox.schedule.*
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*
import kotlin.time.Clock

/** Native implementation of the existing pure schedule contract. */
internal class CampusScheduleSource(private val session: CampusSession) : JwxtScheduleSource {
    private val names = mutableMapOf<String, String>()
    private val weeks = mutableMapOf<String, Int>()
    private val latestChanges = mutableMapOf<String, List<ScheduleChangeEvent>>()
    private var latestTerm: String? = null
    override fun termNames(): Map<String, String> = names.toMap()
    override fun termWeeksOf(term: String): Int? = weeks[term]
    fun clearAccountState() { names.clear(); weeks.clear(); latestChanges.clear(); latestTerm = null }

    override suspend fun currentTerm(): String {
        val row = postRows("/jwapp/sys/wdkb/modules/jshkcb/dqxnxq.do", "dqxnxq").firstOrNull()
            ?: throw CampusProtocolException("教务未返回当前学期")
        val term = row.string("DM").orEmpty()
        validateTerm(term)
        row.string("MC")?.takeIf(String::isNotBlank)?.let { names[term] = it }
        latestTerm = term
        return term
    }
    override suspend fun schedule(term: String?): SourceSchedule {
        val code = term ?: currentTerm()
        validateTerm(code)
        val rows = postRows("/jwapp/sys/wdkb/modules/xskcb/xskcb.do", "xskcb", "XNXQDM" to code).map { row ->
            JwxtRow(row.string("JXBID").orEmpty(), CourseItem(
                courseName = row.string("KCM").orEmpty(), teacher = row.string("SKJS").orEmpty(),
                location = row.string("JASMC").orEmpty(), weekBits = row.string("SKZC").orEmpty(),
                dayOfWeek = row.int("SKXQ") ?: 1, startSection = row.int("KSJC") ?: 1, endSection = row.int("JSJC") ?: 1,
                courseCode = row.string("KCH").orEmpty(),
                courseType = row.string("KCXZMC") ?: row.string("KCXZDM_DISPLAY") ?: row.string("KCFLMC").orEmpty(),
            ))
        }
        val changes = postRows("/jwapp/sys/wdkb/modules/xskcb/xsdkkc.do", "xsdkkc", "XNXQDM" to code).mapNotNull(JwxtChange::of)
        return JwxtChanges.apply(rows, changes).also { latestChanges[code] = it.changes }
    }
    override suspend fun changes(term: String): List<ScheduleChangeEvent> = latestChanges[term] ?: schedule(term).changes
    override suspend fun examSchedule(term: String?): List<ExamItem> {
        val code = term ?: currentTerm()
        validateTerm(code)
        return postRows("/jwapp/sys/studentWdksapApp/modules/wdksap/wdksap.do", "wdksap",
            "XNXQDM" to code, "*order" to "-KSRQ,-KSSJMS").map { row ->
            val rawDate = row.string("KSRQ").orEmpty()
            val date = rawDate.substringBefore(' ')
            val time = row.string("KSSJMS").orEmpty().removePrefix(rawDate).removePrefix(date).trimStart('-', ' ')
            ExamItem(row.string("KCM") ?: row.string("KCMC") ?: row.string("KCH").orEmpty(),
                row.string("KCH").orEmpty(), date, time, row.string("JASMC").orEmpty(), row.string("ZWH").orEmpty())
        }
    }
    override suspend fun termStart(term: String): TermStartData {
        validateTerm(term)
        val row = postRows("/jwapp/sys/wdkb/modules/jshkcb/cxjcs.do", "cxjcs",
            "XN" to term.substringBeforeLast('-'), "XQ" to term.substringAfterLast('-')).firstOrNull()
            ?: throw CampusProtocolException("教务未返回学期开始日期")
        val date = row.string("XQKSRQ")?.substringBefore(' ')?.takeIf(String::isNotBlank)
            ?: throw CampusProtocolException("教务未返回学期开始日期")
        val total = row.int("ZZC")?.takeIf { it in 1..TermWeeks.MAX_REASONABLE } ?: 0
        if (total > 0) weeks[term] = total
        return TermStartData(term = term, startDate = date, totalWeeks = total)
    }
    override suspend fun termList(): List<String> {
        try {
            session.apiRequest(CampusSite.SCHEDULE, "https://$JWXT_HOST/jwapp/sys/kcbcx/*default/index.do")
            val rows = postRows("/jwapp/sys/kcbcx/modules/bjkcb/xnxqcx.do", "xnxqcx", "*order" to "-DM")
            val terms = rows.mapNotNull { row ->
                val code = row.string("DM")?.takeIf { TERM.matches(it) } ?: return@mapNotNull null
                row.string("MC")?.takeIf(String::isNotBlank)?.let { names[code] = it }
                code
            }.distinct().sortedDescending().take(24)
            if (terms.isNotEmpty()) return terms
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: CampusProtocolException) { /* UI can still offer term selection while offline/expired. */ }
        val now = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Shanghai")).date
        val current = latestTerm ?: if (now.month.ordinal + 1 >= 9) "${now.year}-${now.year + 1}-1" else "${now.year - 1}-${now.year}-2"
        val parts = current.split('-').map(String::toInt)
        var year = parts[0]; var semester = parts[2]
        return List(8) {
            "$year-${year + 1}-$semester".also { if (semester == 1) { year--; semester = 3 } else semester-- }
        }
    }
    override suspend fun textbooks(studentId: String, term: String?): List<TextbookItem> =
        throw UnsupportedOperationException("iOS 暂不支持帆软教材查询")

    private suspend fun postRows(path: String, module: String, vararg fields: Pair<String, String>): List<JsonObject> {
        val reply = session.apiRequest(CampusSite.SCHEDULE, "https://$JWXT_HOST$path", HttpMethod.Post,
            formBody(*fields), ContentType.Application.FormUrlEncoded)
        val root = parseObject(reply.body)
        val rows = root.obj("datas")?.obj(module)?.array("rows") ?: throw CampusProtocolException("教务课表响应缺少数据")
        return rows.map { it as? JsonObject ?: throw CampusProtocolException("教务课表数据格式无效") }
    }
    private fun validateTerm(term: String) {
        if (!TERM.matches(term)) throw CampusProtocolException("学期代码无效")
    }
    companion object { private val TERM = Regex("\\d{4}-\\d{4}-[123]") }
}
