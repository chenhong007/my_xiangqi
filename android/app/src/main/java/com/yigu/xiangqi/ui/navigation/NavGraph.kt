package com.yigu.xiangqi.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Study : Screen("study")
    data object Favorite : Screen("favorite")
    data object Profile : Screen("profile")
    data object PhotoRecognition : Screen("photo_recognition")
    data object GameList : Screen("game_list/{manualId}") {
        fun createRoute(manualId: String) = "game_list/$manualId"
    }
    data object Board : Screen("board/{gameId}") {
        fun createRoute(gameId: String) = "board/${android.net.Uri.encode(gameId)}"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "首页", Icons.Default.Home),
    BottomNavItem(Screen.Study, "学习", Icons.Default.School),
    BottomNavItem(Screen.Favorite, "收藏", Icons.Default.CollectionsBookmark),
    BottomNavItem(Screen.Profile, "我的", Icons.Default.Person),
)
