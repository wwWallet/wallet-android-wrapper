package io.yubicolabs.wwwwallet.bridging

import android.content.Context

class JSCodeSnippet(
    code: String,
    replacements: List<Pair<String, String>> = emptyList(),
) {
    val code = code.replaceAll(replacements)

    companion object {
        fun fromRawResource(
            context: Context,
            resource: String,
            replacements: List<Pair<String, String>>
        ): JSCodeSnippet = JSCodeSnippet(
            String(
                context
                    .assets
                    .open(resource)
                    .readAllBytes()
            ),
            replacements
        )
    }
}

private fun String.replaceAll(replacements: List<Pair<String, String>>): String {
    var result: String = this
    replacements.forEach { (old, new) ->
        result = result.replace(old, new)
    }
    return result
}
