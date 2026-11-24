package com.civileg.app.ui.compose.validation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.civileg.app.R
import com.civileg.app.domain.validation.FieldIssue
import com.civileg.app.domain.validation.IssueCode
import com.civileg.app.domain.validation.ValidationResult
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.compose.components.DesignInputField
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.withFrameNanos
import java.util.Locale

/**
 * Screen-level holder for field issues, focus/scroll targets and the "fix N fields" summary.
 * One instance per page: `val form = rememberFormValidationState()`.
 */
@Stable
class FormValidationState {
    private val _issues = mutableStateMapOf<String, FieldIssue>()
    val issues: Map<String, FieldIssue> get() = _issues
    var errorCount by mutableIntStateOf(0)
        private set

    private val focusRequesters = HashMap<String, FocusRequester>()
    @OptIn(ExperimentalFoundationApi::class)
    private val bringIntoViewRequesters = HashMap<String, BringIntoViewRequester>()

    fun focusRequester(id: String): FocusRequester = focusRequesters.getOrPut(id) { FocusRequester() }

    @OptIn(ExperimentalFoundationApi::class)
    fun bringIntoViewRequester(id: String): BringIntoViewRequester =
        bringIntoViewRequesters.getOrPut(id) { BringIntoViewRequester() }

    /** Replace all issues with those of [result] (call on Solve/Design). */
    fun apply(result: ValidationResult) {
        _issues.clear()
        result.issues.forEach { _issues[it.fieldId] = it }
        errorCount = _issues.size
    }

    /** The user edited [id]: its stale error goes away. */
    fun clear(id: String) {
        if (_issues.remove(id) != null) errorCount = _issues.size
    }

    fun clearAll(ids: Collection<String>) = ids.forEach { clear(it) }

    fun clearAll() {
        _issues.clear(); errorCount = 0
    }

    /**
     * Scroll the first invalid field to the middle of the viewport and focus it.
     * [ensureVisible] lets a lazy list compose the item first (items off-screen have no
     * requester attached yet). Failures to focus are swallowed: the red border and message
     * remain the source of truth.
     */
    @OptIn(ExperimentalFoundationApi::class)
    suspend fun focusFirstInvalid(
        result: ValidationResult,
        ensureVisible: suspend (fieldId: String) -> Unit = {}
    ) {
        val id = result.firstInvalidId ?: return
        runCatching { ensureVisible(id) }
        withFrameNanos { }
        runCatching { bringIntoViewRequester(id).bringIntoView() }
        runCatching { focusRequester(id).requestFocus() }
    }
}

@Composable
fun rememberFormValidationState(): FormValidationState = remember { FormValidationState() }

/** Lazy-list helper: bring the item holding a field near the vertical middle of the viewport. */
suspend fun LazyListState.scrollItemToCenter(itemIndex: Int) {
    val viewport = layoutInfo.viewportSize.height
    animateScrollToItem(itemIndex, scrollOffset = -(viewport / 3))
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.validated(form: FormValidationState, id: String): Modifier =
    this.bringIntoViewRequester(form.bringIntoViewRequester(id))
        .focusRequester(form.focusRequester(id))
        .testTag("field_$id")

/** Localized reason text for an issue. [crossMessages] maps CrossRule.messageKey to text. */
@Composable
fun FieldIssue.reasonText(crossMessages: Map<String, String> = emptyMap()): String = when (code) {
    IssueCode.BLANK -> stringResource(R.string.validation_required)
    IssueCode.NOT_A_NUMBER -> stringResource(R.string.validation_not_number)
    IssueCode.NOT_POSITIVE -> stringResource(R.string.validation_must_be_positive)
    IssueCode.NEGATIVE -> stringResource(R.string.validation_must_not_be_negative)
    IssueCode.NOT_WHOLE -> stringResource(R.string.validation_must_be_whole)
    IssueCode.OUT_OF_RANGE -> stringResource(
        R.string.validation_out_of_range, fmt(min), fmt(max)
    )
    IssueCode.CROSS_FIELD -> crossMessages[messageKey] ?: stringResource(R.string.validation_invalid_combination)
}

private fun fmt(v: Double?): String =
    if (v == null) "-" else if (v == Math.floor(v) && Math.abs(v) < 1e9) v.toLong().toString() else String.format(Locale.ROOT, "%s", v)

/** Numeric input with red border, reason under the field, semantics error and focus/scroll hooks. */
@Composable
fun ValidatedNumberField(
    form: FormValidationState,
    id: String,
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    crossMessages: Map<String, String> = emptyMap()
) {
    val issue = form.issues[id]
    val reason = issue?.reasonText(crossMessages)
    DesignInputField(
        value = value,
        label = label,
        onValueChange = onValueChange,
        modifier = modifier
            .validated(form, id)
            .semantics { if (reason != null) error(reason) },
        isError = issue != null,
        supportingText = reason
    )
}

/** "Fix N field(s) marked in red" line shown above the Solve/Design button after a failed attempt. */
@Composable
fun FormErrorSummary(form: FormValidationState, modifier: Modifier = Modifier) {
    if (form.errorCount > 0) {
        AppText(
            text = stringResource(R.string.validation_summary, form.errorCount),
            color = MaterialTheme.colorScheme.error,
            modifier = modifier.fillMaxWidth().testTag("form_error_summary")
        )
    }
}

/**
 * Checkbox that reveals optional fields. Fields are composed only while [checked], and the
 * caller must (a) pass the group id as active to FormValidator only when [checked] and
 * (b) invalidate the old result in [onCheckedChange].
 */
@Composable
fun OptionalInputGroup(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
            Spacer(Modifier.width(8.dp))
            AppText(label)
        }
        AnimatedVisibility(visible = checked) {
            Column(modifier = Modifier.padding(start = 8.dp)) { content() }
        }
    }
}
