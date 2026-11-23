package com.civileg.app.ui.compose.screens.costcontrol

import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.civileg.app.R
import com.civileg.app.viewmodel.costcontrol.CostBoqViewModel

/** Isolated verification host. Real navigation's back/up and project switch must use this same leave action. */
@Composable
fun CostBoqEditorHost(viewModel:CostBoqViewModel,onLeft:()->Unit,onAssistedInput:()->Unit) {
    val state by viewModel.state.collectAsState()
    var leavePrompt by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf(onLeft) }
    fun requestLeave(next:()->Unit) { destination=next;if(viewModel.canLeave()) next() else leavePrompt=true }
    BackHandler { requestLeave(onLeft) }
    if(leavePrompt) AlertDialog(
        onDismissRequest={leavePrompt=false},
        title={Text(stringResource(R.string.cost_boq_leave_title))},
        text={
            androidx.compose.foundation.layout.Column {
                Text(stringResource(R.string.cost_boq_leave_text))
                if(state.loading || state.saving) Text(stringResource(R.string.cost_boq_leave_working))
                if(state.recoveryBlocked) Text(stringResource(R.string.cost_boq_recovery_failed),color=MaterialTheme.colorScheme.error)
                state.errorKey?.let { Text(stringResource(errorResource(it)),color=MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton={TextButton(enabled=!state.loading && !state.saving && !state.recoveryBlocked,onClick={viewModel.leaveEditor(true){leavePrompt=false;destination()}}){Text(stringResource(R.string.cost_boq_keep_leave))}},
        dismissButton={TextButton(enabled=!state.loading && !state.saving,onClick={viewModel.leaveEditor(false){leavePrompt=false;destination()}}){Text(stringResource(R.string.cost_boq_discard_leave))}}
    )
    if(!leavePrompt && (state.currentHeadAdvanced || state.recoveryBlocked)) AlertDialog(
        onDismissRequest={},title={Text(stringResource(R.string.cost_boq_conflict_title))},
        text={Text(stringResource(R.string.cost_boq_conflict_text))},
        confirmButton={TextButton(enabled=!state.loading,onClick={leavePrompt=true}){Text(stringResource(R.string.cost_boq_leave_title))}},
        dismissButton={TextButton(enabled=!state.loading,onClick={viewModel.discardAndReload()}){Text(stringResource(R.string.cost_boq_discard_reload))}}
    )
    CostBoqEditorDraft(state,viewModel::chooseMethod,viewModel::addItem,viewModel::changeItemInput,
        viewModel::applyItemInput,viewModel::confirmManualQuantity,viewModel::save,{requestLeave(onAssistedInput)})
}
