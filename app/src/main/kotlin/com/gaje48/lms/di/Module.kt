package com.gaje48.lms.di

import com.gaje48.lms.data.StorageDataSource
import com.gaje48.lms.data.InternetDataSource
import com.gaje48.lms.data.LmsRepository
import com.gaje48.lms.data.LocalDataSource
import com.gaje48.lms.ui.state.LmsViewModel
import com.gaje48.lms.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val module = module {
    single { Dispatchers.IO }
    single { NotificationHelper(androidContext()) }
    single { InternetDataSource(get()) }
    single { LocalDataSource(androidContext()) }
    single { StorageDataSource(androidContext()) }
    single { LmsRepository(get(), get(), get()) }
    viewModel { LmsViewModel(get(), get()) }
}
