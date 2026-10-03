import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    namespace = "app.toil.musicbridge"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    flavorDimensions += "identity"
    productFlavors {
        // Package name whitelisted by OEM media-session features (Dynamic Island, QS media widget).
        create("asNetease") {
            dimension = "identity"
            applicationId = "com.netease.cloudmusic"
        }
        create("asViper") {
            dimension = "identity"
            applicationId = "com.kugou.viper"
        }
        create("asQQ") {
            dimension = "identity"
            applicationId = "com.tencent.qqmusic"
        }
        create("asLuna") {
            dimension = "identity"
            applicationId = "com.luna.music"
        }
        create("asHiby") {
            dimension = "identity"
            applicationId = "com.hiby.music"
        }
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        // Versions are intentionally pinned to the ones the original 1.0 APK shipped with.
        disable += listOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.haze)
    implementation(libs.material.kolor)

    testImplementation(libs.junit)
}
