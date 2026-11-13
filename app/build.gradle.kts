import java.io.StringReader
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

// Read through a Gradle provider so the configuration cache is invalidated when the file changes.
val localProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText.orNull
        ?.let { load(StringReader(it)) }
}
val tmdbAccessToken = localProperties.getProperty("TMDB_ACCESS_TOKEN").orEmpty().trim()
val useSampleStream = localProperties.getProperty("USE_SAMPLE_STREAM")?.trim().toBoolean()
val webPlayerTvMode = localProperties.getProperty("WEB_PLAYER_TV_MODE")?.trim().toBoolean()

// The release number, "1.2.0": from -PappVersion (the CI passes the Git tag), else the default below.
// The version code follows from it, so that each release installs over the previous one.
val appVersion = providers.gradleProperty("appVersion").orNull?.removePrefix("v") ?: "1.1.0"
val appVersionCode = appVersion.split('.').map { it.toInt() }.let { (major, minor, patch) ->
    major * 10_000 + minor * 100 + patch
}

// The public GitHub repository ("owner/name") whose releases the app offers as updates. Empty: no check.
val updateRepository = (providers.gradleProperty("updateRepository").orNull
    ?: localProperties.getProperty("UPDATE_REPOSITORY")).orEmpty().trim()

// Release signing: the keystore and its passwords stay outside the repository, named in local.properties.
// Without all four values the release build is left unsigned.
val releaseStoreFile = localProperties.getProperty("RELEASE_STORE_FILE")?.trim()?.takeIf { it.isNotEmpty() }
val releaseStorePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")?.takeIf { it.isNotEmpty() }
val releaseKeyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")?.trim()?.takeIf { it.isNotEmpty() }
val releaseKeyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")?.takeIf { it.isNotEmpty() }
val hasReleaseSigning =
    releaseStoreFile != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "dev.codexsoft.codexcine"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.codexsoft.codexcine"
        minSdk = 28
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion

        buildConfigField("String", "TMDB_ACCESS_TOKEN", "\"$tmdbAccessToken\"")
        buildConfigField("boolean", "WEB_PLAYER_TV_MODE", webPlayerTvMode.toString())
        buildConfigField("String", "UPDATE_REPOSITORY", "\"$updateRepository\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                val path = releaseStoreFile.orEmpty()
                storeFile = file(if (path.startsWith("~/")) System.getProperty("user.home") + path.drop(1) else path)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "USE_SAMPLE_STREAM", useSampleStream.toString())
        }
        release {
            optimization {
                enable = true
            }
            buildConfigField("boolean", "USE_SAMPLE_STREAM", "false")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // android.util.Log is called from code under test and is only a stub on the JVM.
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.leanback)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    debugImplementation(libs.okhttp.logging.interceptor)
    implementation(libs.glide)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui.leanback)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.core.splashscreen)

    testImplementation(libs.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.kotlinx.coroutines.test)
}
