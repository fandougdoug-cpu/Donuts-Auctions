package de.lukas.donutsauctions.analytics

import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Conservative helpers for estimating in-game item and spawner economics.
 * Estimates are descriptive only; they do not guarantee future prices.
 */
object InvestmentCalculator {
    enum class Trend { UP, DOWN, FLAT, INSUFFICIENT_DATA }

    data class PriceEstimate(
        val trend: Trend,
        val changePercent: Double?,
        val expectedSellPrice: Long?,
        val expectedProfit: Long?,
        val sampleCount: Int
    )

    data class SpawnerEstimate(
        val purchaseCost: Long,
        val sellValuePerUnit: Long,
        val unitsPerHour: Double,
        val hours: Double,
        val grossRevenue: Long,
        val netProfit: Long,
        val roiPercent: Double
    )

    /**
     * Compares recent and historical medians. It deliberately does not call this a
     * probability: historical prices are not enough to produce a calibrated chance.
     */
    fun estimatePrice(
        recentMedian: Long,
        historicalMedian: Long,
        purchasePrice: Long,
        sampleCount: Int
    ): PriceEstimate {
        if (recentMedian <= 0 || historicalMedian <= 0 || purchasePrice < 0 || sampleCount < 2) {
            return PriceEstimate(Trend.INSUFFICIENT_DATA, null, null, null, sampleCount)
        }

        val change = (recentMedian - historicalMedian).toDouble() / historicalMedian * 100.0
        val trend = when {
            change > 2.0 -> Trend.UP
            change < -2.0 -> Trend.DOWN
            else -> Trend.FLAT
        }
        return PriceEstimate(
            trend = trend,
            changePercent = change,
            expectedSellPrice = recentMedian,
            expectedProfit = recentMedian - purchasePrice,
            sampleCount = sampleCount
        )
    }

    fun estimateSpawnerProfit(
        spawnerCount: Int,
        costPerSpawner: Long,
        unitsPerSpawnerPerHour: Double,
        sellValuePerUnit: Long,
        hours: Double
    ): SpawnerEstimate? {
        if (spawnerCount <= 0 || costPerSpawner < 0 || unitsPerSpawnerPerHour < 0.0 ||
            sellValuePerUnit < 0 || hours < 0.0 ||
            !unitsPerSpawnerPerHour.isFinite() || !hours.isFinite()
        ) return null

        val cost = spawnerCount.toLong() * costPerSpawner
        val units = spawnerCount * unitsPerSpawnerPerHour * hours
        val revenue = (units * sellValuePerUnit).roundToLong()
        val profit = revenue - cost
        val roi = if (cost == 0L) 0.0 else profit.toDouble() / cost * 100.0

        return SpawnerEstimate(
            purchaseCost = cost,
            sellValuePerUnit = sellValuePerUnit,
            unitsPerHour = spawnerCount * unitsPerSpawnerPerHour,
            hours = hours,
            grossRevenue = revenue,
            netProfit = profit,
            roiPercent = roi
        )
    }

    fun breakEvenHours(
        spawnerCount: Int,
        costPerSpawner: Long,
        unitsPerSpawnerPerHour: Double,
        sellValuePerUnit: Long
    ): Double? {
        if (spawnerCount <= 0 || costPerSpawner < 0 || unitsPerSpawnerPerHour <= 0.0 ||
            sellValuePerUnit <= 0 || !unitsPerSpawnerPerHour.isFinite()
        ) return null
        val revenuePerHour = spawnerCount * unitsPerSpawnerPerHour * sellValuePerUnit
        if (revenuePerHour <= 0.0) return null
        return (spawnerCount.toDouble() * costPerSpawner) / revenuePerHour
    }

    fun underpricingPercent(currentPrice: Long, referenceMedian: Long): Double? {
        if (currentPrice < 0 || referenceMedian <= 0) return null
        return max(0.0, (referenceMedian - currentPrice).toDouble() / referenceMedian * 100.0)
    }
}
