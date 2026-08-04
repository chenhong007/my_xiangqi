package com.yigu.xiangqi.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yigu.xiangqi.ui.board.BoardScreen
import com.yigu.xiangqi.ui.favorite.FavoriteScreen
import com.yigu.xiangqi.ui.gamelist.GameListScreen
import com.yigu.xiangqi.ui.home.HomeScreen
import com.yigu.xiangqi.ui.navigation.Screen
import com.yigu.xiangqi.ui.navigation.bottomNavItems
import com.yigu.xiangqi.ui.profile.ProfileScreen
import com.yigu.xiangqi.ui.recognition.PhotoRecognitionScreen
import com.yigu.xiangqi.ui.study.StudyScreen

@Composable
fun YiGuMainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.screen.route
                        } == true
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onManualClick = { manualId ->
                        navController.navigate(Screen.GameList.createRoute(manualId))
                    },
                    onGameClick = { gameId ->
                        navController.navigate(Screen.Board.createRoute(gameId))
                    }
                )
            }
            composable(Screen.Study.route) {
                StudyScreen(
                    onGameClick = { gameId ->
                        navController.navigate(Screen.Board.createRoute(gameId))
                    },
                    onPhotoRecognitionClick = {
                        navController.navigate(Screen.PhotoRecognition.route)
                    }
                )
            }
            composable(Screen.Favorite.route) {
                FavoriteScreen(
                    onGameClick = { gameId ->
                        navController.navigate(Screen.Board.createRoute(gameId))
                    },
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen()
            }
            composable(Screen.PhotoRecognition.route) {
                PhotoRecognitionScreen(
                    onBack = { navController.popBackStack() },
                    onGameClick = { gameId ->
                        navController.navigate(Screen.Board.createRoute(gameId))
                    }
                )
            }
            composable(
                route = Screen.GameList.route,
                arguments = listOf(navArgument("manualId") { type = NavType.StringType }),
            ) {
                GameListScreen(
                    onGameClick = { gameId ->
                        navController.navigate(Screen.Board.createRoute(gameId))
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Screen.Board.route,
                arguments = listOf(navArgument("gameId") { type = NavType.StringType }),
            ) {
                BoardScreen(
                    onBack = { navController.popBackStack() },
                    onNextGame = { nextGameId ->
                        navController.navigate(Screen.Board.createRoute(nextGameId)) {
                            popUpTo(Screen.Board.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
