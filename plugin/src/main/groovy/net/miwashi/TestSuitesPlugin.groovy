package net.miwashi

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test

class TestSuitesPlugin implements Plugin<Project> {

    static final List<String> BLACK_BOX_SUITES = ['smoke', 'integration']

    @Override
    void apply(Project project) {
        project.pluginManager.apply(JavaPlugin)

        def sourceSets = project.extensions.getByType(SourceSetContainer)
        def configurations = project.configurations
        def tasks = project.tasks

        // Ensure directory structure exists
        ['src/test/common/java', 'src/test/common/resources',
         'src/test/unit/java', 'src/test/unit/resources'].each { path ->
            def dir = project.file(path)
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }

        // Common test code
        SourceSet common = sourceSets.create('testCommon')
        common.java.srcDirs = ['src/test/common/java']
        common.resources.srcDirs = ['src/test/common/resources']

        configurations.getByName('testCommonImplementation')
                .extendsFrom(configurations.getByName('testImplementation'))
        configurations.getByName('testCommonRuntimeOnly')
                .extendsFrom(configurations.getByName('testRuntimeOnly'))

        FileCollection commonOutput = common.output

        // White-box: unit tests see main + common
        SourceSet test = sourceSets.getByName('test')
        test.java.srcDirs = ['src/test/unit/java']
        test.resources.srcDirs = ['src/test/unit/resources']
        test.compileClasspath += commonOutput
        test.runtimeClasspath += commonOutput

        tasks.named('test', Test) { it.useJUnitPlatform() }

        Map<String, SourceSet> suites = [unit: test]

        // Black-box: only common + test dependencies, no main
        BLACK_BOX_SUITES.each { String name ->
            if (!project.file("src/test/${name}").exists()) return

            String ssName = "${name}Test"
            SourceSet ss = sourceSets.create(ssName)
            ss.java.srcDirs = ["src/test/${name}/java"]
            ss.resources.srcDirs = ["src/test/${name}/resources"]
            ss.compileClasspath = commonOutput +
                    configurations.getByName("${ssName}CompileClasspath")
            ss.runtimeClasspath = ss.output + commonOutput +
                    configurations.getByName("${ssName}RuntimeClasspath")

            configurations.getByName("${ssName}Implementation")
                    .extendsFrom(configurations.getByName('testImplementation'))
            configurations.getByName("${ssName}RuntimeOnly")
                    .extendsFrom(configurations.getByName('testRuntimeOnly'))

            suites[name] = ss

            def task = tasks.register(ssName, Test) { Test t ->
                t.description = "Runs ${name} tests."
                t.group = 'verification'
                t.testClassesDirs = ss.output.classesDirs
                t.classpath = ss.runtimeClasspath
                t.useJUnitPlatform()
                t.shouldRunAfter(tasks.named('test'))
            }

            tasks.named('check') { it.dependsOn(task) }
        }

        // Regression: @Regression-tagged tests from all suites
        tasks.register('regressionTest', Test) { Test t ->
            t.description = 'Runs @Regression tests from all suites.'
            t.group = 'verification'
            t.testClassesDirs = project.files(suites.values().collect { it.output.classesDirs })
            t.classpath = project.files(suites.values().collect { it.runtimeClasspath })
            t.useJUnitPlatform { it.includeTags('regression') }
        }

        // Shared settings for all test tasks
        tasks.withType(Test).configureEach { Test t ->
            BLACK_BOX_SUITES.each { n ->
                t.systemProperty("${n}.baseUrl",
                        project.findProperty("${n}.baseUrl") ?: 'http://localhost:8080')
            }

            t.testLogging {
                events 'started', 'passed', 'skipped', 'failed', 'standardOut', 'standardError'
                exceptionFormat = 'full'
                showExceptions = true
                showCauses = true
                showStackTraces = true
                showStandardStreams = true
            }

            String taskName = t.name
            t.afterSuite { desc, result ->
                if (!desc.parent) {
                    println("\n${taskName}: ${result.resultType} " +
                            "(${result.testCount} tests, ${result.successfulTestCount} passed, " +
                            "${result.failedTestCount} failed, ${result.skippedTestCount} skipped)")
                }
            }
        }
    }
}
