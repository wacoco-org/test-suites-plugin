# Test Suites Gradle Plugin

A Gradle plugin (`net.miwashi.test-suites`) designed to streamline test organization, optimize build efficiency, and provide clean separation between unit, black-box, white-box, and tagged test suites.

---

## Motivation & Testing Philosophy

Software testing is a nuanced, multifaceted discipline that maps poorly to a simplistic "one project, one test task to test them all" paradigm. 

In modern quality engineering, teams manage dozens of distinct test types classified across multiple axes:
- **Timing & Stage**: Pre-commit verification, pull request gates, nightly regressions, continuous delivery sanity checks, and post-deployment validation.
- **Intent & Scope**: Functional correctness, contract verification, security probing, fault tolerance, performance baselines, and smoke readiness.
- **Target & Environment**: Isolated unit logic with in-memory doubles, component-level integration with ephemeral containers, or black-box system validation executed against running staging clusters.

Attempting to force all these distinct objectives into a monolithic test configuration creates dependency bloat, slows build feedback loops, and obscures failure analysis. 

This plugin adopts an ambitious, tester-centric approach to build configuration. By providing first-class abstractions for black-box isolation, white-box access, tag aggregation, and suite-specific dependencies, it empowers QA engineers and developers to align their build pipelines with the actual structure of their testing strategy—maximizing signal quality, reducing developer friction, and ensuring comprehensive test coverage.

### Reverse Requirement Harvesting & Living Specifications

Traditional build pipelines routinely flush immense value into the void: once a test run finishes, the rich intent embedded in the tests is discarded, leaving behind only transient exit codes and ephemeral logs. 

Every well-crafted test method is an executable specification stating exactly what is required from a named component, endpoint, or domain object. By enabling automated event reporting to a centralized aggregation server, this plugin unlocks **reverse requirement harvesting**. Observing test activity across suites and time builds an omnipresent, living database of every component guarded by tests—using test method names and execution metadata to continuously document and verify real system requirements.

---

## Features

- **Standardized Directory Conventions**: Automatically provisions dedicated directory structures (`common`, `unit`, and custom suites) to eliminate setup friction.
- **Common Test Fixtures (`testCommon`)**: Share helper code, mocks, and fixtures across all test suites without code duplication.
- **Black-box Test Suites**: Isolated source sets and test tasks with zero access to internal application binaries (`main.output`), ideal for API, end-to-end, smoke, and integration testing against running services. Configurable with default target base URLs (`${name}.baseUrl`).
- **White-box Test Suites**: Custom source sets with full access to internal production code and dependencies for deep component or subsystem testing.
- **Tagged Test Suites**: Aggregator test tasks that discover and execute tests matching specific JUnit Platform tags (e.g., `@Tag("regression")`) across all test suites.
- **Suite-Specific Dependencies**: Declare dependencies isolated to individual suites (e.g. Testcontainers for integration tests) via concise DSL or standard Gradle configurations.
- **Console Logging & Result Reporting Controls**: Toggle detailed console test logging and console result summaries globally or per suite target (target level overrides global level).
- **Test Result Reporting & BDT Harvesting**: Optional, silent-by-default HTTP event reporting that captures test outcomes, method names, and execution metadata for BDT and requirement tracing.
- **Lifecycle Integration**: Automatically wires all custom test tasks into Gradle's `check` lifecycle for consistent verification workflows.

---

## Applying the Plugin

### Using the plugins DSL

```groovy
plugins {
    id 'net.miwashi.test-suites' version '1.0.0'
}
```

---

## Configuration DSL (`testSuites`)

Configure test suites in your `build.gradle` file using the `testSuites` extension:

```groovy
testSuites {
    // Black-box suites (no access to main classes/dependencies)
    blackbox 'smoke'
    blackbox('integration') {
        // Specific dependencies isolated to this suite
        dependencies {
            implementation 'org.testcontainers:testcontainers:1.19.0'
            implementation 'org.testcontainers:junit-jupiter:1.19.0'
        }
        // Override console output specifically for integration tests
        consoleLogging = false
    }

    // White-box suites (access to main classes and dependencies)
    whitebox 'component', 'contract'

    // Tagged aggregator suites (executes tests matching @Tag across all suites)
    tagged 'regression'                         // Runs tests with @Tag("regression")
    tagged name: 'smokeRegression', tag: 'smoke' // Custom task name mapping to specific tag

    // Console logging & reporting controls (target overrides global)
    consoleLogging = true     // Toggle detailed test logs on console (default: true)
    consoleReporting = true   // Toggle test result summary on console (default: true)

    // Optional: Harvest test outcomes for BDT requirement finding (silent if omitted)
    reportUrl = 'http://localhost:8080/api/test-events'
}
```

---

## Console Logging & Console Result Reporting

The plugin provides granular control over terminal output during test runs, allowing you to unclutter console logs while retaining critical feedback.

