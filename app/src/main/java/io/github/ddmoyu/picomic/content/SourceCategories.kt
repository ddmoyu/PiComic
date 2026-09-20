package io.github.ddmoyu.picomic.content

import io.github.ddmoyu.picomic.data.Source

data class ContentCategory(val value: String, val label: String = value)
data class ContentCategoryGroup(val title: String, val items: List<ContentCategory>)

/** Public directory metadata; reading a catalog must not require a valid account session. */
object SourceCategories {
    val eh = linkedMapOf("同人志" to 2, "漫画" to 4, "画师 CG" to 8, "游戏 CG" to 16,
        "西方" to 512, "普通内容" to 256, "图集" to 32, "角色扮演" to 64, "亚洲真人" to 128, "其他" to 1)
    val ht = linkedMapOf("同人志" to 5, "同人汉化" to 1, "日文同人" to 12, "英文同人" to 16,
        "CG 画集" to 2, "3D 漫画" to 22, "写真 Cosplay" to 3,
        "单行本" to 6, "中文单行本" to 9, "日文单行本" to 13, "英文单行本" to 17,
        "杂志短篇" to 7, "中文杂志短篇" to 10, "日文杂志短篇" to 14, "英文杂志短篇" to 18,
        "韩漫" to 19, "韩漫汉化" to 20, "韩漫原文" to 21, "AI 作品" to 37)
    val hitomiLanguages = linkedMapOf("中文" to "chinese", "英文" to "english", "日文" to "japanese")
    val hitomiTypes = linkedMapOf("同人志" to "doujinshi", "漫画" to "manga", "画师 CG" to "artistcg",
        "游戏 CG" to "gamecg", "图集" to "imageset", "动画" to "anime")
    val nhLanguages = linkedMapOf("Chinese" to "29963", "English" to "12227", "Japanese" to "6346")
    val nhTypes = linkedMapOf("同人志" to "doujinshi", "漫画" to "manga")

    fun fixed(source: Source): List<String>? = when (source) {
        Source.EHENTAI -> eh.keys.toList()
        Source.HTCOMIC -> ht.keys.toList()
        Source.HITOMI -> hitomiTypes.keys.toList() + hitomiLanguages.keys
        Source.NHENTAI -> nhTypes.keys.toList() + nhLanguages.keys
        else -> null
    }

    fun groups(source: Source, values: List<String>): List<ContentCategoryGroup> = values.groupBy { name ->
        when (source) {
            Source.EHENTAI -> "内容类型"
            Source.HITOMI -> if (name in hitomiLanguages) "语言" else "内容类型"
            Source.NHENTAI -> if (name in nhLanguages) "语言" else "内容类型"
            Source.HTCOMIC -> when (ht[name]) {
                5, 1, 12, 16 -> "同人志"
                6, 9, 13, 17 -> "单行本"
                7, 10, 14, 18 -> "杂志短篇"
                19, 20, 21 -> "韩漫"
                else -> "其他"
            }
            else -> "分类"
        }
    }.map { (title, names) -> ContentCategoryGroup(title, names.map { ContentCategory(it) }) }
}
