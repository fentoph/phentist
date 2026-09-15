package com.fentoph.phentist

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.QueryProductDetailsParams

/**
 * Google Play Billing entry point for digital content.
 * Product IDs must be configured in Play Console and never trusted from the client.
 */
class BillingRepository(context: Context) {
    private val billingClient = BillingClient.newBuilder(context)
        .enablePendingPurchases()
        .setListener { _, _ -> }
        .build()

    fun connect(onReady: () -> Unit) {
        billingClient.startConnection(object : BillingClient.BillingClientStateListener {
            override fun onBillingSetupFinished(result: com.android.billingclient.api.BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) onReady()
            }
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun purchase(activity: Activity, productId: String) {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        billingClient.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val detail = details.productDetailsList.firstOrNull() ?: return@queryProductDetailsAsync
            val flow = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(detail)
                .build()
            billingClient.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(flow))
                    .build()
            )
        }
    }
}
