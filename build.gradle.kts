import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.net.URI

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
}

fun runCommand(command: String): String {
    val output = ByteArrayOutputStream()

    val result: ExecResult? = exec {
        commandLine = listOf("sh", "-c", command)
        standardOutput = output
    }.assertNormalExitValue()

    if (result?.exitValue == 0) {
        return output.toString().lines().filter { it.isNotBlank() }.joinToString("\n")
    }

    throw IllegalStateException("Command '${command}' return exit value: ${result?.exitValue}.")
}

fun getLogs(): String {
    val lastTag = runCommand("git tag --sort=taggerdate | tail -2 | head -1")
    val start = if (lastTag.isBlank()) {
        val first = runCommand("git rev-list HEAD | tail -1")
        first
    } else {
        lastTag
    }
    val end = "HEAD"

    return runCommand("git log $start..$end --format=\"%s\"")
}

fun getReleaseQuoteAndAuthor(): Pair<String, String> {
    val body = JsonSlurper().parse(URI.create("https://zenquotes.io/api/random").toURL())
    var quote = ""
    var author = ""

    val item = (body as? List<*>)?.first() as? Map<*, *>?
    if (item?.contains("q") == true) {
        quote = item["q"] as String? ?: "<parser error>"
    }

    if (item?.contains("a") == true) {
        author = item["a"] as String? ?: "<parser error>"
    }

    return quote to author
}

tasks.register("createReleaseNotes") {
    description = "Create a release log of all recent commits."

    doLast {
        val log = getLogs()

        val (quote, author) = getReleaseQuoteAndAuthor()
        val citation = "<sub>(Inspirational quotes provided by https://zenquotes.io API.)</sub>"

        val releaseNote = "# Release Notes\n\n${
            if (!quote.isBlank()) {
                "> $quote\n> \n> <i>\\- $author</i>\n\n$citation\n"
            } else {
                ""
            }
        }\n## Changes${
            if (log.lines().size > 10) {
                " (abbreviated)"
            } else ""
        }\n${
            log.lines().take(10).joinToString("\n") {
                "* $it"
            }
        }"

        println(releaseNote)
        File(".release-note.md").writeText(releaseNote)
    }
}


group = "yubico.labs"
version = findProperty("wallet.versionName")!!
