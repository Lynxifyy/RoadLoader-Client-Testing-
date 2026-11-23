package dev.roadloader.client.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.roadloader.client.data.SettingsStore
import dev.roadloader.client.model.Instance
import dev.roadloader.client.session.EngineHost
import dev.roadloader.client.session.EngineState
import dev.roadloader.client.session.EngineStats
import dev.roadloader.client.session.GameRenderer
import dev.roadloader.client.session.GameSurfaceView
import dev.roadloader.client.session.NativeOverlay
import dev.roadloader.client.ui.components.GlassPillButton
import dev.roadloader.client.ui.components.StatChip
import dev.roadloader.client.ui.theme.LocalAccent
import dev.roadloader.client.ui.theme.RoadColors
import kotlinx.coroutines.delay

/**
 * Fullscreen in-game view: the GL surface with the ImGui overlay, plus a thin
 * Compose chrome that fades away so it never fights the HUD.
 */
@Composable
fun GameScreen(
    host: EngineHost,
    instance: Instance?,
    stats: EngineStats,
    firstFrame: Boolean,
    engine: EngineState,
    openMenuOnLaunch: Boolean,
    onCommand: (NativeOverlay.Command) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore.get(context) }
    val accent = LocalAccent.current
    val commandSink = remember { mutableStateOf<(NativeOverlay.Command) -> Unit>({}) }
    commandSink.value = onCommand
    val frameSink = remember { mutableStateOf<() -> Unit>({}) }
    val renderer = remember {
        GameRenderer(
            context = context,
            host = host,
            settings = settingsStore,
            onCommand = { command -> commandSink.value(command) },
            onFrameDrawn = { frameSink.value() },
        )
    }

    var showChrome by remember { mutableStateOf(true) }
    var menuOpened by remember { mutableStateOf(false) }

    LaunchedEffect(instance) {
        instance?.let { renderer.bind(it) }
    }
    LaunchedEffect(settingsStore) {
        renderer.reconfigure(settingsStore.current())
    }
    LaunchedEffect(firstFrame, openMenuOnLaunch) {
        if (firstFrame && openMenuOnLaunch && !menuOpened) {
            menuOpened = true
            renderer.setMenuOpen(true)
        }
    }
    LaunchedEffect(showChrome) {
        if (showChrome) {
            delay(5_000)
            showChrome = false
        }
    }

    val currentStop by rememberUpdatedState(onStop)
    var confirmExit by remember { mutableStateOf(false) }
    BackHandler(enabled = !confirmExit) { confirmExit = true }

    val activity = context as? Activity
    DisposableEffect(Unit) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity?.window
        val controller = window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, it.decorView)
        }
        controller?.let {
            it.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation =
                previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                GameSurfaceView(ctx).also { view -> view.attach(host, renderer) }
            },
        )

        // Tap strip along the top edge brings the chrome back.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .statusBarsPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showChrome = !showChrome },
        )

        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xCC0B1122))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (firstFrame) RoadColors.Success else RoadColors.Amber),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = instance?.displayName ?: "32-bit engine",
                                style = MaterialTheme.typography.labelLarge,
                                color = RoadColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    StatChip(label = "FPS", value = stats.fps.toString(), accent = accent.end)
                    Spacer(Modifier.width(6.dp))
                    StatChip(label = "PING", value = "${stats.pingMs} ms")
                }
                GlassPillButton(
                    text = "Stop",
                    icon = Icons.Rounded.Close,
                    onClick = onStop,
                )
            }
        }

        if (!firstFrame) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xF00A1024), Color(0xF0060915)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = accent.end, strokeWidth = 3.dp)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "Starting the 32-bit engine",
                        style = MaterialTheme.typography.titleLarge,
                        color = RoadColors.TextPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = instance?.let { "${it.displayName} · ${it.shortVersion}" }
                            ?: "Preparing shared memory",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoadColors.TextMuted,
                    )
                    Spacer(Modifier.height(22.dp))
                    GlassPillButton(text = "Cancel", onClick = onStop)
                }
            }
        }

        if (confirmExit) {
            AlertDialog(
                onDismissRequest = { confirmExit = false },
                title = { Text("Leave the world?") },
                text = { Text("The 32-bit companion is stopped and you return to the library.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmExit = false
                        currentStop()
                    }) { Text("Leave") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmExit = false }) { Text("Stay") }
                },
                containerColor = RoadColors.NightElevated,
                titleContentColor = RoadColors.TextPrimary,
                textContentColor = RoadColors.TextSecondary,
            )
        }

        if (engine is EngineState.Failed) {
            AlertDialog(
                onDismissRequest = onStop,
                title = { Text("32-bit engine failure") },
                text = { Text(engine.message) },
                confirmButton = { TextButton(onClick = onStop) { Text("Back to library") } },
                containerColor = RoadColors.NightElevated,
                titleContentColor = RoadColors.TextPrimary,
                textContentColor = RoadColors.TextSecondary,
            )
        }
    }
}
