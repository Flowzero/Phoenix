package com.example.scratched.onboarding

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ScreenHorizontalPadding = 24.dp
private val ContentMaxWidth = 520.dp

private enum class OnboardingStep(
    val number: Int,
    val total: Int
) {
    Welcome(number = 1, total = 6),
    About(number = 2, total = 6),
    Bluetooth(number = 3, total = 6),
    Location(number = 4, total = 6),
    Notifications(number = 5, total = 6),
    AllSet(number = 6, total = 6)
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
                OnboardingProgress(step = step)

                Spacer(modifier = Modifier.height(32.dp))

                IconBadge(
                    icon = icon,
                    tint = iconTint
                )

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 25.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (content != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    content()
                }

                Spacer(modifier = Modifier.height(32.dp))

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

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null
                    )
                }

                if (secondaryAction != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    secondaryAction()
                }
            }
        }
    }
}

@Composable
private fun OnboardingProgress(
    step: OnboardingStep
) {
    Column(
        modifier = Modifier.fillMaxWidth()
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

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(step.number.toFloat() / step.total.toFloat())
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

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

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
    onContinue: () -> Unit
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
                onClick = onContinue,
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
            IconBadge(
                icon = Icons.Default.Security
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Permission setup",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Some permissions could not be granted automatically. You can open the system settings and grant them manually.",
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 25.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

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

                Spacer(modifier = Modifier.width(8.dp))

                Text("Grant permissions manually")
            }

            Spacer(modifier = Modifier.height(32.dp))

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

                Spacer(modifier = Modifier.width(8.dp))

                Text("Check permissions")
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

                    Spacer(modifier = Modifier.width(12.dp))

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
            IconBadge(
                icon = Icons.Default.PhoneAndroid
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Main app screen",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your connected devices will appear here.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}