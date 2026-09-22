@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.ddmoyu.picomic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ddmoyu.picomic.data.*
import kotlinx.coroutines.launch

@Composable fun ComicGrid(comics: List<Comic>, onOpen: (Comic) -> Unit, modifier: Modifier = Modifier) {
    if(comics.isEmpty()) EmptyState("没有找到作品", "试试其他关键词，或调整内容筛选。", Glyph.Search)
    else LazyVerticalGrid(columns=GridCells.Adaptive(105.dp), modifier=modifier.fillMaxSize(), contentPadding=PaddingValues(20.dp,16.dp,20.dp,24.dp), horizontalArrangement=Arrangement.spacedBy(12.dp), verticalArrangement=Arrangement.spacedBy(22.dp)) {
        items(comics, key={it.id}) { comic -> ComicCard(comic) { onOpen(comic) } }
    }
}
@Composable fun BrowseScreen(categories: Boolean, ui: UiState, vm: AppViewModel, open: (Source,Comic) -> Unit, category: (Source,String) -> Unit) {
    val pager = rememberPagerState(initialPage = ui.source.ordinal) { Source.entries.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager) {
        // A restored route may have an older page than the source chosen in search.
        pager.scrollToPage(ui.source.ordinal)
        snapshotFlow { pager.settledPage }.collect { page ->
            val source = Source.entries[page]
            if (vm.state.value.source != source) vm.source(source)
        }
    }
    Column(Modifier.fillMaxSize()) {
        SourceTabs(Source.entries[pager.currentPage]) { source ->
            vm.source(source)
            scope.launch { pager.animateScrollToPage(source.ordinal) }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag(if (categories) "category-pages" else "discover-pages"),
            key = { Source.entries[it].name },
            verticalAlignment = Alignment.Top
        ) { page ->
            val source = Source.entries[page]
            if(categories) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                SectionTitle(source.title)
                Note("按平台浏览分类 · 示例内容")
                FlowRow(Modifier.padding(horizontal=20.dp), horizontalArrangement=Arrangement.spacedBy(10.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    source.categories.forEach { label -> FilledTonalButton(onClick={category(source,label)}, contentPadding=PaddingValues(horizontal=18.dp, vertical=12.dp)) { Text(label) } }
                }
                SectionTitle("语言")
                FlowRow(Modifier.padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    listOf("Chinese","English","Japanese").forEach { language -> AssistChip(onClick={category(source,language)},label={Text(language)}) }
                }
            } else Column(Modifier.fillMaxSize()) {
                Note("${source.shortTitle} · 本地示意作品", Modifier.fillMaxWidth())
                ComicGrid(DemoCatalog.filter("","全部",ui.keywords,ui.languages),{open(source,it)})
            }
        }
    }
}
@Composable fun SearchScreen(ui: UiState, vm: AppViewModel, back: () -> Unit, open: (Comic) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    val keyboard=LocalSoftwareKeyboardController.current
    fun submit(value: String=text) { text=value;submitted=value.trim();vm.query(value);keyboard?.hide() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp,4.dp,12.dp,4.dp),verticalAlignment=Alignment.CenterVertically) {
            IconAction(Glyph.Back,"返回",back)
            OutlinedTextField(text,{text=it;if(it.isEmpty()) submitted=""},Modifier.weight(1f),placeholder={Text("作品、作者或标签",fontSize=13.sp)},singleLine=true,
                shape=androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={submit()}),
                trailingIcon={if(text.isNotEmpty()) IconAction(Glyph.Close,"清空关键词") { text="";submitted="" }})
            TextButton(onClick={submit()}) { Text("搜索",maxLines=1) }
        }
        SourceTabs(ui.source) { vm.source(it);if(text.isNotBlank()) submit() }
        Note("在 ${ui.source.shortTitle} 中搜索 · 示例内容")
        if(submitted.isBlank()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("最近搜索",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                if(ui.queries.isNotEmpty()) TextButton(onClick=vm::clearQueries) { Text("清空") }
            }
            FlowRow(Modifier.padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                ui.queries.forEach { query -> SuggestionChip(onClick={submit(query)},label={Text(query)}) }
            }
            Note(if(ui.queries.isEmpty()) "暂无搜索记录。选择漫画源，搜索作品、作者或标签。" else "选择漫画源，搜索作品、作者或标签。")
        } else ComicGrid(DemoCatalog.filter(submitted,"全部",ui.keywords,ui.languages),open)
    }
}

