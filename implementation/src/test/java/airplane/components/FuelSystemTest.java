package airplane.components;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class FuelSystemTest {
    @ParameterizedTest
    @ValueSource(doubles = {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void constructorRejectsInvalidInitialFuel(double initialFuel) {
        assertThrows(IllegalArgumentException.class, () -> new FuelSystem(initialFuel, 20));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void constructorRejectsInvalidMinimumFuel(double minimumFuel) {
        assertThrows(IllegalArgumentException.class, () -> new FuelSystem(100, minimumFuel));
    }

    @ParameterizedTest
    @CsvSource({"19.5, 20, false", "20, 20, true", "20.5, 20, true", "0, 0, true"})
    void checksTakeoffFuelAtAndAroundThreshold(double initial, double minimum, boolean enough) {
        FuelSystem fuel = new FuelSystem(initial, minimum);

        assertEquals(enough, fuel.hasEnoughFuel());
        assertEquals(initial, fuel.getRemainingFuel());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void invalidConsumptionDoesNotChangeFuel(double amount) {
        FuelSystem fuel = new FuelSystem(100, 20);

        assertThrows(IllegalArgumentException.class, () -> fuel.consumeFuel(amount));

        assertEquals(100, fuel.getRemainingFuel());
    }

    @Test
    void consumingAllRemainingFuelLeavesZero() {
        FuelSystem fuel = new FuelSystem(20, 20);

        fuel.consumeFuel(20);

        assertEquals(0, fuel.getRemainingFuel());
        assertFalse(fuel.hasEnoughFuel());
    }

    @Test
    void fractionalConsumptionUpdatesTakeoffAvailability() {
        FuelSystem fuel = new FuelSystem(20.5, 20);

        fuel.consumeFuel(0.75);

        assertEquals(19.75, fuel.getRemainingFuel());
        assertFalse(fuel.hasEnoughFuel());
    }

    @Test
    void insufficientFuelFailsWithoutChangingBalance() {
        FuelSystem fuel = new FuelSystem(20, 10);

        assertThrows(IllegalStateException.class, () -> fuel.consumeFuel(20.5));

        assertEquals(20, fuel.getRemainingFuel());
    }
}
