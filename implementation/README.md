# Airplane Simulator

1. A new aircraft starts in `GROUNDED` state.
2. `prepareForTakeoff()` checks that the engine is operational, enough fuel is
   available, and a route is available. On success, the state becomes `READY`.
3. `takeOff()` starts the engine, retracts the landing gear, and changes the
   state to `IN_FLIGHT`.
4. `cruise(amount)` consumes fuel. If the engine is no longer operational, the
   aircraft makes an emergency landing. A fuel-consumption failure also causes
   an emergency landing and is reported as a `FlightException`.
5. `land()` and `emergencyLand()` deploy the landing gear, stop the engine,
   and change the state to `LANDED`.

An invalid lifecycle operation (for example, calling `takeOff()` before
preparation) throws `FlightException`.

- `FlightException` is an unchecked exception.
- `IllegalArgumentException` is used for invalid numeric input.
- `Engine.fail()` can simulate an engine failure during a flight.
- `NavigationSystem.setRouteAvailable(...)` can change route availability.
- Mockito and JUnit 5 dependencies are already configured with test scope in `pom.xml`.

## Run the CA2 tests

Use a full JDK 17 or 21 and Maven 3.9.x. From the repository root:

```sh
mvn -B -f projects/project-02/implementation/pom.xml clean test
```

Surefire writes individual results to `target/surefire-reports/` inside this
implementation directory. Build output is ignored by Git.

The supplied cloud environment initially had no Maven and only a Java runtime.
The verified run used temporary Maven 3.9.9 and Temurin JDK 21.0.5 installations,
the environment's Java trust store, and Maven proxy settings derived from its
existing `HTTPS_PROXY`. No machine-specific settings were added to `pom.xml`.
To repeat the run in this same environment while those temporary files exist:

```sh
JAVA_HOME=/tmp/ca2-tools/jdk \
MAVEN_OPTS=-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts \
/tmp/ca2-tools/apache-maven-3.9.9/bin/mvn \
  -s /tmp/ca2-tools/settings.xml -Dmaven.repo.local=/tmp/ca2-tools/m2 \
  -B -f projects/project-02/implementation/pom.xml clean test
```

The test resource `mockito-extensions/org.mockito.plugins.MockMaker` selects
Mockito's subclass mock maker. All mocked collaborators are non-final classes
with non-final methods, so these tests do not need inline mocking or a Java
instrumentation agent. This avoids inline class-retransformation failures such
as "Could not modify all classes [class java.lang.Object, class airplane.components.Engine]".
If mock creation still fails, check `mvn -version` to confirm Maven uses JDK 17
or 21, and inspect the deepest `Caused by` entry in the Surefire report.
The full 64-test suite also passed on JDK 21 with
`-DargLine=-XX:+DisableAttachMechanism`, verifying that agent attachment is not
required. Mockito may still print its initialization warning.

## Test design and assignment checklist

The real `Aircraft` receives four constructor-injected Mockito doubles. Stubbing
controls indirect inputs (readiness checks and exceptions); verification checks
indirect outputs (starting/stopping the engine, moving the gear, consuming fuel).
JUnit 5 creates a fresh fixture before each test, and Mockito's extension checks
for unnecessary stubbing. The tests use the setup/exercise/verify structure from
`07-xUnit-Junit.pdf` and the stub/mock distinction from `10-Test-Doubles.pdf`.

`AircraftTest` contains 16 test methods (40 executions with parameterized cases),
exceeding CA2's minimum of six. Every required Mockito API is exercised:

| Required API | Example in `AircraftTest` |
| --- | --- |
| `mock()` | `setUp` creates all four collaborators |
| `reset()` | `resetAllowsASecondFlightWithAChangedEngineCondition` clears engine stubbing and history between flights |
| `when()`, `thenReturn()` | Preparation checks and the engine's healthy-to-failed sequence |
| `verify()`, `times()` | Exact takeoff calls, repeated flights, and fuel consumption |
| `any()` | `any(Double.class)` matches fuel amounts, with separate exact-argument assertions |
| `thenThrow()` | `fuelCheckExceptionLeavesAircraftGrounded` injects a failed fuel check |
| `doNothing()` | Takeoff and cruise explicitly stub void collaborator methods |

Typed `any(Double.class)` supplies a primitive-compatible default for the
`double` parameter; untyped `any()` would unbox null. Void-method failures use
`doThrow()`, while the value-returning fuel check uses `thenThrow()`.
Reset is confined to the explicit reuse scenario; ordinary tests use fresh mocks.

The suite covers all 19 invalid combinations of lifecycle state and operation,
preparation rejection, successful and aborted takeoff, normal and emergency
landing, repeated flights, and preservation of failure causes. Additional tests
check real fuel boundaries and four flights/scenarios using real collaborators.

## Verified results and fixes

Executed on 2026-11-10; counts include parameterized invocations:

| Suite | Executions | Failures before fixes | Failures after fixes |
| --- | ---: | ---: | ---: |
| `AircraftTest` | 40 | 2 | 0 |
| `FuelSystemTest` | 20 | 6 | 0 |
| `AircraftIntegrationTest` | 4 | 0 | 0 |
| **Total** | **64** | **8** | **0** |

Both runs had zero errors and zero skipped tests. The final command was a full
`clean test` and returned `BUILD SUCCESS`. No tests were weakened or disabled.

The eight failing cases exposed missing non-finite-number validation at three
boundaries, consistent with the starter README's invalid-numeric-input contract:

- `FuelSystem` accepted `NaN` and positive infinity for either constructor input
  (four cases), allowing invalid fuel balances or takeoff thresholds.
- `consumeFuel` accepted `NaN`, corrupting the balance, and treated positive
  infinity as a fuel shortage instead of invalid input (two cases).
- `Aircraft.cruise` forwarded `NaN` and positive infinity to the fuel system
  instead of rejecting them before interacting with collaborators (two cases).

`Double.isFinite` checks now reject these values with `IllegalArgumentException`.
Finite non-negative constructor values and finite positive consumption amounts
retain their existing behavior. No other production behavior was changed.
There are no remaining test failures or execution blockers; coverage percentage
was not measured or required by the brief. The project report is intentionally
deferred.

## Starter provenance

The current `work` branch began at `8712ad0` and lacked CA2 and the test-doubles
lecture. The provided files were read from an unmodified temporary snapshot of
`ParsaBukani/Software-Testing-Course` commit
`a05d97b4e7abf71d2d5942f451d272ac8ca964dd`:

- `projects/project-02/assignment/SWT-05-CA2.pdf` (all three pages)
- `projects/project-02/assignment/F05-CA2-main/` (copied here before editing)
- `common/course-materials/10-Test-Doubles.pdf`

The repository rules in that snapshot matched the checked-out rules. The local
`07-xUnit-Junit.pdf` supplied the JUnit reference. No branch was switched or
merged, and no files in `assignment/`, `common/`, or `report/` were changed.
