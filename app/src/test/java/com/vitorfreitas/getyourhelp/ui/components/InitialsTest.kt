package com.vitorfreitas.getyourhelp.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

class InitialsTest {
    @Test
    fun twoWordsGiveTwoInitials() = assertEquals("VF", initialsOf("Vitor Freitas"))

    @Test
    fun extraSpacesAreIgnored() = assertEquals("AS", initialsOf("  ana   silva  "))

    @Test
    fun onlyTheFirstTwoWordsAreUsed() = assertEquals("JM", initialsOf("João Maria Santos"))

    @Test
    fun blankNameGivesAQuestionMark() = assertEquals("?", initialsOf("   "))
}
