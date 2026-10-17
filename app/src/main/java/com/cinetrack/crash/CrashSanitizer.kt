package com.cinetrack.crash

/** Removes credentials and other secrets from crash text before it is stored or sent. */
object CrashSanitizer {
    const val MAX_STACK_TRACE = 65536
    private const val REDACTED = "<redacted>"

    private val bearer = Regex("""(?i)\bBearer\s+[^\s"',;]+""")
    private val authorization = Regex("""(?i)\bAuthorization\s*[:=]\s*[^\r\n]*""")
    private val keyValue = Regex(
        """(?i)\b((?:[\w-]*?)(?:api[_-]?key|x-api-key|access_token|refresh_token|client_secret|token|password)[\w-]*)(\s*[:=]\s*)("[^"]*"|'[^']*'|[^\s"',;&]+)""",
    )
    private val url = Regex("""(?i)\bhttps?://[^\s"'<>)\]]+""")
    private val longToken = Regex("""[0-9A-Za-z_\-]{32,}""")

    fun sanitize(text: String): String {
        // URLs first, so a query string is dropped whole before key/value rules see it.
        var out = url.replace(text) { stripQuery(it.value) }
        out = authorization.replace(out) { "Authorization: $REDACTED" }
        out = bearer.replace(out, "Bearer $REDACTED")
        out = keyValue.replace(out) { "${it.groupValues[1]}${it.groupValues[2]}$REDACTED" }
        out = longToken.replace(out, REDACTED)
        return out
    }

    fun sanitizeStackTrace(text: String): String = sanitize(text).take(MAX_STACK_TRACE)

    private fun stripQuery(value: String): String {
        val fragment = value.indexOf('#').let { if (it >= 0) it else value.length }
        val query = value.indexOf('?')
        return if (query in 0 until fragment) value.substring(0, query) + "?$REDACTED" else value
    }
}
