package net.miwashi

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test

class TestSuitesPlugin implements Plugin<Project> {

    final Map<String, SourceSet> suites = [:]
    final List<String> blackBoxSuites = []
    final List<String> whiteBoxSuites = []
    final Map<String, String> taggedSuites = [:]
    final Map<String, ConfigurableFileCollection> taggedClassesDirs = [:]
    final Map<String, ConfigurableFileCollection> taggedClasspath = [:]
    final Map<String, String> suiteReportUrls = [:]
    final Map<String, Boolean> suiteConsoleLogging = [:]
    final Map<String, Boolean> suiteConsoleReporting = [:]
    SourceSet commonSourceSet
    FileCollection commonOutput

    static class SuiteTestListener implements org.gradle.api.tasks.testing.TestListener, Serializable {
        private final String projectName
        private final String taskName
        private final boolean consoleReporting
        private final String reportUrl
        private final Map<String, String> headers

        SuiteTestListener(String projectName, String taskName, boolean consoleReporting, String reportUrl, Map<String, String> headers) {
            this.projectName = projectName
            this.taskName = taskName
            this.consoleReporting = consoleReporting
            this.reportUrl = reportUrl
            this.headers = headers ? new HashMap<>(headers) : null
        }

        @Override
        void beforeSuite(org.gradle.api.tasks.testing.TestDescriptor suite) {}

        @Override
        void afterSuite(org.gradle.api.tasks.testing.TestDescriptor desc, org.gradle.api.tasks.testing.TestResult result) {
            if (!desc.parent) {
                if (consoleReporting) {
                    println("\n${taskName}: ${result.resultType} " +
                            "(${result.testCount} tests, ${result.successfulTestCount} passed, " +
                            "${result.failedTestCount} failed, ${result.skippedTestCount} skipped)")
                }

                if (reportUrl) {
                    TestReporter.reportSuiteResult(reportUrl, projectName, taskName, desc, result, headers)
                }
            }
        }

        @Override
        void beforeTest(org.gradle.api.tasks.testing.TestDescriptor testDescriptor) {}

        @Override
        void afterTest(org.gradle.api.tasks.testing.TestDescriptor desc, org.gradle.api.tasks.testing.TestResult result) {
            if (reportUrl) {
                TestReporter.reportTestResult(reportUrl, projectName, taskName, desc, result, headers)
            }
        }
    }

    @Override
    void apply(Project project) {
        project.pluginManager.apply(JavaPlugin)

        def sourceSets = project.extensions.getByType(SourceSetContainer)
        def configurations = project.configurations
        def tasks = project.tasks

        // Ensure directory structure exists for common and unit
        ['src/test/common/java', 'src/test/common/resources',
         'src/test/unit/java', 'src/test/unit/resources'].each { path ->
            def dir = project.file(path)
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }

        // Common test code
        commonSourceSet = sourceSets.create('testCommon')
        commonSourceSet.java.srcDirs = ['src/test/common/java']
        commonSourceSet.resources.srcDirs = ['src/test/common/resources']

        configurations.getByName('testCommonImplementation')
                .extendsFrom(configurations.getByName('testImplementation'))
        configurations.getByName('testCommonRuntimeOnly')
                .extendsFrom(configurations.getByName('testRuntimeOnly'))

        createConfigurationAliases(project, 'common', 'testCommon')
        createConfigurationAliases(project, 'commonTest', 'testCommon')

        commonOutput = commonSourceSet.output

        // White-box: unit tests see main + common
        SourceSet test = sourceSets.getByName('test')
        test.java.srcDirs = ['src/test/unit/java']
        test.resources.srcDirs = ['src/test/unit/resources']
        test.compileClasspath += commonOutput
        test.runtimeClasspath += commonOutput

        createConfigurationAliases(project, 'unit', 'test')
        createConfigurationAliases(project, 'unitTest', 'test')

        tasks.named('test', Test) { it.useJUnitPlatform() }

        suites['unit'] = test
        whiteBoxSuites.add('unit')

        // Register the extension
        TestSuitesExtension extension = project.extensions.create('testSuites', TestSuitesExtension, project, this)

        // Shared settings for all test tasks
        tasks.withType(Test).configureEach { Test t ->
            configureTestLogging(t, true)
        }

        project.afterEvaluate {
            taggedClassesDirs.each { String baseName, ConfigurableFileCollection fc ->
                suites.values().each { SourceSet ss ->
                    fc.from(ss.output.classesDirs)
                }
            }
            taggedClasspath.each { String baseName, ConfigurableFileCollection fc ->
                suites.values().each { SourceSet ss ->
                    fc.from(ss.runtimeClasspath)
                }
                SourceSet main = sourceSets.findByName('main')
                if (main != null) {
                    fc.from(main.output)
                    fc.from(main.runtimeClasspath)
                }
            }

            tasks.withType(Test).configureEach { Test t ->
                String taskName = t.name
                configureTestLogging(t, extension.isConsoleLoggingEnabled(taskName))

                boolean consoleReporting = extension.isConsoleReportingEnabled(taskName)
                String reportUrl = extension.getEffectiveReportUrl(taskName)
                Map<String, String> headers = extension.report?.headers ? new HashMap<>(extension.report.headers) : null

                t.addTestListener(new SuiteTestListener(
                        project.name,
                        taskName,
                        consoleReporting,
                        reportUrl,
                        headers
                ))
            }
        }
    }

