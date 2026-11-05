import Testing
@testable import APEX

struct PlateLoadingCalculatorTests {
    @Test func parsesFractionsWithoutRounding() {
        #expect(PlateLoadingCalculator.parseWeight("1.25") == 1250)
        #expect(PlateLoadingCalculator.parseWeight("1,250") == 1250)
        #expect(PlateLoadingCalculator.parseWeight("1.0001") == nil)
        #expect(PlateLoadingCalculator.parseWeight("-5") == nil)
    }
    @Test func exactAndNearestNeverExceedTarget() throws {
        let plates = [PlateInventory(weightMilli: 20_000, totalCount: 4), PlateInventory(weightMilli: 5_000, totalCount: 2)]
        let exact = try #require(PlateLoadingCalculator.calculate(targetMilli: 100_000, barMilli: 20_000, inventory: plates))
        #expect(exact.isExact)
        #expect(exact.perSide.count == 1 && exact.perSide[0].weightMilli == 20_000 && exact.perSide[0].count == 2)
        let near = try #require(PlateLoadingCalculator.calculate(targetMilli: 100_001, barMilli: 20_000, inventory: plates))
        #expect(!near.isExact)
        #expect(near.actualMilli == 100_000 && near.shortfallMilli == 1)
    }
    @Test func unpairedAndInsufficientPlatesRemainUnused() throws {
        let value = try #require(PlateLoadingCalculator.calculate(targetMilli: 80_000, barMilli: 20_000, inventory: [PlateInventory(weightMilli: 20_000, totalCount: 3)]))
        #expect(value.actualMilli == 60_000)
        #expect(value.perSide.count == 1 && value.perSide[0].count == 1)
    }
    @Test func combinesDuplicateSizesAndAllowsZeroInventory() throws {
        let result = try #require(PlateLoadingCalculator.calculate(targetMilli: 40_000, barMilli: 20_000, inventory: [PlateInventory(weightMilli: 5_000, totalCount: 3), PlateInventory(weightMilli: 5_000, totalCount: 1)]))
        #expect(result.actualMilli == 40_000)
        #expect(result.perSide.count == 1 && result.perSide[0].count == 2)
        let empty = try #require(PlateLoadingCalculator.calculate(targetMilli: 40_000, barMilli: 20_000, inventory: [PlateInventory(weightMilli: 5_000, totalCount: 0)]))
        #expect(empty.actualMilli == 20_000 && empty.perSide.isEmpty)
        #expect(PlateLoadingCalculator.calculate(targetMilli: 40_000, barMilli: 20_000, inventory: [PlateInventory(weightMilli: 5_000, totalCount: -1)]) == nil)
    }
    @Test func rejectsTargetBelowBar() {
        #expect(PlateLoadingCalculator.calculate(targetMilli: 10_000, barMilli: 20_000, inventory: []) == nil)
    }
}
