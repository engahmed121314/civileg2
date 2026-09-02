package com.civileg.app.ui.designsystem.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.civileg.app.ui.designsystem.EngineeringStatus
import com.civileg.app.ui.designsystem.engineeringColors

@Composable
fun EngineeringInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    unit: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    validationMessage: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    supportingText: String? = null,
    singleLine: Boolean = true
) {
    val colors = engineeringColors()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        suffix = if (unit.isNotBlank()) {
            { Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        enabled = enabled,
        isError = isError || validationMessage != null,
        supportingText = when {
            validationMessage != null -> {
                { Text(validationMessage, color = colors.warning) }
            }
            supportingText != null -> {
                { Text(supportingText, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            else -> null
        }
    )
}

@Composable
fun EngineeringValidationHint(
    message: String,
    status: EngineeringStatus = EngineeringStatus.WARNING,
    modifier: Modifier = Modifier
) {
    Text(
        text = message,
        color = status.color(),
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
    )
}
