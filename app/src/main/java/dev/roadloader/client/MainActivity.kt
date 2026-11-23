package dev.roadloader.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.roadloader.client.data.formatBytes
import dev.roadloader.client.session.EngineState
import dev.roadloader.client.ui.components.AuroraBackground
import dev.roadloader.client.ui.components.BottomBar
import dev.roadloader.client.ui.components.GlassCard
import dev.roadloader.client.ui.components.GlassPillButton
import dev.roadloader.client.ui.components.GradientPillButton
import dev.roadloader.client.ui.components.InstanceIcon
import dev.roadloader.client.ui.screens.AccountScreen
import dev.roadloader.client.ui.screens.GameScreen
import dev.roadloader.client.ui.screens.HomeScreen
import dev.roadloader.client.ui.screens.ModsScreen
import dev.roadloader.client.ui.screens.SettingsScreen
import dev.roadloader.client.ui.screens.WorldsScreen
import dev.roadloader.client.ui.theme.RoadColors
import dev.roadloader.client.ui.theme.RoadTheme
import dev.roadloader.client.vm.ClientTab
import dev.roadloader.client.vm.ClientUiState
import dev.roadloader.client.vm.ClientViewModel
import dev.roadloader.client.vm.ImportState
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        setContent {
            val viewModel: ClientViewModel = viewModel()
            RoadClientRoot(viewModel)
        }
    }
}

@Composable
private fun RoadClientRoot(viewModel: ClientViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accent = RoadColors.accents[state.settings.accentIndex.coerceIn(0, RoadColors.accents.lastIndex)]
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importApk)
    }
    val importApk = {
        picker.launch(
            arrayOf(
                "application/vnd.android.package-archive",
                "application/octet-stream",
                "*/*",
            ),
        )
    }

    RoadTheme(accent = accent) {
        Box(Modifier.fillMaxSize().background(RoadColors.Ink)) {
            AuroraBackground(
                accentStart = accent.start,
                accentEnd = accent.end,
                intensity = if (state.inGame) 0.35f else 1f,
            )
            if (state.inGame) {
                GameScreen(
                    host = viewModel.host,
                    instance = state.selected,
                    stats = state.stats,
                    firstFrame = state.firstFrame,
                    engine = state.engine,
                    openMenuOnLaunch = state.settings.openMenuOnLaunch,
                    onCommand = viewModel::handleMenuCommand,
                    onStop = viewModel::stopEngine,
                )
            } else {
                LauncherScaffold(
                    state = state,
                    viewModel = viewModel,
                    onImport = importApk,
                )
            }

            state.banner?.let { message ->
                Banner(message = message, onDismiss = viewModel::dismissBanner)
            }

            if (state.importState !is ImportState.Idle) {
                ImportDialog(state = state, onDismiss = viewModel::dismissImport)
            }
        }
    }
}

@Composable
private fun LauncherScaffold(
    state: ClientUiState,
    viewModel: ClientViewModel,
    onImport: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val launch: () -> Unit = {
        val instance = state.selected
        if (instance != null) {
            val longSide = 1280
            val shortSide = (longSide * min(configuration.screenWidthDp, configuration.screenHeightDp) /
                max(configuration.screenWidthDp, configuration.screenHeightDp)).let { (it / 2) * 2 }
            val error = viewModel.launch(instance, longSide, shortSide.coerceAtLeast(480))
            if (error != null) {
                viewModel.host.pushEvent(error)
            }
        } else {
            onImport()
        }
    }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        AnimatedContent(
            targetState = state.tab,
            transitionSpec = {
                (fadeIn() + slideInHorizontally { it / 14 }) togetherWith fadeOut()
            },
            label = "tabs",
        ) { tab ->
            when (tab) {
                ClientTab.Home -> HomeScreen(
                    state = state,
                    onLaunch = launch,
                    onImport = onImport,
                    onNavigate = viewModel::selectTab,
                )
                ClientTab.Worlds -> WorldsScreen(
                    instances = state.instances,
                    selectedId = state.selectedId,
                    onLaunch = { instance ->
                        viewModel.selectInstance(instance.id)
                        launch()
                    },
                    onSelect = { instance -> viewModel.selectInstance(instance.id) },
                    onRemove = { instance -> viewModel.removeInstance(instance.id) },
                    onImport = onImport,
                )
                ClientTab.Mods -> ModsScreen(
                    settings = state.settings,
                    onToggleModule = viewModel::setModule,
                    onUpdate = viewModel::updateSettings,
                )
                ClientTab.Account -> AccountScreen(
                    account = state.activeAccount,
                    auth = state.auth,
                    autoSignIn = state.settings.autoSignIn,
                    onSignIn = viewModel::signIn,
                    onCancel = viewModel::cancelSignIn,
                    onSignOut = viewModel::signOut,
                    onAutoSignIn = viewModel::setAutoSignIn,
                )
                ClientTab.Settings -> SettingsScreen(
                    settings = state.settings,
                    stats = state.stats,
                    coreVersion = dev.roadloader.core.NativeHost.coreVersion(),
                    abi = dev.roadloader.core.NativeHost.abiInfo(),
                    onUpdate = viewModel::updateSettings,
                    onResetCrashGuard = viewModel::resetCrashGuard,
                )
            }
        }

        // Scrim so scrolling content fades out behind the floating bar.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(132.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xE60A0F1E), Color(0xF20A0F1E)),
                    ),
                ),
        )

        BottomBar(
            selected = state.tab,
            onSelect = viewModel::selectTab,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 10.dp),
        )
    }
}

@Composable
private fun Banner(message: String, onDismiss: () -> Unit) {
    LaunchedEffect(message) {
        delay(3_600)
        onDismiss()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0xF00B1122))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelLarge,
                color = RoadColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = RoadColors.TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun ImportDialog(state: ClientUiState, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(24.dp),
        ) {
            when (val importState = state.importState) {
                is ImportState.Working -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = RoadColors.Mint,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                "Importing",
                                style = MaterialTheme.typography.titleLarge,
                                color = RoadColors.TextPrimary,
                            )
                            Text(
                                importState.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = RoadColors.TextMuted,
                            )
                        }
                    }
                }
                is ImportState.Done -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InstanceIcon(instance = importState.instance, size = 54.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                importState.instance.displayName,
                                style = MaterialTheme.typography.titleLarge,
                                color = RoadColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${importState.instance.shortVersion} · ${formatBytes(importState.instance.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = RoadColors.TextMuted,
                            )
                        }
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = if (importState.instance.compatible) RoadColors.Success else RoadColors.Warning,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = importState.instance.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = RoadColors.TextSecondary,
                    )
                    Spacer(Modifier.height(18.dp))
                    GradientPillButton(
                        text = "Done",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is ImportState.Failed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = RoadColors.Danger,
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Import failed",
                            style = MaterialTheme.typography.titleLarge,
                            color = RoadColors.TextPrimary,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = importState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoadColors.TextSecondary,
                    )
                    Spacer(Modifier.height(18.dp))
                    GlassPillButton(
                        text = "Close",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ImportState.Idle -> Unit
            }
        }
    }
}
