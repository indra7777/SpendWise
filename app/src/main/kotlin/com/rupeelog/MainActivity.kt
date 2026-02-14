package com.rupeelog

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rupeelog.agents.roast.DailyRoastWorker
import com.rupeelog.data.local.preferences.UserPreferences
import com.rupeelog.data.repository.AuthRepository
import com.rupeelog.ui.screens.home.HomeScreen
import com.rupeelog.ui.screens.transactions.TransactionsScreen
import com.rupeelog.ui.screens.reports.ReportsScreen
import com.rupeelog.ui.screens.settings.SettingsScreen
import com.rupeelog.ui.screens.onboarding.OnboardingScreen
import com.rupeelog.ui.screens.addtransaction.AddTransactionScreen
import com.rupeelog.ui.screens.review.ReviewTransactionsScreen
import com.rupeelog.ui.theme.RupeeLogTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

import com.rupeelog.utils.SignatureHelper

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val signature = SignatureHelper.getAppSignature(this)
        android.util.Log.e("APP_SIGNATURE", "CURRENT RUNNING SHA-1: $signature")
        
        requestPermissions()
        setContent {
            RupeeLogTheme {
                val isOnboardingCompleted = remember { userPreferences.isOnboardingCompleted() }

                RupeeLogNavigation(
                    startWithOnboarding = !isOnboardingCompleted,
                    authRepository = authRepository,
                    onOnboardingComplete = {
                        userPreferences.setOnboardingCompleted(true)
                    },
                    onRequestNotificationPermission = {
                        openNotificationListenerSettings()
                    }
                )
            }
        }
    }

    private fun requestPermissions() {
        // Basic permissions request logic if needed, 
        // mainly notification listener is handled separately
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    private fun openNotificationListenerSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to app settings
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun scheduleDailyRoast() {
        val roastRequest = PeriodicWorkRequestBuilder<DailyRoastWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(calculateDelayFor9PM(), TimeUnit.MILLISECONDS)
            .addTag("daily_roast")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_roast_work",
            ExistingPeriodicWorkPolicy.KEEP,
            roastRequest
        )
    }

    private fun calculateDelayFor9PM(): Long {
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance()

        dueDate.set(Calendar.HOUR_OF_DAY, 21) // 9 PM
        dueDate.set(Calendar.MINUTE, 0)
        dueDate.set(Calendar.SECOND, 0)

        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24)
        }
        return dueDate.timeInMillis - currentDate.timeInMillis
    }
}

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Onboarding : Screen("onboarding", "Onboarding", Icons.Filled.Home, Icons.Outlined.Home)
    data object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Transactions : Screen("transactions", "Transactions", Icons.Filled.Receipt, Icons.Outlined.Receipt)
    data object Reports : Screen("reports", "Reports", Icons.Filled.Assessment, Icons.Outlined.Assessment)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    data object AddTransaction : Screen("add_transaction", "Add", Icons.Filled.Home, Icons.Filled.Home)
    data object Review : Screen("review", "Review", Icons.Filled.RateReview, Icons.Filled.RateReview)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RupeeLogNavigation(
    startWithOnboarding: Boolean = false,
    authRepository: AuthRepository,
    onOnboardingComplete: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {}
) {
    val navController = rememberNavController()
    val screens = listOf(Screen.Home, Screen.Transactions, Screen.Reports, Screen.Settings)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Hide bottom bar on onboarding and add transaction screens
    val showBottomBar = currentRoute != Screen.Onboarding.route && currentRoute != Screen.AddTransaction.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val currentDestination = navBackStackEntry?.destination

                    screens.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.label
                                )
                            },
                            label = { Text(screen.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (startWithOnboarding) Screen.Onboarding.route else Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Onboarding.route) {
                val isSignedIn = authRepository.isAuthenticated()

                OnboardingScreen(
                    onComplete = {
                        onOnboardingComplete()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    },
                    onRequestPermission = onRequestNotificationPermission,
                    onSignIn = {
                        try {
                            val result = authRepository.signInWithGoogle(context)
                            result.isSuccess
                        } catch (e: Exception) {
                            e.printStackTrace()
                            false
                        }
                    },
                    isSignedIn = isSignedIn
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    onAddTransaction = {
                        navController.navigate("add_transaction")
                    },
                    onReviewClick = {
                        navController.navigate(Screen.Review.route)
                    },
                    onNavigateToTransactions = {
                        navController.navigate(Screen.Transactions.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(
                route = "add_transaction?transactionId={transactionId}",
                arguments = listOf(
                    navArgument("transactionId") {
                        nullable = true
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val transactionId = backStackEntry.arguments?.getString("transactionId")
                AddTransactionScreen(
                    onDismiss = {
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.Review.route) {
                ReviewTransactionsScreen(
                    onBack = {
                        navController.popBackStack()
                    },
                    onEditTransaction = { id ->
                        navController.navigate("add_transaction?transactionId=$id")
                    }
                )
            }
            composable(Screen.Transactions.route) {
                TransactionsScreen()
            }
            composable(Screen.Reports.route) {
                ReportsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}