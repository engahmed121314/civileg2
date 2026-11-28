package com.civileg.app.ui.compose.components.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.civileg.app.R

/** True when the screen language is Arabic, so the tables lay out right to left with Arabic text. */
@Composable
fun isArabicUi(): Boolean {
    val cfg = LocalConfiguration.current
    return cfg.locales.get(0)?.language == "ar"
}

/**
 * Display text for a BOQ line. The estimator writes English lines; in the Arabic UI the known
 * phrases are shown from the string resources and the member mark and numbers are kept.
 * A phrase that is not known is shown unchanged.
 */
@Composable
fun localizedBoqText(text: String): String {
    if (!isArabicUi()) return text
    val pairs = listOf(
        "Reinforcement steel (rough estimate, not for procurement: no hooks, anchorage, laps or bends; uniform stirrups; full-span top steel)" to stringResource(R.string.bx_steel_rough),
        "Net flight steel only" to stringResource(R.string.bx_net_flight),
        "excludes anchorage, bends, laps, top and landing steel" to stringResource(R.string.bx_excludes),
        "Reinforcement steel" to stringResource(R.string.bx_steel),
        "Blinding concrete" to stringResource(R.string.bx_blinding),
        "Concrete" to stringResource(R.string.bx_concrete),
        "Excavation" to stringResource(R.string.bx_excavation),
        "Formwork" to stringResource(R.string.bx_formwork),
        "pads + strap sides" to stringResource(R.string.bx_pads_strap_sides),
        "sides + soffit" to stringResource(R.string.bx_sides_soffit),
        "four faces" to stringResource(R.string.bx_four_faces),
        "both faces" to stringResource(R.string.bx_both_faces),
        "base soffit" to stringResource(R.string.bx_base_soffit),
        "inclined soffit" to stringResource(R.string.bx_inclined_soffit),
        "pile shafts" to stringResource(R.string.bx_pile_shafts),
        "cap sides" to stringResource(R.string.bx_cap_sides),
        "piles + cap" to stringResource(R.string.bx_piles_cap),
        "perimeter" to stringResource(R.string.bx_perimeter),
        "soffit" to stringResource(R.string.bx_soffit)
    )
    var out = text
    for ((en, ar) in pairs) out = out.replace(en, ar)
    return out
}
