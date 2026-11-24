package com.civileg.app.domain.calculations.utils

/** Reference editions authorized for the current repair. A basis identifier is
 * not a claim that every existing method has been rebuilt or accepted to it.
 * New editions are separate entries, never aliases over old formulas.
 */
enum class RcReferenceBasis(val code: String, val edition: String, val verifiedPrimaryTitle: String) {
    ECP_2020("ECP", "ECP 203-2020", "Egyptian Code for Design and Construction of Concrete Structures, 2020"),
    ACI_METRIC_2014("ACI", "ACI 318M-14", "Building Code Requirements for Structural Concrete (ACI 318M-14) and Commentary (ACI 318RM-14)"),
    SBC_CR_2018("SBC", "SBC 304-CR-2018", "Saudi Concrete Structures Code, SBC 304-CR-2018");

    companion object {
        fun forCode(code: String): RcReferenceBasis = entries.single { it.code == code }
    }
}
