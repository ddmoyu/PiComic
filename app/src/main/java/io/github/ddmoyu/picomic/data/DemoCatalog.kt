package io.github.ddmoyu.picomic.data

import java.text.Normalizer

enum class Source(val title: String, val shortTitle: String) {
    PICACG("picacg", "picacg"), EHENTAI("E-Hentai / ExHentai", "E-Hentai"),
    JMCOMIC("禁漫天堂", "禁漫天堂"), HITOMI("Hitomi", "Hitomi"),
    HTCOMIC("绅士漫画", "绅士漫画"), NHENTAI("nhentai", "nhentai");
    // Presentation order is independent of the enum identity used by stored data.
    val displayIndex: Int get() = displayOrder.indexOf(this)
    companion object {
        val displayOrder = listOf(HITOMI, JMCOMIC, HTCOMIC, NHENTAI, EHENTAI, PICACG)
    }
    val categories: List<String> get() = when (this) {
        PICACG -> listOf("全部", "日常", "奇幻", "治愈", "冒险", "全彩", "长篇", "短篇", "单行本", "同人")
        EHENTAI -> listOf("全部", "漫画", "同人志", "画师 CG", "游戏 CG", "图像集")
        JMCOMIC -> listOf("全部", "单行本", "同人", "短篇", "连载", "全彩", "日常", "奇幻")
        HITOMI -> listOf("全部", "漫画", "同人志", "画师 CG", "游戏 CG", "作者", "社团", "系列")
        HTCOMIC -> listOf("全部", "同人志", "单行本", "杂志", "短篇", "连载", "日常")
        NHENTAI -> listOf("全部", "标签", "作者", "社团", "系列", "漫画", "同人志")
    }
}

data class Comic(val id: Int, val title: String, val author: String, val category: String,
                 val chapters: Int, val pages: Int, val language: String, val description: String)
data class ReadingPosition(
    val source: Source, val comicId: Int, val chapter: Int, val page: Int,
    val offsetRatio: Float = 0f, val mode: String = "纵向连续", val updatedAt: Long = 0L
)
data class DemoDownload(val source: Source, val comicId: Int, val chapter: Int, val paused: Boolean = false)

object DemoCatalog {
    val comics = listOf(
        Comic(0,"雨后的第七站","青空工作室","日常",8,24,"Chinese","离开城市之前，她决定再坐一次那列慢车。一个关于雨、错过的站台，以及重新出发的故事。"),
        Comic(1,"月面邮差","无声岛","奇幻",6,32,"English","每一封未寄出的信，都会抵达月球。新来的邮差开始寻找最后一封信的收件人。"),
        Comic(2,"鲸落电台","深蓝编辑部","冒险",12,28,"Japanese","在海底最后一座电台，一段来自遥远夏天的声音，让安静的城市重新开始呼吸。"),
        Comic(3,"森林事务所","绿间","治愈",5,20,"Chinese","门牌藏在树叶之后。这里处理丢失的季节、迷路的风，以及一些微不足道的小心事。"),
        Comic(4,"昨日咖啡","日光社","日常",1,36,"Chinese","只在昨天开门的咖啡店，今天迎来了第一位客人。"),
        Comic(5,"夏日航线","千帆","冒险",9,24,"English","带上一张旧地图，向着海风出发。夏天比我们想象的更长一点。"),
        Comic(6,"星星收集员","木野","奇幻",3,22,"Japanese","她在天亮前，把落在屋顶的星星一颗颗收好。"),
        Comic(7,"风住的街角","淡青","治愈",4,26,"Chinese","街角的花店记得所有来过这里的风。"),
        Comic(8,"蓝色远行","云川","冒险",1,30,"Chinese","我们的旅程，从没有名字的海岸开始。")
    )
    fun comic(id: Int) = comics.firstOrNull { it.id == id } ?: comics.first()
    fun key(source: Source, id: Int) = "${source.name}:$id"
    fun filter(query: String, category: String, blocked: List<String>, languages: Set<String>): List<Comic> = comics.filter { c ->
        val content = normalize("${c.title} ${c.author} ${c.category}")
        (query.isBlank() || content.contains(normalize(query))) &&
            (category == "全部" || c.category == category || c.language == category) &&
            blocked.none { it.isNotBlank() && content.contains(normalize(it)) } &&
            (languages.isEmpty() || c.language in languages)
    }
    fun normalize(value: String) = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).lowercase(java.util.Locale.ROOT)
}
