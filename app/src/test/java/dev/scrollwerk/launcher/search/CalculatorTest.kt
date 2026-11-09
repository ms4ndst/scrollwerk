package dev.scrollwerk.launcher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    private fun eval(s: String) = Calculator.evaluate(s)

    @Test fun basicArithmeticAndPrecedence() {
        assertEquals("4", eval("2+2"))
        assertEquals("14", eval("2+3*4"))
        assertEquals("20", eval("(2+3)*4"))
        assertEquals("1024", eval("2^10"))
        assertEquals("1", eval("7 % 3"))
        assertEquals("-6", eval("-2*3"))
    }

    @Test fun decimalsAndAlternativeOperators() {
        assertEquals("0.5", eval("1/2"))
        assertEquals("2.5", eval("1,5+1"))
        assertEquals("84", eval("12x7"))
        assertEquals("84", eval("12×7"))
        assertEquals("0.333333333333", eval("1/3"))
    }

    @Test fun nonCalculationsReturnNull() {
        assertNull(eval("firefox"))
        assertNull(eval("42"))      // just a number
        assertNull(eval("-5"))      // no binary operator
        assertNull(eval("2+"))      // incomplete
        assertNull(eval("(2+3"))    // unbalanced
        assertNull(eval("1/0"))     // infinite
        assertNull(eval("2 + abc")) // letters
    }
}
