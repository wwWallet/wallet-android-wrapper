package build

import groovy.json.JsonSlurper
import org.gradle.api.Project
import java.net.URI

fun Project.getLogs(): String {
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

fun Project.getReleaseQuoteAndAuthor(): Pair<String, String> {
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

