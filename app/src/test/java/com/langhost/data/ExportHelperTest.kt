package com.langhost.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportHelperTest {
    @Test
    fun csvCellEscapesEmbeddedQuotes() {
        assertEquals("\"a\"\"b\"", ExportHelper.escapeCsvCell("a\"b"))
    }

    @Test
    fun csvCellNeutralizesSpreadsheetFormulas() {
        assertEquals("\"'=1+1\"", ExportHelper.escapeCsvCell("=1+1"))
        assertEquals("\"'@SUM(A1)\"", ExportHelper.escapeCsvCell("@SUM(A1)"))
    }

    @Test
    fun csvCellPreservesCommasAndNewlinesInsideQuotedField() {
        assertEquals("\"a,b\\nc\"", ExportHelper.escapeCsvCell("a,b\\nc"))
    }
}
