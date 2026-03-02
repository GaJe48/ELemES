package com.example.lmsunindra

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt

@Preview
@Composable
fun PreviewDashboard() {
    val dummyProfile = StudentProfile(
        studentName = "Abi Musa Abdurrahman",
        npm = "202443500660",
        studyProgram = "Teknik Informatika S1",
        classCode = "R7A",
        studentPhoto = ""
    )

    val dummyCourse = LectureCourse(
        courseCode = "TIF123",
        courseName = "Pemrograman Mobile",
        day = "Senin",
        clock = "08:00 - 10:00",
        room = "V.202",
        lecturerName = "Pak Dosen",
        lecturerHp = "0812345",
        lecturerPhoto = "",
        "",
        meetingList = emptyList()
    )

    val dummyList = listOf(dummyCourse, dummyCourse, dummyCourse, dummyCourse, dummyCourse)

    MaterialTheme {
        DashboardContent(
            studentProfile = dummyProfile,
            lectureCourse = dummyList,
            isRefreshing = false,
            onRefresh = {},
            onCourseClick = {},
            onPresenceClick = {},
            onTaskClick = {},
            onLogout = {},
        )
    }
}

@Composable
fun Dashboard(
    viewModel: Backend,
    onCourseClick: (Int) -> Unit,
    onPresenceClick: (Int) -> Unit,
    onTaskClick: (Int) -> Unit
) {
    DashboardContent(
        studentProfile = viewModel.studentProfileUI,
        lectureCourse = viewModel.lectureCourseUI,
        isRefreshing = viewModel.isRefreshing,
        onRefresh = { viewModel.refreshDashboard() },
        onCourseClick = onCourseClick,
        onPresenceClick = onPresenceClick,
        onTaskClick = onTaskClick,
        onLogout = { viewModel.logout() },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun DashboardContent(
    studentProfile: StudentProfile,
    lectureCourse: List<LectureCourse>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onCourseClick: (Int) -> Unit,
    onPresenceClick: (Int) -> Unit,
    onTaskClick: (Int) -> Unit,
    onLogout: () -> Unit,
) {
    val hazeState = rememberHazeState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state = rememberPullToRefreshState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
            title = { Text("Keluar") },
            text = { Text("Kamu yakin mau logout? Kamu perlu login ulang untuk masuk kembali.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            state = state,
            onRefresh = onRefresh,
            contentAlignment = Alignment.TopCenter,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = state,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        ) {
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = Color.Transparent,
                topBar = {
                    LargeTopAppBar(
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.ultraThin()
                        ),
                        title = {
                            Column {
                                Text(
                                    text = "Halo, ${studentProfile.studentName.split(" ")[0]}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Selamat datang kembali di LMS",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showLogoutDialog = true },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                            }
                        },
                    )
                }
            ) { paddingValues ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + 16.dp,
                        bottom = 24.dp,
                        start = 20.dp,
                        end = 20.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(32.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                                    modifier = Modifier.size(84.dp)
                                ) {
                                    AsyncImage(
                                        model = studentProfile.studentPhoto,
                                        contentDescription = "Foto Profil",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Spacer(modifier = Modifier.width(20.dp))
                                Column {
                                    Text(
                                        text = studentProfile.studentName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = studentProfile.npm,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ) {
                                        Text(
                                            studentProfile.studyProgram,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (lectureCourse.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillParentMaxWidth().fillParentMaxHeight(0.6f),
                                contentAlignment = Alignment.Center
                            ) { EmptyGif(label = "Belum ada jadwal mata kuliah") }
                        }
                    } else {
                        item {
                            Text(
                                text = "Jadwal Mata Kuliah",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        itemsIndexed(lectureCourse) { courseIndex, course ->
                            CourseExpressiveCard(courseIndex, course, onCourseClick, onPresenceClick, onTaskClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CourseExpressiveCard(
    courseIndex: Int,
    course: LectureCourse,
    onCourseClick: (Int) -> Unit,
    onPresenceClick: (Int) -> Unit,
    onTaskClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onCourseClick(courseIndex) },
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = course.lecturerName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = course.courseName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 28.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoChip(icon = Icons.Default.CalendarMonth, text = course.day)
                InfoChip(icon = Icons.Default.MeetingRoom, text = course.room)
            }

            Spacer(modifier = Modifier.height(16.dp))
            AttendanceGraph(
                meetingList = course.meetingList,
                persenHadirStr = course.persen // Asumsi parameter barumu bernama ini
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = course.clock,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestionChip(
                        onClick = { onPresenceClick(courseIndex) },
                        label = { Text("Absen", fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp),
                        border = null,
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        )
                    )
                    SuggestionChip(
                        onClick = { onTaskClick(courseIndex) },
                        label = { Text("Tugas", fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp),
                        border = null,
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
    }
}

private const val TOTAL_SEMESTER_MEETINGS = 16

@Composable
fun InfoChip(
    icon: ImageVector,
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontWeight: FontWeight = FontWeight.Medium
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = contentColor
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = fontWeight
        )
    }
}

@Composable
fun AttendanceGraph(meetingList: List<String>, persenHadirStr: String) {
    // 1. Ambil jumlah pertemuan dari ukuran list
    val currentMeeting = meetingList.size
    val percentage = persenHadirStr.replace("%", "").toDoubleOrNull() ?: 0.0

    // 2. Hitung jumlah kehadiran berdasarkan persentase
    // Misal: current = 5, persen = 80%, maka hadir = 4, alpa = 1
    val attendedCount = ((percentage / 100.0) * currentMeeting).roundToInt()
    val missedCount = maxOf(0, currentMeeting - attendedCount)

    // 3. Hitung sisa pertemuan (Asumsi 1 semester = 16 pertemuan)
    val totalMeetings = TOTAL_SEMESTER_MEETINGS
    val upcomingCount = maxOf(0, totalMeetings - currentMeeting)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Kehadiran $persenHadirStr ($attendedCount/$currentMeeting)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Membungkus kotak-kotak dalam satu baris (Graph)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Kotak Hijau (Hadir)
            repeat(attendedCount) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF4CAF50)) // Hijau
                )
            }
            // Kotak Merah (Tidak Hadir)
            repeat(missedCount) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFF44336)) // Merah
                )
            }
            // Kotak Abu-abu (Belum pertemuan)
            repeat(upcomingCount) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                )
            }
        }
    }
}