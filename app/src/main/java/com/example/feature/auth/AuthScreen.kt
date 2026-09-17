package com.example.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.auth.AuthState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

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
            .testTag("auth_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cloud Account & Sync",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_auth_back")
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val auth = uiState.authState) {
                is AuthState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is AuthState.Authenticated -> {
                    // Authenticated Account Dashboard & Metadata Sync Status
                    AuthenticatedProfileView(
                        authUser = auth,
                        userProfile = uiState.userProfile,
                        isLoading = uiState.isLoading,
                        onSendVerification = viewModel::sendEmailVerification,
                        onCheckVerification = viewModel::checkEmailVerificationStatus,
                        onSignOut = viewModel::signOut
                    )
                }

                is AuthState.Unauthenticated -> {
                    val context = LocalContext.current
                    // Sign In / Sign Up / Password Reset View
                    if (uiState.isResetPasswordMode) {
                        PasswordResetView(
                            email = uiState.emailInput,
                            isLoading = uiState.isLoading,
                            onEmailChange = viewModel::onEmailChanged,
                            onSubmit = viewModel::sendPasswordReset,
                            onCancel = viewModel::closeResetPasswordMode
                        )
                    } else {
                        LoginOrSignUpView(
                            uiState = uiState,
                            passwordVisible = passwordVisible,
                            confirmPasswordVisible = confirmPasswordVisible,
                            onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
                            onToggleConfirmPasswordVisibility = { confirmPasswordVisible = !confirmPasswordVisible },
                            onEmailChange = viewModel::onEmailChanged,
                            onPasswordChange = viewModel::onPasswordChanged,
                            onConfirmPasswordChange = viewModel::onConfirmPasswordChanged,
                            onTabSelected = { isSignUp -> viewModel.toggleMode(isSignUp) },
                            onSubmit = {
                                if (uiState.isSignUpMode) viewModel.signUp() else viewModel.signIn()
                            },
                            onGoogleSignInClick = {
                                viewModel.signInWithGoogle(context)
                            },
                            onForgotPasswordClick = viewModel::openResetPasswordMode
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedProfileView(
    authUser: AuthState.Authenticated,
    userProfile: com.example.core.firestore.UserProfileDocument?,
    isLoading: Boolean,
    onSendVerification: () -> Unit,
    onCheckVerification: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = authUser.email,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Verified / Unverified Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(
                    color = if (authUser.isEmailVerified) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = if (authUser.isEmailVerified) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (authUser.isEmailVerified) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (authUser.isEmailVerified) "Email Verified" else "Email Unverified",
                style = MaterialTheme.typography.labelMedium,
                color = if (authUser.isEmailVerified) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Firestore Single Source of Truth Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Cloud Identity Details",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                DetailRow(label = "Firebase UID", value = authUser.uid)
                DetailRow(label = "Account Role", value = userProfile?.role ?: "USER")
                DetailRow(
                    label = "Offline Persistence",
                    value = "Enabled (Firestore Cache)"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Verification Actions if unverified
        if (!authUser.isEmailVerified) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Verify Email Address",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "A verification email was sent to your registered address. Please verify to secure cloud synchronization.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSendVerification,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_resend_verification"),
                            enabled = !isLoading
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resend")
                        }
                        Button(
                            onClick = onCheckVerification,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_refresh_verification"),
                            enabled = !isLoading
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Check Status")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Sign Out Button
        Button(
            onClick = onSignOut,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_auth_sign_out"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading
        ) {
            Text("Sign Out of Firebase", color = MaterialTheme.colorScheme.onError)
        }
    }
}

@Composable
private fun LoginOrSignUpView(
    uiState: AuthUiState,
    passwordVisible: Boolean,
    confirmPasswordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    onToggleConfirmPasswordVisibility: () -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTabSelected: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onForgotPasswordClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CloudDone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (uiState.isSignUpMode) "Create Account" else "Welcome Back",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Cloud sync your vault metadata safely with end-to-end user isolation",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Tabs
        TabRow(
            selectedTabIndex = if (uiState.isSignUpMode) 1 else 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = !uiState.isSignUpMode,
                onClick = { onTabSelected(false) },
                text = { Text("Sign In") },
                modifier = Modifier.testTag("tab_sign_in")
            )
            Tab(
                selected = uiState.isSignUpMode,
                onClick = { onTabSelected(true) },
                text = { Text("Create Account") },
                modifier = Modifier.testTag("tab_sign_up")
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Email field
        OutlinedTextField(
            value = uiState.emailInput,
            onValueChange = onEmailChange,
            label = { Text("Email Address") },
            leadingIcon = {
                Icon(Icons.Default.Email, contentDescription = null)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_auth_email")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password field
        OutlinedTextField(
            value = uiState.passwordInput,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            leadingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisibility) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle Password"
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (uiState.isSignUpMode) ImeAction.Next else ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = {
                    focusManager.clearFocus()
                    if (!uiState.isSignUpMode) onSubmit()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_auth_password")
        )

        AnimatedVisibility(visible = uiState.isSignUpMode) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = uiState.confirmPasswordInput,
                    onValueChange = onConfirmPasswordChange,
                    label = { Text("Confirm Password") },
                    leadingIcon = {
                        Icon(Icons.Default.LockReset, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = onToggleConfirmPasswordVisibility) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Confirm Password"
                            )
                        }
                    },
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            onSubmit()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_auth_confirm_password")
                )
            }
        }

        if (!uiState.isSignUpMode) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onForgotPasswordClick,
                    modifier = Modifier.testTag("btn_forgot_password")
                ) {
                    Text("Forgot Password?")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("btn_auth_submit"),
            shape = RoundedCornerShape(12.dp),
            enabled = !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = if (uiState.isSignUpMode) "Sign Up" else "Sign In",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text(
                text = "  OR  ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onGoogleSignInClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("btn_google_sign_in"),
            shape = RoundedCornerShape(12.dp),
            enabled = !uiState.isLoading
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Google",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (uiState.isSignUpMode) "Sign Up with Google" else "Sign In with Google",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PasswordResetView(
    email: String,
    isLoading: Boolean,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.LockReset,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Reset Password",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Enter your email and we'll send you an official Firebase password reset link.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Registered Email") },
            leadingIcon = {
                Icon(Icons.Default.Email, contentDescription = null)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_reset_password_email")
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("btn_send_reset_email"),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text("Send Reset Link", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("btn_cancel_reset_password"),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading
        ) {
            Text("Back to Sign In")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
