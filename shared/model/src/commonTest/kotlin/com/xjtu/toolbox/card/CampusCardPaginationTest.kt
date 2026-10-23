package com.xjtu.toolbox.card

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class CampusCardPaginationTest {
    private val start = LocalDate(2025, 10, 22)
    private val end = LocalDate(2026, 10, 22)

    @Test fun annualHistoryContinuesBeyondTwelvePages() = runTest {
        val source = Pages(707)
        val result = source.allTransactions(start, end, maxPages = 400)
        assertEquals(707, result.size)
        assertEquals(707, result.map { it.uniqueKey() }.toSet().size)
    }

    @Test fun pageLimitCannotLabelTruncatedHistoryComplete() = runTest {
        assertFailsWith<RuntimeException> { Pages(707).allTransactions(start, end, maxPages = 12) }
        assertFailsWith<RuntimeException> { Pages(80).allTransactions(start, end, maxPages = 1) }
    }

    @Test fun incrementalHistoryCannotSilentlyLeaveAnUnfilledGap() = runTest {
        assertFailsWith<RuntimeException> {
            Pages(707).transactionsUntilKnown(start, end, maxPages = 12) { false }
        }
        val result = Pages(707).transactionsUntilKnown(start, end, maxPages = 400) { it.description == "650" }
        assertEquals(700, result.size)
    }

    private class Pages(private val total: Int) : CampusCardSource {
        override suspend fun card() = CardInfo()
        override suspend fun transactions(from: LocalDate, to: LocalDate, page: Int, pageSize: Int): Pair<Int, List<Transaction>> =
            total to ((page - 1) * pageSize until minOf(page * pageSize, total)).map { index ->
                Transaction(time = "record-$index", amount = -1.0, description = "$index")
            }
        override suspend fun snapshot(from: LocalDate, to: LocalDate, accountId: String?): CampusCardSnapshot? = null
        override suspend fun persistCard(card: CardInfo, accountId: String?) = Unit
        override suspend fun persist(card: CardInfo, transactions: List<Transaction>, from: LocalDate, to: LocalDate, accountId: String?) = Unit
    }
}
