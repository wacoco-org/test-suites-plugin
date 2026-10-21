package net.miwashi

import org.gradle.api.Project

class SuiteSpec {
    String name
    String reportUrl
    Boolean consoleLogging
    Boolean consoleReporting
    final Project project
    final TestSuitesPlugin plugin

    SuiteSpec(String name, Project project, TestSuitesPlugin plugin) {
        this.name = name
        this.project = project
        this.plugin = plugin
    }

    void reportUrl(String url) {
        this.reportUrl = url
    }

    void consoleLogging(boolean enabled) {
        this.consoleLogging = enabled
    }

    void consoleReporting(boolean enabled) {
        this.consoleReporting = enabled
    }

    void consoleReport(boolean enabled) {
        this.consoleReporting = enabled
    }

    void testResultReporting(boolean enabled) {
        this.consoleReporting = enabled
    }

    void reportConsole(boolean enabled) {
        this.consoleReporting = enabled
    }

    void setConsoleReport(boolean enabled) {
        this.consoleReporting = enabled
    }

    void setTestResultReporting(boolean enabled) {
        this.consoleReporting = enabled
    }

    void dependencies(Closure closure) {
        def handler = new SuiteDependenciesHandler(project, name, plugin)
        closure.delegate = handler
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
    }

    void dependency(Object notation) {
        plugin.addSuiteDependency(project, name, 'implementation', notation)
    }

    void dependency(String configuration, Object notation) {
        plugin.addSuiteDependency(project, name, configuration, notation)
    }

    void implementation(Object... notations) {
        notations.each { notation ->
            plugin.addSuiteDependency(project, name, 'implementation', notation)
        }
    }

    void runtimeOnly(Object... notations) {
        notations.each { notation ->
            plugin.addSuiteDependency(project, name, 'runtimeOnly', notation)
        }
    }

    void compileOnly(Object... notations) {
        notations.each { notation ->
            plugin.addSuiteDependency(project, name, 'compileOnly', notation)
        }
    }

    void annotationProcessor(Object... notations) {
        notations.each { notation ->
            plugin.addSuiteDependency(project, name, 'annotationProcessor', notation)
        }
    }
}
