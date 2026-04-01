package com.gaje48.elemes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.navigation.NavType
import androidx.navigation.navArgument
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.gaje48.elemes.ui.theme.LMSUnindraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LMSUnindraTheme {
                Frontend()
            }
        }
    }
}

@Composable
fun Frontend() {
    val navController = rememberNavController()
    val viewModel: Backend = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Do nothing specifically for now if denied
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        viewModel.checkLoginStatus()
    }

    LaunchedEffect(viewModel.isLogin) {
        if (viewModel.isLogin) {
            navController.navigate("dashboard") {
                popUpTo(0) { inclusive = true }
            }
        } else {
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel.snackbarEvent, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.snackbarEvent.collect { msg ->
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController, startDestination = "login") {

            composable("login") { Login(viewModel) }

            composable("dashboard") {
                Dashboard(
                    viewModel,
                    onCourseClick = { navController.navigate("meeting-list/$it") },
                    onPresenceClick = { navController.navigate("presence/$it") },
                    onTaskClick = { navController.navigate("task/$it") },
                )
            }

            composable(
                route = "meeting-list/{courseIndex}",
                arguments = listOf(navArgument("courseIndex") { type = NavType.IntType })
            ) {
                val courseIndex = it.arguments?.getInt("courseIndex") ?: 0
                MeetingList(
                    viewModel = viewModel,
                    courseIndex = courseIndex,
                    onBackClick = { navController.popBackStack() },
                    onMeetingClick = { meetingUrl ->
                        navController.navigate("meeting-detail/${URLEncoder.encode(meetingUrl, StandardCharsets.UTF_8.toString())}")
                    }
                )
            }

            composable(
                route = "meeting-detail/{meetingUrl}",
                arguments = listOf(navArgument("meetingUrl") { type = NavType.StringType })
            ) {
                val meetingUrl = it.arguments?.getString("meetingUrl") ?: ""
                MeetingDetail(
                    viewModel,
                    meetingUrl = meetingUrl,
                    onBackClick = { navController.popBackStack() },
                )
            }

            composable(
                route = "task/{courseIndex}",
                arguments = listOf(navArgument("courseIndex") { type = NavType.IntType })
            ) {
                val courseIndex = it.arguments?.getInt("courseIndex") ?: 0
                AssignmentScreen(
                    viewModel = viewModel,
                    courseIndex = courseIndex,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = "presence/{courseIndex}",
                arguments = listOf(navArgument("courseIndex") { type = NavType.IntType })
            ) {
                val courseIndex = it.arguments?.getInt("courseIndex") ?: 0
                LayarRekapAbsen(
                    courseIndex = courseIndex,
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}