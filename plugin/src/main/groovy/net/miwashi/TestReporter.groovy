package net.miwashi

import groovy.json.JsonOutput
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets

class TestReporter {

    static void reportTestResult(String reportUrl, String projectName, String taskName,
                                 TestDescriptor desc, TestResult result,
                                 Map<String, String> headers = null) {
        if (!reportUrl) {
            return
        }
        try {
            String className = desc.className ?: ''
            String simpleName = className ? className.tokenize('.').last() : ''
            String suiteName = taskName.endsWith('Test') && taskName.length() > 4 ?
                    taskName.substring(0, taskName.length() - 4) : taskName

            Map<String, Object> payload = [
                    event          : 'testResult',
                    project        : projectName,
                    suite          : taskName,
                    suiteName      : suiteName,
                    className      : className,
                    classSimpleName: simpleName,
                    testName       : desc.name,
                    methodName     : desc.name,
                    displayName    : desc.displayName,
                    result         : result.resultType.toString(),
                    status         : result.resultType.toString(),
                    durationMs     : (result.endTime - result.startTime),
                    startTime      : result.startTime,
                    endTime        : result.endTime,
                    error          : result.exceptions ? result.exceptions.collect { it.message ?: it.toString() }.join('\n') : null
            ]

            sendJson(reportUrl, payload, headers)
        } catch (Throwable ignored) {
            // Silently ignore to avoid failing test runs when reporting server is unreachable
        }
    }

    static void reportSuiteResult(String reportUrl, String projectName, String taskName,
                                  TestDescriptor desc, TestResult result,
                                  Map<String, String> headers = null) {
        if (!reportUrl) {
            return
        }
        try {
            String suiteName = taskName.endsWith('Test') && taskName.length() > 4 ?
                    taskName.substring(0, taskName.length() - 4) : taskName

            Map<String, Object> payload = [
                    event              : 'suiteResult',
                    project            : projectName,
                    suite              : taskName,
                    suiteName          : suiteName,
                    result             : result.resultType.toString(),
                    status             : result.resultType.toString(),
                    totalTests         : result.testCount,
                    passed             : result.successfulTestCount,
                    failed             : result.failedTestCount,
                    skipped            : result.skippedTestCount,
                    durationMs         : (result.endTime - result.startTime),
                    startTime          : result.startTime,
                    endTime            : result.endTime
            ]

            sendJson(reportUrl, payload, headers)
        } catch (Throwable ignored) {
            // Silently ignore to avoid failing test runs when reporting server is unreachable
        }
    }

    static void sendJson(String endpoint, Map<String, Object> payload, Map<String, String> headers = null) {
        String json = JsonOutput.toJson(payload)
        byte[] body = json.getBytes(StandardCharsets.UTF_8)

        URI uri = URI.create(endpoint)
        URL url = uri.toURL()
        HttpURLConnection conn = (HttpURLConnection) url.openConnection()
        try {
            conn.requestMethod = 'POST'
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty('Content-Type', 'application/json; charset=UTF-8')
            conn.setRequestProperty('Accept', 'application/json')
            if (headers) {
                headers.each { k, v ->
                    if (k && v) {
                        conn.setRequestProperty(k, v)
                    }
                }
            }
            conn.outputStream.withCloseable { os ->
                os.write(body)
                os.flush()
            }
            int responseCode = conn.responseCode
            if (responseCode >= 200 && responseCode < 400) {
                conn.inputStream?.close()
            } else {
                conn.errorStream?.close()
            }
        } finally {
            conn.disconnect()
        }
    }
}
