package com.example.core.billing

object BillingConstants {
    /**
     * Subscription product ID configured in Google Play Console.
     * Can be replaced or mapped to Console SKU ID.
     */
    const val PREMIUM_SUBSCRIPTION_PRODUCT_ID = "vault_premium_monthly_sub"
    const val PREMIUM_SUBSCRIPTION_YEARLY_PRODUCT_ID = "vault_premium_yearly_sub"

    val ALL_SUBSCRIPTION_PRODUCT_IDS = listOf(
        PREMIUM_SUBSCRIPTION_PRODUCT_ID,
        PREMIUM_SUBSCRIPTION_YEARLY_PRODUCT_ID
    )
}