    static void configureTestLogging(Test t, boolean loggingEnabled) {
        if (loggingEnabled) {
            t.testLogging {
                events 'started', 'passed', 'skipped', 'failed', 'standardOut', 'standardError'
                exceptionFormat = 'full'
                showExceptions = true
                showCauses = true
                showStackTraces = true
                showStandardStreams = true
            }
        } else {
            t.testLogging {
                events = [] as Set
                showExceptions = false
                showCauses = false
                showStackTraces = false
                showStandardStreams = false
            }
        }
    }

    void configureTestLogging(Test t, TestSuitesExtension extension) {
        configureTestLogging(t, extension.isConsoleLoggingEnabled(t.name))
    }

    static String normalizeSuiteName(String name) {
        if (!name) return name
        if (name == 'test' || name == 'unit' || name == 'unitTest') {
            return 'unit'
        }
        if (name.endsWith('Test') && name.length() > 4) {
            return name.substring(0, name.length() - 4)
        }
        return name
    }

    void setSuiteConsoleLogging(String suiteName, Boolean enabled) {
        String baseName = normalizeSuiteName(suiteName)
        suiteConsoleLogging[baseName] = enabled
        suiteConsoleLogging["${baseName}Test"] = enabled
        if (baseName == 'unit') {
            suiteConsoleLogging['test'] = enabled
            suiteConsoleLogging['unit'] = enabled
            suiteConsoleLogging['unitTest'] = enabled
        }
    }

    Boolean getSuiteConsoleLogging(String suiteName) {
        String baseName = normalizeSuiteName(suiteName)
        if (suiteConsoleLogging.containsKey(baseName)) {
            return suiteConsoleLogging[baseName]
        }
        if (suiteConsoleLogging.containsKey(suiteName)) {
            return suiteConsoleLogging[suiteName]
        }
        if (baseName == 'unit' && suiteConsoleLogging.containsKey('test')) {
            return suiteConsoleLogging['test']
        }
        return null
    }

    void setSuiteConsoleReporting(String suiteName, Boolean enabled) {
        String baseName = normalizeSuiteName(suiteName)
        suiteConsoleReporting[baseName] = enabled
        suiteConsoleReporting["${baseName}Test"] = enabled
        if (baseName == 'unit') {
            suiteConsoleReporting['test'] = enabled
            suiteConsoleReporting['unit'] = enabled
            suiteConsoleReporting['unitTest'] = enabled
        }
    }

    Boolean getSuiteConsoleReporting(String suiteName) {
        String baseName = normalizeSuiteName(suiteName)
        if (suiteConsoleReporting.containsKey(baseName)) {
            return suiteConsoleReporting[baseName]
        }
        if (suiteConsoleReporting.containsKey(suiteName)) {
            return suiteConsoleReporting[suiteName]
        }
        if (baseName == 'unit' && suiteConsoleReporting.containsKey('test')) {
            return suiteConsoleReporting['test']
        }
        return null
    }

