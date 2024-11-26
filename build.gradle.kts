import java.io.ByteArrayOutputStream

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
}


data class ShResult(
    val output: String,
    val error: String,
    val exitCode: Int,
)

fun sh(
    dir: String?,
    vararg args: String
): ShResult {
    val out = ByteArrayOutputStream()
    val err = ByteArrayOutputStream()

    val result = exec {
        if (dir != null) {
            workingDir = File(dir)
        }
        standardOutput = out
        isIgnoreExitValue = true
        commandLine(*args)
    }

    return ShResult(
        String(out.toByteArray()).trim(),
        String(err.toByteArray()).trim(),
        result.exitValue
    )
}

/**
 * yarn build -> Creates build folder
 * yarn add @capacitor/core @capacitor/cli @capacitor/android adds the dependencies for the react to react native bridge code
 * npx cap init -> inits capacitor
 * npx cap add android -> adds android build to capacitor (webview abstraction + js bridge)
 * (update gradle -> local java versions)
 * mv -f webview/app/src/main/assets/* webview/src/main/assets
 * mv -f webview/app/src/main/res/xml/* webview/src/main/res/xml
 * npx cap copy  -> copies the web updates to the cap project
 * npx cap run or
 */
tasks.register<DefaultTask>("updateWalletFrontend") {
    println("Checking yarn installation")
    val (yarnPath, _) = sh(dir = null, "which", "yarn")
    val (npmPath, _) = sh(dir = null, "which", "npm")
    val (npxPath, _) = sh(dir = null, "which", "npx")

    val install = sh("funke-wallet-frontend", npmPath, "install", "--force")
        if (install.exitCode != 0) {
        System.err.println("Couldn't update wallet frontend.")
        System.err.println(install.error)
    } else {
        // BUILD WEB!
        val (build, _) = sh("funke-wallet-frontend", yarnPath, "build")
        println(build)

        // install capacitor
        val (capacitor, _) = sh("", npxPath, "npm", "install", "-D", "typescript")
        println(capacitor)
    }

}

group = "yubico.labs"
version = "0.0.7"
