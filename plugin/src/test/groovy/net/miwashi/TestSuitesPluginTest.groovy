package net.miwashi

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test as TestTask
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.io.File
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.*

class TestSuitesPluginTest {

    @Test
    void "applying plugin creates test common and unit directories and configures sourceSets"(@TempDir Path tempDir) {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()

        File commonJava = new File(project.projectDir, 'src/test/common/java')
        File commonResources = new File(project.projectDir, 'src/test/common/resources')
        File unitJava = new File(project.projectDir, 'src/test/unit/java')
        File unitResources = new File(project.projectDir, 'src/test/unit/resources')

        assertFalse(commonJava.exists())
        assertFalse(commonResources.exists())
        assertFalse(unitJava.exists())
        assertFalse(unitResources.exists())

        project.pluginManager.apply('net.miwashi.test-suites')

        assertTrue(commonJava.exists() && commonJava.isDirectory())
        assertTrue(commonResources.exists() && commonResources.isDirectory())
        assertTrue(unitJava.exists() && unitJava.isDirectory())
        assertTrue(unitResources.exists() && unitResources.isDirectory())

        def sourceSets = project.extensions.getByType(SourceSetContainer)
        def testCommon = sourceSets.findByName('testCommon')
        assertNotNull(testCommon)
        assertEquals(Collections.singleton(commonJava.canonicalFile), testCommon.java.srcDirs.collect { it.canonicalFile } as Set)
        assertEquals(Collections.singleton(commonResources.canonicalFile), testCommon.resources.srcDirs.collect { it.canonicalFile } as Set)

        def test = sourceSets.findByName('test')
        assertNotNull(test)
        assertEquals(Collections.singleton(unitJava.canonicalFile), test.java.srcDirs.collect { it.canonicalFile } as Set)
        assertEquals(Collections.singleton(unitResources.canonicalFile), test.resources.srcDirs.collect { it.canonicalFile } as Set)

        // Verify configurations wiring
        def testCommonImpl = project.configurations.getByName('testCommonImplementation')
        assertTrue(testCommonImpl.extendsFrom.contains(project.configurations.getByName('testImplementation')))

        def testCommonRuntime = project.configurations.getByName('testCommonRuntimeOnly')
        assertTrue(testCommonRuntime.extendsFrom.contains(project.configurations.getByName('testRuntimeOnly')))
    }

    @Test
    void "adding blackbox suite creates sourceSet and task without system dependencies"(@TempDir Path tempDir) {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()

        project.pluginManager.apply('net.miwashi.test-suites')

        TestSuitesExtension ext = project.extensions.getByType(TestSuitesExtension)
        ext.blackbox('smoke')

        File smokeJava = new File(project.projectDir, 'src/test/smoke/java')
        File smokeResources = new File(project.projectDir, 'src/test/smoke/resources')
        assertTrue(smokeJava.exists() && smokeJava.isDirectory())
        assertTrue(smokeResources.exists() && smokeResources.isDirectory())

        def sourceSets = project.extensions.getByType(SourceSetContainer)
        def smokeTest = sourceSets.findByName('smokeTest')
        assertNotNull(smokeTest)
        assertEquals(Collections.singleton(smokeJava.canonicalFile), smokeTest.java.srcDirs.collect { it.canonicalFile } as Set)

        def smokeImpl = project.configurations.getByName('smokeTestImplementation')
        assertTrue(smokeImpl.extendsFrom.contains(project.configurations.getByName('testImplementation')))
        assertFalse(smokeImpl.extendsFrom.contains(project.configurations.getByName('implementation')))

        TestTask task = project.tasks.getByName('smokeTest') as TestTask
        assertNotNull(task)
        assertEquals('verification', task.group)
        assertEquals('Runs smoke tests.', task.description)
        assertEquals('http://localhost:8080', task.systemProperties.get('smoke.baseUrl'))
    }

    @Test
    void "adding whitebox suite creates sourceSet with system dependencies"(@TempDir Path tempDir) {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()

        project.pluginManager.apply('net.miwashi.test-suites')

        TestSuitesExtension ext = project.extensions.getByType(TestSuitesExtension)
        ext.whitebox('component')

        File compJava = new File(project.projectDir, 'src/test/component/java')
        File compResources = new File(project.projectDir, 'src/test/component/resources')
        assertTrue(compJava.exists() && compJava.isDirectory())
        assertTrue(compResources.exists() && compResources.isDirectory())

        def sourceSets = project.extensions.getByType(SourceSetContainer)
        def compTest = sourceSets.findByName('componentTest')
        assertNotNull(compTest)
        assertEquals(Collections.singleton(compJava.canonicalFile), compTest.java.srcDirs.collect { it.canonicalFile } as Set)

        def compImpl = project.configurations.getByName('componentTestImplementation')
        assertTrue(compImpl.extendsFrom.contains(project.configurations.getByName('testImplementation')))
        assertTrue(compImpl.extendsFrom.contains(project.configurations.getByName('implementation')))

        TestTask task = project.tasks.getByName('componentTest') as TestTask
        assertNotNull(task)
        assertEquals('verification', task.group)
        assertEquals('Runs component tests.', task.description)
    }

