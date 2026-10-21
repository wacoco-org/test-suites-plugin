package net.miwashi

import org.gradle.api.Project

class TestSuitesExtension {
    final Project project
    final TestSuitesPlugin plugin
    String reportUrl
    final ReportConfig report = new ReportConfig()
    Boolean consoleLogging = null
    Boolean consoleReporting = null

    TestSuitesExtension(Project project, TestSuitesPlugin plugin) {
        this.project = project
        this.plugin = plugin
    }

    void reportUrl(String url) {
        this.reportUrl = url
        this.report.url = url
    }

    void report(Closure closure) {
        closure.delegate = report
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
        if (report.url && !this.reportUrl) {
            this.reportUrl = report.url
        }
    }

    void reporting(Closure closure) {
        report(closure)
    }

    void consoleLogging(boolean enabled) {
        this.consoleLogging = enabled
    }

    void consoleLogging(String target, boolean enabled) {
        plugin.setSuiteConsoleLogging(target, enabled)
    }

    void consoleReporting(boolean enabled) {
        this.consoleReporting = enabled
    }

    void consoleReporting(String target, boolean enabled) {
        plugin.setSuiteConsoleReporting(target, enabled)
    }

    void consoleReport(boolean enabled) {
        this.consoleReporting = enabled
    }

    void consoleReport(String target, boolean enabled) {
        plugin.setSuiteConsoleReporting(target, enabled)
    }

    void testResultReporting(boolean enabled) {
        this.consoleReporting = enabled
    }

    void testResultReporting(String target, boolean enabled) {
        plugin.setSuiteConsoleReporting(target, enabled)
    }

    void reportConsole(boolean enabled) {
        this.consoleReporting = enabled
    }

    void reportConsole(String target, boolean enabled) {
        plugin.setSuiteConsoleReporting(target, enabled)
    }

    void setConsoleReport(boolean enabled) {
        this.consoleReporting = enabled
    }

    Boolean getConsoleReport() {
        return this.consoleReporting
    }

    void setTestResultReporting(boolean enabled) {
        this.consoleReporting = enabled
    }

    Boolean getTestResultReporting() {
        return this.consoleReporting
    }

    boolean isConsoleLoggingEnabled(String taskName = null) {
        if (taskName) {
            Boolean suiteSetting = plugin.getSuiteConsoleLogging(taskName)
            if (suiteSetting != null) {
                return suiteSetting
            }
            String baseName = TestSuitesPlugin.normalizeSuiteName(taskName)
            def targetProp = project.findProperty("testSuites.${taskName}.consoleLogging") ?:
                    project.findProperty("testSuites.${baseName}.consoleLogging")
            if (targetProp != null) {
                return Boolean.valueOf(targetProp.toString())
            }
        }

        def globalProp = project.findProperty('testSuites.consoleLogging') ?:
                project.findProperty('consoleLogging')
        if (globalProp != null) {
            return Boolean.valueOf(globalProp.toString())
        }

        if (this.consoleLogging != null) {
            return this.consoleLogging
        }

        return true // default: enabled
    }

    boolean isConsoleReportingEnabled(String taskName = null) {
        if (taskName) {
            Boolean suiteSetting = plugin.getSuiteConsoleReporting(taskName)
            if (suiteSetting != null) {
                return suiteSetting
            }
            String baseName = TestSuitesPlugin.normalizeSuiteName(taskName)
            def targetProp = project.findProperty("testSuites.${taskName}.consoleReporting") ?:
                    project.findProperty("testSuites.${baseName}.consoleReporting") ?:
                    project.findProperty("testSuites.${taskName}.consoleReport") ?:
                    project.findProperty("testSuites.${baseName}.consoleReport")
            if (targetProp != null) {
                return Boolean.valueOf(targetProp.toString())
            }
        }

        def globalProp = project.findProperty('testSuites.consoleReporting') ?:
                project.findProperty('testSuites.consoleReport') ?:
                project.findProperty('consoleReporting')
        if (globalProp != null) {
            return Boolean.valueOf(globalProp.toString())
        }

        if (this.consoleReporting != null) {
            return this.consoleReporting
        }

        return true // default: enabled
    }

    String getEffectiveReportUrl(String taskName = null) {
        if (taskName) {
            String suiteUrl = plugin.getSuiteReportUrl(taskName)
            if (suiteUrl) {
                return suiteUrl
            }
        }
        if (report?.url) {
            return report.url
        }
        if (reportUrl) {
            return reportUrl
        }
        return (project.findProperty('testSuites.reportUrl') ?:
                project.findProperty('reportUrl') ?:
                System.getProperty('testSuites.reportUrl')) as String
    }

    void blackbox(String... names) {
        names.each { name -> plugin.addBlackBoxSuite(project, name) }
    }

    void blackbox(List<String> names) {
        names.each { name -> plugin.addBlackBoxSuite(project, name) }
    }

    void blackbox(String name, Closure configureClosure) {
        plugin.addBlackBoxSuite(project, name)
        def spec = new SuiteSpec(name, project, plugin)
        configureClosure.delegate = spec
        configureClosure.resolveStrategy = Closure.DELEGATE_FIRST
        configureClosure.call()
        if (spec.reportUrl) {
            plugin.setSuiteReportUrl(name, spec.reportUrl)
        }
        if (spec.consoleLogging != null) {
            plugin.setSuiteConsoleLogging(name, spec.consoleLogging)
        }
        if (spec.consoleReporting != null) {
            plugin.setSuiteConsoleReporting(name, spec.consoleReporting)
        }
    }

