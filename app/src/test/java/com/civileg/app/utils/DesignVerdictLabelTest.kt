package com.civileg.app.utils

import com.civileg.app.db.DesignType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignVerdictLabelTest {
    @Test fun columnFalseIsNotConfirmedNeverUnsafe() {
        val t = DesignVerdictLabel.label(DesignType.COLUMN, false)
        assertTrue(t.startsWith("Not confirmed"))
        assertFalse(t.contains("UNSAFE"))
    }
    @Test fun columnTrueIsPreliminaryNeverSafe() {
        val t = DesignVerdictLabel.label(DesignType.COLUMN, true)
        assertTrue(t.startsWith("Preliminary"))
        assertFalse(t.contains("SAFE"))
    }
    @Test fun beamAndHordiBehaveLikeColumn() {
        assertTrue(DesignVerdictLabel.label(DesignType.BEAM, false).startsWith("Not confirmed"))
        assertTrue(DesignVerdictLabel.label(DesignType.HORDI_SLAB, true).startsWith("Preliminary"))
    }
    @Test fun otherTypesKeepComponentPassFail() {
        assertTrue(DesignVerdictLabel.label(DesignType.RETAINING_WALL, true) == "Checks passed")
        assertTrue(DesignVerdictLabel.label(DesignType.RETAINING_WALL, false) == "A check did not pass")
    }
}
