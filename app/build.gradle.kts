import org.jetbrains.kotlin.gradle.plugin.extraProperties
import org.jetbrains.kotlin.konan.exec.Command
import java.util.Locale

plugins {
    id("com.android.application")
    kotlin("android")

    // Add the Firebase Crashlytics plugin.
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")

    id("spoon")
}

val versionMajor = 2
val versionMinor = 6
val versionPatch = 0
val versionBuild = 0

val dropboxAppKey =
    (project.properties["RELEASE_DROPBOX_APP_KEY"] as String?) ?: "dhjh8ke9wf05948"

fun gitSha(): String {
    return Command("git", "rev-parse", "--short", "HEAD").getOutputLines()[0].trim()
}

android {
    namespace = "org.gnucash.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.gnucash.pocket.pnemonic"
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
//            isTestCoverageEnabled = true
            signingConfig = signingConfigs["debug"]
        }
        release {
//            isMinifyEnabled = true
            proguardFile(getDefaultProguardFile("proguard-android.txt"))
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
            dimension = "stability"
            isDefault = true
            applicationIdSuffix = ".devel"
            versionName =
                "${versionMajor}.${versionMinor}.${versionPatch}.${versionBuild}-${gitSha()}"
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

    compileOptions { //we want switch with strings during xml parsing
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Jetpack
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    implementation("net.objecthunter:exp4j:0.4.7")
    implementation("com.ezylang:EvalEx:3.2.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")

    // Logging
    implementation("com.google.firebase:firebase-crashlytics:19.4.4")
    implementation("com.jakewharton.timber:timber:5.0.1")

    implementation("com.github.nextcloud:android-library:1.0.31")
    implementation("com.squareup:android-times-square:1.6.5")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    implementation("joda-time:joda-time:2.13.0")
    implementation("org.apache.jackrabbit:jackrabbit-webdav:2.13.3")
    implementation("com.code-troopers.betterpickers:library:3.1.0")
    implementation("com.github.techfreak:wizardpager:1.0.3")
    implementation("com.dropbox.core:dropbox-android-sdk:7.0.0")
    implementation("com.kobakei:ratethisapp:0.0.7")

    // Export
    implementation("com.opencsv:opencsv:5.9") {
        exclude(group = "commons-logging", module = "commons-logging")
    }

    // Debug
    debugImplementation("com.facebook.stetho:stetho:1.5.0")

    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.assertj:assertj-core:3.27.3")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")

    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")

    val androidEspressoVersion = "3.6.1"
    androidTestImplementation("androidx.test.espresso:espresso-core:$androidEspressoVersion")
    androidTestImplementation("androidx.test.espresso:espresso-intents:$androidEspressoVersion")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:$androidEspressoVersion")

    androidTestImplementation("org.assertj:assertj-core:3.27.3")

    androidTestImplementation("com.squareup.spoon:spoon-client:1.7.1")
}

afterEvaluate {
    spoon {
        debug = true
        grantAllPermissions = true
        codeCoverage = true
    }

    // Disable Google Services plugin for some flavors.
    android.productFlavors.forEach { flavor ->
        val flavorName = flavor.name.capitalize(Locale.ROOT)
        tasks.matching { task ->
            task.name.contains("GoogleServices") && task.name.contains(flavorName)
        }.forEach { task ->
            task.enabled = flavor.extraProperties["useGoogleGcm"] as Boolean
        }
    }
}