import build.env
import build.getApkTargets
import build.getLogs
import build.getReleaseQuoteAndAuthor
import build.getServerTargets

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

tasks.register("checkFingerprints") {
    description = "Check apk's sha256fingerprint is ./well-known files on given host."

    val host = env("WWWALLET_ANDROID_HOST")
    doLast {
        val serverTargets = getServerTargets(host)
        val apkTargets = getApkTargets()

        val foundMatch = apkTargets.firstNotNullOfOrNull { apkTarget ->
            serverTargets.firstNotNullOfOrNull { serverTarget ->
                if (apkTarget.packageName == serverTarget.packageName) {
                    val serverShaFound = serverTarget.shas.firstOrNull { serverSha ->
                        apkTarget.shas.contains(serverSha)
                    }

                    if (serverShaFound != null) {
                        serverTarget
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        }

        if (foundMatch != null) {
            println("✅ Found signatures for all apks(${apkTargets.joinToString(", ") { it.name.split('/').last() }}) with package '${foundMatch.packageName}' on server '$host'.")
        } else {
            throw GradleException(
                "🙅Could not find any matching signarure from host '$host' for\n${
                    apkTargets.joinToString(separator = "\n") {
                        "  ${it.name} with package ${it.packageName} and sha ${it.shas.joinToString()}"
                    }
                }"
            )
        }
    }
}

group = "yubico.labs"
version = findProperty("wallet.versionName")!!