    @Test
    void "adding tagged suite registers test task"(@TempDir Path tempDir) {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()

        project.pluginManager.apply('net.miwashi.test-suites')

        TestSuitesExtension ext = project.extensions.getByType(TestSuitesExtension)
        ext.blackbox('smoke')
        ext.whitebox('component')
        ext.tagged('regression')

        TestTask task = project.tasks.getByName('regressionTest') as TestTask
        assertNotNull(task)
        assertEquals('verification', task.group)
        assertEquals('Runs @Regression tests from all suites.', task.description)
        assertNotNull(task.testClassesDirs)
        assertNotNull(task.classpath)
    }

    @Test
    void "dsl closure allows configuring blackbox, whitebox and tagged suites"(@TempDir Path tempDir) {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()

        project.pluginManager.apply('net.miwashi.test-suites')

        project.testSuites {
            blackbox 'smoke', 'integration'
            whitebox 'functional'
            tagged 'regression'
        }

        def sourceSets = project.extensions.getByType(SourceSetContainer)
        assertNotNull(sourceSets.findByName('smokeTest'))
        assertNotNull(sourceSets.findByName('integrationTest'))
        assertNotNull(sourceSets.findByName('functionalTest'))

        assertNotNull(project.tasks.findByName('smokeTest'))
        assertNotNull(project.tasks.findByName('integrationTest'))
        assertNotNull(project.tasks.findByName('functionalTest'))
        assertNotNull(project.tasks.findByName('regressionTest'))
    }

    @Test
    void "suite specific dependency can be added via dsl without polluting unit tests"(@TempDir Path tempDir) {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()

        project.pluginManager.apply('net.miwashi.test-suites')

        project.testSuites {
            blackbox('integration') {
                dependencies {
                    implementation 'org.testcontainers:testcontainers:1.19.0'
                }
            }
        }

        def integrationImpl = project.configurations.getByName('integrationTestImplementation')
        def unitImpl = project.configurations.getByName('testImplementation')

        boolean integrationHasDep = integrationImpl.dependencies.any {
            it.group == 'org.testcontainers' && it.name == 'testcontainers'
        }
        boolean unitHasDep = unitImpl.dependencies.any {
            it.group == 'org.testcontainers' && it.name == 'testcontainers'
        }

        assertTrue(integrationHasDep, "Integration suite should have testcontainers dependency")
        assertFalse(unitHasDep, "Unit suite should not have testcontainers dependency")
    }

    @Test
    void "suite specific dependencies can be added via helper methods and aliases"(@TempDir Path tempDir) {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()

        project.pluginManager.apply('net.miwashi.test-suites')

        project.testSuites {
            blackbox 'smoke'
            dependency 'smoke', 'org.apache.httpcomponents.client5:httpclient5:5.2.1'
            dependencies('smoke') {
                runtimeOnly 'ch.qos.logback:logback-classic:1.4.14'
            }
        }

        def smokeImpl = project.configurations.getByName('smokeTestImplementation')
        def smokeRuntime = project.configurations.getByName('smokeTestRuntimeOnly')
        def unitImpl = project.configurations.getByName('testImplementation')

        assertTrue(smokeImpl.dependencies.any { it.name == 'httpclient5' })
        assertTrue(smokeRuntime.dependencies.any { it.name == 'logback-classic' })
        assertFalse(unitImpl.dependencies.any { it.name == 'httpclient5' })

        // Test configuration alias
        project.dependencies.add('smokeImplementation', 'com.google.guava:guava:33.0.0-jre')
        def smokeAlias = project.configurations.getByName('smokeImplementation')
        assertTrue(smokeImpl.extendsFrom.contains(smokeAlias))
    }

