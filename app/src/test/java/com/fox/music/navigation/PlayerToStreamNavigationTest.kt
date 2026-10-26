package com.fox.music.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.fox.music.feature.discover.DISCOVER_ROUTE
import com.fox.music.feature.home.HOME_ROUTE
import com.fox.music.feature.player.ui.screen.PLAYER_ROUTE
import com.fox.music.feature.stream.StreamRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 播放列表点「音流」必须先回到主壳再切 Tab。
 * 直接把音流压在播放页上时，首页的 saveState/restoreState 会把播放页一起恢复回来。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PlayerToStreamNavigationTest {

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        navController = TestNavHostController(context)
        navController.setViewModelStore(ViewModelStore())
        navController.setLifecycleOwner(ResumedOwner())
        navController.navigatorProvider.addNavigator(ComposeNavigator())
        navController.graph = navController.createGraph(startDestination = HOME_ROUTE) {
            composable(HOME_ROUTE) {}
            composable(DISCOVER_ROUTE) {}
            composable(StreamRoutes.STREAM) {}
            composable(PLAYER_ROUTE) {}
            composable(DETAIL_ROUTE) {}
        }
    }

    @Test
    fun playlistJumpToStreamLeavesShellThenSelectsStreamTab() {
        openPlayerFromStreamThenHome()

        navController.navigateFromPlayerToTab(StreamRoutes.STREAM)

        assertEquals(StreamRoutes.STREAM, navController.currentDestination?.route)
        assertFalse(navController.leafRoutes().contains(PLAYER_ROUTE))
        assertEquals(listOf(HOME_ROUTE, StreamRoutes.STREAM), navController.leafRoutes())

        navController.navigateToTab(HOME_ROUTE)
        assertEquals(HOME_ROUTE, navController.currentDestination?.route)
        assertEquals(listOf(HOME_ROUTE), navController.leafRoutes())

        navController.navigateToTab(DISCOVER_ROUTE)
        assertEquals(DISCOVER_ROUTE, navController.currentDestination?.route)
        assertFalse(navController.leafRoutes().contains(PLAYER_ROUTE))
    }

    @Test
    fun playlistJumpFromDiscoverDoesNotKeepPlayerUnderStream() {
        navController.navigateToTab(DISCOVER_ROUTE)
        navController.navigateToPlayer()

        navController.navigateFromPlayerToTab(StreamRoutes.STREAM)

        assertEquals(StreamRoutes.STREAM, navController.currentDestination?.route)
        assertEquals(listOf(HOME_ROUTE, StreamRoutes.STREAM), navController.leafRoutes())

        navController.navigateToTab(HOME_ROUTE)
        assertEquals(HOME_ROUTE, navController.currentDestination?.route)
        assertEquals(listOf(HOME_ROUTE), navController.leafRoutes())
    }

    @Test
    fun playlistJumpFromNestedDetailDoesNotRestorePlayerOrDetailOnHome() {
        navController.navigate(DETAIL_ROUTE)
        navController.navigateToPlayer()

        navController.navigateFromPlayerToTab(StreamRoutes.STREAM)

        assertEquals(StreamRoutes.STREAM, navController.currentDestination?.route)
        assertFalse(navController.leafRoutes().contains(PLAYER_ROUTE))
        assertFalse(navController.leafRoutes().contains(DETAIL_ROUTE))

        navController.navigateToTab(HOME_ROUTE)
        assertEquals(HOME_ROUTE, navController.currentDestination?.route)
        assertEquals(listOf(HOME_ROUTE), navController.leafRoutes())
    }

    private fun openPlayerFromStreamThenHome() {
        navController.navigateToTab(StreamRoutes.STREAM)
        navController.navigateToTab(HOME_ROUTE)
        assertEquals(HOME_ROUTE, navController.currentDestination?.route)
        navController.navigateToPlayer()
        assertEquals(PLAYER_ROUTE, navController.currentDestination?.route)
    }

    private fun NavController.leafRoutes(): List<String> =
        currentBackStack.value.mapNotNull { it.destination.route }

    private class ResumedOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        init {
            registry.currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle
            get() = registry
    }

    private companion object {
        const val DETAIL_ROUTE = "playlist_detail/1/playlist"
    }
}