    void setSuiteReportUrl(String suiteName, String url) {
        String baseName = suiteName.endsWith('Test') && suiteName.length() > 4 ?
                suiteName.substring(0, suiteName.length() - 4) : suiteName
        suiteReportUrls[baseName] = url
        suiteReportUrls["${baseName}Test"] = url
    }

    String getSuiteReportUrl(String suiteName) {
        String baseName = suiteName.endsWith('Test') && suiteName.length() > 4 ?
                suiteName.substring(0, suiteName.length() - 4) : suiteName
        return suiteReportUrls[baseName] ?: suiteReportUrls[suiteName]
    }

    void createConfigurationAliases(Project project, String baseName, String ssName) {
        ['Implementation', 'RuntimeOnly', 'CompileOnly', 'AnnotationProcessor'].each { suffix ->
            String aliasName = "${baseName}${suffix}"
            String targetName = ssName == 'test' ? "test${suffix}" :
                    (ssName == 'testCommon' ? "testCommon${suffix}" : "${ssName}${suffix}")

            def targetConfig = project.configurations.findByName(targetName)
            if (targetConfig) {
                def aliasConfig = project.configurations.maybeCreate(aliasName)
                if (!targetConfig.extendsFrom.contains(aliasConfig)) {
                    targetConfig.extendsFrom(aliasConfig)
                }
            }
        }
    }

    void addSuiteDependency(Project project, String suiteName, String configType, Object notation) {
        String baseName = suiteName.endsWith('Test') && suiteName.length() > 4 ?
                suiteName.substring(0, suiteName.length() - 4) : suiteName

        String configName
        if (baseName == 'unit' || baseName == 'test') {
            configName = configType.startsWith('test') ? configType : "test${configType.capitalize()}"
        } else if (baseName == 'common' || baseName == 'testCommon') {
            configName = configType.startsWith('testCommon') ? configType : "testCommon${configType.capitalize()}"
        } else {
            String ssName = "${baseName}Test"
            String targetName = "${ssName}${configType.capitalize()}"
            String aliasName = "${baseName}${configType.capitalize()}"

            if (project.configurations.findByName(targetName)) {
                configName = targetName
            } else if (project.configurations.findByName(aliasName)) {
                configName = aliasName
            } else {
                def target = project.configurations.maybeCreate(targetName)
                def alias = project.configurations.maybeCreate(aliasName)
                if (!target.extendsFrom.contains(alias)) {
                    target.extendsFrom(alias)
                }
                configName = targetName
            }
        }

        project.dependencies.add(configName, notation)
    }

    void addBlackBoxSuite(Project project, String name) {
        String baseName = name.endsWith('Test') ? name.substring(0, name.length() - 4) : name
        String ssName = "${baseName}Test"

        if (suites.containsKey(baseName)) {
            return
        }

        def sourceSets = project.extensions.getByType(SourceSetContainer)
        def configurations = project.configurations
        def tasks = project.tasks

        ['src/test/' + baseName + '/java', 'src/test/' + baseName + '/resources'].each { path ->
            def dir = project.file(path)
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }

        SourceSet ss = sourceSets.create(ssName)
        ss.java.srcDirs = ["src/test/${baseName}/java"]
        ss.resources.srcDirs = ["src/test/${baseName}/resources"]
        ss.compileClasspath = commonOutput +
                configurations.getByName("${ssName}CompileClasspath")
        ss.runtimeClasspath = ss.output + commonOutput +
                configurations.getByName("${ssName}RuntimeClasspath")

        configurations.getByName("${ssName}Implementation")
                .extendsFrom(configurations.getByName('testImplementation'))
        configurations.getByName("${ssName}RuntimeOnly")
                .extendsFrom(configurations.getByName('testRuntimeOnly'))

        createConfigurationAliases(project, baseName, ssName)

        suites[baseName] = ss
        blackBoxSuites.add(baseName)
        taggedClassesDirs.values().each { it.from(ss.output.classesDirs) }
        taggedClasspath.values().each { it.from(ss.runtimeClasspath) }

        def task = tasks.register(ssName, Test) { Test t ->
            t.description = "Runs ${baseName} tests."
            t.group = 'verification'
            t.testClassesDirs = ss.output.classesDirs
            t.classpath = ss.runtimeClasspath
            t.useJUnitPlatform()
            t.shouldRunAfter(tasks.named('test'))
            t.systemProperty("${baseName}.baseUrl",
                    project.findProperty("${baseName}.baseUrl") ?: 'http://localhost:8080')
        }

        tasks.named('check') { it.dependsOn(task) }
    }

