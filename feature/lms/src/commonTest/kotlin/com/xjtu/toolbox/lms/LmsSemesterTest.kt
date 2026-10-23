package com.xjtu.toolbox.lms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class LmsSemesterTest {
    @Test fun calendarSemesterDoesNotDependOnTheNewestReturnedCourse() {
        assertEquals("2026-2027 秋", currentLmsSemester(LocalDate(2026, 10, 22)))
        assertEquals("2026-2027 秋", currentLmsSemester(LocalDate(2027, 1, 10)))
        assertEquals("2026-2027 春", currentLmsSemester(LocalDate(2027, 3, 10)))
    }
}
