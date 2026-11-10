package airplane;

import airplane.components.Engine;
import airplane.components.FuelSystem;
import airplane.components.LandingGear;
import airplane.components.NavigationSystem;
import airplane.exceptions.FlightException;
import airplane.models.FlightStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.stream.Stream;

import static airplane.models.FlightStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AircraftTest {
    private Engine engine;
    private FuelSystem fuel;
    private LandingGear gear;
    private NavigationSystem navigation;
    private Aircraft aircraft;

    @BeforeEach
    void setUp() {
        engine = mock(Engine.class);
        fuel = mock(FuelSystem.class);
        gear = mock(LandingGear.class);
        navigation = mock(NavigationSystem.class);
        aircraft = new Aircraft(engine, fuel, gear, navigation);
    }

    @Test
    void newAircraftIsGroundedWithoutCallingComponents() {
        assertEquals(GROUNDED, aircraft.getStatus());
        verifyNoInteractions(engine, fuel, gear, navigation);
    }

    @Test
    void preparationChecksAllPrerequisitesWithoutStartingFlight() {
        allowPreparation();

        aircraft.prepareForTakeoff();

        assertEquals(READY, aircraft.getStatus());
        verify(engine).isOperational();
        verify(fuel).hasEnoughFuel();
        verify(navigation).isRouteAvailable();
        verifyNoMoreInteractions(engine, fuel, navigation);
        verifyNoInteractions(gear);
    }

    @Test
    void failedEnginePreventsPreparationAndSkipsRemainingChecks() {
        when(engine.isOperational()).thenReturn(false);

        assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertEquals(GROUNDED, aircraft.getStatus());
        verify(engine).isOperational();
        verifyNoMoreInteractions(engine);
        verifyNoInteractions(fuel, gear, navigation);
    }

    @Test
    void insufficientFuelPreventsPreparationAndSkipsRouteCheck() {
        when(engine.isOperational()).thenReturn(true);
        when(fuel.hasEnoughFuel()).thenReturn(false);

        assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertEquals(GROUNDED, aircraft.getStatus());
        verify(engine, never()).start();
        verify(fuel).hasEnoughFuel();
        verifyNoInteractions(gear, navigation);
    }

    @Test
    void unavailableRoutePreventsPreparation() {
        when(engine.isOperational()).thenReturn(true);
        when(fuel.hasEnoughFuel()).thenReturn(true);
        when(navigation.isRouteAvailable()).thenReturn(false);

        assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertEquals(GROUNDED, aircraft.getStatus());
        verify(navigation).isRouteAvailable();
        verify(engine, never()).start();
        verifyNoInteractions(gear);
    }

    @Test
    void fuelCheckExceptionLeavesAircraftGrounded() {
        when(engine.isOperational()).thenReturn(true);
        FlightException failure = new FlightException("Fuel sensor unavailable.");
        when(fuel.hasEnoughFuel()).thenThrow(failure);

        assertSame(failure, assertThrows(FlightException.class, aircraft::prepareForTakeoff));

        assertEquals(GROUNDED, aircraft.getStatus());
        verify(engine, never()).start();
        verifyNoInteractions(gear, navigation);
    }

    @Test
    void takeoffStartsEngineBeforeRetractingGearExactlyOnce() {
        prepare();
        doNothing().when(engine).start();

        aircraft.takeOff();

        assertEquals(IN_FLIGHT, aircraft.getStatus());
        InOrder order = inOrder(engine, gear);
        order.verify(engine, times(1)).start();
        order.verify(gear, times(1)).retract();
        verify(gear, never()).deploy();
        verify(engine, never()).stop();
    }

    @ParameterizedTest
    @ValueSource(strings = {"engine", "gear"})
    void takeoffFailureAbortsAndPreservesCause(String failingComponent) {
        prepare();
        IllegalStateException failure = new IllegalStateException("Takeoff component failed.");
        if (failingComponent.equals("engine")) {
            doThrow(failure).when(engine).start();
        } else {
            doThrow(failure).when(gear).retract();
        }

        FlightException thrown = assertThrows(FlightException.class, aircraft::takeOff);

        assertSame(failure, thrown.getCause());
        assertEquals(ABORTED, aircraft.getStatus());
        verify(engine).start();
        verify(gear, times(failingComponent.equals("engine") ? 0 : 1)).retract();
    }

    @Test
    void cruisePassesExactFuelAmountsAndKeepsFlying() {
        fly();
        doNothing().when(fuel).consumeFuel(any(Double.class));

        aircraft.cruise(12.5);
        aircraft.cruise(7.25);

        assertEquals(IN_FLIGHT, aircraft.getStatus());
        verify(fuel).consumeFuel(12.5);
        verify(fuel).consumeFuel(7.25);
        verify(fuel, times(2)).consumeFuel(any(Double.class));
        verify(gear, never()).deploy();
        verify(engine, never()).stop();
    }

    @Test
    void engineFailureDuringCruiseLandsWithoutConsumingFuel() {
        allowPreparation();
        when(engine.isOperational()).thenReturn(true, false);
        aircraft.prepareForTakeoff();
        aircraft.takeOff();

        aircraft.cruise(10);

        assertEquals(LANDED, aircraft.getStatus());
        verify(fuel, never()).consumeFuel(any(Double.class));
        verify(gear).deploy();
        verify(engine).stop();
    }

    @Test
    void fuelConsumptionFailureLandsAndPreservesCause() {
        fly();
        IllegalStateException failure = new IllegalStateException("Fuel pump failed.");
        doThrow(failure).when(fuel).consumeFuel(15);

        FlightException thrown = assertThrows(FlightException.class, () -> aircraft.cruise(15));

        assertSame(failure, thrown.getCause());
        assertEquals(LANDED, aircraft.getStatus());
        verify(fuel).consumeFuel(15);
        verify(gear).deploy();
        verify(engine).stop();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void landingDeploysGearThenStopsEngine(boolean emergency) {
        fly();

        if (emergency) {
            aircraft.emergencyLand();
        } else {
            aircraft.land();
        }

        assertEquals(LANDED, aircraft.getStatus());
        InOrder order = inOrder(gear, engine);
        order.verify(gear).deploy();
        order.verify(engine).stop();
    }

    @Test
    void landedAircraftCanPrepareAndFlyAgain() {
        fly();
        aircraft.land();

        aircraft.prepareForTakeoff();
        aircraft.takeOff();

        assertEquals(IN_FLIGHT, aircraft.getStatus());
        verify(engine, times(2)).start();
        verify(gear, times(2)).retract();
        verify(engine, times(1)).stop();
    }

    @Test
    void resetAllowsASecondFlightWithAChangedEngineCondition() {
        fly();
        aircraft.land();
        // The same injected engine is reused; reset removes both stubbing and call history.
        reset(engine);
        verifyNoInteractions(engine);

        assertThrows(FlightException.class, aircraft::prepareForTakeoff);
        assertEquals(LANDED, aircraft.getStatus());
        verify(engine).isOperational();
        verify(engine, never()).start();

        when(engine.isOperational()).thenReturn(true);
        aircraft.prepareForTakeoff();
        aircraft.takeOff();

        assertEquals(IN_FLIGHT, aircraft.getStatus());
        verify(engine, times(2)).isOperational();
        verify(engine, times(1)).start();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void invalidCruiseAmountIsRejectedBeforeCallingComponents(double amount) {
        fly();
        clearInvocations(engine, fuel, gear, navigation);

        assertThrows(IllegalArgumentException.class, () -> aircraft.cruise(amount));

        assertEquals(IN_FLIGHT, aircraft.getStatus());
        verifyNoInteractions(engine, fuel, gear, navigation);
    }

    @ParameterizedTest(name = "{0} rejects {1}")
    @MethodSource("invalidTransitions")
    void invalidLifecycleOperationPreservesStateWithoutCallingComponents(
            FlightStatus status, String operation) {
        enterStatus(status);
        clearInvocations(engine, fuel, gear, navigation);

        assertThrows(FlightException.class, () -> perform(operation));

        assertEquals(status, aircraft.getStatus());
        verifyNoInteractions(engine, fuel, gear, navigation);
    }

    private static Stream<Arguments> invalidTransitions() {
        return Arrays.stream(FlightStatus.values()).flatMap(status ->
                Stream.of("prepare", "takeoff", "cruise", "land", "emergency").filter(operation ->
                        !switch (operation) {
                            case "prepare" -> status == GROUNDED || status == LANDED;
                            case "takeoff" -> status == READY;
                            default -> status == IN_FLIGHT;
                        }).map(operation -> Arguments.of(status, operation)));
    }

    private void enterStatus(FlightStatus status) {
        switch (status) {
            case GROUNDED -> { }
            case READY -> prepare();
            case IN_FLIGHT -> fly();
            case LANDED -> {
                fly();
                aircraft.land();
            }
            case ABORTED -> {
                prepare();
                doThrow(new IllegalStateException("Engine failed.")).when(engine).start();
                assertThrows(FlightException.class, aircraft::takeOff);
            }
        }
        assertEquals(status, aircraft.getStatus());
    }

    private void perform(String operation) {
        switch (operation) {
            case "prepare" -> aircraft.prepareForTakeoff();
            case "takeoff" -> aircraft.takeOff();
            case "cruise" -> aircraft.cruise(10);
            case "land" -> aircraft.land();
            case "emergency" -> aircraft.emergencyLand();
            default -> throw new AssertionError("Unknown operation: " + operation);
        }
    }

    private void allowPreparation() {
        when(engine.isOperational()).thenReturn(true);
        when(fuel.hasEnoughFuel()).thenReturn(true);
        when(navigation.isRouteAvailable()).thenReturn(true);
    }

    private void prepare() {
        allowPreparation();
        aircraft.prepareForTakeoff();
    }

    private void fly() {
        prepare();
        aircraft.takeOff();
    }
}
