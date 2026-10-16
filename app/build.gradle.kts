plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

// --- Version: driven by the git tag in CI, with safe local fallbacks ---------
// CI passes -PappVersionName=1.2.0 -PappVersionCode=42 (derived from the tag and
// the workflow run number). Local builds fall back to 1.0 / 1 so nothing breaks.
val appVersionName = (project.findProperty("appVersionName") as String?) ?: "1.0"
val appVersionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1

// --- Release signing: only wired up when a keystore is provided --------------
// CI decodes the keystore from secrets and sets these properties. When absent
// (e.g. a local debug build or a fork without secrets), release stays unsigned
// so the build still configures cleanly.
val keystoreFile = (project.findProperty("ANDROID_KEYSTORE_FILE") as String?)
    ?: System.getenv("ANDROID_KEYSTORE_FILE")
val keystorePassword = (project.findProperty("ANDROID_KEYSTORE_PASSWORD") as String?)
    ?: System.getenv("ANDROID_KEYSTORE_PASSWORD")
val keyAlias = (project.findProperty("ANDROID_KEY_ALIAS") as String?)
    ?: System.getenv("ANDROID_KEY_ALIAS")
val keyPassword = (project.findProperty("ANDROID_KEY_PASSWORD") as String?)
    ?: System.getenv("ANDROID_KEY_PASSWORD")
val hasSigningConfig = keystoreFile != null && file(keystoreFile).exists()

android {
    namespace = "com.partyoke.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.partyoke.tv"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = file(keystoreFile!!)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Attach the release signing config only when the keystore is present.
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }

    lint {
        // Produce the report but don't fail CI on pre-existing lint issues.
        // Tighten this (abortOnError = true) once the baseline is clean.
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    // Compose for TV
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // Media3 / ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // USB / document access
    implementation(libs.androidx.documentfile)

    // Embedded Ktor server + WebSockets
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Coroutines + serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // QR code
    implementation(libs.zxing.core)

    // Material Components for Material3 themes
    implementation(libs.material)
}

// --- ktlint: Kotlin lint/format checks ---------------------------------------
ktlint {
    android.set(true)
    // Report-only for now: the existing code was never ktlint-checked, so we
    // surface issues without failing CI. Flip to false to make it blocking once
    // `./gradlew ktlintFormat` has cleaned the codebase.
    ignoreFailures.set(true)
    // Skip generated sources (BuildConfig, R, etc.).
    filter {
        exclude { element -> element.file.path.contains("generated/") }
    }
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
}
