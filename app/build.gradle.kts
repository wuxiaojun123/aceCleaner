import com.bytedance.android.plugin.extensions.AabResGuardExtension
import com.github.megatronking.stringfog.plugin.StringFogExtension
import com.kotlin.model.ActivityGuardExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
}

apply(plugin = "activityGuard")
apply(plugin = "stringfog")
apply(plugin = "com.bytedance.android.aabResGuard")
apply(from = "signing.gradle")


extensions.configure<ActivityGuardExtension>("actGuard") {
    isEnable = true
    whiteClassList = hashSetOf("com.activityGuard.model.Bean")
    otherClassList = hashSetOf("com.nice.aceclean.*")
    changePackageList = hashSetOf("com.nice.aceclean.*")
    classNameCharPool = "abcdefghijklmnopqrstuvwxyz0123456789"
    dirNameCharPool = "abcdefghijklmnopqrstuvwxyz"
}

extensions.configure<StringFogExtension>("stringfog") {
    enable = true
    debug = false
    implementation = "com.github.megatronking.stringfog.xor.StringFogImpl"
    fogPackages = arrayOf("com.nice.aceclean")
}

extensions.configure<AabResGuardExtension>("aabResGuard") {
    val releaseSigningConfig = android.signingConfigs.getByName("release")

    enableObfuscate = true
    mappingFile = file("aabresguard-mapping.txt").toPath()
    whiteList = mutableSetOf("*.R.raw.*", "*.R.drawable.icon")
    obfuscatedBundleFileName = "ace-clean-obfuscated.aab"
    mergeDuplicatedRes = true
    enableFilterFiles = true
    filterList = mutableSetOf("META-INF/*")
    enableFilterStrings = false
    signFilepath = releaseSigningConfig.storeFile?.absolutePath.orEmpty()
    signPwd = releaseSigningConfig.storePassword.orEmpty()
    signAlias = releaseSigningConfig.keyAlias.orEmpty()
}

android {
    namespace = "com.nice.aceclean"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nice.aceclean"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.airbnb.android:lottie:6.5.2")
    implementation("com.github.megatronking.stringfog:xor:5.0.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
