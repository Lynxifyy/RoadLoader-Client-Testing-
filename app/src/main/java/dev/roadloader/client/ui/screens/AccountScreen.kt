package dev.roadloader.client.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.roadloader.client.auth.Account
import dev.roadloader.client.auth.DeviceCode
import dev.roadloader.client.ui.components.BrandHeader
import dev.roadloader.client.ui.components.GlassCard
import dev.roadloader.client.ui.components.GlassPillButton
import dev.roadloader.client.ui.components.GradientPillButton
import dev.roadloader.client.ui.components.SectionHeader
import dev.roadloader.client.ui.components.ToggleRow
import dev.roadloader.client.ui.theme.LocalAccent
import dev.roadloader.client.ui.theme.RoadColors
import dev.roadloader.client.vm.AuthState

@Composable
fun AccountScreen(
    account: Account?,
    auth: AuthState,
    autoSignIn: Boolean,
    onSignIn: () -> Unit,
    onCancel: () -> Unit,
    onSignOut: () -> Unit,
    onAutoSignIn: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { BrandHeader(title = "Account", subtitle = "Microsoft sign-in for Minecraft") }

        when (auth) {
            is AuthState.Waiting -> item { DeviceCodeCard(auth.code, auth.stage, onCancel) }
            is AuthState.Requesting -> item { ProgressCard(auth.message, onCancel) }
            is AuthState.Failed -> item { FailureCard(auth.message, onSignIn) }
            else -> Unit
        }

        if (account != null) {
            item { ProfileCard(account) }
            item {
                GlassCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                    ToggleRow(
                        title = "Auto sign-in",
                        subtitle = "Refresh the session and bind it to the world on launch",
                        checked = autoSignIn,
                        onCheckedChange = onAutoSignIn,
                    )
                }
            }
            item {
                GlassPillButton(
                    text = "Sign out",
                    icon = Icons.Rounded.Logout,
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else if (auth is AuthState.Idle || auth is AuthState.Failed) {
            item { SignInCard(onSignIn) }
        }

        item { SectionHeader(title = "How it works") }
        item {
            GlassCard(contentPadding = PaddingValues(18.dp)) {
                Text(
                    text = "RoadClient runs the device-code flow: Microsoft shows a short code, " +
                        "you approve it in any browser, and the client receives an Xbox Live " +
                        "token which it exchanges for a Minecraft session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoadColors.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Tokens are encrypted with an AES-GCM key held in the Android " +
                        "keystore. Only the gamertag is stored in plain text.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RoadColors.TextMuted,
                )
            }
        }
        item {
            GlassCard(contentPadding = PaddingValues(18.dp), glow = accent.end) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(accent.end.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("32", style = MaterialTheme.typography.labelLarge, color = accent.end)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "The signed-in profile is attached to every world you launch " +
                            "and shown in the in-game watermark.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoadColors.TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SignInCard(onSignIn: () -> Unit) {
    val accent = LocalAccent.current
    GlassCard(contentPadding = PaddingValues(22.dp), glow = accent.start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicrosoftMark()
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Sign in with Microsoft",
                    style = MaterialTheme.typography.headlineMedium,
                    color = RoadColors.TextPrimary,
                )
                Text(
                    "Sync your profile and gamertag",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoadColors.TextMuted,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        GradientPillButton(
            text = "Continue",
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth(),
            height = 54.dp,
        )
    }
}

@Composable
private fun MicrosoftMark() {
    Column(
        modifier = Modifier.size(48.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(22.dp).background(Color(0xFFF25022)))
            Box(Modifier.size(22.dp).background(Color(0xFF7FBA00)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(22.dp).background(Color(0xFF00A4EF)))
            Box(Modifier.size(22.dp).background(Color(0xFFFFB900)))
        }
    }
}

@Composable
private fun DeviceCodeCard(code: DeviceCode, stage: String, onCancel: () -> Unit) {
    val accent = LocalAccent.current
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    GlassCard(contentPadding = PaddingValues(22.dp), glow = accent.end) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spinner()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Waiting for approval",
                    style = MaterialTheme.typography.titleLarge,
                    color = RoadColors.TextPrimary,
                )
                Text(
                    stage,
                    style = MaterialTheme.typography.bodySmall,
                    color = RoadColors.TextMuted,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Enter this code at microsoft.com/link",
            style = MaterialTheme.typography.bodyMedium,
            color = RoadColors.TextSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.28f))
                .border(1.dp, accent.end.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = code.userCode,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 30.sp,
                    letterSpacing = 6.sp,
                ),
                color = Color.White,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassPillButton(
                text = "Open browser",
                icon = Icons.Rounded.OpenInNew,
                onClick = { runCatching { uriHandler.openUri(code.verificationUri) } },
                modifier = Modifier.weight(1f),
            )
            GlassPillButton(
                text = "Copy",
                icon = Icons.Rounded.ContentCopy,
                onClick = { clipboard.setText(AnnotatedString(code.userCode)) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        GlassPillButton(
            text = "Cancel",
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProgressCard(message: String, onCancel: () -> Unit) {
    GlassCard(contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spinner()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(message, style = MaterialTheme.typography.titleMedium, color = RoadColors.TextPrimary)
                Text(
                    "This takes a second",
                    style = MaterialTheme.typography.bodySmall,
                    color = RoadColors.TextMuted,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassPillButton(text = "Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FailureCard(message: String, onRetry: () -> Unit) {
    GlassCard(contentPadding = PaddingValues(20.dp), glow = RoadColors.Danger) {
        Text(
            "Sign-in failed",
            style = MaterialTheme.typography.titleLarge,
            color = RoadColors.Danger,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = RoadColors.TextSecondary,
        )
        Spacer(Modifier.height(14.dp))
        GlassPillButton(text = "Try again", onClick = onRetry, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ProfileCard(account: Account) {
    val accent = LocalAccent.current
    GlassCard(contentPadding = PaddingValues(20.dp), glow = accent.start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(accent.start, accent.end))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    account.initial,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    account.displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = RoadColors.TextPrimary,
                )
                Text(
                    "Xbox · ${account.gamertag}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RoadColors.TextMuted,
                )
                if (account.minecraftUuid != null) {
                    Text(
                        "UUID ${account.minecraftUuid.take(8)}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoadColors.TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun Spinner() {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "angle",
    )
    val accent = LocalAccent.current
    Box(
        modifier = Modifier
            .size(30.dp)
            .rotate(angle),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            color = accent.end,
            strokeWidth = 2.5.dp,
        )
    }
}
