plugins {
    kotlin("jvm") version "2.2.20"
}

group = "io.causalguard"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    testImplementation(kotlin("test"))
}
