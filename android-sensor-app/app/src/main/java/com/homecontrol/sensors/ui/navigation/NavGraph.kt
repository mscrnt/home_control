package com.homecontrol.sensors.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.homecontrol.sensors.ui.screens.home.HomeScreen
import com.homecontrol.sensors.ui.screens.hue.HueScreen
import com.homecontrol.sensors.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Hue : Screen("hue")
    object Spotify : Screen("spotify")
    object Calendar : Screen("calendar")
    object Cameras : Screen("cameras")
    object Entertainment : Screen("entertainment")
    object Settings : Screen("settings")
    object Screensaver : Screen("screensaver")

    // Detail screens
    object PlaylistDetail : Screen("spotify/playlist/{playlistId}") {
        fun createRoute(playlistId: String) = "spotify/playlist/$playlistId"
    }
    object AlbumDetail : Screen("spotify/album/{albumId}") {
        fun createRoute(albumId: String) = "spotify/album/$albumId"
    }
    object ArtistDetail : Screen("spotify/artist/{artistId}") {
        fun createRoute(artistId: String) = "spotify/artist/$artistId"
    }
    object CameraDetail : Screen("camera/{cameraName}") {
        fun createRoute(cameraName: String) = "camera/$cameraName"
    }
}

@Composable
fun HomeControlNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // Main screens
        composable(Screen.Home.route) {
            HomeScreen()
        }

        composable(Screen.Hue.route) {
            HueScreen()
        }

        composable(Screen.Spotify.route) {
            // SpotifyScreen will be implemented in Phase 4
            PlaceholderScreen("Spotify")
        }

        composable(Screen.Calendar.route) {
            // CalendarScreen will be implemented in Phase 5
            PlaceholderScreen("Calendar")
        }

        composable(Screen.Cameras.route) {
            // CamerasScreen will be implemented in Phase 6
            PlaceholderScreen("Cameras")
        }

        composable(Screen.Entertainment.route) {
            // EntertainmentScreen will be implemented in Phase 6
            PlaceholderScreen("Entertainment")
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }

        composable(Screen.Screensaver.route) {
            // ScreensaverScreen will be implemented in Phase 6
            PlaceholderScreen("Screensaver")
        }

        // Detail screens
        composable(
            route = Screen.PlaylistDetail.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
            PlaceholderScreen("Playlist: $playlistId")
        }

        composable(
            route = Screen.AlbumDetail.route,
            arguments = listOf(navArgument("albumId") { type = NavType.StringType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId") ?: ""
            PlaceholderScreen("Album: $albumId")
        }

        composable(
            route = Screen.ArtistDetail.route,
            arguments = listOf(navArgument("artistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val artistId = backStackEntry.arguments?.getString("artistId") ?: ""
            PlaceholderScreen("Artist: $artistId")
        }

        composable(
            route = Screen.CameraDetail.route,
            arguments = listOf(navArgument("cameraName") { type = NavType.StringType })
        ) { backStackEntry ->
            val cameraName = backStackEntry.arguments?.getString("cameraName") ?: ""
            PlaceholderScreen("Camera: $cameraName")
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
