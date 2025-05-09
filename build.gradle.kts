import build.env
import build.getApkFingerprints
import build.getLogs
import build.getReleaseQuoteAndAuthor
import build.getServerFingerprints

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    `kotlin-dsl` apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
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
