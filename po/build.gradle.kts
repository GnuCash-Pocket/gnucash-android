plugins {
    //alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.kotlin.jvm")
}

group = "org.gnucash"
version = "1.0"

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}