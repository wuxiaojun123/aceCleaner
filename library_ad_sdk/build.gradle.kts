plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.nice.library_ad_sdk"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(libs.androidx.core.ktx)
    api(libs.androidx.fragment.ktx)
    api(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okio)

    implementation(libs.topon.core)
    implementation(libs.topon.adapter.admob)
    implementation(libs.topon.adapter.sdm)
    implementation(libs.topon.adapter.vungle)
    implementation(libs.topon.adapter.unityads)
    implementation(libs.topon.adapter.pangle)
    implementation(libs.topon.adapter.applovin)
    implementation(libs.topon.adapter.mintegral)
    implementation(libs.topon.adapter.yandex)
    implementation(libs.topon.tramini.plugin)

    implementation(libs.admob)
    implementation(libs.google.play.services.basement)
    implementation(libs.google.play.services.ads.identifier)
    implementation(libs.google.ump)
    implementation(libs.vungle.ads)
    implementation(libs.unity.ads)
    implementation(libs.pangle.sdk)
    implementation(libs.smartdigimkttech.sdk)
    implementation(libs.applovin.sdk)
    implementation(libs.mintegral.sdk)
    implementation(libs.yandex.mobileads)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytic)
    implementation(libs.firebase.config)

    //
//    implementation("com.google.ads.mediation:vungle:7.7.8.0")
    implementation("com.google.ads.mediation:mintegral:17.1.81.0")
    implementation("com.google.ads.mediation:pangle:8.2.0.4.0")
    implementation("com.google.ads.mediation:applovin:13.6.4.1")
    implementation("com.google.ads.mediation:facebook:6.22.0.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
