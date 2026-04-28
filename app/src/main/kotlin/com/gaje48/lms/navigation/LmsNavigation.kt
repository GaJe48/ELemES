package com.gaje48.lms.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.gaje48.lms.model.CourseInfo
import com.gaje48.lms.ui.screens.AssignmentScreen
import com.gaje48.lms.ui.screens.Dashboard
import com.gaje48.lms.ui.screens.LayarRekapAbsen
import com.gaje48.lms.ui.screens.Login
import com.gaje48.lms.ui.screens.MeetingDetail
import com.gaje48.lms.ui.screens.MeetingList
import com.gaje48.lms.ui.state.LmsViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
object LoginNavKey : NavKey
@Serializable
object DashboardNavKey : NavKey
@Serializable
data class MeetingListNavKey(val course: CourseInfo) : NavKey
@Serializable
data class MeetingDetailNavKey(val meetingUrl: String) : NavKey
@Serializable
data class TaskNavKey(val course: CourseInfo) : NavKey
@Serializable
data class PresenceNavKey(val course: CourseInfo) : NavKey

@Composable
fun LmsApp(viewModel: LmsViewModel = koinViewModel()) {
    val backStack = rememberNavBackStack(LoginNavKey)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // No special handling for denied notifications yet.
    }

    LaunchedEffect(null) {
        viewModel.checkLoginStatus()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(state.isLogin) {
        backStack.removeAt(0)
        backStack.add(0, if (state.isLogin) DashboardNavKey else LoginNavKey)
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
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeAt(backStack.lastIndex) },
            entryProvider = entryProvider {
                entry<LoginNavKey> {
                    Login(viewModel)
                }

                entry<DashboardNavKey> {
                    Dashboard(
                        viewModel,
                        onCourseClick = { backStack.add(MeetingListNavKey(it)) },
                        onPresenceClick = { backStack.add(PresenceNavKey(it)) },
                        onTaskClick = { backStack.add(TaskNavKey(it)) },
                    )
                }

                entry<MeetingListNavKey> { destination ->
                    MeetingList(
                        viewModel = viewModel,
                        course = destination.course,
                        onBackClick = { backStack.removeAt(backStack.lastIndex) },
                        onMeetingClick = { meetingUrl ->
                            backStack.add(MeetingDetailNavKey(meetingUrl))
                        }
                    )
                }

                entry<MeetingDetailNavKey> { destination ->
                    MeetingDetail(
                        viewModel,
                        meetingUrl = destination.meetingUrl,
                        onBackClick = { backStack.removeAt(backStack.lastIndex) },
                    )
                }

                entry<TaskNavKey> { destination ->
                    AssignmentScreen(
                        viewModel = viewModel,
                        course = destination.course,
                        onBackClick = { backStack.removeAt(backStack.lastIndex) }
                    )
                }

                entry<PresenceNavKey> { destination ->
                    LayarRekapAbsen(
                        course = destination.course,
                        viewModel = viewModel,
                        onBackClick = { backStack.removeAt(backStack.lastIndex) }
                    )
                }
            }
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}