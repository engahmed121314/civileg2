package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.R
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import com.civileg.app.utils.tools.AndroidFloatPrefs
import com.civileg.app.utils.tools.RulerMath
import com.civileg.app.utils.tools.ToolCalibrationStore
import java.util.Locale

/**
 * On-screen ruler, vertical: millimetre scale on the left edge, centimetre scale on the right edge.
 * Zero is the top of the ruler area (below the app bar), not the physical bezel.
 * Accuracy depends on calibration: until the user matches an ID-1 card (85.60 mm), the scale uses the
 * device-reported ydpi, which is not reliable on every device, and the screen says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulerScreen(onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    val store = remember { ToolCalibrationStore(AndroidFloatPrefs(context)) }
    val dpi = remember { context.resources.displayMetrics.ydpi }
    var stored by remember { mutableStateOf(store.rulerFactorFor(dpi)) }
    var calibrating by remember { mutableStateOf(false) }
    val factor = stored ?: 1.0
    val pxPerMm = RulerMath.pxPerMm(dpi, factor)
    val measurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.onSurface
    val cardColor = MaterialTheme.colorScheme.primary

    // Calibration handle: y position in px of the card's far end, measured from the ruler top.
    var handlePx by remember(pxPerMm) { mutableFloatStateOf((RulerMath.ID1_CARD_LONG_MM * pxPerMm).toFloat()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AppText(stringResource(R.string.tool_ruler), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AppText(
                stringResource(if (stored != null) R.string.ruler_calibrated else R.string.ruler_uncalibrated),
                color = if (stored != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (calibrating) {
                AppText(stringResource(R.string.ruler_calibrate_help), modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            }
            Canvas(
                Modifier.weight(1f).fillMaxWidth().pointerInput(calibrating, pxPerMm) {
                    if (calibrating) detectVerticalDragGestures { change, dy ->
                        change.consume()
                        handlePx = (handlePx + dy).coerceIn(pxPerMm.toFloat() * 10f, size.height.toFloat())
                    }
                }
            ) {
                val lengthMm = RulerMath.pxToMm(size.height.toDouble(), pxPerMm)
                val labelStyle = TextStyle(fontSize = 11.sp, color = lineColor)
                for (t in RulerMath.ticks(lengthMm)) {
                    val y = RulerMath.mmToPx(t.mm.toDouble(), pxPerMm).toFloat()
                    val len = when (t.kind) { RulerMath.TickKind.CM -> 36.dp.toPx(); RulerMath.TickKind.HALF_CM -> 24.dp.toPx(); RulerMath.TickKind.MM -> 14.dp.toPx() }
                    // Left edge: millimetres. Right edge: centimetres.
                    drawLine(lineColor, Offset(0f, y), Offset(len, y), 1.5f)
                    if (t.kind == RulerMath.TickKind.CM) {
                        drawLine(lineColor, Offset(size.width, y), Offset(size.width - len, y), 1.5f)
                        val cm = t.mm / 10
                        val tl = measurer.measure(cm.toString(), labelStyle)
                        drawText(tl, topLeft = Offset(size.width - len - tl.size.width - 4.dp.toPx(), y - tl.size.height / 2f))
                    }
                    if (t.kind != RulerMath.TickKind.MM) {
                        val tl = measurer.measure(t.mm.toString(), labelStyle)
                        drawText(tl, topLeft = Offset(len + 4.dp.toPx(), y - tl.size.height / 2f))
                    }
                }
                // Right edge also carries mm-free cm ticks only; unit captions:
                drawText(measurer.measure("mm", labelStyle), topLeft = Offset(4.dp.toPx(), 2.dp.toPx()))
                val cmCap = measurer.measure("cm", labelStyle)
                drawText(cmCap, topLeft = Offset(size.width - cmCap.size.width - 4.dp.toPx(), 2.dp.toPx()))
                if (calibrating) {
                    val cw = RulerMath.ID1_CARD_SHORT_MM * pxPerMm
                    val left = (size.width - cw.toFloat()) / 2f
                    drawRect(cardColor.copy(alpha = 0.15f), Offset(left, 0f), Size(cw.toFloat(), handlePx))
                    drawRect(cardColor, Offset(left, 0f), Size(cw.toFloat(), handlePx), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                }
            }
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!calibrating) {
                    AppButton(label = stringResource(R.string.ruler_calibrate), onClick = { calibrating = true }, modifier = Modifier.weight(1f))
                    if (stored != null) AppButton(
                        label = stringResource(R.string.ruler_reset),
                        onClick = { store.clearRuler(); stored = null },
                        style = AppButtonStyle.Secondary, modifier = Modifier.weight(1f)
                    )
                } else {
                    AppButton(
                        label = stringResource(R.string.ruler_save),
                        onClick = {
                            // The rectangle is 85.60 mm long at the current scale; the user stretched it to the card's real length.
                            // shownMm = what the ruler currently calls the length of the card; realMm = the card's true length.
                            val shownMm = RulerMath.pxToMm(handlePx.toDouble(), pxPerMm)
                            val newFactor = RulerMath.recalibrate(factor, RulerMath.ID1_CARD_LONG_MM, shownMm)
                            store.saveRuler(newFactor, dpi); stored = store.rulerFactorFor(dpi); calibrating = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    AppButton(label = stringResource(R.string.ruler_cancel), onClick = { calibrating = false }, style = AppButtonStyle.Secondary, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