    void addWhiteBoxSuite(Project project, String name) {
        String baseName = name.endsWith('Test') ? name.substring(0, name.length() - 4) : name
        String ssName = "${baseName}Test"

        if (suites.containsKey(baseName)) {
            return
        }

        def sourceSets = project.extensions.getByType(SourceSetContainer)
        def configurations = project.configurations
        def tasks = project.tasks
        SourceSet main = sourceSets.getByName('main')

        ['src/test/' + baseName + '/java', 'src/test/' + baseName + '/resources'].each { path ->
            def dir = project.file(path)
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }

        SourceSet ss = sourceSets.create(ssName)
        ss.java.srcDirs = ["src/test/${baseName}/java"]
        ss.resources.srcDirs = ["src/test/${baseName}/resources"]
        ss.compileClasspath = main.output + commonOutput +
                configurations.getByName("${ssName}CompileClasspath")
        ss.runtimeClasspath = ss.output + main.output + commonOutput +
                configurations.getByName("${ssName}RuntimeClasspath")

        configurations.getByName("${ssName}Implementation")
                .extendsFrom(configurations.getByName('testImplementation'), configurations.getByName('implementation'))
        configurations.getByName("${ssName}RuntimeOnly")
                .extendsFrom(configurations.getByName('testRuntimeOnly'), configurations.getByName('runtimeOnly'))

        createConfigurationAliases(project, baseName, ssName)

        suites[baseName] = ss
        whiteBoxSuites.add(baseName)
        taggedClassesDirs.values().each { it.from(ss.output.classesDirs) }
        taggedClasspath.values().each { it.from(ss.runtimeClasspath) }

        def task = tasks.register(ssName, Test) { Test t ->
            t.description = "Runs ${baseName} tests."
            t.group = 'verification'
            t.testClassesDirs = ss.output.classesDirs
            t.classpath = ss.runtimeClasspath
            t.useJUnitPlatform()
            t.shouldRunAfter(tasks.named('test'))
        }

        tasks.named('check') { it.dependsOn(task) }
    }

    void addTaggedSuite(Project project, String name, String tag) {
        String baseName = name.endsWith('Test') ? name.substring(0, name.length() - 4) : name
        String taskName = "${baseName}Test"

        if (taggedSuites.containsKey(baseName)) {
            return
        }
        taggedSuites[baseName] = tag

        def tasks = project.tasks
        def sourceSets = project.extensions.getByType(SourceSetContainer)

        def classesDirs = project.objects.fileCollection()
        def runtimeClasspath = project.objects.fileCollection()

        taggedClassesDirs[baseName] = classesDirs
        taggedClasspath[baseName] = runtimeClasspath

        suites.values().each { SourceSet ss ->
            classesDirs.from(ss.output.classesDirs)
            runtimeClasspath.from(ss.runtimeClasspath)
        }
        SourceSet main = sourceSets.findByName('main')
        if (main != null) {
            runtimeClasspath.from(main.output)
            runtimeClasspath.from(main.runtimeClasspath)
        }

        tasks.register(taskName, Test) { Test t ->
            t.description = "Runs @${tag.capitalize()} tests from all suites."
            t.group = 'verification'
            t.testClassesDirs = classesDirs
            t.classpath = runtimeClasspath
            t.useJUnitPlatform { it.includeTags(tag) }
            t.shouldRunAfter(tasks.named('test'))
        }
    }
}
