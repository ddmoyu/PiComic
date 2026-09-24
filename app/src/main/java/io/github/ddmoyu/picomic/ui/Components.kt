@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.ddmoyu.picomic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/** Official Phosphor Regular vectors; a filled heart denotes the selected favorite state. */
enum class Glyph(@param:androidx.annotation.DrawableRes @get:androidx.annotation.DrawableRes val resource: Int) {
    Back(R.drawable.ic_phosphor_back), Next(R.drawable.ic_phosphor_chevron),
    Search(R.drawable.ic_phosphor_search), Settings(R.drawable.ic_phosphor_settings),
    Explore(R.drawable.ic_phosphor_explore), Grid(R.drawable.ic_phosphor_categories),
    Book(R.drawable.ic_phosphor_book), Heart(R.drawable.ic_phosphor_heart),
    HeartFilled(R.drawable.ic_phosphor_heart_filled), Download(R.drawable.ic_phosphor_download),
    User(R.drawable.ic_phosphor_user), Filter(R.drawable.ic_phosphor_filter),
    Moon(R.drawable.ic_phosphor_moon), Refresh(R.drawable.ic_phosphor_refresh),
    Folder(R.drawable.ic_phosphor_folder), Wifi(R.drawable.ic_phosphor_wifi),
    Info(R.drawable.ic_phosphor_info), Close(R.drawable.ic_phosphor_close),
    Eye(R.drawable.ic_phosphor_eye), EyeSlash(R.drawable.ic_phosphor_eye_slash),
    Check(R.drawable.ic_phosphor_check), Play(R.drawable.ic_phosphor_play),
    Pause(R.drawable.ic_phosphor_pause), Menu(R.drawable.ic_phosphor_menu),
    Trash(R.drawable.ic_phosphor_trash), Clock(R.drawable.ic_phosphor_clock), Plus(R.drawable.ic_phosphor_plus)
}

@Composable fun AppIcon(glyph: Glyph, description: String? = null, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Icon(painterResource(glyph.resource), description, modifier.size(24.dp), tint = color)
}
@Composable fun IconAction(glyph: Glyph, description: String, action: () -> Unit) = IconAction(glyph, description, true, action)
@Composable fun IconAction(glyph: Glyph, description: String, enabled: Boolean, action: () -> Unit) {
    IconButton(onClick = action, enabled = enabled, modifier = Modifier.size(48.dp)) { AppIcon(glyph, description) }
}
@Composable fun ContentLoading(modifier: Modifier = Modifier.fillMaxSize()) {
    // Page roots may receive exact screen constraints. The container keeps those off the spinner.
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(40.dp).semantics { contentDescription = "正在加载" })
    }
}
@Composable fun PageTop(title: String, back: (() -> Unit)? = null, search: (() -> Unit)? = null, settings: (() -> Unit)? = null,
    refresh: (() -> Unit)? = null, refreshEnabled: Boolean = true, windowInsets: WindowInsets = TopAppBarDefaults.windowInsets) {
    TopAppBar(title = { Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
        navigationIcon = { if(back != null) IconAction(Glyph.Back,"返回",action = back) },
        actions = {
            if(search != null) IconAction(Glyph.Search,"搜索",action = search)
            if(settings != null) IconAction(Glyph.Settings,"设置",action = settings)
            if(refresh != null) IconAction(Glyph.Refresh,"刷新详情", enabled = refreshEnabled, action = refresh)
        }, windowInsets = windowInsets,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background,
            navigationIconContentColor = MaterialTheme.colorScheme.outline,
            actionIconContentColor = MaterialTheme.colorScheme.outline))
}
@Composable fun SourceTabs(selected: Source, onSelect: (Source) -> Unit) {
    SecondaryScrollableTabRow(selectedTabIndex = selected.displayIndex, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background) {
        Source.displayOrder.forEach { source -> Tab(selected = source == selected, onClick = { onSelect(source) },
            selectedContentColor = MaterialTheme.colorScheme.primary, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            text = { Text(source.shortTitle, maxLines = 1, fontSize = 14.sp,
                fontWeight = if (source == selected) FontWeight.SemiBold else FontWeight.Normal) }) }
    }
}
@Composable fun SectionTitle(title: String, modifier: Modifier = Modifier, separated: Boolean = false) {
    if (separated) HorizontalDivider(Modifier.padding(top = 4.dp))
    Text(title, modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
        fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}
@Composable fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable fun SettingRow(title: String, subtitle: String = "", glyph: Glyph? = null, value: String = "", onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 72.dp).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if(glyph != null) AppIcon(glyph, color = MaterialTheme.colorScheme.outline)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if(subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        if(trailing != null) trailing() else {
            if(value.isNotBlank()) Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 112.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            AppIcon(Glyph.Next, modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.outline)
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
