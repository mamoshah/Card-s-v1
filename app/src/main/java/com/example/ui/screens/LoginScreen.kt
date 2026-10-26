package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import com.example.viewmodel.AuthStep
import com.example.viewmodel.AuthUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    uiState: AuthUiState,
    onHostNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmitHostName: () -> Unit,
    onSubmitPassword: () -> Unit,
    onClearMessages: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = BrandPitchBlack,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            DarkRedSurface.copy(alpha = 0.85f),
                            BrandPitchBlack,
                            BrandPitchBlack
                        ),
                        radius = 1200f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Logo & Header info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Logo at Top (Brand Image with Red Glow)
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .shadow(
                                elevation = 24.dp,
                                shape = CircleShape,
                                spotColor = CrimsonRed,
                                ambientColor = NeonRedGlow
                            )
                            .clip(CircleShape)
                            .background(BrandPitchBlack)
                            .border(
                                BorderStroke(
                                    2.dp,
                                    Brush.sweepGradient(
                                        listOf(
                                            CrimsonRed,
                                            DarkCrimson,
                                            BrandPitchBlack,
                                            CrimsonRed
                                        )
                                    )
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_login_brand_logo),
                            contentDescription = "Mtsho Hunt Logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Static Main Title Text as strictly specified
                    Text(
                        text = "Enter information to connect to the server",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    // Subtitle indicating the active step
                    Text(
                        text = if (uiState.currentStep == AuthStep.STEP_ONE_HOST) {
                            "Step 1: Host Name Verification"
                        } else {
                            "Step 2: Server Access Password"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        ),
                        color = NeonRedGlow,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Right Column: Main Connection Card Container
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.Center
                ) {
                    Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .border(
                            BorderStroke(
                                1.5.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        DarkRedBorder,
                                        DarkSurfaceBorder.copy(alpha = 0.5f)
                                    )
                                )
                            ),
                            RoundedCornerShape(24.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = DarkSurface.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Error Banner if present
                        AnimatedVisibility(
                            visible = uiState.errorMessage != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Surface(
                                color = DarkRedContainerHigh,
                                border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.8f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_error_banner")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = NeonRedGlow,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = uiState.errorMessage ?: "",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }


                        if (uiState.currentStep == AuthStep.STEP_ONE_HOST) {
                            // ==================== STEP ONE: HOST NAME ====================

                            // Host Name Input Field
                            OutlinedTextField(
                                value = uiState.hostNameInput,
                                onValueChange = onHostNameChange,
                                enabled = !uiState.isHostFieldLocked,
                                label = { Text("Host Name") },
                                placeholder = { Text("Enter Host Name") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (uiState.isHostFieldLocked) Icons.Default.Lock else Icons.Default.Dns,
                                        contentDescription = null,
                                        tint = if (uiState.isHostFieldLocked) CoralAlert else CrimsonRed
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        onSubmitHostName()
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CrimsonRed,
                                    unfocusedBorderColor = DarkRedBorder,
                                    focusedLabelColor = NeonRedGlow,
                                    unfocusedLabelColor = TextSecondary,
                                    focusedContainerColor = DarkSurfaceElevated,
                                    unfocusedContainerColor = DarkSurfaceElevated,
                                    disabledContainerColor = DarkSurfaceElevated.copy(alpha = 0.5f),
                                    disabledBorderColor = CoralAlert.copy(alpha = 0.6f),
                                    disabledTextColor = TextSecondary,
                                    cursorColor = CrimsonRed,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("host_name_input")
                            )

                            // Instructional text directly below the Host Name input field
                            if (uiState.instructionalText != null) {
                                Surface(
                                    color = DarkRedContainer.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("instructional_text_banner")
                                 ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Chat,
                                            contentDescription = null,
                                            tint = AmberWarning,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = uiState.instructionalText ?: "",
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            // Notice when locked
                            if (uiState.isHostFieldLocked) {
                                Text(
                                    text = "Input is locked. Please restart the application to try again.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CoralAlert,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Submit Button for Step One
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    onSubmitHostName()
                                },
                                enabled = !uiState.isLoading && !uiState.isHostFieldLocked,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CrimsonRed,
                                    contentColor = Color.White,
                                    disabledContainerColor = DarkSurfaceElevated,
                                    disabledContentColor = TextMuted
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .shadow(
                                        elevation = 12.dp,
                                        shape = RoundedCornerShape(14.dp),
                                        spotColor = CrimsonRed,
                                        ambientColor = DarkCrimson
                                    )
                                    .testTag("submit_host_button")
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color.White,
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Connecting to Host...",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                } else {
                                    Text(
                                        text = "Connect to Server",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                        } else {
                            // ==================== STEP TWO: PASSWORD ====================

                            // Connected Host confirmation chip
                            Surface(
                                color = DarkRedContainer,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Host Verified: ${uiState.hostNameInput}",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }
                            }

                            // Password Input Field
                            OutlinedTextField(
                                value = uiState.passwordInput,
                                onValueChange = onPasswordChange,
                                label = { Text("Password") },
                                placeholder = { Text("••••••••••••") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = CrimsonRed
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = onTogglePasswordVisibility) {
                                        Icon(
                                            imageVector = if (uiState.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (uiState.isPasswordVisible) "Hide password" else "Show password",
                                            tint = TextSecondary
                                        )
                                    }
                                },
                                visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        onSubmitPassword()
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CrimsonRed,
                                    unfocusedBorderColor = DarkRedBorder,
                                    focusedLabelColor = NeonRedGlow,
                                    unfocusedLabelColor = TextSecondary,
                                    focusedContainerColor = DarkSurfaceElevated,
                                    unfocusedContainerColor = DarkSurfaceElevated,
                                    cursorColor = CrimsonRed,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("password_input")
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Submit Button for Step Two
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    onSubmitPassword()
                                },
                                enabled = !uiState.isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CrimsonRed,
                                    contentColor = Color.White,
                                    disabledContainerColor = DarkSurfaceElevated,
                                    disabledContentColor = TextMuted
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .shadow(
                                        elevation = 12.dp,
                                        shape = RoundedCornerShape(14.dp),
                                        spotColor = CrimsonRed,
                                        ambientColor = DarkCrimson
                                    )
                                    .testTag("submit_password_button")
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color.White,
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Verifying Credentials...",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Login,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Enter Server",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
}
