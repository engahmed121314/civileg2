package com.civileg.app.ui.compose.screens.costcontrol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.civileg.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.civileg.app.db.costcontrol.CostBoqItemEntity
import com.civileg.app.viewmodel.costcontrol.BoqWorkMethod
import com.civileg.app.viewmodel.costcontrol.CostBoqUiState
import com.civileg.app.viewmodel.costcontrol.CostBoqItemInput

/**
 * UNPUBLISHED unbound editor draft. Parent owns revision creation/hash, validation, lifecycle collection,
 * translated labels, discard confirmation and persistent repository. No remember-only fake saved data.
 */
@Composable
fun CostBoqEditorDraft(
    state: CostBoqUiState,
    onMethod: (BoqWorkMethod) -> Unit,
    onAdd: () -> Unit,
    onInput: (String, CostBoqItemInput) -> Unit,
    onApply: (String) -> Unit,
    onReviewQuantity: (String) -> Unit,
    onSave: () -> Unit,
    onAssistedInput: () -> Unit
) {
    val busy = state.loading || state.saving || state.recoveryBlocked || state.currentHeadAdvanced
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.cost_boq_method), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.method == BoqWorkMethod.ENTER_ITEMS_AND_QUANTITIES,
                onClick = { onMethod(BoqWorkMethod.ENTER_ITEMS_AND_QUANTITIES) }, enabled = !busy && state.itemInputs.isEmpty(),
                label = { Text(stringResource(R.string.cost_boq_manual)) }, modifier = Modifier.weight(1f))
            FilterChip(selected = state.method == BoqWorkMethod.DERIVE_FROM_PROJECT_DATA,
                onClick = { onMethod(BoqWorkMethod.DERIVE_FROM_PROJECT_DATA) }, enabled = !busy && state.itemInputs.isEmpty(),
                label = { Text(stringResource(R.string.cost_boq_assisted)) }, modifier = Modifier.weight(1f))
        }
        if (state.projectId == null) { Text(stringResource(R.string.cost_boq_select_project)); return@Column }
        if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); return@Column }
        state.errorKey?.let { key -> Text(stringResource(errorResource(key)), color = MaterialTheme.colorScheme.error) }
        if(state.checkpointPending) Text(stringResource(R.string.cost_boq_checkpoint_pending))
        if(state.checkpointAcknowledged) Text(stringResource(R.string.cost_boq_checkpoint_ack))
        if(state.currentHeadAdvanced) Text(stringResource(R.string.cost_boq_head_advanced),color=MaterialTheme.colorScheme.error)
        Text(if (state.unsavedChanges) stringResource(R.string.cost_boq_unsaved) else if (state.saved != null) stringResource(R.string.cost_boq_saved_revision,state.saved.revision.revisionId) else stringResource(R.string.cost_boq_no_saved))
        Text(stringResource(R.string.cost_boq_unknown), style = MaterialTheme.typography.bodySmall)
        if (state.method == BoqWorkMethod.DERIVE_FROM_PROJECT_DATA) {
            Text(stringResource(R.string.cost_boq_assisted_note))
            OutlinedButton(onClick = onAssistedInput, enabled = !busy) { Text(stringResource(R.string.cost_boq_review_scope)) }
        }
        Button(onClick = onAdd, enabled = !busy) { Text(stringResource(R.string.cost_boq_add)) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.draft?.items.orEmpty(), key = { it.itemId }) { item ->
                ItemCard(item, state.itemInputs[item.itemId] ?: CostBoqItemInput.from(item), busy, onInput, onApply, onReviewQuantity)
            }
        }
        if (state.itemInputs.isNotEmpty()) Text(stringResource(R.string.cost_boq_apply_first))
        Button(onClick = onSave, enabled = state.itemInputs.isEmpty() && !busy && state.unsavedChanges && state.draft?.items?.isNotEmpty() == true,
            modifier = Modifier.fillMaxWidth()) { Text(if (state.saving) stringResource(R.string.cost_boq_saving) else stringResource(R.string.cost_boq_save)) }
        Text(stringResource(R.string.cost_boq_export_pending), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ItemCard(item: CostBoqItemEntity, input:CostBoqItemInput, busy: Boolean,
                     onInput:(String,CostBoqItemInput)->Unit,onApply:(String)->Unit,onReviewQuantity:(String)->Unit) {
    val dirty = input != CostBoqItemInput.from(item)
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input.number, { onInput(item.itemId,input.copy(number=it)) }, label = { Text(stringResource(R.string.cost_boq_number)) }, enabled = !busy)
            OutlinedTextField(input.description, { onInput(item.itemId,input.copy(description=it)) }, label = { Text(stringResource(R.string.cost_boq_description)) }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(input.unit, { onInput(item.itemId,input.copy(unit=it)) }, label = { Text(stringResource(R.string.cost_boq_unit)) }, modifier = Modifier.weight(1f), enabled = !busy)
                OutlinedTextField(input.quantity, { onInput(item.itemId,input.copy(quantity=it)) }, label = { Text(stringResource(R.string.cost_boq_quantity)) }, modifier = Modifier.weight(1f), enabled = !busy)
            }
            OutlinedTextField(input.rate, { onInput(item.itemId,input.copy(rate=it)) }, label = { Text(stringResource(R.string.cost_boq_rate)) }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            Text(stringResource(R.string.cost_boq_origin_currency,stringResource(originResource(item.quantityOrigin)),item.currency ?: stringResource(R.string.cost_boq_not_recorded)), style = MaterialTheme.typography.bodySmall)
            if(item.quantityOrigin=="MANUAL" && item.quantityText!=null && !item.quantityReviewConfirmed) {
                OutlinedButton(onClick={ onReviewQuantity(item.itemId) },enabled=!busy && !dirty) { Text(stringResource(R.string.cost_boq_confirm_quantity)) }
            }
            Text(stringResource(R.string.cost_boq_rate_note), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { onApply(item.itemId) }, enabled = !busy && dirty) { Text(stringResource(R.string.cost_boq_apply)) }
        }
    }
}

internal fun errorResource(key:String):Int = when(key) {
    "cost_boq_recovery_failed" -> R.string.cost_boq_recovery_failed
    "cost_boq_checkpoint_failed" -> R.string.cost_boq_checkpoint_failed
    "cost_boq_checkpoint_cleanup_failed" -> R.string.cost_boq_checkpoint_cleanup_failed
    "cost_boq_head_unverified" -> R.string.cost_boq_head_unverified
    "cost_boq_load_failed" -> R.string.cost_boq_load_failed
    "cost_boq_save_failed" -> R.string.cost_boq_save_failed
    "cost_boq_invalid_item" -> R.string.cost_boq_invalid_item
    "cost_boq_review_failed" -> R.string.cost_boq_review_failed
    "cost_boq_project_removed" -> R.string.cost_boq_project_removed
    else -> R.string.cost_boq_unknown_error
}
private fun originResource(origin:String):Int = when(origin) {
    "MANUAL" -> R.string.cost_boq_origin_manual
    "DECLARED_GEOMETRY" -> R.string.cost_boq_origin_geometry
    "VERIFIED_DESIGN" -> R.string.cost_boq_origin_design
    "ASSUMED" -> R.string.cost_boq_origin_assumed
    else -> R.string.cost_boq_origin_missing
}
