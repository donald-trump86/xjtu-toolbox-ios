package com.xjtu.toolbox.campusmap

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 校区简图与地点：数据由 OpenStreetMap 生成（[GENERATED_CAMPUS_MAPS]，© OpenStreetMap contributors，ODbL 1.0），
 * 打包在 App 里，不调任何地图接口。
 */
enum class PlaceKind(val label: String) {
    BUILDING("楼"), CANTEEN("食堂"), LIBRARY("图书馆"), DORM("宿舍"), GATE("校门"),
    SPORT("运动场"), SHOP("商店"), LANDMARK("景点"), OTHER("其他"),
}

class CampusPlace(
    val name: String,
    val kind: PlaceKind,
    val lat: Double,
    val lon: Double,
    /** 轮廓（Google polyline 编码，精度 1e-5）；空 = 只有一个点。 */
    val outline: String = "",
)

class CampusMapData(
    val campus: String,
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
    /** 校区边界（可能有多圈）。 */
    val boundary: List<String>,
    val places: List<CampusPlace>,
    /** 校内道路与步道。 */
    val paths: List<String>,
)

object CampusMaps {

    /** OSM 上没有、但坐标查得实的地点（WGS-84），按校区并进 [all]。 */
    private val SUPPLEMENTS = mapOf(
        // OSM way 1411003625「科教楼」（雁塔西路 277 号，一附院院内），在校区边界外，生成时被滤掉
        "雁塔校区" to listOf(CampusPlace("一附院科教楼", PlaceKind.BUILDING, 34.22206, 108.93377)),
    )

    val all: List<CampusMapData> by lazy {
        GENERATED_CAMPUS_MAPS.map { m ->
            val extra = SUPPLEMENTS[m.campus] ?: return@map m
            CampusMapData(m.campus, m.minLat, m.minLon, m.maxLat, m.maxLon, m.boundary, m.places + extra, m.paths)
        }
    }

    const val ATTRIBUTION = "地图数据 © OpenStreetMap 贡献者（ODbL）"

    fun of(campus: String): CampusMapData? = all.firstOrNull { campus.contains(it.campus.removeSuffix("校区")) }

    /**
     * 课表、空教室里的楼名 → OSM 上的叫法。只收能对上的；对不上（OSM 没标、没名字）的不在这里，查不到就如实说不知道。
     */
    private val ALIASES = mapOf(
        "主楼A" to "主楼A座", "主楼B" to "主楼B座", "主楼C" to "主楼C座", "主楼D" to "主楼D座", "主楼E" to "主楼E座",
        "中1" to "中心一楼", "中2" to "中心二楼", "中3" to "中心三楼",
        "东1东" to "东一楼", "东1西" to "东一楼", "东1" to "东一楼", "东2" to "东二楼", "东3" to "东三楼",
        "西2东" to "西二楼", "西2西" to "西二楼", "西2" to "西二楼",
        "教1楼" to "教一楼", "教2楼" to "教二楼", "教一" to "教一楼", "教二" to "教二楼",
        "工程坊A区" to "工程坊", "计教中心" to "计算机教学实验中心",
        "图书馆" to "钱学森图书馆", "钱图" to "钱学森图书馆",
        "外文楼A" to "外国语学院", "外文楼B" to "外国语学院", "外文楼" to "外国语学院",
        "创新港图书馆" to "创新港图书资料中心",
    )

    /** 只在某个校区成立的别名，压过 [ALIASES]。创新港巨构编号 ↔ 楼名见创新港入学指南。 */
    private val CAMPUS_ALIASES = mapOf(
        "创新港校区" to mapOf(
            "1号巨构" to "敏行楼", "2号巨构" to "力行楼", "3号巨构" to "躬行楼", "4号巨构" to "泓理楼",
            "5号巨构" to "涵英楼", "20号巨构" to "泓德楼",
            "图书馆" to "创新港图书资料中心", "工程博物馆" to "高等工程教育博物馆",
        ),
    )

