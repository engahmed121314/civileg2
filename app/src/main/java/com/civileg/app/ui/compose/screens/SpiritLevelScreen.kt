package com.civileg.app.ui.compose.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.civileg.app.R
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.utils.tools.AndroidFloatPrefs
import com.civileg.app.utils.tools.SpiritLevelMath
import com.civileg.app.utils.tools.ToolCalibrationStore
import java.util.Locale

/** Display range: the bubble reaches the rim at this tilt. A presentation scale, not a tolerance. */
private const val DIAL_FULL_SCALE_DEG = 10.0
/** Display smoothing only (see SpiritLevelMath.smooth). */
private const val SMOOTHING_ALPHA = 0.2

/**
 * Sensor spirit level (flat, face-up). Not the survey "Water Level" tool.
 * Android needs no runtime permission for the accelerometer / gravity sensor at normal rates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpiritLevelScreen(onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    val view = LocalView.current
    val store = remember { ToolCalibrationStore(AndroidFloatPrefs(context)) }
    val sm = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val sensor = remember(sm) { sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    // Raw device-axis tilt, smoothed for display. Zero offset is also in device axes so rotation cannot corrupt it.
    var rawX by remember { mutableStateOf<Double?>(null) }
    var rawY by remember { mutableStateOf<Double?>(null) }
    var faceUp by remember { mutableStateOf(true) }
    var unreliable by remember { mutableStateOf(false) }
    var zero by remember { mutableStateOf(store.levelZero()) }
    var rotation by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, sensor) {
        if (sm == null || sensor == null) return@DisposableEffect onDispose {}
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                rotation = (view.display?.rotation ?: 0) * 90
                faceUp = SpiritLevelMath.isFaceUp(e.values[2].toDouble())
                val t = SpiritLevelMath.tilt(e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble()) ?: return
                rawX = SpiritLevelMath.smooth(rawX, t.xDeg, SMOOTHING_ALPHA)
                rawY = SpiritLevelMath.smooth(rawY, t.yDeg, SMOOTHING_ALPHA)
            }
            override fun onAccuracyChanged(s: Sensor, accuracy: Int) { unreliable = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE }
        }
        // Listen only while visible: sensors are not delivered in the background on Android 9+ and drain battery.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                Lifecycle.Event.ON_PAUSE -> { sm.unregisterListener(listener); rawX = null; rawY = null }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sm.unregisterListener(listener)
        }
    }

    val screenTilt: SpiritLevelMath.Tilt? = if (rawX != null && rawY != null) {
        val z = zero
        val zeroed = SpiritLevelMath.withZero(SpiritLevelMath.Tilt(rawX!!, rawY!!), z?.xDeg ?: 0.0, z?.yDeg ?: 0.0)
        val (sx, sy) = SpiritLevelMath.toScreenAxes(zeroed.xDeg, zeroed.yDeg, rotation)
        SpiritLevelMath.Tilt(sx, sy)
    } else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AppText(stringResource(R.string.tool_spirit_level), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (sensor == null) {
                AppText(stringResource(R.string.level_sensor_unavailable), color = MaterialTheme.colorScheme.error)
                return@Column
            }
            if (!faceUp) AppText(stringResource(R.string.level_face_up_hint), color = MaterialTheme.colorScheme.error)
            if (unreliable) AppText(stringResource(R.string.level_sensor_unreliable), color = MaterialTheme.colorScheme.error)

            val tilt = screenTilt
            val ringColor = MaterialTheme.colorScheme.outline
            val bubbleColor = if (tilt != null && SpiritLevelMath.displayedLevel(tilt)) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val dialSize = minOf(maxWidth, maxHeight)
                Canvas(Modifier.size(dialSize)) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f * 0.95f
                    drawCircle(ringColor, r, c, style = Stroke(3f))
                    drawCircle(ringColor, r * 0.5f, c, style = Stroke(1.5f))
                    drawLine(ringColor, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.5f)
                    drawLine(ringColor, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.5f)
                    if (tilt != null) {
                        // Bubble goes to the high side: +x right edge higher -> right; +y top higher -> up (canvas y is down).
                        val bx = SpiritLevelMath.bubbleOffset(tilt.xDeg, DIAL_FULL_SCALE_DEG).toFloat()
                        val by = SpiritLevelMath.bubbleOffset(tilt.yDeg, DIAL_FULL_SCALE_DEG).toFloat()
                        val br = r * 0.12f
                        val limit = r - br
                        var ox = bx * limit; var oy = -by * limit
                        val len = kotlin.math.sqrt(ox * ox + oy * oy)
                        if (len > limit) { ox *= limit / len; oy *= limit / len }
                        drawCircle(bubbleColor, br, Offset(c.x + ox, c.y + oy))
                    }
                }
            }
            if (tilt != null) {
                AppText(
                    stringResource(R.string.level_readout,
                        String.format(Locale.ROOT, "%.1f", SpiritLevelMath.round1(tilt.xDeg)),
                        String.format(Locale.ROOT, "%.1f", SpiritLevelMath.round1(tilt.yDeg)),
                        String.format(Locale.ROOT, "%.1f", SpiritLevelMath.slopePercent(tilt.xDeg)),
                        String.format(Locale.ROOT, "%.1f", SpiritLevelMath.slopePercent(tilt.yDeg))),
                    fontWeight = FontWeight.Bold
                )
            } else {
                AppText(stringResource(R.string.level_waiting))
            }
            AppText(
                stringResource(if (zero == null) R.string.level_zero_none else R.string.level_zero_set),
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(
                    label = stringResource(R.string.level_set_zero),
                    onClick = {
                        val x = rawX; val y = rawY
                        if (x != null && y != null) {
                            // Zero is the raw device-axis tilt on a surface the user treats as level.
                            store.saveLevelZero(x, y); zero = store.levelZero()
                        }
                    },
                    enabled = tilt != null && faceUp,
                    modifier = Modifier.weight(1f)
                )
                AppButton(
                    label = stringResource(R.string.level_reset_zero),
                    onClick = { store.clearLevelZero(); zero = null },
                    style = AppButtonStyle.Secondary,
                    modifier = Modifier.weight(1f)
                )
            }
            AppText(stringResource(R.string.level_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}