@Composable fun DetailScreen(source: Source, comic: Comic, ui: UiState, vm: AppViewModel, read: (Int,Int) -> Unit, notice: (String) -> Unit) {
    val history=ui.history.firstOrNull { it.source==source&&it.comicId==comic.id }
    var downloadSheet by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=30.dp)) {
        item {
            Row(Modifier.padding(20.dp),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                ComicCover(comic,Modifier.width(120.dp).aspectRatio(2f/3))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(source.shortTitle,color=MaterialTheme.colorScheme.primary,fontSize=12.sp)
                    Text(comic.title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
                    Text(comic.author,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${comic.chapters} 话 · ${comic.pages} 页 / 话",style=MaterialTheme.typography.bodySmall)
                    Text("${comic.language} · 示意作品",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                Button(onClick={read(history?.chapter?:1,history?.page?:1)},enabled=ui.historyReady,modifier=Modifier.weight(1f).height(50.dp)) { AppIcon(Glyph.Book);Spacer(Modifier.width(8.dp));Text(if(!ui.historyReady) "读取进度" else if(history==null) "开始阅读" else "继续阅读") }
                FilledTonalIconButton(onClick={vm.favorite(source,comic)},modifier=Modifier.size(50.dp)) { AppIcon(if(DemoCatalog.key(source,comic.id) in ui.favorites) Glyph.HeartFilled else Glyph.Heart, if(DemoCatalog.key(source,comic.id) in ui.favorites) "取消收藏" else "收藏作品") }
                FilledTonalIconButton(onClick={downloadSheet=true},modifier=Modifier.size(50.dp)) { AppIcon(Glyph.Download,"下载章节") }
            }
            if(history!=null) Note("上次读到第 ${history.chapter} 话 · 第 ${history.page} 页")
            ui.historyError?.let { Note(it) }
            Row(Modifier.padding(20.dp,12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(comic.category,comic.language).forEach { label -> SuggestionChip(onClick={},label={Text(label)}) } }
            Text(comic.description,Modifier.padding(horizontal=20.dp),style=MaterialTheme.typography.bodyMedium,lineHeight=24.sp)
            SectionTitle("目录 · ${comic.chapters} 话")
        }
        items((1..comic.chapters).toList()) { chapter ->
            SettingRow(if(comic.chapters==1) "全册" else "第 $chapter 话",if(history?.chapter==chapter) "正在阅读 · 第 ${history.page} 页" else "${comic.pages} 页",value=if(history?.chapter==chapter) "继续" else "",onClick={read(chapter,1)})
        }
    }
    if(downloadSheet) ModalBottomSheet(onDismissRequest={downloadSheet=false}) {
        Text("下载章节",Modifier.padding(24.dp,8.dp),style=MaterialTheme.typography.titleLarge)
        Note("只添加演示任务，不下载文件。")
        LazyColumn(Modifier.heightIn(max=380.dp)) { items((1..comic.chapters).toList()) { chapter ->
            SettingRow("第 $chapter 话",value="添加",onClick={vm.download(source,comic.id,chapter);downloadSheet=false;notice("已添加演示下载任务")})
        } }
    }
}

@Composable fun LibraryScreen(ui: UiState, vm: AppViewModel, open: (Source,Comic) -> Unit, read: (ReadingPosition) -> Unit) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    var removing by remember { mutableStateOf<DemoDownload?>(null) }
    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex=pager.currentPage,containerColor=MaterialTheme.colorScheme.background) {
            listOf("收藏","阅读历史","下载管理").forEachIndexed { i,t -> Tab(selected=pager.currentPage==i,onClick={scope.launch { pager.animateScrollToPage(i) }},text={Text(t)}) }
        }
        HorizontalPager(state=pager,modifier=Modifier.weight(1f).fillMaxWidth().testTag("library-pages"),verticalAlignment=Alignment.Top) { tab ->
            Column(Modifier.fillMaxSize()) {
                when(tab) {
                    0 -> {
                        val entries=ui.favorites.mapNotNull { key -> val parts=key.split(":");runCatching { Source.valueOf(parts[0]) to DemoCatalog.comic(parts[1].toInt()) }.getOrNull() }
                        if(entries.isEmpty()) EmptyState("书架还是空的","在作品详情中点一下收藏，把喜欢的故事留在这里。",Glyph.Heart)
                        else LazyVerticalGrid(GridCells.Adaptive(105.dp),contentPadding=PaddingValues(20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                            items(entries,key={DemoCatalog.key(it.first,it.second.id)}) { (source,comic) -> Column { ComicCard(comic) { open(source,comic) };Text(source.shortTitle,fontSize=10.sp,color=MaterialTheme.colorScheme.primary) } }
                        }
                    }
                    1 -> if(!ui.historyReady) Note("正在读取阅读记录…") else if(ui.historyError!=null) Note(ui.historyError) else if(ui.history.isEmpty()) EmptyState("还没有阅读记录","读过的作品会保存在这里，下次继续阅读。",Glyph.Clock) else LazyColumn { items(ui.history,key={DemoCatalog.key(it.source,it.comicId)}) { h ->
                        LibraryRow(DemoCatalog.comic(h.comicId),"${h.source.shortTitle} · 第 ${h.chapter} 话 · 第 ${h.page} 页",{read(h)}) { AppIcon(Glyph.Play) }
                    } }
                    else -> if(ui.downloads.isEmpty()) EmptyState("暂无下载任务","可从作品详情添加演示任务。",Glyph.Download) else LazyColumn {
                        item { Note("演示队列 · 不进行网络下载") }
                        items(ui.downloads) { d -> LibraryRow(DemoCatalog.comic(d.comicId),"第 ${d.chapter} 话 · ${if(d.paused) "已暂停" else "等待接入下载服务"}",{}) {
                            IconAction(if(d.paused) Glyph.Play else Glyph.Pause,if(d.paused) "继续任务" else "暂停任务") { vm.pause(d) }
                            IconAction(Glyph.Trash,"删除任务") { removing=d }
                        } }
                    }
                }
            }
        }
    }
    removing?.let { d -> AlertDialog(onDismissRequest={removing=null},title={Text("移除演示任务？")},text={Text("不会影响收藏和阅读记录。")},confirmButton={TextButton(onClick={vm.remove(d);removing=null}) { Text("移除") }},dismissButton={TextButton(onClick={removing=null}) { Text("取消") }}) }
}
@Composable private fun LibraryRow(comic: Comic, subtitle: String, onClick: () -> Unit, trailing: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(20.dp,12.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
        ComicCover(comic,Modifier.width(54.dp).height(78.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) { Text(comic.title,style=MaterialTheme.typography.titleSmall);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        trailing()
    }
}
