package airplane;

import airplane.components.Engine;
import airplane.components.FuelSystem;
import airplane.components.LandingGear;
import airplane.components.NavigationSystem;
import airplane.exceptions.FlightException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static airplane.models.FlightStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class AircraftIntegrationTest {
    private Engine engine;
    private FuelSystem fuel;
    private LandingGear gear;
    private NavigationSystem navigation;
    private Aircraft aircraft;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        fuel = new FuelSystem(100, 20);
        gear = new LandingGear();
        navigation = new NavigationSystem("Tehran");
        aircraft = new Aircraft(engine, fuel, gear, navigation);
    }

    @Test
    void completeFlightUpdatesRealComponents() {
        assertEquals(GROUNDED, aircraft.getStatus());
        assertFalse(engine.isRunning());
        assertTrue(gear.isDeployed());
        aircraft.prepareForTakeoff();
        assertEquals(READY, aircraft.getStatus());

        aircraft.takeOff();
        assertEquals(IN_FLIGHT, aircraft.getStatus());
        assertTrue(engine.isRunning());
        assertFalse(gear.isDeployed());
        aircraft.cruise(80);
        assertEquals(20, fuel.getRemainingFuel());
        aircraft.land();

        assertLanded();
        aircraft.prepareForTakeoff();
        assertEquals(READY, aircraft.getStatus());
    }

    @Test
    void realEngineFailureTriggersEmergencyLanding() {
        fly();
        engine.fail();

        aircraft.cruise(10);

        assertLanded();
        assertEquals(100, fuel.getRemainingFuel());
        assertThrows(FlightException.class, aircraft::prepareForTakeoff);
    }

    @Test
    void realFuelShortageTriggersEmergencyLandingAndReportsCause() {
        fly();

        FlightException failure = assertThrows(FlightException.class, () -> aircraft.cruise(101));

        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals(100, fuel.getRemainingFuel());
        assertLanded();
    }

    @Test
    void restoredRouteAllowsPreparationAfterRejection() {
        navigation.setRouteAvailable(false);
        assertThrows(FlightException.class, aircraft::prepareForTakeoff);
        assertEquals(GROUNDED, aircraft.getStatus());

        navigation.setRouteAvailable(true);
        aircraft.prepareForTakeoff();

        assertEquals(READY, aircraft.getStatus());
        assertFalse(engine.isRunning());
        assertTrue(gear.isDeployed());
    }

    private void fly() {
        aircraft.prepareForTakeoff();
        aircraft.takeOff();
    }

    private void assertLanded() {
        assertEquals(LANDED, aircraft.getStatus());
        assertTrue(gear.isDeployed());
        assertFalse(engine.isRunning());
    }
}
