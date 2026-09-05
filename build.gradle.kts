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
}
