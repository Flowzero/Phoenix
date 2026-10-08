package com.example.scratched.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

private val ScreenHorizontalPadding = 24.dp
private val ContentMaxWidth = 520.dp

private val ProgressAreaHeight = 70.dp
private val ProgressBarHeight = 54.dp

private enum class OnboardingStep(
    val number: Int,
    val total: Int
) {
    Welcome(
        number = 1,
        total = 6
    ),

    About(
        number = 2,
        total = 6
    ),

    Bluetooth(
        number = 3,
        total = 6
    ),

    Location(
        number = 4,
        total = 6
    ),

    Notifications(
        number = 5,
        total = 6
    ),

    AllSet(
        number = 6,
        total = 6
    )
}

@Composable
private fun OnboardingLayout(
    modifier: Modifier = Modifier,
    step: OnboardingStep,
    icon: ImageVector,
    title: String,
    description: String,
    actionText: String,
    onContinue: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    secondaryAction: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    var isVisible by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .navigationBarsPadding()
                .padding(horizontal = ScreenHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            /*
             * Фиксированная верхняя область.
             *
             * ProgressBar больше не центрируется вместе с динамическим
             * содержимым и не перемещается после запуска анимаций.
             */
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ProgressAreaHeight),
                contentAlignment = Alignment.TopCenter
            ) {
                OnboardingProgress(
                    step = step
                )
            }

            /*
             * Только эта область центрируется.
             *
             * Появление иконки, текста и кнопок больше не влияет
             * на позицию ProgressBar.
             */
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .width(ContentMaxWidth),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(
                            animationSpec = tween(
                                durationMillis = 500,
                                delayMillis = 100
                            )
                        ) + scaleIn(
                            animationSpec = tween(
                                durationMillis = 500,
                                delayMillis = 100
                            ),
                            initialScale = 0.85f
                        )
                    ) {
                        IconBadge(
                            icon = icon,
                            tint = iconTint
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(28.dp)
                    )

                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(
                            animationSpec = tween(
                                durationMillis = 500,
                                delayMillis = 200
                            )
                        ) + slideInVertically(
                            animationSpec = tween(
                                durationMillis = 500,
                                delayMillis = 200
                            ),
                            initialOffsetY = { 20 }
                        )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Spacer(
                                modifier = Modifier.height(14.dp)
                            )

                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 25.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (content != null) {
                                Spacer(
                                    modifier = Modifier.height(24.dp)
                                )

                                content()
                            }
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(32.dp)
                    )

                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(
                            animationSpec = tween(
                                durationMillis = 500,
                                delayMillis = 300
                            )
                        ) + slideInVertically(
                            animationSpec = tween(
                                durationMillis = 500,
                                delayMillis = 300
                            ),
                            initialOffsetY = { 20 }
                        )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(
                                onClick = onContinue,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = actionText,
                                    style = MaterialTheme.typography.labelLarge
                                )

                                Spacer(
                                    modifier = Modifier.width(8.dp)
                                )

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null
                                )
                            }

                            if (secondaryAction != null) {
                                Spacer(
                                    modifier = Modifier.height(8.dp)
                                )

                                secondaryAction()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingProgress(
    step: OnboardingStep
) {
    val targetFraction = (
            step.number.toFloat() / step.total.toFloat()
            ).coerceIn(0f, 1f)

    val previousFraction = (
            (step.number - 1).toFloat() / step.total.toFloat()
            ).coerceIn(0f, 1f)

    val progress = remember(step) {
        Animatable(
            initialValue = previousFraction
        )
    }

    LaunchedEffect(step) {
        progress.animateTo(
            targetValue = targetFraction,
            animationSpec = tween(
                durationMillis = 600,
                easing = FastOutSlowInEasing
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(ProgressBarHeight)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SETUP",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.5.sp
            )

            Text(
                text = "${step.number} of ${step.total}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.value)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun IconBadge(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = Modifier
            .size(104.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = tint
        )
    }
}

@Composable
private fun InfoCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(23.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(
                modifier = Modifier.width(14.dp)
            )

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(
                    modifier = Modifier.height(3.dp)
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==========================================
// ЭКРАНЫ ОНБОРДИНГА
// ==========================================

@Composable
fun WelcomeScreen(
    modifier: Modifier = Modifier,
    onContinue: () -> Unit
) {
    OnboardingLayout(
        modifier = modifier,
        step = OnboardingStep.Welcome,
        icon = Icons.Default.ThumbUp,
        title = "Welcome!",
        description = "Let's get everything ready so you can use the app comfortably and safely.",
        actionText = "Get started",
        onContinue = onContinue
    )
}

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    onContinue: () -> Unit
) {
    OnboardingLayout(
        modifier = modifier,
        step = OnboardingStep.About,
        icon = Icons.Default.Info,
        title = "A little about the app",
        description = "The app connects to nearby devices and uses location-based services to provide the best experience.",
        actionText = "Continue",
        onContinue = onContinue,
        content = {
            InfoCard(
                icon = Icons.Default.Security,
                title = "Your privacy matters",
                description = "Permissions are requested only when they are needed."
            )
        }
    )
}

@Composable
fun BluetoothPermissionScreen(
    modifier: Modifier = Modifier,
    onContinue: () -> Unit
) {
    OnboardingLayout(
        modifier = modifier,
        step = OnboardingStep.Bluetooth,
        icon = Icons.Default.Bluetooth,
        iconTint = Color(0xFF1976D2),
        title = "Connect nearby devices",
        description = "Bluetooth allows the app to discover and communicate with compatible devices around you.",
        actionText = "Allow Bluetooth",
        onContinue = onContinue,
        content = {
            InfoCard(
                icon = Icons.Default.PhoneAndroid,
                title = "Nearby device access",
                description = "Used to scan for and connect to supported devices."
            )
        }
    )
}

@Composable
fun EnableBluetoothScreen(
    modifier: Modifier = Modifier,
    onContinue: () -> Unit
) {
    OnboardingLayout(
        modifier = modifier,
        step = OnboardingStep.Bluetooth,
        icon = Icons.Default.Bluetooth,
        iconTint = Color(0xFFE65100),
        title = "Bluetooth is turned off",
        description = "To discover and connect to nearby devices, Bluetooth must be enabled on your device.",
        actionText = "Turn on Bluetooth",
        onContinue = onContinue,
        content = {
            InfoCard(
                icon = Icons.Default.Security,
                title = "System prompt",
                description = "Your device will ask for your confirmation to enable this feature."
            )
        }
    )
}

@Composable
fun LocationPermissionScreen(
    modifier: Modifier = Modifier,
    onContinue: () -> Unit
) {
    OnboardingLayout(
        modifier = modifier,
        step = OnboardingStep.Location,
        icon = Icons.Default.LocationOn,
        iconTint = Color(0xFFE65100),
        title = "Enable location access",
        description = "Location access is required for Bluetooth Low Energy scanning and location-based features.",
        actionText = "Allow location",
        onContinue = onContinue,
        content = {
            InfoCard(
                icon = Icons.Default.Wifi,
                title = "Why is this needed?",
                description = "Android requires location access for BLE discovery on supported versions."
            )
        }
    )
}

@Composable
fun NotificationPermissionScreen(
    modifier: Modifier = Modifier,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    OnboardingLayout(
        modifier = modifier,
        step = OnboardingStep.Notifications,
        icon = Icons.Default.Notifications,
        iconTint = Color(0xFF7B1FA2),
        title = "Stay informed",
        description = "Receive important alerts about your devices and changes in their status.",
        actionText = "Allow notifications",
        onContinue = onContinue,
        secondaryAction = {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Skip for now",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        content = {
            Text(
                text = "You can change this permission later in system settings.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

@Composable
fun GrantPermissionsManually(
    modifier: Modifier = Modifier,
    onContinue: () -> Unit,
    onCheck: () -> Unit
) {
    var isVisible by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .navigationBarsPadding()
                .padding(horizontal = ScreenHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .width(ContentMaxWidth),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(
                        animationSpec = tween(500)
                    ) + scaleIn(
                        animationSpec = tween(500),
                        initialScale = 0.85f
                    )
                ) {
                    IconBadge(
                        icon = Icons.Default.Security,
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(
                    modifier = Modifier.height(28.dp)
                )

                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(
                        animationSpec = tween(
                            durationMillis = 500,
                            delayMillis = 100
                        )
                    ) + slideInVertically(
                        animationSpec = tween(
                            durationMillis = 500,
                            delayMillis = 100
                        ),
                        initialOffsetY = { 20 }
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Permission setup",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(
                            modifier = Modifier.height(14.dp)
                        )

                        Text(
                            text = "Some permissions could not be granted automatically. You can open the system settings and grant them manually.",
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 25.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(32.dp)
                )

                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(
                        animationSpec = tween(
                            durationMillis = 500,
                            delayMillis = 200
                        )
                    ) + slideInVertically(
                        animationSpec = tween(
                            durationMillis = 500,
                            delayMillis = 200
                        ),
                        initialOffsetY = { 20 }
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        OutlinedButton(
                            onClick = onContinue,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null
                            )

                            Spacer(
                                modifier = Modifier.width(8.dp)
                            )

                            Text(
                                text = "Grant permissions manually"
                            )
                        }

                        Spacer(
                            modifier = Modifier.height(32.dp)
                        )

                        OutlinedButton(
                            onClick = onCheck,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null
                            )

                            Spacer(
                                modifier = Modifier.width(8.dp)
                            )

                            Text(
                                text = "Check permissions"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AllSetScreen(
    modifier: Modifier = Modifier,
    onContinue: () -> Unit
) {
    OnboardingLayout(
        modifier = modifier,
        step = OnboardingStep.AllSet,
        icon = Icons.Default.Check,
        iconTint = Color(0xFF2E7D32),
        title = "You're all set!",
        description = "Everything is configured. You can now start using the app and connect your devices.",
        actionText = "Start using the app",
        onContinue = onContinue,
        content = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(
                        modifier = Modifier.width(12.dp)
                    )

                    Text(
                        text = "All required permissions are ready.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    )
}

@Composable
fun MainAppScreen(
    modifier: Modifier
) {
    var isVisible by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(
                    animationSpec = tween(600)
                ) + scaleIn(
                    animationSpec = tween(600),
                    initialScale = 0.8f
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconBadge(
                        icon = Icons.Default.PhoneAndroid
                    )

                    Spacer(
                        modifier = Modifier.height(24.dp)
                    )

                    Text(
                        text = "Main app screen",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = "Your connected devices will appear here.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}