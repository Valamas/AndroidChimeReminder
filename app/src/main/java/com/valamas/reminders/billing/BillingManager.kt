package com.valamas.reminders.billing

import android.app.Activity
import android.app.Application
import com.valamas.reminders.BuildConfig
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingManager private constructor(app: Application) : PurchasesUpdatedListener {

    private val _isPro = MutableStateFlow<Boolean>(BuildConfig.DEBUG)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val billingClient = BillingClient.newBuilder(app)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPurchases()
                }
            }
            override fun onBillingServiceDisconnected() {
                // Will retry on next launchPurchase() or restorePurchases() call
            }
        })
    }

    fun debugSetPro(enabled: Boolean) {
        if (BuildConfig.DEBUG) _isPro.value = enabled
    }

    fun restorePurchases() {
        if (billingClient.isReady) {
            queryPurchases()
        } else {
            connect()
        }
    }

    fun launchPurchase(activity: Activity) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRO_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            ).build()

        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val productDetails = productDetailsList.firstOrNull() ?: return@queryProductDetailsAsync
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .build()
                    )
                ).build()
            billingClient.launchBillingFlow(activity, flowParams)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            handlePurchases(purchases ?: emptyList())
        }
    }

    private fun queryPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { _, purchases -> handlePurchases(purchases) }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        val hasPro = purchases.any { purchase ->
            purchase.products.contains(PRO_PRODUCT_ID) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        _isPro.value = hasPro

        purchases
            .filter { it.products.contains(PRO_PRODUCT_ID) && !it.isAcknowledged }
            .forEach { purchase ->
                billingClient.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                ) { /* will retry automatically on next connect */ }
            }
    }

    companion object {
        const val PRO_PRODUCT_ID = "pro_unlock"

        @Volatile private var INSTANCE: BillingManager? = null

        fun getInstance(app: Application): BillingManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: BillingManager(app).also { INSTANCE = it }
            }
    }
}
