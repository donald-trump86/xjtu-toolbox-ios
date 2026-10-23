package com.xjtu.toolbox.agent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentMarkdownTest {
    private fun render(text: String) = inlineMarkdown(text, Color.Blue, Color.Gray, Color.Red, {})

    @Test fun formulaInsideBoldKeepsBothMathAndBold() {
        val result = render("公式 **${'$'}\\frac{1}{2} + x^2${'$'}**")
        assertEquals("公式 1/2 + x²", result.text)
        assertTrue(result.spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.start == 3 && it.end == result.length })
    }

    @Test fun parenthesizedMathInsideBoldItalicRenders() {
        val result = render("***\\(\\alpha \\leq x^2\\)***")
        assertEquals("α ≤ x²", result.text)
        assertTrue(result.spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.item.fontStyle == FontStyle.Italic })
    }

    @Test fun codeAndCurrencyInsideBoldStayLiteral() {
        val code = render("**`${'$'}x^2${'$'}`**")
        assertEquals(" ${'$'}x^2${'$'} ", code.text)
        assertTrue(code.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
        assertEquals("金额 ${'$'}5 和 ${'$'}10", render("**金额 ${'$'}5 和 ${'$'}10**").text)
    }
}
