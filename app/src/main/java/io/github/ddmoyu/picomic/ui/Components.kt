@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.ddmoyu.picomic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ddmoyu.picomic.R
import io.github.ddmoyu.picomic.data.*

enum class Glyph(val path: String) {
    Back("M19 12H5 M12 5l-7 7 7 7"), Next("M9 5l7 7-7 7"),
    Search("M20 20l-5-5 M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0"),
    Settings("M12 8a4 4 0 1 1 0 8 4 4 0 0 1 0-8 M10 2h4l1 3 3 1 3 2-1 4 1 4-3 2-3 1-1 3h-4l-1-3-3-1-3-2 1-4-1-4 3-2 3-1z"),
    Explore("M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0 M16 8l-3 5-5 3 3-5z"),
    Grid("M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z"),
    Book("M3 4h6q3 0 3 3 0-3 3-3h6v16h-6q-3 0-3 2 0-2-3-2H3z M12 7v15"),
    Heart("M12 21L3 12C-3 3 8-1 12 6c4-7 15-3 9 6z"),
    Download("M12 3v12 M7 10l5 5 5-5 M4 16v5h16v-5"),
    User("M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M4 21v-2a8 8 0 0 1 16 0v2z"),
    Filter("M3 5h18 M6 12h12 M9 19h6"),
    Moon("M20 15A9 9 0 0 1 9 3a9 9 0 1 0 11 12"),
    Refresh("M20 8A8 8 0 1 0 20 16 M20 3v5h-5"),
    Folder("M3 5h7l2 3h9v12H3z"),
    Wifi("M2 8a16 16 0 0 1 20 0 M5 12a11 11 0 0 1 14 0 M8 16a6 6 0 0 1 8 0 M12 20h.01"),
    Info("M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0 M12 11v6 M12 7h.01"),
    Close("M6 6l12 12 M6 18L18 6"), Check("M4 12l5 5L20 6"),
    Play("M8 4l12 8-12 8z"), Pause("M8 4v16 M16 4v16"),
    Menu("M4 6h16 M4 12h16 M4 18h16"), Trash("M3 6h18 M9 6V3h6v3 M5 6l1 15h12l1-15 M10 10v7 M14 10v7"),
    Clock("M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0 M12 6v6l4 3"), Plus("M12 4v16 M4 12h16")
}

@Composable fun AppIcon(glyph: Glyph, description: String? = null, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    val path = remember(glyph) { PathParser().parsePathString(glyph.path).toPath() }
    Canvas(modifier.size(24.dp).then(if(description == null) Modifier else Modifier.semantics { contentDescription = description })) {
        scale(size.width / 24, size.height / 24, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(path, color, style = Stroke(1.7f, cap = StrokeCap.Round))
        }
    }
}
@Composable fun IconAction(glyph: Glyph, description: String, action: () -> Unit) {
    IconButton(onClick = action) { AppIcon(glyph, description) }
}
@Composable fun ContentLoading(modifier: Modifier = Modifier.fillMaxSize()) {
    // Page roots may receive exact screen constraints. The container keeps those off the spinner.
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(40.dp).semantics { contentDescription = "正在加载" })
    }
}
@Composable fun PageTop(title: String, back: (() -> Unit)? = null, search: (() -> Unit)? = null, settings: (() -> Unit)? = null) {
    TopAppBar(title = { Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
        navigationIcon = { if(back != null) IconAction(Glyph.Back,"返回",back) },
        actions = { if(search != null) IconAction(Glyph.Search,"搜索",search); if(settings != null) IconAction(Glyph.Settings,"设置",settings) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
}
@Composable fun SourceTabs(selected: Source, onSelect: (Source) -> Unit) {
    SecondaryScrollableTabRow(selectedTabIndex = selected.ordinal, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background) {
        Source.entries.forEach { source -> Tab(selected = source == selected, onClick = { onSelect(source) }, text = { Text(source.shortTitle, maxLines = 1) }) }
    }
}
@Composable fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}
@Composable fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable fun SettingRow(title: String, subtitle: String = "", glyph: Glyph? = null, value: String = "", onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if(glyph != null) AppIcon(glyph, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if(subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        if(trailing != null) trailing() else {
            if(value.isNotBlank()) Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 130.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            AppIcon(Glyph.Next, modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.outline)
        }
    }
}
@Composable fun EmptyState(title: String, description: String, glyph: Glyph = Glyph.Book) {
    Column(Modifier.fillMaxWidth().padding(36.dp, 70.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) { AppIcon(glyph, modifier = Modifier.padding(22.dp).size(30.dp), color = MaterialTheme.colorScheme.primary) }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
private val covers = listOf(R.drawable.cover_0,R.drawable.cover_1,R.drawable.cover_2,R.drawable.cover_3,R.drawable.cover_4,R.drawable.cover_5,R.drawable.cover_6,R.drawable.cover_7,R.drawable.cover_8)
val readerPages = listOf(R.drawable.page_1,R.drawable.page_2,R.drawable.page_3,R.drawable.page_4,R.drawable.page_5,R.drawable.page_6)
@Composable fun ComicCover(comic: Comic, modifier: Modifier = Modifier) {
    Image(painterResource(covers[comic.id]), "${comic.title}封面", modifier.clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
}
@Composable fun ComicCard(comic: Comic, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box {
            ComicCover(comic, Modifier.fillMaxWidth().aspectRatio(2f / 3))
            Surface(Modifier.align(Alignment.BottomEnd).padding(6.dp), shape = RoundedCornerShape(4.dp), color = Color(0xBB172136)) { Text(if(comic.chapters == 1) "全册" else "${comic.chapters} 话", Modifier.padding(4.dp,2.dp), color = Color.White, fontSize = 10.sp) }
        }
        Text(comic.title, Modifier.padding(top = 7.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(comic.author, Modifier.padding(top = 3.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
