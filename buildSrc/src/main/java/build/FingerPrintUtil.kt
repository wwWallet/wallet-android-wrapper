package build

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.net.URI

data class Target(
    val name: String,
    val shas: List<String>,
    val packageName: String
)

fun Project.getServerTargets(host: String): List<Target> {
    val body = JsonSlurper().parse(URI.create("$host/.well-known/assetlinks.json").toURL())
    val items = (body as? List<*>) ?: emptyList<Any?>()
    return items.mapNotNull { item ->
        if (item as? Map<*, *> != null) {
            val target = item["target"] as? Map<*, *>?
            val shas = (target?.get("sha256_cert_fingerprints") as? List<*>)?.mapNotNull {
                (it as? String)?.replace(":", "")
            }

            val packageName = target?.getOrDefault("package_name", null) as? String

            if (shas == null || packageName == null) {
                null
            } else {
                Target(host, shas, packageName)
            }
        } else {
            null
        }
    }
}

fun Project.getApkTargets(): List<Target> {
    val apksignerFound = runCommand("command apksigner")
    if (apksignerFound.contains("command not found")) {
        throw GradleException("Couldn't find 'apksigner'.")
    }

    val apks = runCommand("find . -iname '*apk' -type f").lines()
    if (apks.isEmpty()) {
        throw GradleException("No apks found, please 'assemble' first.")
    }

    return apks.map { apk ->
        val fingers = runCommand("apksigner verify --print-certs $apk")
            .lines()
            .mapNotNull {
                if (it.contains("SHA-256")) {
                    it.split(":")
                        .last()
                        .trim()
                        .uppercase()
                } else {
                    null
                }
            }

        val packageName = runCommand("aapt dump badging $apk")
            .lines()
            .firstNotNullOfOrNull { line ->
                if ("package: " in line) {
                    line
                        .split(" ")
                        .firstNotNullOfOrNull { kv ->
                            if ("=" in kv) {
                                val (key, value) = kv.split("=")
                                if (key == "name") {
                                    value.replace("'", "")
                                } else {
                                    null
                                }
                            } else {
                                null
                            }
                        }
                } else {
                    null
                }
            }
        Target(
            apk,
            fingers,
            packageName ?: ""
        )
    }
}
