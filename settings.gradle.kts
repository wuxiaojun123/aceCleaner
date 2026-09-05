pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven(url = "https://raw.githubusercontent.com/martinloren/AabResGuard/mvn-repo")
        maven(url = "https://raw.githubusercontent.com/MetaCubeX/maven-backup/main/releases")
        maven("https://jfrog.anythinktech.com/artifactory/overseas_sdk")
        maven("https://jfrog.anythinktech.com/artifactory/debugger")
        maven("https://artifact.bytedance.com/repository/pangle")
        maven("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea")
    }
}

rootProject.name = "AceClean"
include(":app")