- **`consoleLogging`** (Default: `true`): Controls Gradle's detailed test lifecycle events (`started`, `passed`, `skipped`, `failed`, `standardOut`, `standardError`) and stack trace outputs.
- **`consoleReporting`** (Default: `true`): Controls the terminal test completion summary banner (e.g. `smokeTest: SUCCESS (5 tests, 5 passed, 0 failed, 0 skipped)`).

### Target-Level Overrides

Target-level settings override global settings, allowing quiet unit tests with verbose smoke/integration tests (or vice-versa):

```groovy
testSuites {
    // Global defaults for all suites
    consoleLogging = false
    consoleReporting = true

    // Target-level overrides
    blackbox('smoke') {
        consoleLogging = true    // Enable detailed logs for smoke tests only
        consoleReporting = true
    }

    whitebox('component') {
        consoleLogging = false
        consoleReporting = false // Complete terminal silence for component tests
    }

    suite('unit') {
        consoleLogging = false
    }
}
```

*Can also be toggled via Gradle CLI properties:*
```bash
# Silence console logging globally
./gradlew test -PtestSuites.consoleLogging=false

# Override for a specific target
./gradlew smokeTest -PtestSuites.smoke.consoleLogging=true
```

---

## Suite-Specific Dependencies

Different test tiers often need specialized libraries (e.g., Testcontainers for integration tests, Mockito for unit tests). The plugin lets you configure dependencies per suite without leaking them into unit tests or production builds.

### 1. Inside `testSuites` DSL
```groovy
testSuites {
    blackbox('integration') {
        dependencies {
            implementation 'org.testcontainers:testcontainers:1.19.0'
            implementation 'org.testcontainers:postgresql:1.19.0'
        }
    }

    // Alternative helper syntax
    dependencies('integration') {
        runtimeOnly 'org.testcontainers:kafka:1.19.0'
    }
    dependency 'smoke', 'org.apache.httpcomponents.client5:httpclient5:5.2.1'
}
```

### 2. In Standard `dependencies` Block
Standard configurations (`<suite>TestImplementation`, `<suite>TestRuntimeOnly`) and clean aliases (`<suite>Implementation`, `<suite>RuntimeOnly`) are fully supported:

```groovy
dependencies {
    // Unit tests only
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'

    // Integration suite only
    integrationTestImplementation 'org.testcontainers:testcontainers:1.19.0'
    // or using alias:
    integrationImplementation 'org.testcontainers:testcontainers:1.19.0'

    // Common fixtures shared by all suites
    testCommonImplementation 'org.assertj:assertj-core:3.25.3'
}
```

---

## Test Result Reporting & BDT Harvesting

When configured with an endpoint URL, the plugin emits structured HTTP POST events whenever individual tests complete. This unlocks reverse requirement harvesting by capturing test outcomes, component names, and method descriptions to feed centralized requirement databases and Behavior-Driven Testing (BDT) analytics.

**Key characteristics:**
- **Silent by Default**: If no `reportUrl` is configured, zero network requests or additional logs are generated.
- **Fail-Safe**: Network or server timeouts do not fail the build.

### Configuration

```groovy
testSuites {
    // Simple property or method
    reportUrl = 'http://localhost:8080/api/test-results'

    // Or with custom headers / authentication
    report {
        url = 'http://localhost:8080/api/test-results'
        headers = [
            'Authorization': 'Bearer <token>',
            'X-Project-Version': version.toString()
        ]
    }
}
```

*Can also be specified via Gradle CLI property without editing `build.gradle`:*
```bash
./gradlew check -PtestSuites.reportUrl=http://reporting-server/api/test-results
```

### Event Payload Schema (`application/json`)

When each test method finishes, a JSON payload is posted to `reportUrl`:

```json
{
  "event": "testResult",
  "project": "order-service",
  "suite": "integrationTest",
  "suiteName": "integration",
  "className": "com.example.OrderApiIntegrationTest",
  "classSimpleName": "OrderApiIntegrationTest",
  "testName": "shouldCreateOrderWhenStockIsAvailable",
  "methodName": "shouldCreateOrderWhenStockIsAvailable",
  "displayName": "shouldCreateOrderWhenStockIsAvailable()",
  "result": "SUCCESS",
  "status": "SUCCESS",
  "durationMs": 142,
  "startTime": 1729512000000,
  "endTime": 1729512000142,
  "error": null
}
```

---

## Project Structure Conventions

When the plugin is applied and suites are defined, the following directory layout is established:

```text
src/
├── main/
│   ├── java/
│   └── resources/
└── test/
    ├── common/          # Shared fixtures, assertions, and utilities (testCommon)
    │   ├── java/
    │   └── resources/
    ├── unit/            # Standard unit tests (ran via `test` task)
    │   ├── java/
    │   └── resources/
    ├── smoke/           # Blackbox suite (ran via `smokeTest` task)
    │   ├── java/
    │   └── resources/
    ├── integration/     # Blackbox suite (ran via `integrationTest` task)
    │   ├── java/
    │   └── resources/
    └── component/       # Whitebox suite (ran via `componentTest` task)
        ├── java/
        └── resources/
```

