package io.github.ddmoyu.picomic

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Headless, synthetic text and placeholder covers only. */
class CategorySortUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Test fun switchingSortResetsTheListAndRestoringTheScreenKeepsSelectionAndPosition() {
        val vm = AppViewModel(ui.activity.application)
        val store = ViewModelStore().apply { put("sort-test", vm) }
        val requests = CopyOnWriteArrayList<ContentQuery>()
        val fixture = object : ComicSource {
            override val source = Source.PICACG
            override suspend fun categories() = listOf("测试分类")
            override suspend fun search(query: ContentQuery): ContentPage<ComicSummary> {
                requests += query
                return ContentPage((1..12).map { index ->
                    ComicSummary(ComicKey(source, "${query.sort}-${query.page}-$index"), "测试 ${query.sort}-${query.page}-$index")
                }, if (query.page == 1) 2 else null)
            }
            override suspend fun details(id: String): ComicDetails = error("No details requests")
            override suspend fun pages(comicId: String, chapter: Chapter): List<PageRef> = error("No reader requests")
        }
        try {
            runBlocking {
                vm.network.awaitReady(); vm.picacgAccount.cancel(); vm.network.sessions.logout("picacg")
                vm.network.sessions.validateAndCommit(vm.network.sessions.begin("picacg"),
                    SessionCandidate(CredentialKind.USER_TOKEN, "sorting-fixture".toByteArray())) { ValidationResult.Verified("测试账号") }
            }
            vm.content = ContentRepository(vm.network, picacgFactory = { fixture })
            val restore = StateRestorationTester(ui)
            restore.setContent { PiComicTheme(false, false) {
                Box(Modifier.width(360.dp).fillMaxHeight()) {
                    ContentCategoryScreen(Source.PICACG, "测试分类", UiState(), vm, {}, {})
                }
            } }
            ui.waitUntil(5000) { ui.onAllNodesWithText("测试 dd-1-1").fetchSemanticsNodes().isNotEmpty() }
            val list = ui.onNodeWithTag("content-list-PICACG")
            list.performScrollToNode(hasText("加载更多"))
            ui.onNodeWithText("加载更多").performClick()
            ui.waitUntil(5000) { requests.any { it.sort == "dd" && it.page == 2 } }
            list.performScrollToNode(hasText("测试 dd-2-12"))
            ui.onNodeWithTag("category-sort-ld").assertIsDisplayed().performClick().assertIsSelected()
            ui.waitUntil(5000) { ui.onAllNodesWithText("测试 ld-1-1").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("测试 ld-1-1").assertIsDisplayed()
            assertTrue(requests.all { it.category == "测试分类" })
            assertEquals(listOf("dd" to 1, "dd" to 2, "ld" to 1), requests.map { it.sort to it.page })
            list.performScrollToNode(hasText("加载更多"))
            ui.onNodeWithText("加载更多").performClick()
            ui.waitUntil(5000) { requests.any { it.sort == "ld" && it.page == 2 } }
            list.performScrollToNode(hasText("测试 ld-2-12"))
            ui.onNodeWithText("测试 ld-2-12").assertIsDisplayed()
            val beforeScroll = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            val before = requests.size
            restore.emulateSavedInstanceStateRestore()
            ui.onNodeWithTag("category-sort-ld").assertIsSelected()
            ui.runOnIdle { assertEquals(before, requests.size) }
            assertEquals("Restored list offset", beforeScroll, list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), .01f)
            ui.onNodeWithText("测试 ld-2-12").assertIsDisplayed()
        } finally {
            runBlocking { vm.network.sessions.logout("picacg") }
            ui.runOnIdle { store.clear() }
        }
    }
}
