package build

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.net.URI

fun Project.getServerFingerprints(host: String): List<String> {
    val body = JsonSlurper().parse(URI.create("$host/.well-known/assetlinks.json").toURL())
    val item = (body as? List<*>)?.first() as? Map<*, *>?
    if (item != null) {
        val target = item["target"] as? Map<*, *>?
        val fingerprints = target?.get("sha256_cert_fingerprints") as? List<*>
        if (fingerprints != null) {
            return fingerprints.mapNotNull {
                (it as? String)?.replace(":", "")
            }
        }
    }

    return emptyList()
}

fun Project.getApkFingerprints(): Map<String, String> {
    val apksignerFound = runCommand("command apksigner")
    if (apksignerFound.contains("command not found")) {
        throw GradleException("Couldn't find 'apksigner'.")
    }

    val apks = runCommand("find . -iname '*apk' -type f").lines()
    if (apks.isEmpty()) {
        throw GradleException("No apks found, please 'assemble' first.")
    }

    return apks.associate { apk ->
        apk to (runCommand("apksigner verify --print-certs $apk").lines().mapNotNull {
            if (it.contains("SHA-256")) {
                it.split(":")
                    .last()
                    .trim()
                    .uppercase()
            } else {
                null
            }
        }.joinToString(","))
    }
}
