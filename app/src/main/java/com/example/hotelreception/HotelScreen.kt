@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.hotelreception

import android.os.Bundle
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

// =====================================================================
// КОРНЕВОЙ ЭКРАН: навигация (Navigation Compose), верхняя и нижняя панели.
// Здесь же создаются все ViewModel'и. Они привязаны к Activity,
// поэтому переживают поворот экрана и общие для всех экранов.
// =====================================================================

// Пять разделов нижней панели
enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    DASHBOARD("dashboard", "Главная", HotelIcons.Dashboard),
    RESERVATIONS("reservations", "Брони", HotelIcons.Calendar),
    ROOMS("rooms", "Номера", HotelIcons.Hotel),
    REQUESTS("requests", "Запросы", HotelIcons.Bell),
    CONTACTS("contacts", "Контакты", HotelIcons.Phone)
}

// Остальные экраны (открываются поверх разделов, нижней панели на них нет)
object Routes {
    const val ARG_ID = "id"
    const val ARG_DEPARTMENT = "department"

    const val RESERVATION = "reservation/{id}"
    const val BILLING = "billing/{id}"
    const val NEW_RESERVATION = "new_reservation"
    const val AVAILABILITY = "availability"
    const val NEW_REQUEST = "new_request/{department}"

    fun reservation(id: Int) = "reservation/$id"
    fun billing(id: Int) = "billing/$id"
    fun newRequest(department: Department) = "new_request/${department.name}"
}

private fun titleFor(route: String?, args: Bundle?): String = when (route) {
    Destination.DASHBOARD.route -> "Главная"
    Destination.RESERVATIONS.route -> "Брони"
    Destination.ROOMS.route -> "Номера"
    Destination.REQUESTS.route -> "Запросы"
    Destination.CONTACTS.route -> "Контакты отделов"
    Routes.RESERVATION -> "Бронь №${args?.getInt(Routes.ARG_ID) ?: 0}"
    Routes.BILLING -> "Биллинг · бронь №${args?.getInt(Routes.ARG_ID) ?: 0}"
    Routes.NEW_RESERVATION -> "Новая бронь"
    Routes.AVAILABILITY -> "Доступность номеров"
    Routes.NEW_REQUEST -> "Новый запрос"
    else -> "Hotel Reception"
}

@Composable
fun HotelScreen() {
    val navController = rememberNavController()
    // Хост для коротких сообщений (Snackbar). remember: объект не нужно сохранять при повороте
    val snackbarHostState = remember { SnackbarHostState() }

    val dashboardViewModel: DashboardViewModel = viewModel()
    val reservationsViewModel: ReservationsViewModel = viewModel()
    val roomsViewModel: RoomsViewModel = viewModel()
    val requestsViewModel: RequestsViewModel = viewModel()
    val contactsViewModel: ContactsViewModel = viewModel()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val topLevel = Destination.entries.firstOrNull { it.route == route }

    // Переход в раздел нижней панели (так же, как при нажатии на неё)
    fun openTab(destination: Destination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        topBar = {
            HotelTopBar(
                title = titleFor(route, backStackEntry?.arguments),
                isHome = route == Destination.DASHBOARD.route,
                onBack = if (topLevel == null) { { navController.popBackStack() } } else null
            )
        },
        bottomBar = {
            if (topLevel != null) {
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = topLevel == destination,
                            // Возвращаемся к началу графа, запоминая состояние разделов
                            onClick = { openTab(destination) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label, maxLines = 1) }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.DASHBOARD.route,
            modifier = Modifier.padding(padding),
            // Короткое мягкое перекрёстное затухание. Верхняя панель меняется за то же время (220 мс),
            // поэтому панель и страница не расходятся и нет резких вспышек
            enterTransition = { fadeIn(animationSpec = tween(220)) },
            exitTransition = { fadeOut(animationSpec = tween(220)) },
            popEnterTransition = { fadeIn(animationSpec = tween(220)) },
            popExitTransition = { fadeOut(animationSpec = tween(220)) }
        ) {
            composable(Destination.DASHBOARD.route) {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    // Карточки главной выставляют нужный фильтр во ViewModel и открывают раздел «Брони»
                    onOpenArrivals = {
                        reservationsViewModel.showArrivals()
                        openTab(Destination.RESERVATIONS)
                    },
                    onOpenDeparted = {
                        reservationsViewModel.showDeparted()
                        openTab(Destination.RESERVATIONS)
                    },
                    onOpenInHouse = {
                        reservationsViewModel.showInHouse()
                        openTab(Destination.RESERVATIONS)
                    },
                    onOpenAvailability = { navController.navigate(Routes.AVAILABILITY) }
                )
            }
            composable(Routes.AVAILABILITY) {
                AvailabilityScreen(viewModel = dashboardViewModel)
            }
            composable(Destination.RESERVATIONS.route) {
                ReservationsScreen(
                    viewModel = reservationsViewModel,
                    snackbarHostState = snackbarHostState,
                    onOpenReservation = { id -> navController.navigate(Routes.reservation(id)) },
                    onNewReservation = { navController.navigate(Routes.NEW_RESERVATION) }
                )
            }
            composable(
                route = Routes.RESERVATION,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.IntType })
            ) { entry ->
                val id = entry.arguments?.getInt(Routes.ARG_ID) ?: 0
                ReservationDetailsScreen(
                    resId = id,
                    viewModel = reservationsViewModel,
                    snackbarHostState = snackbarHostState,
                    onOpenBilling = { navController.navigate(Routes.billing(id)) }
                )
            }
            composable(
                route = Routes.BILLING,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.IntType })
            ) { entry ->
                BillingScreen(
                    resId = entry.arguments?.getInt(Routes.ARG_ID) ?: 0,
                    viewModel = reservationsViewModel,
                    snackbarHostState = snackbarHostState
                )
            }
            composable(Routes.NEW_RESERVATION) {
                NewReservationScreen(
                    viewModel = reservationsViewModel,
                    snackbarHostState = snackbarHostState,
                    onCreated = { id ->
                        // Закрываем форму и сразу открываем созданную бронь
                        navController.popBackStack()
                        navController.navigate(Routes.reservation(id))
                    }
                )
            }
            composable(Destination.ROOMS.route) {
                RoomsScreen(viewModel = roomsViewModel, snackbarHostState = snackbarHostState)
            }
            composable(Destination.REQUESTS.route) {
                RequestsScreen(
                    viewModel = requestsViewModel,
                    snackbarHostState = snackbarHostState,
                    onNewRequest = { department -> navController.navigate(Routes.newRequest(department)) }
                )
            }
            composable(
                route = Routes.NEW_REQUEST,
                arguments = listOf(navArgument(Routes.ARG_DEPARTMENT) { type = NavType.StringType })
            ) { entry ->
                val department = Department.valueOf(
                    entry.arguments?.getString(Routes.ARG_DEPARTMENT) ?: Department.IT.name
                )
                NewRequestScreen(
                    department = department,
                    viewModel = requestsViewModel,
                    snackbarHostState = snackbarHostState,
                    onSent = { navController.popBackStack() }
                )
            }
            composable(Destination.CONTACTS.route) {
                ContactsScreen(viewModel = contactsViewModel)
            }
        }
    }
}
