package com.valamas.reminders

import android.app.Application
import com.valamas.reminders.billing.BillingManager
import com.valamas.reminders.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val billingManager by lazy { BillingManager.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        billingManager.connect()
    }
}
