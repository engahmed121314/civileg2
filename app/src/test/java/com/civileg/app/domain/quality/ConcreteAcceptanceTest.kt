package com.civileg.app.domain.quality

import org.junit.Assert.*
import org.junit.Test

class ConcreteAcceptanceTest {
    @Test fun floorBelow35IsFcMinus3_5() = assertEquals(21.5, ConcreteAcceptance.individualFloor(25.0), 1e-9)
    @Test fun floorAbove35IsNinetyPercent() = assertEquals(45.0, ConcreteAcceptance.individualFloor(50.0), 1e-9)

    @Test fun passingSeries() {
        val r = ConcreteAcceptance.evaluate(25.0, listOf(26.0, 25.0, 27.0, 24.0))
        assertEquals(true, r.accepted)   // averages 26.0 and 25.33
    }

    @Test fun movingAverageFailure() {
        val r = ConcreteAcceptance.evaluate(25.0, listOf(24.0, 24.5, 25.0, 24.0))
        assertEquals(false, r.movingAverageOk)
        assertEquals(false, r.accepted)
    }

    @Test fun singleLowTestFailsEvenWithGoodAverage() {
        val r = ConcreteAcceptance.evaluate(25.0, listOf(21.0, 30.0, 30.0))
        assertFalse(r.individualOk)
        assertEquals(false, r.accepted)
    }

    @Test fun lowTestFailsWithFewerThanThree() {
        assertEquals(false, ConcreteAcceptance.evaluate(25.0, listOf(20.0)).accepted)
    }

    @Test fun fewerThanThreeIsNotAssessed() {
        assertNull(ConcreteAcceptance.evaluate(25.0, listOf(26.0, 27.0)).accepted)
    }

    @Test fun frequency() {
        assertEquals(5, ConcreteAcceptance.minimumSamples(100.0, null, 1))
        assertEquals(10, ConcreteAcceptance.minimumSamples(1000.0, null, 3))   // ceil(1000/110)=10
        assertEquals(12, ConcreteAcceptance.minimumSamples(100.0, 5500.0, 3))  // ceil(5500/460)=12
    }

    @Test fun parse() = assertEquals(listOf(26.0, 27.5, 24.0), ConcreteAcceptance.parseSeries("26, 27.5 ; 24 x"))
}
