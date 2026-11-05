package com.apex.app.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlateLoadingCalculatorTest {
    @Test fun parsesFractionalWeightsWithoutRounding() {
        assertEquals(1250, PlateLoadingCalculator.parseWeight("1.25"))
        assertEquals(1250, PlateLoadingCalculator.parseWeight("1,250"))
        assertNull(PlateLoadingCalculator.parseWeight("1.0001"))
        assertNull(PlateLoadingCalculator.parseWeight("-5"))
    }
    @Test fun exactAndNearestNeverExceedTarget() {
        val plates = listOf(PlateInventory(20_000, 4), PlateInventory(5_000, 2))
        val exact = PlateLoadingCalculator.calculate(100_000, 20_000, plates)
        assertTrue(exact.isExact)
        assertEquals(listOf(PlatePerSide(20_000, 2)), exact.perSide)
        val near = PlateLoadingCalculator.calculate(100_001, 20_000, plates)
        assertFalse(near.isExact)
        assertEquals(100_000, near.actualMilli)
        assertEquals(1, near.shortfallMilli)
    }
    @Test fun unpairedAndInsufficientPlatesRemainUnused() {
        val value = PlateLoadingCalculator.calculate(80_000, 20_000, listOf(PlateInventory(20_000, 3)))
        assertEquals(60_000, value.actualMilli)
        assertEquals(listOf(PlatePerSide(20_000, 1)), value.perSide)
    }
    @Test fun combinesDuplicateSizesAndAllowsZeroInventory() {
        val duplicate = PlateLoadingCalculator.calculate(40_000, 20_000, listOf(PlateInventory(5_000, 3), PlateInventory(5_000, 1)))
        assertEquals(40_000, duplicate.actualMilli)
        assertEquals(listOf(PlatePerSide(5_000, 2)), duplicate.perSide)
        val empty = PlateLoadingCalculator.calculate(40_000, 20_000, listOf(PlateInventory(5_000, 0)))
        assertEquals(20_000, empty.actualMilli)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeInventory() { PlateLoadingCalculator.calculate(40_000, 20_000, listOf(PlateInventory(5_000, -1))) }

    @Test(expected = IllegalArgumentException::class)
    fun refusesTargetBelowBar() { PlateLoadingCalculator.calculate(10_000, 20_000, emptyList()) }

    @Test fun matchesBruteForceForBoundedInventory() {
        val random = Random(971)
        repeat(500) {
            val inventory = List(random.nextInt(1, 6)) { PlateInventory(random.nextInt(1, 21) * 100, random.nextInt(0, 9)) }
            val target = random.nextInt(1, 101) * 100
            val bar = random.nextInt(0, target / 100 + 1) * 100
            var reachable = setOf(0)
            val combined = inventory.groupBy { it.weightMilli }.map { (weight, rows) -> PlateInventory(weight, rows.sumOf { it.totalCount }) }
            for (plate in combined) reachable = reachable.flatMap { existing ->
                (0..plate.totalCount / 2).map { existing + it * plate.weightMilli }
            }.filter { it <= (target - bar) / 2 }.toSet()
            val result = PlateLoadingCalculator.calculate(target, bar, inventory)
            assertEquals(bar + 2 * (reachable.maxOrNull() ?: 0), result.actualMilli)
            assertEquals(result.actualMilli, bar + 2 * result.perSide.sumOf { it.weightMilli * it.count })
        }
    }
}
