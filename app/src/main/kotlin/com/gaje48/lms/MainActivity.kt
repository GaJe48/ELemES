package com.gaje48.lms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.gaje48.lms.navigation.LmsApp
import com.gaje48.lms.ui.state.LmsViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val viewModel: LmsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { !viewModel.uiState.value.isSplashReady }
        setContent { LmsApp(viewModel = viewModel) }
    }
}