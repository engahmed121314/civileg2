package com.civileg.app.support

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.civileg.app.R

/**
 * Persists the launch counter and the user's answer to the donation prompt.
 *
 * Backed by SharedPreferences (not DataStore) on purpose: the decision is read
 * synchronously before the first frame, and a failure here must never delay or
 * block startup.
 */
class DonationPromptState(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Records this launch and reports whether the prompt should be shown now.
     * The prompt is shown at most once per install: both answers mark it answered.
     */
    fun registerLaunchAndCheck(): Boolean {
        val launches = prefs.getInt(KEY_LAUNCHES, 0) + 1
        prefs.edit().putInt(KEY_LAUNCHES, launches).apply()
        val answered = prefs.getBoolean(KEY_ANSWERED, false)
        return DonationConfig.ENABLED &&
            !answered &&
            launches >= DonationConfig.MIN_LAUNCHES_BEFORE_PROMPT
    }

    /** Call for both "Donate" and "Not now" so the prompt never repeats. */
    fun markAnswered() {
        prefs.edit().putBoolean(KEY_ANSWERED, true).apply()
    }

    private companion object {
        const val PREFS_NAME = "donation_prompt"
        const val KEY_LAUNCHES = "launch_count"
        const val KEY_ANSWERED = "answered"
    }
}

/**
 * Opens [DonationConfig.SPONSORS_URL] in a browser.
 *
 * @return `true` when a browser was launched, `false` on any failure. Never throws:
 * a device without a browser must still be able to use the app.
 */
fun openSponsorsPage(context: Context): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DonationConfig.SPONSORS_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (error: ActivityNotFoundException) {
        Log.w(TAG, "No activity can open ${DonationConfig.SPONSORS_URL}", error)
        false
    } catch (error: Exception) {
        Log.w(TAG, "Could not open the sponsorship page", error)
        false
    }
}

/**
 * One-time, skippable donation request. Declining closes the dialog and leaves
 * the app exactly as it was; it is never shown again automatically.
 */
@Composable
fun DonationDialog(
    onDonate: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.donation_title)) },
        text = { Text(stringResource(R.string.donation_message)) },
        confirmButton = {
            TextButton(onClick = onDonate) {
                Text(stringResource(R.string.donation_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(R.string.donation_decline))
            }
        }
    )
}

private const val TAG = "Donation"
