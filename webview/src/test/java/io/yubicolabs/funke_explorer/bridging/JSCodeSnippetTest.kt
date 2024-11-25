package io.yubicolabs.funke_explorer.bridging

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertNotNull
import org.junit.Test

class ParsingTest {
    @Test
    fun loadRawStringSnippet() {
        val code = JSCodeSnippet(
            """
                window.alert("REPLACEME injected!")
            """.trimIndent(), listOf(
                "REPLACEME" to "foo bar"
            )
        )

        assertNotNull(code.code)

        assertThat(
            code.code, not(containsString("REPLACEME"))
        )

        assertThat(
            code.code, containsString("foo bar")
        )
    }
}