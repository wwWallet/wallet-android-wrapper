package build

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.process.ExecResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

fun Project.env(name: String): String {
    val variable = System.getenv(name)

    if (variable.isNullOrBlank()) {
        throw GradleException("Environment variable '$name' not set or blank. Check build settings.")
    } else {
        return variable
    }
}

fun Project.fileFromEnv(project: Project, envName: String, fileName: String): File {
    val envVar = env(envName)
    val bytes = Base64.getDecoder().decode(envVar)
    val file = project.rootProject.file(fileName)
    file.createNewFile()
    file.writeBytes(bytes)
    return file
}

fun Project.runCommand(command: String): String {
    val output = ByteArrayOutputStream()

    val result: ExecResult? = exec(Action {
        commandLine = listOf("sh", "-c", command)
        standardOutput = output
    }).assertNormalExitValue()

    if (result?.exitValue == 0) {
        return output.toString().lines().filter { it.isNotBlank() }.joinToString("\n")
    }

    throw IllegalStateException("Command '${command}' return exit value: ${result?.exitValue}.")
}
