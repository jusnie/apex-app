package com.apex.app.models

/** Weights are thousandths of the selected unit; inventory counts are plates in total. */
data class PlateInventory(val weightMilli: Int, val totalCount: Int)
data class PlatePerSide(val weightMilli: Int, val count: Int)
data class PlateLoadingResult(
    val targetMilli: Int,
    val barMilli: Int,
    val actualMilli: Int,
    val perSide: List<PlatePerSide>
) {
    val isExact: Boolean get() = actualMilli == targetMilli
    val shortfallMilli: Int get() = targetMilli - actualMilli
}

object PlateLoadingCalculator {
    const val MAX_MILLI = 1_000_000
    const val MAX_ROWS = 10
    const val MAX_COUNT = 20

    /** Up to three decimal places, in the currently selected unit. */
    fun parseWeight(raw: String): Int? {
        val text = raw.trim().replace(',', '.')
        if (!text.matches(Regex("[0-9]{1,4}(?:\\.[0-9]{1,3})?"))) return null
        val parts = text.split('.')
        val whole = parts[0].toIntOrNull() ?: return null
        val fraction = parts.getOrNull(1)?.padEnd(3, '0')?.toIntOrNull() ?: 0
        val value = whole * 1000 + fraction
        return value.takeIf { it in 0..MAX_MILLI }
    }

    /** Find the closest achievable total not exceeding the target, using equal sides. */
    fun calculate(targetMilli: Int, barMilli: Int, inventory: List<PlateInventory>): PlateLoadingResult {
        require(targetMilli in 1..MAX_MILLI && barMilli in 0..MAX_MILLI && targetMilli >= barMilli)
        require(inventory.size <= MAX_ROWS && inventory.all { it.weightMilli in 1..MAX_MILLI && it.totalCount in 0..MAX_COUNT })
        val capacity = (targetMilli - barMilli) / 2
        // Duplicate rows represent the same physical plate size, so pair their combined counts.
        val usable = inventory.groupBy { it.weightMilli }
            .map { (weight, rows) -> PlateInventory(weight, rows.sumOf { it.totalCount }) }
            .filter { it.totalCount >= 2 && it.weightMilli <= capacity }
        if (usable.isEmpty()) return PlateLoadingResult(targetMilli, barMilli, barMilli, emptyList())
        fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
        val divisor = usable.map { it.weightMilli }.reduce(::gcd)
        val limit = capacity / divisor
        // Binary-split bounded inventory. First discovery is never replaced, so predecessor
        // chains always refer to an earlier item and cannot use a plate pair twice.
        data class Group(val weight: Int, val amount: Int, val source: Int)
        val groups = mutableListOf<Group>()
        for ((index, plate) in usable.withIndex()) {
            var remaining = plate.totalCount / 2
            var chunk = 1
            while (remaining > 0) {
                val take = minOf(chunk, remaining)
                groups.add(Group((plate.weightMilli / divisor) * take, take, index))
                remaining -= take
                chunk *= 2
            }
        }
        val previous = IntArray(limit + 1) { -1 }
        val chosen = IntArray(limit + 1) { -1 }
        previous[0] = 0
        for ((index, group) in groups.withIndex()) {
            if (group.weight > limit) continue
            for (sum in limit downTo group.weight) {
                if (previous[sum] < 0 && previous[sum - group.weight] >= 0) {
                    previous[sum] = sum - group.weight
                    chosen[sum] = index
                }
            }
        }
        var best = limit
        while (best > 0 && previous[best] < 0) best--
        val counts = IntArray(usable.size)
        var cursor = best
        while (cursor > 0) {
            val group = groups[chosen[cursor]]
            counts[group.source] += group.amount
            cursor = previous[cursor]
        }
        val perSide = usable.indices.filter { counts[it] > 0 }
            .groupBy { usable[it].weightMilli }
            .map { (weight, indexes) -> PlatePerSide(weight, indexes.sumOf { counts[it] }) }
            .sortedByDescending { it.weightMilli }
        return PlateLoadingResult(targetMilli, barMilli, barMilli + 2 * best * divisor, perSide)
    }
}
