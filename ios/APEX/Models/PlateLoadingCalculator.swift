import Foundation

/// Weights are thousandths of the selected unit; inventory counts are plates in total.
nonisolated struct PlateInventory: Sendable {
    let weightMilli: Int
    let totalCount: Int
}
nonisolated struct PlatePerSide: Sendable {
    let weightMilli: Int
    let count: Int
}
nonisolated struct PlateLoadingResult: Sendable {
    let targetMilli: Int
    let barMilli: Int
    let actualMilli: Int
    let perSide: [PlatePerSide]
    var isExact: Bool { actualMilli == targetMilli }
    var shortfallMilli: Int { targetMilli - actualMilli }
}

nonisolated enum PlateLoadingCalculator {
    static let maxMilli = 1_000_000
    static let maxRows = 10
    static let maxCount = 20

    /// Accept up to three decimal places in the currently selected unit.
    static func parseWeight(_ raw: String) -> Int? {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ",", with: ".")
        let parts = text.split(separator: ".", omittingEmptySubsequences: false)
        guard (1...2).contains(parts.count), (1...4).contains(parts[0].count),
              parts[0].unicodeScalars.allSatisfy({ (48...57).contains($0.value) }), let whole = Int(parts[0]) else { return nil }
        var fraction = 0
        if parts.count == 2 {
            let digits = parts[1]
            guard (1...3).contains(digits.count), digits.unicodeScalars.allSatisfy({ (48...57).contains($0.value) }),
                  let value = Int(String(digits) + String(repeating: "0", count: 3 - digits.count)) else { return nil }
            fraction = value
        }
        let value = whole * 1000 + fraction
        return (0...maxMilli).contains(value) ? value : nil
    }

    /// Find the closest achievable total not exceeding the target, using equal sides.
    static func calculate(targetMilli: Int, barMilli: Int, inventory: [PlateInventory]) -> PlateLoadingResult? {
        guard (1...maxMilli).contains(targetMilli), (0...maxMilli).contains(barMilli), targetMilli >= barMilli,
              inventory.count <= maxRows, inventory.allSatisfy({ (1...maxMilli).contains($0.weightMilli) && (0...maxCount).contains($0.totalCount) }) else { return nil }
        let capacity = (targetMilli - barMilli) / 2
        // Duplicate rows represent the same physical plate size, so pair their combined counts.
        var totals: [Int: Int] = [:]
        for plate in inventory { totals[plate.weightMilli, default: 0] += plate.totalCount }
        let usable = totals.map { PlateInventory(weightMilli: $0.key, totalCount: $0.value) }
            .filter { $0.totalCount >= 2 && $0.weightMilli <= capacity }
            .sorted { $0.weightMilli > $1.weightMilli }
        guard !usable.isEmpty else { return PlateLoadingResult(targetMilli: targetMilli, barMilli: barMilli, actualMilli: barMilli, perSide: []) }
        func gcd(_ a: Int, _ b: Int) -> Int { b == 0 ? a : gcd(b, a % b) }
        let divisor = usable.map(\.weightMilli).reduce(0, gcd)
        let limit = capacity / divisor
        struct Group { let weight: Int; let amount: Int; let source: Int }
        var groups: [Group] = []
        for (index, plate) in usable.enumerated() {
            var remaining = plate.totalCount / 2, chunk = 1
            while remaining > 0 {
                let take = min(chunk, remaining)
                groups.append(Group(weight: (plate.weightMilli / divisor) * take, amount: take, source: index))
                remaining -= take; chunk *= 2
            }
        }
        var previous = [Int](repeating: -1, count: limit + 1)
        var chosen = [Int](repeating: -1, count: limit + 1)
        previous[0] = 0
        for (index, group) in groups.enumerated() where group.weight <= limit {
            for sum in stride(from: limit, through: group.weight, by: -1) where previous[sum] < 0 && previous[sum - group.weight] >= 0 {
                previous[sum] = sum - group.weight; chosen[sum] = index
            }
        }
        var best = limit
        while best > 0 && previous[best] < 0 { best -= 1 }
        var counts = [Int](repeating: 0, count: usable.count)
        var cursor = best
        while cursor > 0 {
            let group = groups[chosen[cursor]]
            counts[group.source] += group.amount
            cursor = previous[cursor]
        }
        var combined: [Int: Int] = [:]
        for index in usable.indices where counts[index] > 0 { combined[usable[index].weightMilli, default: 0] += counts[index] }
        let perSide = combined.map { PlatePerSide(weightMilli: $0.key, count: $0.value) }.sorted { $0.weightMilli > $1.weightMilli }
        return PlateLoadingResult(targetMilli: targetMilli, barMilli: barMilli, actualMilli: barMilli + 2 * best * divisor, perSide: perSide)
    }
}
