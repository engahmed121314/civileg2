package com.civileg.app.db

import java.text.DateFormat
import java.util.Date

/**
 * Designs migrated from the old nullable-createdAt shape have no recorded creation date.
 * They are stored as 0 (internal "date unknown" marker); no real design is saved at 1970-01-01.
 * Never show that marker as a date.
 */
const val UNKNOWN_CREATED_AT_MILLIS = 0L

val Design.hasKnownCreatedAt: Boolean get() = createdAt.time > UNKNOWN_CREATED_AT_MILLIS

/** The formatted creation date, or [unknownLabel] when the row has no recorded date. */
fun Design.createdAtLabel(format: DateFormat, unknownLabel: String): String =
    if (hasKnownCreatedAt) format.format(createdAt) else unknownLabel
