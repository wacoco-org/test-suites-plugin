package net.miwashi

class ReportConfig {
    String url
    Map<String, String> headers = [:]

    void url(String url) {
        this.url = url
    }

    void header(String key, String value) {
        if (key && value) {
            this.headers[key] = value
        }
    }

    void headers(Map<String, String> headers) {
        if (headers) {
            this.headers.putAll(headers)
        }
    }
}
