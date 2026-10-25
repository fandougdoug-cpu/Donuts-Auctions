package de.lukas.donutsauctions.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InvestmentCalculatorTest {
    @Test
    fun `price estimate reports trend and simple profit without claiming probability`() {
        val estimate = InvestmentCalculator.estimatePrice(
            recentMedian = 120_000,
            historicalMedian = 100_000,
            purchasePrice = 90_000,
            sampleCount = 12
        )
        assertEquals(InvestmentCalculator.Trend.UP, estimate.trend)
        assertEquals(120_000L, estimate.expectedSellPrice)
        assertEquals(30_000L, estimate.expectedProfit)
    }

    @Test
    fun `price estimate requires enough history`() {
        val estimate = InvestmentCalculator.estimatePrice(100, 90, 50, 1)
        assertEquals(InvestmentCalculator.Trend.INSUFFICIENT_DATA, estimate.trend)
    }

    @Test
    fun `spawner estimate calculates gross revenue net profit and roi`() {
        val estimate = InvestmentCalculator.estimateSpawnerProfit(
            spawnerCount = 10,
            costPerSpawner = 100_000,
            unitsPerSpawnerPerHour = 20.0,
            sellValuePerUnit = 50,
            hours = 5.0
        )!!
        assertEquals(1_000_000L, estimate.purchaseCost)
        assertEquals(50_000L, estimate.grossRevenue)
        assertEquals(-950_000L, estimate.netProfit)
    }

    @Test
    fun `break even is unavailable without positive revenue`() {
        assertNull(InvestmentCalculator.breakEvenHours(1, 100, 0.0, 50))
    }
}
