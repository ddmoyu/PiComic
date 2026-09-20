package io.github.ddmoyu.picomic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic account names; no network, avatars, screenshots or web content. */
class AccountFeedbackTest {
    @get:Rule val ui = createComposeRule()

    @Test fun sharedFeedbackShowsIdentityAndClearsAuthenticatedBadgesWhenExpiredOrLoggedOut() {
        var account by mutableStateOf(AccountState(AccountStatus.AUTHENTICATED, "测试账号"))
        var success by mutableStateOf(true)
        var dark by mutableStateOf(false)
        var opened = 0
        ui.setContent { PiComicTheme(dark, false) {
            Column {
                AccountStatusCard(account)
                AccountSettingRow(account, "测试平台") { opened++ }
            }
            LoginSuccessFeedback(success, "测试平台", account, rememberScrollState()) { success = false }
        } }
        ui.onNodeWithTag("login-success-dialog").assertIsDisplayed()
        ui.onNode(hasText("测试平台") and hasAnyAncestor(hasTestTag("login-success-dialog"))).assertIsDisplayed()
        ui.onNodeWithText("知道了").performClick()
        ui.onNodeWithTag("account-status").assertIsDisplayed()
        ui.onNodeWithText("已登录", substring = false).assertIsDisplayed().performClick()
        ui.runOnIdle { assertEquals(1, opened); dark = true }
        ui.onNodeWithTag("login-success-dialog").assertDoesNotExist()
        ui.onNodeWithText("已登录", substring = false).assertIsDisplayed()
        ui.runOnIdle { account = AccountState(AccountStatus.EXPIRED) }
        ui.onNodeWithText("已登录", substring = false).assertDoesNotExist()
        ui.onNode(hasText("登录已失效，请重新登录") and hasAnyAncestor(hasTestTag("account-status"))).assertIsDisplayed()
        ui.runOnIdle { account = AccountState(AccountStatus.NEEDS_VALIDATION, "测试账号") }
        ui.onNode(hasText("会话待验证") and hasAnyAncestor(hasTestTag("account-status"))).assertIsDisplayed()
        ui.onNodeWithTag("login-success-dialog").assertDoesNotExist()
        ui.runOnIdle { account = AccountState() }
        ui.onNode(hasText("未登录") and hasAnyAncestor(hasTestTag("account-status"))).assertIsDisplayed()
        ui.onNodeWithText("已登录", substring = false).assertDoesNotExist()
    }
}
