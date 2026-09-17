package com.example.feature.premium

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SyncLock
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.billing.BillingConnectionState
import com.example.core.subscription.SubscriptionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    viewModel: PremiumViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("premium_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Vault Premium",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_premium_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Current Status Badge
            StatusBadgeCard(status = uiState.subscriptionStatus, isPremium = uiState.isPremium)

            // 2. Premium Benefits Card
            PremiumBenefitsCard()

            // 3. Pricing / Product Selection Card
            PricingSection(
                uiState = uiState,
                onRetry = { viewModel.retryConnection() },
                onSelectProduct = { viewModel.selectProduct(it) }
            )

            // 4. Action Buttons
            if (!uiState.isPremium) {
                Button(
                    onClick = {
                        activity?.let { viewModel.subscribe(it) }
                    },
                    enabled = !uiState.isProcessingPurchase &&
                            !uiState.isRestoring &&
                            uiState.selectedProduct != null &&
                            uiState.billingConnectionState == BillingConnectionState.CONNECTED,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_subscribe"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isProcessingPurchase) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Processing with Google Play...")
                    } else {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        val priceText = uiState.selectedProduct?.formattedPrice ?: ""
                        val periodText = uiState.selectedProduct?.billingPeriod ?: ""
                        val buttonLabel = if (priceText.isNotBlank()) "Subscribe for $priceText / $periodText" else "Subscribe to Premium"
                        Text(buttonLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "You are currently enjoying all Calculator Vault Premium benefits.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // License Key Redemption Card (No-Gmail Premium Activation)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Redeem Activation Code (No Gmail)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Purchased via WhatsApp, EasyPaisa, or Admin? Enter your key to activate Monthly, Yearly, or Lifetime access. Remains safe even if app is uninstalled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    var licenseCodeInput by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = licenseCodeInput,
                        onValueChange = { licenseCodeInput = it.uppercase().trim() },
                        label = { Text("Enter License Key (e.g. VAULT-LIFE-XXXX)") },
                        placeholder = { Text("VAULT-XXXX-XXXX") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (licenseCodeInput.isNotBlank()) {
                                    viewModel.redeemLicenseKey(context, licenseCodeInput)
                                }
                            },
                            enabled = licenseCodeInput.isNotBlank() && !uiState.isRestoring,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Activate Key")
                        }

                        OutlinedButton(
                            onClick = { viewModel.restoreWithLicenseKey(context) },
                            enabled = !uiState.isRestoring,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Restore Key")
                        }
                    }
                }
            }

            // Restore Purchases Button
            OutlinedButton(
                onClick = { viewModel.restorePurchases() },
                enabled = !uiState.isProcessingPurchase && !uiState.isRestoring,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_restore_purchases"),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState.isRestoring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking Purchases...")
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restore Purchases")
                }
            }

            // Disclosure Text
            Text(
                text = "Subscriptions automatically renew unless canceled at least 24 hours before the end of the current billing cycle. Manage subscriptions anytime in your Google Play Store account settings. Payments are securely processed by Google Play.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun StatusBadgeCard(status: SubscriptionStatus, isPremium: Boolean) {
    val (statusLabel, statusColor, subtitle) = when (status) {
        SubscriptionStatus.PREMIUM_ACTIVE, SubscriptionStatus.SUBSCRIPTION_ACTIVE -> {
            Triple(
                "PREMIUM ACTIVE",
                MaterialTheme.colorScheme.primary,
                "All cloud backup and security privileges unlocked"
            )
        }
        SubscriptionStatus.SUBSCRIPTION_CANCELED_ACTIVE -> {
            Triple(
                "ACTIVE UNTIL EXPIRY",
                MaterialTheme.colorScheme.tertiary,
                "Subscription canceled but premium access remains active"
            )
        }
        SubscriptionStatus.SUBSCRIPTION_PENDING -> {
            Triple(
                "PENDING VERIFICATION",
                MaterialTheme.colorScheme.secondary,
                "Google Play is processing your transaction"
            )
        }
        SubscriptionStatus.SUBSCRIPTION_EXPIRED -> {
            Triple(
                "SUBSCRIPTION EXPIRED",
                MaterialTheme.colorScheme.error,
                "Renew now to restore cloud backup capabilities"
            )
        }
        SubscriptionStatus.FREE -> {
            Triple(
                "FREE PLAN",
                MaterialTheme.colorScheme.onSurfaceVariant,
                "Local encrypted vault active on device"
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isPremium) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPremium) Icons.Default.Star else Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (isPremium) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PremiumBenefitsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Premium Privileges",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            BenefitRow(
                icon = Icons.Default.CloudSync,
                title = "Encrypted Cloud Backup",
                description = "Automatic encrypted backup to Cloudinary with military-grade AES-256."
            )
            BenefitRow(
                icon = Icons.Default.SyncLock,
                title = "Cloud Media Restore",
                description = "Restore photos, videos, and folders seamlessly to any device."
            )
            BenefitRow(
                icon = Icons.Default.Videocam,
                title = "4K Recording & Unlimited Vault",
                description = "Capture ultra-HD video recordings with zero duration limits."
            )
            BenefitRow(
                icon = Icons.Default.Lock,
                title = "100% Ad-Free Vault",
                description = "Zero interruptions, tracking, or advertisements ever."
            )
        }
    }
}

@Composable
private fun BenefitRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(22.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PricingSection(
    uiState: PremiumUiState,
    onRetry: () -> Unit,
    onSelectProduct: (com.example.core.billing.SubscriptionProductDetails) -> Unit
) {
    when (uiState.billingConnectionState) {
        BillingConnectionState.CONNECTING -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Connecting to Google Play...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        BillingConnectionState.CONNECTED -> {
            if (uiState.availableProducts.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Standard Subscription Plan",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ready for Google Play Console subscription billing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.availableProducts.forEach { product ->
                        val isSelected = uiState.selectedProduct?.productId == product.productId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProduct(product) }
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(12.dp)
                                        )
                                    } else Modifier
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = product.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (product.description.isNotBlank()) {
                                        Text(
                                            text = product.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = "${product.formattedPrice} / ${product.billingPeriod}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
        BillingConnectionState.DISCONNECTED, BillingConnectionState.UNAVAILABLE, BillingConnectionState.ERROR -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google Play Unavailable",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Please check your internet or Google Play account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onRetry) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry Connection",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
