package com.nhom4.subtrack.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nhom4.subtrack.R
import com.nhom4.subtrack.ui.edit.EditScreen
import com.nhom4.subtrack.ui.list.ListScreen
import com.nhom4.subtrack.ui.tags.TagsScreen

/** Hai tab ở thanh điều hướng dưới cùng. */
private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    LIST("list", R.string.tab_subscriptions, Icons.Outlined.Subscriptions),
    TAGS("tags", R.string.tab_tags, Icons.Outlined.Label),
}

/** Màn hình form: edit/0 = thêm mới, edit/{id} = sửa/xóa gói có id đó. */
private const val EDIT_ROUTE = "edit/{id}"
private fun editRoute(id: Long) = "edit/$id"

/**
 * Gốc giao diện: thanh tab + NavHost.
 *   Gói đăng ký (list) ──nút Thêm──▶ edit/0
 *                      ──chạm gói──▶ edit/{id}
 *   Tag (tags)
 */
@Composable
fun SubTrackRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = Tab.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label), maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            nav,
            startDestination = Tab.LIST.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Tab.LIST.route) {
                ListScreen(onOpen = { nav.navigate(editRoute(it)) }, onAdd = { nav.navigate(editRoute(0)) })
            }
            composable(Tab.TAGS.route) { TagsScreen() }
            composable(EDIT_ROUTE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                EditScreen(
                    subscriptionId = entry.arguments?.getLong("id") ?: 0L,
                    onDone = { nav.popBackStack() },
                )
            }
        }
    }
}
