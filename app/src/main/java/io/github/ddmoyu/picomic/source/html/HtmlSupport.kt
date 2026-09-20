package io.github.ddmoyu.picomic.source.html

import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.network.*
import okhttp3.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

const val BROWSER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
fun parseChanged(site: String) = ContentFailure(ContentFailureKind.PARSE, "$site 页面结构发生变化，请等待来源更新")
fun positiveId(value: String, site: String) = value.takeIf { it.matches(Regex("[1-9][0-9]{0,11}")) } ?: throw parseChanged(site)
fun secureUrl(base: HttpUrl, raw: String, site: String): HttpUrl {
    val normalized = raw.trim().let { if (it.startsWith("//")) "https:$it" else it }
    val url = base.resolve(normalized) ?: throw parseChanged(site)
    if (!url.isHttps || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null) throw parseChanged(site)
    return url
}
fun parseHtml(bytes: ByteArray, url: HttpUrl, site: String): Document {
    val document = Jsoup.parse(bytes.toString(Charsets.UTF_8), url.toString())
    if (document.selectFirst("#challenge-form, #cf-challenge-running") != null || document.title().contains("Just a moment", true))
        throw ContentFailure(ContentFailureKind.LOGIN, "$site 需要在网页中完成验证")
    return document
}
/** Extract exactly one JSON object from a known call; strings/escapes never change brace depth. No JavaScript is executed. */
fun jsonArgument(script: String, call: String, site: String): String {
    val start = script.indexOf(call).takeIf { it >= 0 } ?: throw parseChanged(site)
    val open = start + call.length
    var index = open
    while (index < script.length && script[index].isWhitespace()) index++
    if (script.getOrNull(index) != '{') throw parseChanged(site)
    val first = index
    var depth = 0; var quoted = false; var escaped = false
    while (index < script.length) {
        val char = script[index++]
        if (quoted) { if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false }
        else when (char) { '"' -> quoted = true; '{', '[' -> { depth++; if (depth > 64) throw parseChanged(site) }; '}', ']' -> if (--depth == 0) {
            var tail = index
            while (tail < script.length && script[tail].isWhitespace()) tail++
            if (script.getOrNull(tail) != ')') throw parseChanged(site)
            return script.substring(first, index)
        } }
    }
    throw parseChanged(site)
}
/** Some data-only site literals use trailing commas. Remove only commas outside strings before ] or }. */
fun withoutTrailingCommas(value: String): String {
    val result = StringBuilder(value.length)
    var quoted = false; var escaped = false
    value.forEachIndexed { index, char ->
        if (quoted) {
            result.append(char)
            if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
        } else {
            if (char == '"') quoted = true
            if (char == ',') {
                var next = index + 1
                while (next < value.length && value[next].isWhitespace()) next++
                if (value.getOrNull(next) in listOf(']', '}')) return@forEachIndexed
            }
            result.append(char)
        }
    }
    return result.toString()
}
