package com.civileg.app.domain.calculations.utils
import com.civileg.app.domain.entities.SlabDesignResult
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
class HordiAcceptanceCompatibilityTest {
 @Test fun oldPayloadRemainsUnassessedDespiteLegacyGreenNumericFlag() {
  val g=Gson()
  val old="""{"isSafe":true}"""
  val a=g.fromJson(old,SlabDesignResult::class.java)
  val b=g.fromJson(old,CalculatorEngine.SlabResult::class.java)
  assertTrue(a.isSafe);assertTrue(b.isSafe)
  assertFalse(a.engineeringAcceptance);assertFalse(b.engineeringAcceptance)
  assertNull(a.barBondClass);assertNull(b.barBondClass)
  assertFalse(a.serviceabilityAssessed);assertFalse(b.serviceabilityAssessed)
 }
 @Test fun booleansWithoutKnownMaterialNeverQualifyOrPersistDerivedGetter() {
  val g=Gson()
  for (surface in listOf("null","\"UNKNOWN\"","\"UNRECOGNIZED_LEGACY\"")) {
   val data="""{"isSafe":true,"bondClassAssessed":true,"serviceabilityAssessed":true,"barBondClass":$surface,"engineeringAcceptance":true}"""
   val a=g.fromJson(data,SlabDesignResult::class.java)
   val b=g.fromJson(data,CalculatorEngine.SlabResult::class.java)
   assertFalse(a.engineeringAcceptance);assertFalse(b.engineeringAcceptance)
   assertFalse(g.toJsonTree(a).asJsonObject.has("engineeringAcceptance"))
   assertFalse(g.toJsonTree(b).asJsonObject.has("engineeringAcceptance"))
  }
 }
}