    @Test
    void "test reporter dispatches result to configured url and remains silent when unconfigured"() {
        List<Map<String, Object>> receivedRequests = Collections.synchronizedList([])

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext('/report', new HttpHandler() {
            @Override
            void handle(HttpExchange exchange) {
                byte[] bytes = exchange.requestBody.readAllBytes()
                String body = new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                def json = new JsonSlurper().parseText(body) as Map<String, Object>
                receivedRequests.add(json)
                byte[] response = '{"status":"ok"}'.getBytes()
                exchange.sendResponseHeaders(200, response.length)
                exchange.responseBody.write(response)
                exchange.close()
            }
        })
        server.start()

        try {
            String serverUrl = "http://localhost:${server.address.port}/report"

            TestDescriptor desc = [
                    getName       : { 'testUserEndpoint' },
                    getClassName  : { 'com.example.UserEndpointSmokeTest' },
                    getDisplayName: { 'testUserEndpoint()' }
            ] as TestDescriptor

            TestResult result = [
                    getResultType        : { TestResult.ResultType.SUCCESS },
                    getStartTime         : { 1000L },
                    getEndTime           : { 1050L },
                    getExceptions        : { [] },
                    getTestCount         : { 1L },
                    getSuccessfulTestCount: { 1L },
                    getFailedTestCount   : { 0L },
                    getSkippedTestCount  : { 0L }
            ] as TestResult

            // 1. Silent when reportUrl is null / not set
            TestReporter.reportTestResult(null, 'test-project', 'smokeTest', desc, result)
            assertEquals(0, receivedRequests.size(), "Should remain silent when reportUrl is null")

            // 2. Dispatches when reportUrl is provided
            TestReporter.reportTestResult(serverUrl, 'test-project', 'smokeTest', desc, result)
            assertEquals(1, receivedRequests.size())

            Map<String, Object> payload = receivedRequests[0]
            assertEquals('testResult', payload.event)
            assertEquals('test-project', payload.project)
            assertEquals('smokeTest', payload.suite)
            assertEquals('smoke', payload.suiteName)
            assertEquals('com.example.UserEndpointSmokeTest', payload.className)
            assertEquals('UserEndpointSmokeTest', payload.classSimpleName)
            assertEquals('testUserEndpoint', payload.methodName)
            assertEquals('testUserEndpoint()', payload.displayName)
            assertEquals('SUCCESS', payload.result)
            assertEquals(50, payload.durationMs)

            // 3. Suite reporting
            TestReporter.reportSuiteResult(serverUrl, 'test-project', 'smokeTest', desc, result)
            assertEquals(2, receivedRequests.size())
            Map<String, Object> suitePayload = receivedRequests[1]
            assertEquals('suiteResult', suitePayload.event)
            assertEquals('smokeTest', suitePayload.suite)
            assertEquals(1, suitePayload.totalTests)
            assertEquals(1, suitePayload.passed)
        } finally {
            server.stop(0)
        }
    }

    @Test
    void "test reporter sends custom headers when configured"() {
        List<String> receivedAuthHeaders = Collections.synchronizedList([])

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext('/report', new HttpHandler() {
            @Override
            void handle(HttpExchange exchange) {
                String auth = exchange.requestHeaders.getFirst('Authorization')
                if (auth) {
                    receivedAuthHeaders.add(auth)
                }
                exchange.requestBody.readAllBytes()
                byte[] response = '{"status":"ok"}'.getBytes()
                exchange.sendResponseHeaders(200, response.length)
                exchange.responseBody.write(response)
                exchange.close()
            }
        })
        server.start()

        try {
            String serverUrl = "http://localhost:${server.address.port}/report"

            TestDescriptor desc = [
                    getName       : { 'testAuth' },
                    getClassName  : { 'com.example.AuthTest' },
                    getDisplayName: { 'testAuth()' }
            ] as TestDescriptor

            TestResult result = [
                    getResultType        : { TestResult.ResultType.SUCCESS },
                    getStartTime         : { 2000L },
                    getEndTime           : { 2010L },
                    getExceptions        : { [] }
            ] as TestResult

            TestReporter.reportTestResult(serverUrl, 'test-project', 'integrationTest', desc, result, ['Authorization': 'Bearer secret-token'])
            assertEquals(1, receivedAuthHeaders.size())
            assertEquals('Bearer secret-token', receivedAuthHeaders[0])
        } finally {
            server.stop(0)
        }
    }

    @Test
    void "whitebox suite dependencies and extension report dsl configuration"(@TempDir Path tempDir) {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()

        project.pluginManager.apply('net.miwashi.test-suites')

        project.testSuites {
            reportUrl 'http://localhost:8080/reports'
            report {
                header 'X-Test-Run', 'run-123'
            }
            whitebox('component') {
                dependencies {
                    implementation 'org.mockito:mockito-core:5.11.0'
                }
            }
        }

        TestSuitesExtension ext = project.extensions.getByType(TestSuitesExtension)
        assertEquals('http://localhost:8080/reports', ext.getEffectiveReportUrl())
        assertEquals('run-123', ext.report.headers['X-Test-Run'])

        def compImpl = project.configurations.getByName('componentTestImplementation')
        assertTrue(compImpl.dependencies.any { it.name == 'mockito-core' })
    }

