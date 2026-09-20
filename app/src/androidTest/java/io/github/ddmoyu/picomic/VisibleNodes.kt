package io.github.ddmoyu.picomic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule

// Pager prefetch keeps neighbouring pages in the semantics tree off screen.
internal fun ComposeContentTestRule.onVisibleText(text: String): SemanticsNodeInteraction {
    val matches = onAllNodesWithText(text)
    return (0 until matches.fetchSemanticsNodes().size)
        .map { matches[it] }
        .single { it.isDisplayed() }
}
