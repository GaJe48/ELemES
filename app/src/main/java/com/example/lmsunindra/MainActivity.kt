package com.example.lmsunindra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.navigation.NavType
import androidx.navigation.navArgument

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            View()
        }
    }
}

@Composable
fun View() {
    val navController = rememberNavController()

    val viewModel: Backend = viewModel()

    NavHost(navController, startDestination = "login") {

        composable("login") {
            Login(
                viewModel,
                onLoginSuccess = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("dashboard") {
            Dashboard(
                viewModel,
                onCourseClick = { courseCode ->
                    navController.navigate("meeting-list/$courseCode")
                }
            )
        }

        composable(
            route = "meeting-list/{courseCode}",
            arguments = listOf(navArgument("courseCode") { type = NavType.StringType })
        ) { navBackStackEntry ->
            val courseCode = navBackStackEntry.arguments?.getString("courseCode") ?: ""

            MeetingList(
                viewModel = viewModel,
                courseCode = courseCode,
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
            navBackStackEntry ->
            val meetingUrl = navBackStackEntry.arguments?.getString("meetingUrl") ?: ""

            MeetingDetail(
                viewModel,
                meetingUrl = meetingUrl,
                onBackClick = { navController.popBackStack() },
                onTaskClick = { taskUrl ->
                    navController.navigate("task/${URLEncoder.encode(taskUrl, StandardCharsets.UTF_8.toString())}")
                }
            )
        }

        composable(
            route = "task/{taskUrl}",
            arguments = listOf(navArgument("taskUrl") { type = NavType.StringType })
        ) { backStackEntry ->
            // Tangkap dan Decode URL-nya
            val encodedUrl = backStackEntry.arguments?.getString("taskUrl") ?: ""
            val taskUrl = java.net.URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString())

            AssignmentScreen(
                viewModel = viewModel,
                taskUrl = taskUrl,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}