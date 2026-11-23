import java.io.IOException
import javax.inject.Inject
import org.gradle.process.ExecOperations

buildscript {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven(url = "https://raw.githubusercontent.com/martinloren/AabResGuard/mvn-repo")
        maven(url = "https://raw.githubusercontent.com/MetaCubeX/maven-backup/main/releases")
    }

    dependencies {
        classpath("com.github.denglongfei:activityGuard:1.3.0")
        classpath("com.github.megatronking.stringfog:gradle-plugin:5.2.0")
        classpath("com.github.megatronking.stringfog:xor:5.0.0")
        classpath(files("gradle/local-plugins/aabResGuard-plugin-1.0.4.3_gp8.jar"))
        classpath(files("gradle/local-plugins/aabresguard-core-1.0.4.2.jar"))
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false
    id("org.jetbrains.kotlin.kapt") version "2.2.21" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
    id("com.google.firebase.crashlytics") version "3.0.8" apply false
}

abstract class CheckStringResources : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val pythonCommand: Property<String>

    @get:Internal
    abstract val sourceDirectory: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun checkResources() {
        val directory = sourceDirectory.get().asFile
        val candidates = pythonCommand.orNull?.let { listOf(listOf(it)) } ?: if (
            System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        ) {
            listOf(listOf("py", "-3"), listOf("python"), listOf("python3"))
        } else {
            listOf(listOf("python3"), listOf("python"))
        }
        val python = candidates.firstOrNull { command ->
            try {
                ProcessBuilder(command + listOf("-c", "import sys; sys.exit(sys.version_info.major != 3)"))
                    .directory(directory)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor() == 0
            } catch (_: IOException) {
                false
            }
        } ?: throw GradleException(
            "No working Python 3 interpreter found (tried ${candidates.joinToString { it.joinToString(" ") }}). " +
                "Install Python 3 and add it to PATH, or set -PpythonCommand to the absolute path of the executable."
        )

        execOperations.exec {
            workingDir(directory)
            commandLine(python + listOf("tools/check_string_resources.py", "--report", "build/reports/localization/strings.json"))
        }
    }
}

val checkStringResources by tasks.registering(CheckStringResources::class) {
    group = "verification"
    description = "Check centralized string resources and hardcoded XML/Kotlin/Java UI text."
    sourceDirectory.set(layout.projectDirectory)
    pythonCommand.set(providers.gradleProperty("pythonCommand"))
}

subprojects {
    tasks.matching { it.name == "preReleaseBuild" }.configureEach {
        dependsOn(checkStringResources)
    }
}
