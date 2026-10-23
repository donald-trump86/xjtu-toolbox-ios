package com.xjtu.toolbox.campusmap

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CampusMapTest {

    @Test
    fun `polyline 按 Google 的样例解码`() {
        val pts = decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453), pts)
    }

    @Test
    fun `课表里的楼名与教室号认得出，地图上没有的如实找不到`() {
        assertEquals("主楼A座", CampusMaps.find("主楼A-403")?.second?.name)
        assertEquals("东一楼", CampusMaps.find("东1东-303")?.second?.name)
        assertEquals("中心二楼", CampusMaps.find("中2-2314")?.second?.name)
        assertEquals("兴庆校区", CampusMaps.find("梧桐苑")?.first?.campus)
        assertEquals("创新港校区", CampusMaps.find("和鸣苑")?.first?.campus)
        assertEquals("敏行楼", CampusMaps.find("1号巨构-2301")?.second?.name)
        assertEquals("外国语学院", CampusMaps.find("外文楼A-101")?.second?.name)
        assertEquals("一附院科教楼", CampusMaps.find("一附院科教楼", "雁塔校区")?.second?.name)
        assertEquals("高等工程教育博物馆", CampusMaps.find("工程博物馆-创新港")?.second?.name)
        assertNull(CampusMaps.find("9号巨构"))
    }

    @Test
    fun `同一个楼名在不同校区指不同的地方`() {
        assertEquals("钱学森图书馆", CampusMaps.find("图书馆")?.second?.name)
        assertEquals("创新港图书资料中心", CampusMaps.find("图书馆", "创新港校区")?.second?.name)
    }

    @Test
    fun `主楼到梧桐苑走几分钟，数量级对`() {
        val a = CampusMaps.find("主楼A座")!!.second
        val b = CampusMaps.find("梧桐苑")!!.second
        val min = CampusMaps.walkMinutes(a, b)
        assertTrue(min in 3..10, "主楼A座 → 梧桐苑 算出 $min 分钟")
        assertEquals("西", CampusMaps.bearing(a, b))
    }

    @Test
    fun `每个校区都有边界和地点，轮廓都解得开`() {
        assertEquals(listOf("兴庆校区", "雁塔校区", "曲江校区", "创新港校区"), CampusMaps.all.map { it.campus })
        CampusMaps.all.forEach { m ->
            assertTrue(m.places.isNotEmpty(), m.campus)
            assertTrue(m.boundary.all { decodePolyline(it).size >= 3 }, m.campus)
            m.places.filter { it.outline.isNotEmpty() }.forEach { assertTrue(decodePolyline(it.outline).size >= 2, it.name) }
        }
    }

    @Test
    fun `campus_map 工具：路线与附近`() = runTest {
        val out = CampusMapAgentTools().execute("campus_map", """{"from":"主楼A-403","to":"钱学森图书馆","near":"canteen"}""")
        assertTrue(out.contains("到 钱学森图书馆：步行约"), out)
        assertTrue(out.contains("康桥苑") || out.contains("梧桐苑"), out)
        assertNotNull(Regex("error: not_found; place: 9号巨构").find(CampusMapAgentTools().execute("campus_map", """{"from":"9号巨构"}""")))
    }
}
