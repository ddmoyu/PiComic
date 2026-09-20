package io.github.ddmoyu.picomic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState

/** The system recognizes the gesture; a destination changes only after Back is committed. */
internal fun NavGraphBuilder.systemBackPage(
    route: String,
    navController: NavHostController,
    onBack: () -> Unit,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(route, arguments = arguments) { destination ->
        val current by navController.currentBackStackEntryAsState()
        // Register inside the destination to take precedence over NavHost's predictive preview.
        // Child dialogs/reader controls can still handle Back, and the root falls through to Android.
        BackHandler(enabled = current == destination && navController.previousBackStackEntry != null, onBack = onBack)
        content(destination)
    }
}
