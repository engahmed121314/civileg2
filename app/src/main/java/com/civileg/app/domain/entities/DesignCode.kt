package com.civileg.app.domain.entities

enum class DesignCode(val displayName: String, val version: String) {
    ECP("Egyptian Code", "ECP 203-2020"),
    ACI("American Code", "ACI 318-19"),
    SBC("Saudi Code", "SBC 304-2018")
}