    /** 在 [campus]（缺省全部校区）里找一个地点：先认别名，再全名，再「包含」。 */
    fun find(query: String, campus: String? = null): Pair<CampusMapData, CampusPlace>? {
        val q = buildingOf(query)
        val maps = campus?.let { c -> listOfNotNull(of(c)) }?.ifEmpty { null } ?: all
        for (pass in 0..2) {
            for (m in maps) {
                val target = CAMPUS_ALIASES[m.campus]?.get(q) ?: ALIASES[q] ?: q
                val hit = m.places.firstOrNull { p ->
                    when (pass) {
                        0 -> p.name == target
                        1 -> p.name.endsWith(target) || p.name.substringAfterLast(' ') == target
                        else -> target.length >= 2 && (p.name.contains(target) || target.contains(p.name))
                    }
                }
                if (hit != null) return m to hit
            }
        }
        return null
    }

    /** 「主楼A-403」「西2东-1203」→ 楼名；本来就是楼名的原样返回。 */
    fun buildingOf(location: String): String =
        location.trim().substringBefore('-').substringBefore('－').trim().ifBlank { location.trim() }

    /** 两点的直线距离（米）。 */
    fun distanceMeters(a: CampusPlace, b: CampusPlace): Double {
        val dLat = (a.lat - b.lat) * 111_320.0
        val dLon = (a.lon - b.lon) * 111_320.0 * cos((a.lat + b.lat) / 2 * PI / 180)
        return sqrt(dLat * dLat + dLon * dLon)
    }

    /** 步行分钟：直线 × 1.3（校内路网的绕行）÷ 每分钟 75 米，至少 1 分钟。 */
    fun walkMinutes(a: CampusPlace, b: CampusPlace): Int =
        (distanceMeters(a, b) * WALK_DETOUR / WALK_METERS_PER_MIN).roundToInt().coerceAtLeast(1)

    /** [from] 附近最近的 [kind] 地点，按步行分钟排。 */
    fun nearest(from: CampusPlace, map: CampusMapData, kind: PlaceKind, count: Int = 3): List<Pair<CampusPlace, Int>> =
        map.places.filter { it.kind == kind && it !== from }
            .map { it to walkMinutes(from, it) }
            .sortedBy { it.second }
            .take(count)

    /**
     * 坐标（WGS-84，手机 GPS 原样给的就是）落在哪个校区、离哪栋楼最近。
     * 不在任何校区附近（外扩约 300 米）返回 null。
     */
    fun locate(lat: Double, lon: Double): Pair<CampusMapData, Pair<CampusPlace, Int>>? {
        val here = CampusPlace("当前位置", PlaceKind.OTHER, lat, lon)
        val pad = 0.003
        val map = all.firstOrNull { m ->
            lat in (m.minLat - pad)..(m.maxLat + pad) && lon in (m.minLon - pad)..(m.maxLon + pad)
        } ?: return null
        val nearest = map.places
            .filter { it.kind in setOf(PlaceKind.BUILDING, PlaceKind.LIBRARY, PlaceKind.CANTEEN, PlaceKind.DORM, PlaceKind.GATE) }
            .minByOrNull { distanceMeters(here, it) } ?: return null
        return map to (nearest to distanceMeters(here, nearest).toInt())
    }

    /** 方位：从 [a] 看 [b] 在哪个方向（「东北」）。 */
    fun bearing(a: CampusPlace, b: CampusPlace): String {
        val dy = b.lat - a.lat
        val dx = (b.lon - a.lon) * cos(a.lat * PI / 180)
        val angle = kotlin.math.atan2(dy, dx) * 180 / PI
        return when {
            angle >= 157.5 || angle < -157.5 -> "西"
            angle >= 112.5 -> "西北"
            angle >= 67.5 -> "北"
            angle >= 22.5 -> "东北"
            angle >= -22.5 -> "东"
            angle >= -67.5 -> "东南"
            angle >= -112.5 -> "南"
            else -> "西南"
        }
    }

    private const val WALK_DETOUR = 1.3
    private const val WALK_METERS_PER_MIN = 75.0
}

/** 解 Google polyline（精度 1e-5）成 (lat, lon) 列表。 */
fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
    val out = ArrayList<Pair<Double, Double>>()
    var i = 0
    var lat = 0
    var lon = 0
    fun next(): Int {
        var shift = 0
        var result = 0
        while (i < encoded.length) {
            val b = encoded[i++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
            if (b < 0x20) break
        }
        return if (result and 1 != 0) (result shr 1).inv() else result shr 1
    }
    while (i < encoded.length) {
        lat += next()
        lon += next()
        out += lat / 1e5 to lon / 1e5
    }
    return out
}
