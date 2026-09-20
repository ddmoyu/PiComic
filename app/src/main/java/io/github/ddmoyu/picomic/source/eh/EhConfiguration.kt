package io.github.ddmoyu.picomic.source.eh

import org.json.JSONObject
import org.json.JSONTokener

data class EhConfiguration(val site: String, val original: Boolean, val ignoreWarning: Boolean, val subtitle: Boolean) {
    fun preferences() = mapOf("eh.site" to site, "eh.original" to original.toString(), "eh.warning" to ignoreWarning.toString(), "eh.subtitle" to subtitle.toString())
    companion object {
        fun decode(bytes: ByteArray): EhConfiguration {
            require(bytes.size in 1..4096) { "配置文件超过 4 KB 上限" }
            val parser = JSONTokener(bytes.toString(Charsets.UTF_8)); val root = parser.nextValue() as? JSONObject ?: error("配置格式无效")
            require(parser.nextClean() == '\u0000')
            require(root.keys().asSequence().toSet() == setOf("schemaVersion", "site", "original", "ignoreWarning", "subtitle")) { "配置包含未知字段；Cookie 不应放入设置文件" }
            require(root.get("schemaVersion") == 1)
            val site = root.get("site") as? String ?: error("站点无效")
            require(site in setOf("e-hentai.org", "exhentai.org"))
            fun flag(name: String) = root.get(name) as? Boolean ?: error("开关值无效")
            return EhConfiguration(site, flag("original"), flag("ignoreWarning"), flag("subtitle"))
        }
    }
}
