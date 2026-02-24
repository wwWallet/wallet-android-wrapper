package org.siros.wwwallet.bridging

import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Assert
import org.junit.Test

class JSCodeSnippetTest {
    @Test
    fun loadRawStringSnippet() {
        val code =
            JSCodeSnippet(
                """
                window.alert("REPLACEME injected!")
                """.trimIndent(),
                listOf(
                    "REPLACEME" to "foo bar",
                ),
            )

        Assert.assertNotNull(code.code)

        MatcherAssert.assertThat(
            code.code,
            CoreMatchers.not(CoreMatchers.containsString("REPLACEME")),
        )

        MatcherAssert.assertThat(
            code.code,
            CoreMatchers.containsString("foo bar"),
        )
    }
}