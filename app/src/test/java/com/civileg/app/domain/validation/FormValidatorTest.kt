package com.civileg.app.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormValidatorTest {

    private fun spec(id: String, raw: String, kind: FieldKind = FieldKind.POSITIVE, group: String? = null,
                     min: Double? = null, max: Double? = null) = FieldSpec(id, raw, kind, group, min, max)

    @Test fun validValuesAreReturnedAndNoIssues() {
        val r = FormValidator.validate(listOf(spec("a", "4.5"), spec("b", "0", FieldKind.NON_NEGATIVE)))
        assertTrue(r.isValid)
        assertEquals(4.5, r.value("a"), 0.0)
        assertEquals(0.0, r.value("b"), 0.0)
        assertNull(r.firstInvalidId)
    }

    @Test fun blankIsAnIssueNotADefault() {
        val r = FormValidator.validate(listOf(spec("a", "  ")))
        assertEquals(IssueCode.BLANK, r.issues.single().code)
        assertNull(r.valueOrNull("a"))
    }

    @Test fun nanInfinityHexSuffixAndGarbageAreRejected() {
        for (bad in listOf("NaN", "Infinity", "-Infinity", "1e999", "0x1p3", "5d", "5f", "1,000.5", "abc", "1..2", "--1", "1 2")) {
            val r = FormValidator.validate(listOf(spec("a", bad, FieldKind.FINITE)))
            assertEquals("input '$bad'", IssueCode.NOT_A_NUMBER, r.issues.single().code)
        }
    }

    @Test fun arabicDigitsAndDecimalMarksParse() {
        assertEquals(12.5, FormValidator.parseNumber("١٢٫٥")!!, 0.0)
        assertEquals(12.5, FormValidator.parseNumber("۱۲.۵")!!, 0.0)
        assertEquals(12.5, FormValidator.parseNumber("12,5")!!, 0.0)
        assertEquals(1500.0, FormValidator.parseNumber("1.5e3")!!, 0.0)
    }

    @Test fun kindRules() {
        assertEquals(IssueCode.NOT_POSITIVE, FormValidator.validate(listOf(spec("a", "0"))).issues.single().code)
        assertEquals(IssueCode.NOT_POSITIVE, FormValidator.validate(listOf(spec("a", "-1"))).issues.single().code)
        assertEquals(IssueCode.NEGATIVE, FormValidator.validate(listOf(spec("a", "-0.1", FieldKind.NON_NEGATIVE))).issues.single().code)
        assertEquals(IssueCode.NOT_WHOLE, FormValidator.validate(listOf(spec("a", "12.5", FieldKind.POSITIVE_INT))).issues.single().code)
        assertTrue(FormValidator.validate(listOf(spec("a", "12", FieldKind.POSITIVE_INT))).isValid)
        assertTrue(FormValidator.validate(listOf(spec("a", "-3.5", FieldKind.FINITE))).isValid)
    }

    @Test fun callerSuppliedRangeIsInclusiveAndCarriesBounds() {
        val ok = FormValidator.validate(listOf(spec("a", "100", min = 100.0, max = 200.0), spec("b", "200", min = 100.0, max = 200.0)))
        assertTrue(ok.isValid)
        val bad = FormValidator.validate(listOf(spec("a", "99.9", min = 100.0, max = 200.0))).issues.single()
        assertEquals(IssueCode.OUT_OF_RANGE, bad.code)
        assertEquals(100.0, bad.min!!, 0.0)
        assertEquals(200.0, bad.max!!, 0.0)
    }

    @Test fun issuesFollowScreenOrderAndFirstInvalidIsTopmost() {
        val r = FormValidator.validate(listOf(spec("a", "1"), spec("b", ""), spec("c", "x")))
        assertEquals(listOf("b", "c"), r.issues.map { it.fieldId })
        assertEquals("b", r.firstInvalidId)
    }

    @Test fun inactiveOptionalGroupIsExcludedEvenWithRetainedValueAndBlank() {
        val specs = listOf(spec("w", "5"), spec("mu", "123", group = "custom"), spec("vu", "", group = "custom"))
        val off = FormValidator.validate(specs, activeGroups = emptySet())
        assertTrue(off.isValid)                 // blank hidden field is not an error
        assertNull(off.valueOrNull("mu"))       // retained typed value does not contribute
        assertNull(off.valueOrNull("vu"))
        assertEquals(setOf("w"), off.values.keys)
    }

    @Test fun enabledOptionalGroupValidatesAndContributes() {
        val specs = listOf(spec("w", "5"), spec("mu", "123", group = "custom"), spec("vu", "", group = "custom"))
        val on = FormValidator.validate(specs, activeGroups = setOf("custom"))
        assertEquals(listOf("vu"), on.issues.map { it.fieldId })
        assertEquals(IssueCode.BLANK, on.issues.single().code)
        assertEquals(123.0, on.value("mu"), 0.0) // valid active field is returned; invalid one is not
        assertNull(on.valueOrNull("vu"))
    }

    @Test fun mandatoryFieldStaysMandatoryWhenGroupOff() {
        val r = FormValidator.validate(listOf(spec("w", ""), spec("mu", "1", group = "custom")), emptySet())
        assertEquals(listOf("w"), r.issues.map { it.fieldId })
    }

    @Test fun crossRuleRunsOnlyWhenDependenciesValid() {
        val rule = CrossRule("cover", setOf("cover", "width"), "cover_too_large") { v -> 2 * v.getValue("cover") < v.getValue("width") }
        val bad = FormValidator.validate(listOf(spec("width", "250"), spec("cover", "130")), crossRules = listOf(rule))
        assertEquals(IssueCode.CROSS_FIELD, bad.issues.single().code)
        assertEquals("cover", bad.issues.single().fieldId)
        assertEquals("cover_too_large", bad.issues.single().messageKey)
        assertFalse(bad.values.containsKey("cover"))
        val skipped = FormValidator.validate(listOf(spec("width", "250"), spec("cover", "")), crossRules = listOf(rule))
        assertEquals(IssueCode.BLANK, skipped.issues.single().code)
        val ok = FormValidator.validate(listOf(spec("width", "250"), spec("cover", "40")), crossRules = listOf(rule))
        assertTrue(ok.isValid)
    }

    @Test fun crossRuleOnInactiveGroupIsSkipped() {
        val rule = CrossRule("mu", setOf("mu", "w"), "k") { false }
        val r = FormValidator.validate(listOf(spec("w", "1"), spec("mu", "9", group = "g")), emptySet(), listOf(rule))
        assertTrue(r.isValid)
    }
}
