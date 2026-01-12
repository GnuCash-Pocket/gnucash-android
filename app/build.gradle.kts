import org.gnucash.GitCommitValueSource
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.extraProperties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    // Add the Firebase Crashlytics plugins.
    alias(libs.plugins.google.services)
    alias(libs.plugins.crashlytics)
}

val versionMajor = 2
val versionMinor = 13
val versionPatch = 0
val versionBuild = 0

val dropboxAppKey =
    (project.properties["RELEASE_DROPBOX_APP_KEY"] as String?) ?: "dhjh8ke9wf05948"

android {
    namespace = "org.gnucash.android"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "org.gnucash.pocket"
        minSdk = 21
        targetSdk = 36
        versionCode = (((((versionMajor * 100) + versionMinor) * 1000) + versionPatch) * 1000) + versionBuild
        versionName = "${versionMajor}.${versionMinor}.${versionPatch}"
        resValue("string", "app_name", "GnuCash")
        resValue("string", "app_playstore_url", "market://details?id=${applicationId}")
        resValue("string", "app_version_name", "$versionName")
        buildConfigField("boolean", "CAN_REQUEST_RATING", "false")
        buildConfigField("boolean", "GOOGLE_GCM", "false")
        buildConfigField("String", "DROPBOX_APP_KEY", "\"${dropboxAppKey}\"")
        manifestPlaceholders["dropbox_app_key"] = "db-${dropboxAppKey}"

        testInstrumentationRunner = "org.gnucash.android.test.ui.util.GnucashAndroidTestRunner"
    }

    packaging {
        resources {
            excludes += "LICENSE.txt"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.keystore")
        }

        create("release") {
            if (project.hasProperty("RELEASE_STORE_FILE")) {
                storeFile = file(project.properties["RELEASE_STORE_FILE"] as String)
                storePassword = project.properties["RELEASE_STORE_PASSWORD"] as String
                keyAlias = project.properties["RELEASE_KEY_ALIAS"] as String
                keyPassword = project.properties["RELEASE_KEY_PASSWORD"] as String
            } else {
                storeFile = file("../debug.keystore")
            }
        }
    }

    buildTypes {
        //todo re-enable minify and test coverage
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
            signingConfig = signingConfigs["debug"]
        }
        release {
//            isMinifyEnabled = true
            proguardFile(getDefaultProguardFile("proguard-android-optimize.txt"))
            proguardFile("proguard-rules.pro")
            signingConfig = signingConfigs["release"]
        }
    }

    lint {
        abortOnError = false
    }

    flavorDimensions += "stability"

    productFlavors {
        create("development") {
            val gitCommitProvider = providers.of(GitCommitValueSource::class.java) {}
            val gitCommit = gitCommitProvider.get()

            dimension = "stability"
            isDefault = true
            applicationIdSuffix = ".devel"
            versionName =
                "${versionMajor}.${versionMinor}.${versionPatch}.${versionBuild}-$gitCommit"
            resValue("string", "app_name", "GnuCash dev")
            resValue("string", "app_version_name", versionName.toString())

            extraProperties["useGoogleGcm"] = false
        }

        create("beta") {
            dimension = "stability"
            versionName = "${versionMajor}.${versionMinor}.${versionPatch}.${versionBuild}"
            resValue("string", "app_name", "GnuCash beta")
            resValue("string", "app_version_name", versionName.toString())

            buildConfigField("Boolean", "GOOGLE_GCM", "true")
            extraProperties["useGoogleGcm"] = true
        }

        create("production") {
            dimension = "stability"
            buildConfigField("boolean", "CAN_REQUEST_RATING", "true")

            buildConfigField("Boolean", "GOOGLE_GCM", "true")
            extraProperties["useGoogleGcm"] = true
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    bundle {
        language {
            // This is disabled so that the App Bundle does NOT split the APK for each language.
            // We're gonna use the same APK for all languages.
            enableSplit = false
        }
    }

    compileOptions {
        // For older Java 1.8 devices.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_1_8
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Jetpack
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.core)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.work.runtime)

    implementation(libs.evalex)
    implementation(libs.exp4j)
    implementation(libs.flexbox)
    implementation(libs.material)

    // Logging
    implementation(libs.crashlytics)
    implementation(libs.timber)

    implementation(libs.chart)
    implementation(libs.joda.time)
    implementation(libs.betterpickers)
    implementation(libs.times.square)
    implementation(libs.wizardpager)
    implementation(libs.ratethisapp)
    implementation(libs.okhttp)

    // Export
    implementation(libs.apache.http) { // for OwnCloudClient
        exclude(group = "commons-logging", module = "commons-logging")
    }
    implementation(libs.dropbox)
    implementation(libs.nextcloud) {
        // unused in Android and brings wrong Junit version
        exclude(group = "org.ogce", module = "xpp3")
    }
    implementation(libs.opencsv) {
        exclude(group = "commons-logging", module = "commons-logging")
    }

    testImplementation(libs.robolectric)
    testImplementation(libs.junit)
    testImplementation(libs.assertj.core)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.bundles.espresso)
    androidTestImplementation(libs.assertj.core)

    // For older Java 1.8 devices.
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

afterEvaluate {
    // Disable Google Services plugin for some flavors.
    android.productFlavors.forEach { flavor ->
        val flavorName = flavor.name.uppercaseFirstChar()
        tasks.matching { task ->
            task.name.contains("GoogleServices") && task.name.contains(flavorName)
        }.forEach { task ->
            task.enabled = flavor.extraProperties["useGoogleGcm"] as Boolean
        }
    }
}