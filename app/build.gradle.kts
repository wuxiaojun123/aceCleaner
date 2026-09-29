import com.bytedance.android.plugin.extensions.AabResGuardExtension
import com.github.megatronking.stringfog.plugin.StringFogExtension
import com.kotlin.model.ActivityGuardExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

apply(plugin = "activityGuard")
apply(plugin = "stringfog")
apply(plugin = "com.bytedance.android.aabResGuard")
apply(from = "signing.gradle")


extensions.configure<ActivityGuardExtension>("actGuard") {
    isEnable = true
    whiteClassList = hashSetOf(
        "com.activityGuard.model.Bean",
        "com.airbnb.*",
        "com.applovin.*",
        "com.artifex.*",
        "com.bytedance.*",
        "com.isseiaoki.*",
        "com.mbridge.*",
        "com.monetization.*",
        "com.smartdigimkt.*",
        "com.squareup.*",
        "com.thinkup.*",
        "com.tramini.*",
        "com.unity3d.*",
        "com.vungle.*",
        "com.yandex.*",
        "io.appmetrica.*"
    )
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
    whiteList = mutableSetOf(
        "*.R.raw.*",
        "*.R.drawable.icon",
        // FirebaseOptions loads these generated Google Services resources by name at runtime.
        "*.R.string.google_storage_bucket",
        "*.R.string.com.crashlytics.android.build_id",
        "*.R.string.google_app_id",
        "*.R.string.project_id",
        "*.R.drawable.ic_launcher",
        "*.R.mipmap.ic_launcher",
        "*.R.mipmap.icon",
        "*.R.drawable.icon",
        "*.R.string.app_name",

        "*.R.array.Thumbnails*",
        "*.R.drawable.gp*",
        "*.R.raw.click",
        "*.R.raw.background_*",

        //过滤使用ref_前缀资源。
        "*.R.anim.ref_*",
        "*.R.drawable.ref_*",
        "*.R.mipmap.ref_*",
        "*.R.id.ref_*",
        "*.R.string.ref_*",
        "*.R.layout.ref_*",
        "*.R.dimen.ref_*",
        "*.R.style.ref_*",
        "*.R.color.ref_*",

        // for fabric
        "*.R.string.com.crashlytics.*",

        // for google-services
        "*.R.string.google_app_id",
        "*.R.string.gcm_defaultSenderId",
        "*.R.string.default_web_client_id",
        "*.R.string.ga_trackingId",
        "*.R.string.firebase_database_url",
        "*.R.string.google_api_key",
        "*.R.string.google_crash_reporting_api_key",

        // Firebase
        "*.R.string.project_id",

        //mtg
        "*.R.layout.mtg_*",
        "*.R.anim.mbridge_*",
        "*.R.drawable.mbridge_*",
        "*.R.layout.mbridge_*",
        "*.R.string.mbridge_*",
        "*.R.id.mbridge_*",
        "*.R.color.mbridge_*",
        "*.R.dimen.mbridge_*",
        "*.R.style.mbridge_*",
        "*.R.anim.tt_*",
        "*.R.drawable.tt*", //R.drawable.tt_*,R.drawable.ttdownloader_*
        "*.R.layout.tt*", //R.layout.tt_*,R.layout.ttdownloader_*
        "*.R.string.tt_*",
        "*.R.color.tt_*",
        "*.R.dimen.tt_*",
        "*.R.id.tt*",//R.id.tt_browser_webview
        "*.R.integer.tt_*",

        "*.R.string.game_view_content_description",//游戏反射的名称，不能混淆
        "*.R.string.anythink_*",
        "*.R.drawable.anythink_*",
        "*.R.layout.anythink_*",
        "*.R.id.anythink_*",
        "*.R.dimen.anythink_*",
        "*.R.style.anythink_*",
        "*.R.color.anythink_*",
        "*.R.anim.antyhink_*",

        "*.R.string.thinkup_*",
        "*.R.drawable.thinkup_*",
        "*.R.layout.thinkup_*",
        "*.R.id.thinkup_*",
        "*.R.dimen.thinkup_*",
        "*.R.style.thinkup_*",
        "*.R.color.thinkup_*",
        "*.R.anim.thinkup_*",

        // hms
        "*.R.string.hms*",
        "*.R.string.connect_server_fail_prompt_toast",
        "*.R.string.getting_message_fail_prompt_toast",
        "*.R.string.no_available_network_prompt_toast",
        "*.R.string.third_app_*",
        "*.R.string.upsdk_*",
        "*.R.layout.hms*",
        "*.R.layout.upsdk_*",
        "*.R.drawable.upsdk*",
        "*.R.color.upsdk*",
        "*.R.dimen.upsdk*",
        "*.R.style.upsdk*",
        "*.R.string.agc*",
        "*.R.string.com.google.*",
        "*.R.string.com.google.firebase.crashlytics_*",

        "*.R.string.sdm_*",
        "*.R.drawable.sdm_*",
        "*.R.layout.sdm_*",
        "*.R.id.sdm_*",
        "*.R.dimen.sdm_*",
        "*.R.style.sdm_*",
        "*.R.color.sdm_*",
        "*.R.anim.sdm_*",
    )
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

    // review
    implementation(libs.google.review)
    implementation(libs.google.review.ktx)
    // firebase相关
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytic)
    implementation(libs.firebase.crash)
    implementation(libs.firebase.messaging)


}
