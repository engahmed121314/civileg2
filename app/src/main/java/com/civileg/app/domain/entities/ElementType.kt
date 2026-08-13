package com.civileg.app.domain.entities

enum class ElementType(val displayName: String) {
    COLUMN("Column"),
    BEAM("Beam"),
    SLAB("Slab"),
    STEEL("Steel Member"),
    FOOTING("Footing"),
    TANK("Water Tank"),
    STAIR("Staircase"),
    RETAINING_WALL("Retaining Wall")
}
