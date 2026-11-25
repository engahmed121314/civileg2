package com.civileg.app.domain.entities

/** Bar surface/bond classification. fy alone does not establish it; when the
 * caller cannot declare it, pass UNKNOWN — engines must take the conservative
 * envelope and report it as an estimate, never as acceptance. */
enum class BarBondClass { DEFORMED, PLAIN, UNKNOWN }
