package com.civileg.app.support

/**
 * Single source of truth for the optional donation prompt.
 *
 * Rules this config must keep true:
 * - The app is fully usable without donating: declining only hides the prompt.
 * - Nothing here requires network access, billing permissions or a Play Billing
 *   dependency. [SPONSORS_URL] opens in the user's browser.
 */
object DonationConfig {

    /** Set to `false` to ship a build with no donation prompt at all. */
    const val ENABLED: Boolean = true

    /** Owner's GitHub Sponsors page (change this one line to use another provider). */
    const val SPONSORS_URL: String = "https://github.com/sponsors/engahmed121314"

    /**
     * Launches before the one-time prompt appears. Small enough to be seen,
     * large enough that the user has already had value from the app.
     */
    const val MIN_LAUNCHES_BEFORE_PROMPT: Int = 3
}
