package com.civileg.app.ui.compose.screens

import androidx.compose.runtime.Composable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.civileg.app.R
import com.civileg.app.domain.modeler.FrameInputValidation

/** Field-specific persistent feedback, in the selected resource locale, never a disappearing Toast. */
@Composable
internal fun FrameInputField(value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, rule: FrameInputValidation.Rule = FrameInputValidation.Rule.FINITE,
    contextualError: String? = null) {
    val message = contextualError ?: if (FrameInputValidation.valid(value, rule)) null else stringResource(when(rule) {
        FrameInputValidation.Rule.FINITE -> R.string.frame_input_finite
        FrameInputValidation.Rule.POSITIVE -> R.string.frame_input_positive
        FrameInputValidation.Rule.NON_NEGATIVE -> R.string.frame_input_nonnegative
        FrameInputValidation.Rule.ID -> R.string.frame_input_id
        FrameInputValidation.Rule.REQUIRED -> R.string.frame_input_required
    })
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) },
        isError = message != null, supportingText = { if (message != null) Text(message) },
        keyboardOptions = KeyboardOptions(keyboardType = if(rule == FrameInputValidation.Rule.REQUIRED) KeyboardType.Text else KeyboardType.Decimal),
        singleLine = true, modifier = modifier)
}