    @Test
    void "console logging and reporting defaults to true"(@TempDir Path tempDir) {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()

        project.pluginManager.apply('net.miwashi.test-suites')

        TestSuitesExtension ext = project.extensions.getByType(TestSuitesExtension)
        assertTrue(ext.isConsoleLoggingEnabled('test'))
        assertTrue(ext.isConsoleReportingEnabled('test'))

        ext.blackbox('smoke')
        assertTrue(ext.isConsoleLoggingEnabled('smokeTest'))
        assertTrue(ext.isConsoleReportingEnabled('smokeTest'))

        TestTask testTask = project.tasks.getByName('test') as TestTask
        assertTrue(testTask.testLogging.showStandardStreams)
    }

    @Test
    void "global console logging and reporting configuration"(@TempDir Path tempDir) {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()

        project.pluginManager.apply('net.miwashi.test-suites')

        project.testSuites {
            consoleLogging = false
            consoleReporting = false
            blackbox 'smoke'
        }

        TestSuitesExtension ext = project.extensions.getByType(TestSuitesExtension)
        assertFalse(ext.isConsoleLoggingEnabled('test'))
        assertFalse(ext.isConsoleReportingEnabled('test'))
        assertFalse(ext.isConsoleLoggingEnabled('smokeTest'))
        assertFalse(ext.isConsoleReportingEnabled('smokeTest'))

        TestTask smokeTask = project.tasks.getByName('smokeTest') as TestTask
        TestSuitesPlugin plugin = project.plugins.getPlugin('net.miwashi.test-suites') as TestSuitesPlugin
        plugin.configureTestLogging(smokeTask, ext)
        assertFalse(smokeTask.testLogging.showStandardStreams)
        assertTrue(smokeTask.testLogging.events.isEmpty())
    }

    @Test
    void "target level console logging and reporting overrides global configuration"(@TempDir Path tempDir) {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()

        project.pluginManager.apply('net.miwashi.test-suites')

        project.testSuites {
            consoleLogging = false
            consoleReporting = false

            blackbox('smoke') {
                consoleLogging = true
                consoleReporting = true
            }

            whitebox('component') {
                consoleLogging = false
                consoleReporting = true
            }

            suite('unit') {
                consoleLogging = true
                consoleReporting = false
            }

            tagged('regression') {
                consoleLogging = true
                consoleReporting = false
            }
        }

        TestSuitesExtension ext = project.extensions.getByType(TestSuitesExtension)

        // smoke overrides both to true
        assertTrue(ext.isConsoleLoggingEnabled('smokeTest'))
        assertTrue(ext.isConsoleReportingEnabled('smokeTest'))
        assertTrue(ext.isConsoleLoggingEnabled('smoke'))
        assertTrue(ext.isConsoleReportingEnabled('smoke'))

        // component has logging false, reporting true
        assertFalse(ext.isConsoleLoggingEnabled('componentTest'))
        assertTrue(ext.isConsoleReportingEnabled('componentTest'))

        // unit has logging true, reporting false
        assertTrue(ext.isConsoleLoggingEnabled('test'))
        assertFalse(ext.isConsoleReportingEnabled('test'))
        assertTrue(ext.isConsoleLoggingEnabled('unit'))
        assertFalse(ext.isConsoleReportingEnabled('unit'))

        // regression has logging true, reporting false
        assertTrue(ext.isConsoleLoggingEnabled('regressionTest'))
        assertFalse(ext.isConsoleReportingEnabled('regressionTest'))
    }

    @Test
    void "extension helper methods and project properties control console logging and reporting"(@TempDir Path tempDir) {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()

        project.pluginManager.apply('net.miwashi.test-suites')

        TestSuitesExtension ext = project.extensions.getByType(TestSuitesExtension)
        ext.blackbox('smoke')

        ext.consoleLogging('smoke', false)
        ext.consoleReporting('smoke', true)

        assertFalse(ext.isConsoleLoggingEnabled('smokeTest'))
        assertTrue(ext.isConsoleReportingEnabled('smokeTest'))

        // Unit test global override
        ext.consoleLogging(false)
        ext.consoleReporting(false)
        assertFalse(ext.isConsoleLoggingEnabled('test'))
        assertFalse(ext.isConsoleReportingEnabled('test'))
        // Target smoke still has explicit logging=false, reporting=true
        assertFalse(ext.isConsoleLoggingEnabled('smokeTest'))
        assertTrue(ext.isConsoleReportingEnabled('smokeTest'))
    }
}
