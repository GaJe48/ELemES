package com.example.lmsunindra

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun LayarRekapAbsen(
    courseIndex: Int,
    viewModel: Backend,
    onBackClick: () -> Unit
) {
    val dataAbsen = viewModel.presenceDetailUI
    val isLoading = viewModel.isLoading
    val matkul = viewModel.lectureCourseUI[courseIndex]

    LaunchedEffect(courseIndex) {
        viewModel.getPresence(courseIndex)
    }

    LayarRekapAbsenStateless(
        courseName = matkul.courseName,
        dataAbsen = dataAbsen,
        errorMessage = viewModel.errorMessage,
        isLoading = isLoading,
        isRefreshing = viewModel.isRefreshing,
        onRefresh = { viewModel.getPresence(courseIndex, isSwipe = true) },
        onRetry = { viewModel.getPresence(courseIndex) },
        onAbsenClick = { url -> viewModel.executePresence(url) },
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun LayarRekapAbsenStateless(
    courseName: String,
    dataAbsen: List<String>,
    errorMessage: String,
    isLoading: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onAbsenClick: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val hazeState = rememberHazeState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state = rememberPullToRefreshState()

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
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
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
                        navigationIcon = {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier
                                    .padding(start = 8.dp, end = 8.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    "Rekap Presensi",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    courseName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            ) { paddingValues ->
                if (dataAbsen.isEmpty()) {
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
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    isLoading -> LoadingGif()
                                    errorMessage.isNotEmpty() -> ErrorGif(
                                        message = errorMessage,
                                        onRetry = onRetry
                                    )
                                    else -> EmptyGif(label = "Belum ada data absen")
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(hazeState),
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding() + 16.dp,
                            bottom = 32.dp,
                            start = 20.dp,
                            end = 20.dp
                        )
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                shape = RoundedCornerShape(32.dp),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(24.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            "Status Kehadiran",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            "${dataAbsen.count { it.isEmpty() }} dari ${dataAbsen.size} Pertemuan",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                    Text(
                                        text = "${(dataAbsen.count { it.isEmpty() } * 100 / dataAbsen.size)}%",
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 10.dp)
                                    )
                                }
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Pemberitahuan",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Column {
                                        Text(
                                            "Pemberitahuan Sistem Presensi",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        Text(
                                            "Secara bawaan, sistem mencatat kehadiran Anda saat mengunduh file materi. Namun, kebijakan presensi dapat berbeda tergantung dosen (misalnya via link form atau pengumpulan tugas).",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )
                                        Text(
                                            "Jika status presensi belum berubah, mohon tidak menekan tombol berulang kali. Tindakan tersebut dapat membebani server dan berisiko memblokir akses Anda. Harap ikuti instruksi presensi dari masing-masing dosen.",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "Riwayat Sesi",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black
                            )
                        }

                        itemsIndexed(dataAbsen) { index, presenceUrl ->
                            KotakPertemuanExpressive(
                                pertemuanKe = index + 1,
                                isHadir = presenceUrl.isEmpty(),
                                onAbsenClick = { onAbsenClick(presenceUrl) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KotakPertemuanExpressive(
    pertemuanKe: Int,
    isHadir: Boolean,
    onAbsenClick: () -> Unit
) {
    val containerColor = if (isHadir)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)

    val contentColor = if (isHadir)
        MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.error

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SESI",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor.copy(alpha = 0.7f),
                    letterSpacing = 2.sp
                )
                
                if (isHadir) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle, 
                        contentDescription = null, 
                        modifier = Modifier.size(16.dp), 
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Warning, 
                        contentDescription = null, 
                        modifier = Modifier.size(16.dp), 
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Text(
                text = "%02d".format(pertemuanKe),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = contentColor
            )

            if (isHadir) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        "Telah Hadir", 
                        style = MaterialTheme.typography.labelMedium, 
                        fontWeight = FontWeight.Bold, 
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Button(
                    onClick = onAbsenClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Text("Isi Absen", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewLayarRekapAbsen() {
    MaterialTheme {
        LayarRekapAbsenStateless(
            courseName = "Pemrograman Visual Lanjut",
            dataAbsen = listOf("", "", "a", "", "", "a"),
            errorMessage = "",
            isLoading = false,
            isRefreshing = false,
            onRefresh = {},
            onRetry = {},
            onAbsenClick = {},
            onBackClick = {},
        )
    }
}