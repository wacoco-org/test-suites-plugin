package net.miwashi

import org.gradle.api.Project

class SuiteDependenciesHandler {
    final Project project
    final String suiteName
    final TestSuitesPlugin plugin

    SuiteDependenciesHandler(Project project, String suiteName, TestSuitesPlugin plugin) {
        this.project = project
        this.suiteName = suiteName
        this.plugin = plugin
    }

    void implementation(Object... notations) {
        notations.each { notation ->
            plugin.addSuiteDependency(project, suiteName, 'implementation', notation)
        }
    }

    void runtimeOnly(Object... notations) {
        notations.each { notation ->
            plugin.addSuiteDependency(project, suiteName, 'runtimeOnly', notation)
        }
    }

    void compileOnly(Object... notations) {
        notations.each { notation ->
            plugin.addSuiteDependency(project, suiteName, 'compileOnly', notation)
        }
    }

    void annotationProcessor(Object... notations) {
        notations.each { notation ->
            plugin.addSuiteDependency(project, suiteName, 'annotationProcessor', notation)
        }
    }

    def methodMissing(String name, Object args) {
        if (args instanceof Object[]) {
            (args as Object[]).each { notation ->
                plugin.addSuiteDependency(project, suiteName, name, notation)
            }
        } else if (args != null) {
            plugin.addSuiteDependency(project, suiteName, name, args)
        }
    }
}