    void blackBox(String... names) {
        blackbox(names)
    }

    void blackBox(List<String> names) {
        blackbox(names)
    }

    void blackBox(String name, Closure configureClosure) {
        blackbox(name, configureClosure)
    }

    void whitebox(String... names) {
        names.each { name -> plugin.addWhiteBoxSuite(project, name) }
    }

    void whitebox(List<String> names) {
        names.each { name -> plugin.addWhiteBoxSuite(project, name) }
    }

    void whitebox(String name, Closure configureClosure) {
        plugin.addWhiteBoxSuite(project, name)
        def spec = new SuiteSpec(name, project, plugin)
        configureClosure.delegate = spec
        configureClosure.resolveStrategy = Closure.DELEGATE_FIRST
        configureClosure.call()
        if (spec.reportUrl) {
            plugin.setSuiteReportUrl(name, spec.reportUrl)
        }
        if (spec.consoleLogging != null) {
            plugin.setSuiteConsoleLogging(name, spec.consoleLogging)
        }
        if (spec.consoleReporting != null) {
            plugin.setSuiteConsoleReporting(name, spec.consoleReporting)
        }
    }

    void whiteBox(String... names) {
        whitebox(names)
    }

    void whiteBox(List<String> names) {
        whitebox(names)
    }

    void whiteBox(String name, Closure configureClosure) {
        whitebox(name, configureClosure)
    }

    void suite(String name, Closure configureClosure) {
        def spec = new SuiteSpec(name, project, plugin)
        configureClosure.delegate = spec
        configureClosure.resolveStrategy = Closure.DELEGATE_FIRST
        configureClosure.call()
        if (spec.reportUrl) {
            plugin.setSuiteReportUrl(name, spec.reportUrl)
        }
        if (spec.consoleLogging != null) {
            plugin.setSuiteConsoleLogging(name, spec.consoleLogging)
        }
        if (spec.consoleReporting != null) {
            plugin.setSuiteConsoleReporting(name, spec.consoleReporting)
        }
    }

    void dependencies(String suiteName, Closure configureClosure) {
        def handler = new SuiteDependenciesHandler(project, suiteName, plugin)
        configureClosure.delegate = handler
        configureClosure.resolveStrategy = Closure.DELEGATE_FIRST
        configureClosure.call()
    }

    void dependencies(String suiteName, Object... notations) {
        notations.each { notation ->
            plugin.addSuiteDependency(project, suiteName, 'implementation', notation)
        }
    }

    void dependency(String suiteName, Object notation) {
        plugin.addSuiteDependency(project, suiteName, 'implementation', notation)
    }

    void dependency(String suiteName, String configuration, Object notation) {
        plugin.addSuiteDependency(project, suiteName, configuration, notation)
    }

    void tagged(String name, String tag = null) {
        plugin.addTaggedSuite(project, name, tag ?: name)
    }

    void tagged(String name, Closure closure) {
        plugin.addTaggedSuite(project, name, name)
        def spec = new SuiteSpec(name, project, plugin)
        closure.delegate = spec
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
        if (spec.reportUrl) {
            plugin.setSuiteReportUrl(name, spec.reportUrl)
        }
        if (spec.consoleLogging != null) {
            plugin.setSuiteConsoleLogging(name, spec.consoleLogging)
        }
        if (spec.consoleReporting != null) {
            plugin.setSuiteConsoleReporting(name, spec.consoleReporting)
        }
    }

    void tagged(String name, String tag, Closure closure) {
        plugin.addTaggedSuite(project, name, tag)
        def spec = new SuiteSpec(name, project, plugin)
        closure.delegate = spec
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
        if (spec.reportUrl) {
            plugin.setSuiteReportUrl(name, spec.reportUrl)
        }
        if (spec.consoleLogging != null) {
            plugin.setSuiteConsoleLogging(name, spec.consoleLogging)
        }
        if (spec.consoleReporting != null) {
            plugin.setSuiteConsoleReporting(name, spec.consoleReporting)
        }
    }

    void tagged(Map<String, ?> args) {
        String name = args.name as String
        String tag = (args.tag ?: name) as String
        plugin.addTaggedSuite(project, name, tag)
    }

    void tagged(Map<String, ?> args, Closure closure) {
        String name = args.name as String
        String tag = (args.tag ?: name) as String
        plugin.addTaggedSuite(project, name, tag)
        def spec = new SuiteSpec(name, project, plugin)
        closure.delegate = spec
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
        if (spec.reportUrl) {
            plugin.setSuiteReportUrl(name, spec.reportUrl)
        }
        if (spec.consoleLogging != null) {
            plugin.setSuiteConsoleLogging(name, spec.consoleLogging)
        }
        if (spec.consoleReporting != null) {
            plugin.setSuiteConsoleReporting(name, spec.consoleReporting)
        }
    }

    void taggedSuite(String name, String tag = null) {
        tagged(name, tag)
    }

    void taggedSuite(String name, Closure closure) {
        tagged(name, closure)
    }

    void taggedSuite(String name, String tag, Closure closure) {
        tagged(name, tag, closure)
    }

    void taggedSuite(Map<String, ?> args) {
        tagged(args)
    }

    void taggedSuite(Map<String, ?> args, Closure closure) {
        tagged(args, closure)
    }
}