---

## Common

The `testCommon` source set (`src/test/common/java`) is shared across all test suites without code duplication. By placing shared superclasses, fixtures, and tag interfaces in `common`, you maximize consistency across the testing lifecycle and simplify suite configuration.

### Superclass Examples (`src/test/common/java`)

```java
package test.common;

import org.junit.jupiter.api.BeforeEach;

/** Base class for standard unit tests (src/test/unit). */
public abstract class UnitTest {
    // Shared unit test setup, mocks, and helper utilities
}

/** Base class for integration tests (src/test/integration). */
public abstract class IntegrationTest {
    protected String baseUrl;

    @BeforeEach
    void setUpBaseUrl() {
        this.baseUrl = System.getProperty("integration.baseUrl", "http://localhost:8080");
    }
}

/** Base class for smoke tests (src/test/smoke). */
public abstract class SmokeTest {
    protected String baseUrl;

    @BeforeEach
    void setUpBaseUrl() {
        this.baseUrl = System.getProperty("smoke.baseUrl", "http://localhost:8080");
    }
}
```

### Tag Interfaces & Annotations (`RegressionTest`)

JUnit 5 supports tags on interfaces—any test class implementing the interface inherits the tag. You can also define it as a composed annotation:

```java
package test.common;

import org.junit.jupiter.api.Tag;

/**
 * Tagging interface for regression test suites.
 * Implementing this interface automatically tags the test class with @Tag("regression").
 */
@Tag("regression")
public interface RegressionTest {
}
```

*Alternative as composed annotation:*

```java
package test.common;

import org.junit.jupiter.api.Tag;
import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag("regression")
public @interface RegressionTest {
}
```

### Utilising the Plugin with Superclasses

Test classes in each suite extend the corresponding superclass from `common` and implement tag interfaces to automatically integrate with the plugin's tasks and lifecycle:

```java
// src/test/unit/java/com/example/OrderServiceTest.java
class OrderServiceTest extends UnitTest {
    @Test
    void testCalculateDiscount() {
        // Executed via `./gradlew test`
    }
}

// src/test/smoke/java/com/example/PingSmokeTest.java
class PingSmokeTest extends SmokeTest implements RegressionTest {
    @Test
    void testServiceHealth() {
        // Uses baseUrl configured via -Psmoke.baseUrl or default http://localhost:8080
        // Executed via `./gradlew smokeTest` AND `./gradlew regressionTest`
    }
}

// src/test/integration/java/com/example/OrderApiIntegrationTest.java
class OrderApiIntegrationTest extends IntegrationTest implements RegressionTest {
    @Test
    void testCreateOrderEndpoint() {
        // Uses baseUrl configured via -Pintegration.baseUrl or default http://localhost:8080
        // Executed via `./gradlew integrationTest` AND `./gradlew regressionTest`
    }
}
```

---

## Suite Types

### 1. Black-box Suites (`blackbox`)
- **Intent**: External system testing where tests interact solely with external interfaces.
- **Classpath**: Includes `testCommon` and `testImplementation` dependencies, but strictly excludes `main.output` and `implementation` dependencies.
- **Tasks & Lifecycle**: Registers `<name>Test` (e.g. `smokeTest`), included in `check`.
- **System Properties**: Configures `<name>.baseUrl` defaulting to `http://localhost:8080` (overridable via Gradle project properties `-P<name>.baseUrl=...`).

### 2. White-box Suites (`whitebox`)
- **Intent**: Specialized internal tests that require access to production implementation classes.
- **Classpath**: Includes `main.output`, `testCommon`, `implementation`, and `testImplementation`.
- **Tasks & Lifecycle**: Registers `<name>Test` (e.g. `componentTest`), included in `check`.

### 3. Tagged Suites (`tagged`)
- **Intent**: Cross-cutting test execution by aggregating tests marked with JUnit 5 `@Tag("<tag>")` from all registered suites.
- **Execution**: Registers `<name>Test` (e.g. `regressionTest`) without creating a separate source directory.

```java
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class UserEndpointSmokeTest {
    @Test
    @Tag("regression")
    void testUserEndpoint() {
        // Included in both smokeTest and regressionTest
    }
}
```

---

## Common Gradle Commands

```bash
# Run unit tests only
./gradlew test

# Run a specific suite
./gradlew smokeTest
./gradlew integrationTest
./gradlew regressionTest

# Run all verification tasks (unit + all configured blackbox/whitebox suites)
./gradlew check

# Pass custom base URL to blackbox suites
./gradlew smokeTest -Psmoke.baseUrl=http://staging.example.com
```