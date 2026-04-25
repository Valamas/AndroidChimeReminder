package com.valamas.chimereminder

import android.app.Application
import com.valamas.chimereminder.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
