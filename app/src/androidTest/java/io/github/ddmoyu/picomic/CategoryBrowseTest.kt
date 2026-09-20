package io.github.ddmoyu.picomic

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.network.NetworkEngine
import io.github.ddmoyu.picomic.source.jm.*
import io.github.ddmoyu.picomic.ui.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Headless semantics test: only synthetic names, no website rendering, covers or screenshots. */
class CategoryBrowseTest {
    @get:Rule val ui = createComposeRule()

    @Test fun everyPlatformShowsItsLastCategoryAndKeepsItsSourceWhenSelected() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app)
        val store = ViewModelStore().apply { put("category-test", vm) }
        val names = (1..70).map { "测试目录 $it" }
        val fixture = object : ComicSource {
            override val source = Source.PICACG
            override suspend fun categories() = names
            override suspend fun search(query: ContentQuery): ContentPage<ComicSummary> = error("No content requests in category test")
            override suspend fun details(id: String): ComicDetails = error("No details")
            override suspend fun pages(comicId: String, chapter: Chapter): List<PageRef> = error("No pages")
        }
        MockWebServer().use { server ->
            val time = 1700000000L
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(JmProtocol.token(time).toByteArray(), "AES"))
            val payload = """{"categories":[{"id":0,"name":"最新目录","slug":""},{"id":1,"name":"目录甲","slug":"a","sub_categories":[{"name":"译本","slug":"a-translated"}]},{"id":2,"name":"目录乙","slug":"b","sub_categories":[{"name":"译本","slug":"b-translated"}]}],"blocks":[{"title":"主题","content":["日常"]},{"title":"主题 / 风格","content":["日常","黑白 / 彩色"]}]}"""
            val encrypted = JSONObject().put("code", 200).put("data", Base64.getEncoder().encodeToString(cipher.doFinal(payload.toByteArray()))).toString()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = if (request.requestUrl!!.encodedPath == "/categories") MockResponse().setBody(encrypted) else MockResponse().setResponseCode(404)
            }
            server.start()
            try {
                runBlocking {
                    vm.network.awaitReady()
                    vm.picacgAccount.cancel()
                    vm.network.sessions.logout("picacg")
                    vm.network.sessions.validateAndCommit(vm.network.sessions.begin("picacg"), SessionCandidate(CredentialKind.USER_TOKEN, "category-fixture".toByteArray())) { ValidationResult.Verified("测试账号") }
                }
                vm.content = ContentRepository(vm.network,
                    jmClient = { JmClient(NetworkEngine(), server.url("/")) { time } }, picacgFactory = { fixture })
                vm.source(Source.PICACG)
                var selected: Pair<Source, String>? = null
                ui.setContent {
                    val state by vm.state.collectAsState()
                    PiComicTheme(false, false) {
                        ContentBrowseScreen(true, state, vm, { error("No comic navigation") }, { source, name -> selected = source to name }, {})
                    }
                }
                for (source in Source.entries) {
                    ui.onNode(hasText(source.shortTitle) and hasClickAction()).performScrollTo().performClick()
                    val tag = "categories-${source.name}"
                    ui.waitUntil(10000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
                    val last = when (source) { Source.PICACG -> names.last(); Source.JMCOMIC -> "主题 / 日常"; else -> SourceCategories.fixed(source)!!.last() }
                    ui.onNodeWithTag("category-${source.name}-$last").performScrollTo().assertIsDisplayed()
                        .assertTextEquals(if (source == Source.JMCOMIC) "日常" else last).performClick()
                    ui.runOnIdle { assertEquals(source to last, selected) }
                    if (source == Source.JMCOMIC) {
                        ui.onNodeWithText("主题 / 日常").assertDoesNotExist()
                        for ((value, label) in listOf("目录甲 / 译本" to "译本", "目录乙 / 译本" to "译本",
                            "主题 / 风格 / 日常" to "日常", "主题 / 风格 / 黑白 / 彩色" to "黑白 / 彩色")) {
                            ui.onNodeWithTag("category-${source.name}-$value").performScrollTo().assertIsDisplayed().assertTextEquals(label).performClick()
                            ui.runOnIdle { assertEquals(source to value, selected) }
                        }
                        ui.onNodeWithTag("category-group-JMCOMIC-主题 / 风格").assertExists()
                    }
                    if (source in listOf(Source.HITOMI, Source.NHENTAI)) {
                        ui.onNodeWithTag("category-group-${source.name}-内容类型").assertExists()
                        ui.onNodeWithTag("category-group-${source.name}-语言").assertExists()
                    }
                    ui.onNodeWithText("所有分类").assertDoesNotExist()
                }
            } finally {
                runBlocking { vm.network.sessions.logout("picacg") }
                ui.runOnIdle { store.clear() }
            }
        }
    }
}
